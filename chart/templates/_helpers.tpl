{{- define "urlshortener.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "urlshortener.fullname" -}}
{{- default .Release.Name .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "urlshortener.labels" -}}
app.kubernetes.io/name: {{ include "urlshortener.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "urlshortener.image" -}}
{{- $registry := .root.Values.image.registry -}}
{{- if $registry -}}
{{ $registry }}/{{ .img.repository }}:{{ .img.tag }}
{{- else -}}
{{ .img.repository }}:{{ .img.tag }}
{{- end -}}
{{- end -}}

{{/*
Derive the per-pod HikariCP pool size from the database connection budget.

    pool_per_pod = (postgres.maxConnections - reservedConnections) / hpa.maxReplicas

This is the invariant that was previously violated: a pool of 50 across a
maxReplicas of 15 demands 750 connections against a max_connections of 200,
so the pool exhausts the database at 4 pods. Computing it here means the two
values cannot drift apart again. Set config.hikariMaxPoolSize explicitly to
override (e.g. when PgBouncer fronts the database).
*/}}
{{- define "urlshortener.hikariPoolSize" -}}
{{- if .Values.config.hikariMaxPoolSize -}}
{{ .Values.config.hikariMaxPoolSize }}
{{- else -}}
{{- $budget := int (sub .Values.postgres.maxConnections .Values.postgres.reservedConnections) -}}
{{- $maxPods := int (ternary .Values.backend.hpa.maxReplicas .Values.backend.replicas .Values.backend.hpa.enabled) -}}
{{- $pool := div $budget $maxPods -}}
{{- if lt $pool 2 -}}
{{- fail (printf "Computed HikariCP pool size is %d, which is unusable. Raise postgres.maxConnections (currently %d) or lower backend.hpa.maxReplicas (currently %d)." $pool (int .Values.postgres.maxConnections) $maxPods) -}}
{{- end -}}
{{ $pool }}
{{- end -}}
{{- end -}}

{{- define "urlshortener.requireSecret" -}}
{{- $v := index .Values.secrets .key -}}
{{- if not $v -}}
{{- fail (printf "secrets.%s must be set. Provide it via -f values-<env>.yaml or --set secrets.%s=..." .key .key) -}}
{{- end -}}
{{ $v }}
{{- end -}}

{{/*
Concurrency ceiling for database-bound paths, as a multiple of the connection pool.

Twice the pool: enough that a short burst waits at the pool rather than being rejected,
few enough that a sustained overload sheds immediately instead of parking Tomcat threads
on a 10s connection timeout. Derived rather than configured so it cannot drift from the
pool the way the pool once drifted from max_connections.
*/}}
{{- define "urlshortener.dbBulkhead" -}}
{{- if .Values.config.dbBulkheadMaxConcurrent -}}
{{ .Values.config.dbBulkheadMaxConcurrent }}
{{- else -}}
{{ mul (int (include "urlshortener.hikariPoolSize" .)) 2 }}
{{- end -}}
{{- end -}}

{{/*
Environment shared by the backend Deployment and every one-shot task pod, so a CronJob can
never end up talking to a different database than the service it maintains.
*/}}
{{- define "urlshortener.secretEnv" -}}
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "urlshortener.fullname" . }}-secret
      key: POSTGRES_USER
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "urlshortener.fullname" . }}-secret
      key: POSTGRES_PASSWORD
- name: JWT_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ include "urlshortener.fullname" . }}-secret
      key: JWT_SECRET
{{- if .Values.secrets.rateLimitBypassSecret }}
- name: RATE_LIMIT_BYPASS_SECRET
  valueFrom:
    secretKeyRef:
      name: {{ include "urlshortener.fullname" . }}-secret
      key: RATE_LIMIT_BYPASS_SECRET
{{- end }}
{{- end -}}
