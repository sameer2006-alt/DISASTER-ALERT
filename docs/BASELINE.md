# Baseline Environment & Build Status

**Date**: 2026-09-30  
**Branch**: `fix/pipeline`  

---

## 1. Tool & Runtime Versions Detected

| Tool / Runtime | Version Found | Notes |
| :--- | :--- | :--- |
| **Java** | `17.0.20.1` | OpenJDK Temurin-17.0.20.1+1 (64-Bit Server VM) |
| **Maven** | `3.9.9` | Installed at `C:\dev\tools\apache-maven-3.9.9` |
| **Node.js** | `v24.21.0` | |
| **npm** | `11.19.0` | Notice: npm suggests update to 12.2.0 |
| **Python** | `3.14.7` | Windows amd64 |
| **Docker** | `29.8.1` | build `4a63305` |

---

## 2. Build Verification Results

| Component | Command | Result | Details / Output |
| :--- | :--- | :--- | :--- |
| **Backend** (`disaster-management-system/backend`) | `mvn -q -DskipTests package` | **PASS** | Successfully generated `target/disaster-backend-1.0.0.jar` (~34 MB). |
| **Frontend** (`disaster-management-system/frontend`) | `npm ci` | **PASS** | Installed 238 packages. Deprecation warning for `leaflet-markercluster@0.2.2`. 14 vulnerabilities reported (2 low, 5 moderate, 6 high, 1 critical). |
| **Frontend** (`disaster-management-system/frontend`) | `npm run build` | **PASS** | Vite production build succeeded in 21.4s. Generated `dist/index.html` and assets. |
| **Python Service** (`python-service`) | `pip install -r requirements.txt` | **PASS** | Successfully installed `fastapi`, `uvicorn`, `pymongo`, `dnspython`, `annotated-doc`, `opentelemetry-api`. `pydantic` & `requests` already satisfied. |
| **GEE Service** (`disaster-management-system/gee-service`) | `pip install -r requirements.txt` | **PASS / WARNING** | Installed `earthengine-api-1.7.46`, but triggered pip dependency resolver conflict on `protobuf` (see Error Log below). |
| **Docker Compose** (`disaster-management-system`) | `docker compose config` | **PASS** | Syntax validation of `docker-compose.yml` passed. |

---

## 3. Errors, Warnings & Issues Encountered

### A. GEE Service Dependency Conflict (Pip Resolver Error)
When running `pip install -r requirements.txt` in `disaster-management-system/gee-service`, pip upgraded `protobuf` to `7.36.2`, breaking existing packages:
```text
ERROR: pip's dependency resolver does not currently take into account all the packages that are installed. This behaviour is the source of the following dependency conflicts.
google-ai-generativelanguage 0.6.15 requires protobuf!=4.21.0,!=4.21.1,!=4.21.2,!=4.21.3,!=4.21.4,!=4.21.5,<6.0.0dev,>=3.20.2, but you have protobuf 7.36.2 which is incompatible.
grpcio-status 1.71.2 requires protobuf<6.0dev,>=5.26.1, but you have protobuf 7.36.2 which is incompatible.
Successfully installed earthengine-api-1.7.46 google-api-core-2.40.0 google-auth-2.59.1 google-auth-httplib2-0.4.3 google-cloud-core-2.8.0 google-cloud-storage-3.15.1 google-crc32c-1.9.0 google-resumable-media-2.11.0 httplib2-0.32.0 protobuf-7.36.2
```

### B. Missing `python-service/Dockerfile`
`disaster-management-system/docker-compose.yml` defines the `python-nlp` service with:
```yaml
python-nlp:
  build: ../python-service
```
However, `python-service/Dockerfile` does NOT exist in the repository (`Test-Path python-service/Dockerfile` returned `False`). Any `docker compose build` will fail when attempting to build this container.

### C. Missing Automated Tests
- `disaster-management-system/backend`: No `src/test` directory exists.
- `disaster-management-system/frontend`: No test runner (Vitest/Jest) configured in `package.json`.
- `python-service`: No test files or test framework configured.

---

## 4. Port Configuration Matrix & Mismatch Analysis

The table below catalogs every port configured across the application's configuration files, containers, and client scripts.

