# Industry-Grade URL Shortener Platform

An advanced, full-stack URL shortening platform built for performance, security, and scalability. This project features a robust Spring Boot backend handling analytics and redirection, coupled with a fast, modern React frontend.

## 📸 Application Showcase

### Demo Video
*(Add your demo video here: `![Demo Video](./media/demo.gif)`)*

### Screenshots

**Frontend Application**
![App Screenshot](./assets/app.png)


**Swagger UI**
![Swagger Screenshot](./assets/swagger.png)

**Architecture Diagram**
![Architecture Diagram](./assets/architecture.png)

## ✨ Features
- **Secure Authentication:** JWT-based stateless authentication.
- **Advanced Analytics:** Track clicks, geographical locations, and User-Agent data for deep insights.
- **Robust Rate Limiting:** Prevent abuse with Redis-backed rate limiting per IP/user.
- **High Performance Redirection:** Caching layer via Redis for ultra-fast link resolutions.
- **API Documentation:** Interactive Swagger UI documentation via OpenAPI 3.
- **Modern UI:** Responsive, accessible, and stunning frontend built with React, Vite, and Mantine.
- **Production Ready:** Database migrations with Flyway, Dockerized infrastructure, and PostgreSQL.

## 🏗️ Architecture

```
                       ┌────────────────────────┐
                       │   Client / Web Browser │
                       └───────────┬────────────┘
                                   │
                                   ▼
                       ┌────────────────────────┐
                       │  Nginx / Ingress LB    │
                       └───────────┬────────────┘
                                   │
                                   ▼
                   ┌────────────────────────────────┐
                   │  Kubernetes Service / HPA      │
                   │  (Scales 3 - 15 Backend Pods)   │
                   └───────────────┬────────────────┘
                                   │
         ┌─────────────────────────┴─────────────────────────┐
         ▼                                                   ▼
┌─────────────────┐                                 ┌─────────────────┐
│ Spring Boot Pod │                                 │ Spring Boot Pod │ ...
└────────┬────────┘                                 └────────┬────────┘
         │                                                   │
         ├─────────────────────────┬─────────────────────────┤
         ▼                         ▼                         ▼
┌───────────────────┐    ┌───────────────────┐
│ PostgreSQL DB     │    │ Valkey (Redis)    │
│ (Persistent Data) │    │ (Caching & Limits)│
└───────────────────┘    └───────────────────┘
```

## 🛠️ Tech Stack

### Backend
- **Framework:** Java 21 & Spring Boot 3
- **Database:** PostgreSQL (with Flyway Migrations)
- **Caching & Rate Limiting:** Valkey (High-performance Redis fork)
- **Security:** Spring Security & JWT
- **Analytics:** MaxMind GeoIP2, UA-Parser

### Frontend
- **Framework:** React 19 & Vite
- **Language:** TypeScript
- **State & Data Fetching:** React Query, Axios
- **Styling:** Custom Vanilla CSS with dynamic Skeleton Loaders (No heavy component libraries for maximum performance)
- **Forms & Validation:** React Hook Form, Zod

### Infrastructure & Scaling
- **Containerization:** Docker
- **Orchestration:** Kubernetes (with Horizontal Pod Autoscaling manifests)
- **Load Balancing:** Kubernetes Ingress (Nginx)
- **Load Testing:** k6 (Tested up to 10,000+ RPS)

## 🚀 Getting Started

### Prerequisites
- Java 21
- Node.js 18+
- Docker & Kubernetes (e.g., Minikube, Docker Desktop with K8s enabled)
- `kubectl` CLI

### 1. Clone the repository
```bash
git clone https://github.com/Sarcastic-Soul/springboot-url-shortner.git
cd url_shortner
```

### 2. Environment Variables
Copy `.env.example` to `.env` in the root directory (if available) or create a `.env` file with your database and JWT secrets.

### 3. Start the Entire Platform
The entire platform is designed to run on a Kubernetes cluster, including a horizontally scaled backend (via HPA), a React frontend, PostgreSQL, and Valkey (Redis).

Ensure you have a Kubernetes cluster running (e.g., Minikube) and `kubectl` configured. If using Minikube, enable the Ingress and Metrics Server addons:
```bash
minikube addons enable ingress
minikube addons enable metrics-server
```

Apply the Kubernetes manifests:
```bash
kubectl apply -f k8s/
```

### 4. Access the Application
If you have an Ingress controller running, you can access the platform at:
- **Frontend (Web App):** [http://localhost](http://localhost) (or via your Ingress IP)
- **Backend APIs:** [http://localhost/api](http://localhost/api)
- **Swagger UI:** [http://localhost/swagger-ui/index.html](http://localhost/swagger-ui/index.html)

Alternatively, if using NodePorts on Minikube:
- **Frontend:** `minikube service url-shortener-frontend-service --url`
- **Backend API:** `minikube service url-shortener-backend-service --url`

## 📂 Project Structure
- `/backend`: Spring Boot application containing all business logic, REST APIs, and database migrations.
- `/frontend`: React SPA with routing and UI components.
- `/k8s`: Production Kubernetes deployment manifests (Deployments, Services, ConfigMaps, Secrets, Ingress, and HPA).
- `/load_tests`: k6 performance testing scripts (Spike test & Soak test).
- `load_test.js`: k6 standard load test script.

## 📖 API Documentation (Swagger)
Once the backend is running, you can explore and test the REST APIs using the interactive Swagger UI interface provided by OpenAPI:
- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **API Docs (JSON):** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)


## 🧪 Testing

### Backend Unit Tests
The backend is equipped with unit tests built using JUnit 5 and Mockito. To run the test suite:
```bash
cd backend
./mvnw test
```

### Performance & Reliability Testing (k6)
The project includes three specialized performance test suites in `k6` to test horizontal scalability, traffic bursts, and endurance:

1. **Standard Load Test** (staged throughput ramping):
   ```bash
   k6 run load_tests/load_test.js
   ```
2. **Spike Test** (sudden 4,000 VU traffic bursts & HPA recovery):
   ```bash
   k6 run load_tests/spike_test.js
   ```
3. **Soak / Endurance Test** (30+ minute steady high throughput):
   ```bash
   k6 run load_tests/soak_test.js
   ```

*Note: Pass `API_URL` when testing against Kubernetes (e.g., `API_URL=http://$(minikube ip):30080 k6 run load_test.js`).*


## 📜 License
This project is licensed under the MIT License.
