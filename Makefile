# Three ways to run this project. See ENVIRONMENTS.md for the full story.
#
#   dev   -- deps in Docker, app on host, fast iteration
#   prod  -- Helm chart onto a real cluster
#   test  -- same chart in KinD, driven by k6

HELM    ?= helm
RELEASE ?= urlshortener
CHART   ?= ./chart

# Must match secrets.rateLimitBypassSecret in chart/values-loadtest.yaml, or the generator is
# rate limited like any other single-IP flood and the run measures the limiter.
LOADTEST_BYPASS ?= ci_loadgen_bypass_secret
API_URL         ?= http://localhost:8080

.DEFAULT_GOAL := help
.PHONY: help dev-up dev-down dev-reset dev-backend dev-frontend backend-jar backend-test \
        images chart-lint chart-render test-up test-gate test-k6 test-resilience test-down \
        prod-deploy prod-diff prod-status db-connections obs-deps obs-deploy obs-down

help:
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------- dev --------

dev-up: ## Start local Postgres + Valkey (app runs on your host)
	docker compose -f dev/docker-compose.dev.yml up -d
	@echo "postgres :5432  valkey :6379   -- now run 'make dev-backend' and 'make dev-frontend'"

dev-down: ## Stop local dependencies (keeps data)
	docker compose -f dev/docker-compose.dev.yml down

dev-reset: ## Stop local dependencies AND delete all data
	docker compose -f dev/docker-compose.dev.yml down -v

dev-backend: ## Run the backend on your host (profile: local)
	cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

dev-frontend: ## Run the Vite dev server on your host
	cd frontend && npm run dev

# --------------------------------------------------------------- build ------

backend-jar: ## Package the backend jar (the image is built FROM this artifact)
	cd backend && ./mvnw -B clean package -DskipTests

backend-test: ## Unit tests. Add RUN_INTEGRATION_TESTS=true (with make dev-up) for the context test
	cd backend && ./mvnw -B test

images: backend-jar ## Build both production Docker images
	docker build -t url-shortener-backend:latest ./backend
	docker build -t url-shortener-frontend:latest ./frontend

# --------------------------------------------------------------- chart ------

chart-lint: ## Lint the chart against all three environments
	$(HELM) lint $(CHART) -f chart/values-dev.yaml
	$(HELM) lint $(CHART) -f chart/values-loadtest.yaml
	$(HELM) lint $(CHART) -f chart/values-prod.yaml \
		--set secrets.postgresUser=ci --set secrets.postgresPassword=ci --set secrets.jwtSecret=ci

chart-render: ## Render manifests for one env: make chart-render ENV=prod
	@test -n "$(ENV)" || (echo "usage: make chart-render ENV=dev|prod|loadtest" && exit 1)
	$(HELM) template $(RELEASE) $(CHART) -f chart/values-$(ENV).yaml \
		--set secrets.postgresUser=x --set secrets.postgresPassword=x --set secrets.jwtSecret=x

# ---------------------------------------------------------------- test ------

test-up: images ## Create KinD cluster and deploy the loadtest overlay
	kind create cluster --name kind || true
	kind load docker-image url-shortener-backend:latest --name kind
	kind load docker-image url-shortener-frontend:latest --name kind
	kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
	kubectl patch deployment metrics-server -n kube-system --type=json \
		-p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
	kubectl rollout status deployment metrics-server -n kube-system --timeout=120s
	$(HELM) upgrade --install $(RELEASE) $(CHART) -f chart/values-loadtest.yaml --wait --timeout 10m

test-gate: ## Tier A -- the fast PR gate (~2.5 min)
	@$(MAKE) --no-print-directory k6 SUITE=gate_test

test-k6: ## Tier B -- capacity suites: load, spike, soak
	@for suite in load_test spike_test soak_test; do \
		$(MAKE) --no-print-directory k6 SUITE=$$suite || true; \
	done

test-resilience: ## Tier C -- attack traffic vs legitimate users
	@$(MAKE) --no-print-directory k6 SUITE=resilience_test

k6:
	@test -n "$(SUITE)" || (echo "usage: make k6 SUITE=load_test" && exit 1)
	@echo "port-forwarding svc/$(RELEASE)-backend -> localhost:8080"
	@pkill -f "port-forward svc/$(RELEASE)-backend" 2>/dev/null || true
	@kubectl port-forward svc/$(RELEASE)-backend 8080:8080 >/dev/null 2>&1 & \
	sleep 3; \
	echo "=== $(SUITE) ==="; \
	API_URL=$(API_URL) RATE_LIMIT_BYPASS_SECRET=$(LOADTEST_BYPASS) \
		k6 run --summary-export=k6-summary-$(SUITE).json load_tests/$(SUITE).js; \
	status=$$?; \
	pkill -f "port-forward svc/$(RELEASE)-backend" 2>/dev/null || true; \
	exit $$status

test-down: ## Destroy the KinD cluster
	kind delete cluster --name kind

# ------------------------------------------------------- observability ------

obs-deps: ## Fetch the upstream charts for chart/observability (needs network)
	$(HELM) repo add prometheus-community https://prometheus-community.github.io/helm-charts
	$(HELM) repo add grafana https://grafana.github.io/helm-charts
	$(HELM) repo update
	$(HELM) dependency build chart/observability

obs-deploy: obs-deps ## Install Prometheus + Grafana + Loki, then point the app at them
	$(HELM) upgrade --install observability chart/observability \
		-n monitoring --create-namespace --wait --timeout 15m
	@echo "Enabling the app's ServiceMonitor and alert rules now that the CRDs exist"
	$(HELM) upgrade $(RELEASE) $(CHART) --reuse-values \
		--set metrics.serviceMonitor.enabled=true \
		--set metrics.prometheusRule.enabled=true
	@echo
	@echo "Grafana:  kubectl -n monitoring port-forward svc/observability-grafana 3000:80"
	@echo "  user admin, password: kubectl -n monitoring get secret observability-grafana -o jsonpath='{.data.admin-password}' | base64 -d"

obs-down: ## Remove the monitoring stack
	$(HELM) uninstall observability -n monitoring || true

# ---------------------------------------------------------------- prod ------

prod-deploy: ## Deploy to the current kubectl context (needs POSTGRES_PASSWORD, JWT_SECRET)
	@test -n "$$POSTGRES_PASSWORD" || (echo "POSTGRES_PASSWORD is required" && exit 1)
	@test -n "$$JWT_SECRET"        || (echo "JWT_SECRET is required" && exit 1)
	$(HELM) upgrade --install $(RELEASE) $(CHART) \
		-f chart/values-prod.yaml \
		--set secrets.postgresPassword="$$POSTGRES_PASSWORD" \
		--set secrets.jwtSecret="$$JWT_SECRET" \
		--wait --timeout 10m

prod-diff: ## Show what a deploy would change (needs the helm-diff plugin)
	$(HELM) diff upgrade $(RELEASE) $(CHART) -f chart/values-prod.yaml \
		--set secrets.postgresPassword="$$POSTGRES_PASSWORD" --set secrets.jwtSecret="$$JWT_SECRET"

prod-status: ## Show rollout, HPA and pod status
	kubectl get deploy,hpa,cronjob,pods -l app.kubernetes.io/instance=$(RELEASE)

db-connections: ## Verify the pool/max_connections invariant holds live
	kubectl exec $(RELEASE)-postgres-0 -- psql -U postgres -d url_shortener \
		-c "SELECT count(*) AS in_use, (SELECT setting FROM pg_settings WHERE name='max_connections') AS max FROM pg_stat_activity;"