| Service / Component | Configuration File | Parameter / Setting | Port / URL Configured | Conflict / Risk Description |
| :--- | :--- | :--- | :--- | :--- |
| **Backend (Spring Boot)** | `backend/src/main/resources/application.yml` | `server.port` | `${SERVER_PORT:8081}` (Default: **8081**) | Spring Boot binds to 8081 by default when `SERVER_PORT` is unset. |
| **Backend (MongoDB)** | `backend/src/main/resources/application.yml` | `spring.data.mongodb.uri` | `mongodb://localhost:27017/disaster_db` (Port **27017**) | Standard MongoDB port. |
| **Backend (SMTP Mail)** | `backend/src/main/resources/application.yml` | `spring.mail.port` | `${MAIL_PORT:587}` (Default: **587**) | Standard TLS SMTP port. |
| **Backend (CORS)** | `backend/src/main/resources/application.yml` | `app.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}` | Allows Vite dev server (`5173`) and React alternate (`3000`). |
| **Backend (Dockerfile)** | `backend/Dockerfile` | `EXPOSE` | **8080** | **MISMATCH**: Dockerfile exposes `8080`, but Spring Boot defaults to `8081`. |
| **Docker Compose: Backend** | `disaster-management-system/docker-compose.yml` | `ports` | `"8080:8080"` | **CRITICAL MISMATCH**: Exposes container 8080 to host 8080, but Spring Boot inside container starts on 8081 (since `SERVER_PORT` is not set in docker-compose). Container port 8080 will refuse connections. |
| **Docker Compose: Backend CORS** | `disaster-management-system/docker-compose.yml` | `environment.CORS_ORIGINS` | `http://localhost:5173,http://localhost:80,http://localhost` | **MISMATCH**: Environment variable name in `application.yml` is `CORS_ALLOWED_ORIGINS`, not `CORS_ORIGINS`. |
| **Docker Compose: MongoDB** | `disaster-management-system/docker-compose.yml` | `ports` | `"27017:27017"` | Maps host 27017 to container 27017. |
| **Docker Compose: Python NLP** | `disaster-management-system/docker-compose.yml` | `ports` | `"8000:8000"` | Maps host 8000 to container 8000. |
| **Docker Compose: Python Ingest Target**| `disaster-management-system/docker-compose.yml` | `environment.BACKEND_INGEST_URL` | `http://backend:8080/api/events/ingest` | **MISMATCH**: Points to `backend:8080`. If backend listens on 8081, internal communication fails. |
| **Docker Compose: Frontend** | `disaster-management-system/docker-compose.yml` | `ports` | `"80:80"` | Maps host 80 to container 80 (nginx). |
| **Nginx (Frontend Container)** | `frontend/nginx.conf` | `listen` | **80** | Nginx listens on container port 80. |
| **Nginx API Proxy** | `frontend/nginx.conf` | `location /api/` | `proxy_pass http://backend:8081/api/;` | Proxies API to `backend:8081`. Matches backend default 8081, but conflicts with docker-compose `8080:8080`. |
| **Nginx WebSocket Proxy** | `frontend/nginx.conf` | `location /ws/` | `proxy_pass http://backend:8081/ws/;` | Proxies WebSocket to `backend:8081`. Matches backend default 8081. |
| **Frontend .env.example** | `frontend/.env.example` | `VITE_API_URL` | `http://localhost:8080` | **MISMATCH**: Uses port 8080, while backend default is 8081. |
| **Frontend .env.example** | `frontend/.env.example` | `VITE_WS_URL` | `http://localhost:8080` | **MISMATCH**: Uses port 8080, while backend default is 8081. |
| **Frontend API Client** | `frontend/src/lib/api.js` | `API_URL` fallback | `http://localhost:8081` | Defaults to 8081 if `VITE_API_URL` is omitted. |
| **Frontend WebSocket Client** | `frontend/src/lib/websocket.js` | `WS_URL` fallback | `http://localhost:8081/ws` | **BUG & MISMATCH**: Fallback is `http://localhost:8081/ws`, but line 8 creates `new SockJS(`${WS_URL}/ws`)`, resulting in `http://localhost:8081/ws/ws`! |
| **Python Poller Service** | `python-service/sachet_poller.py` | `SPRING_BOOT_INGEST_URL` fallback | `http://localhost:8081/api/events/ingest` | Defaults to port 8081 (conflicts with docker-compose's `backend:8080`). |
| **Python Poller MongoDB** | `python-service/sachet_poller.py` | `MONGODB_URI` fallback | `mongodb://localhost:27017/disaster_db` | Port 27017. |
| **GEE Service** | `gee-service/Dockerfile`, `app.py` | `EXPOSE` / `uvicorn` | **5050** | Runs standalone on 5050; not integrated into docker-compose.yml. Backend `application.yml` has empty `app.gee.service-url: ""`. |
