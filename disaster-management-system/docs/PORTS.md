# System Port Mapping & Service Architecture

**Status**: Unified on Port **8080**  
**Updated**: 2026-10-03  

All services across the platform are aligned to use port **8080** for the Spring Boot backend API and WebSocket endpoints.

---

## 1. Unified Service Port Allocations

| Service | Protocol | Local / Host Port | Container Port | Configuration Sources | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Backend API & Actuator** | HTTP / REST | `8080` | `8080` | `application.yml` (`SERVER_PORT: 8080`), `Dockerfile` (`EXPOSE 8080`), `docker-compose.yml` (`8080:8080`) | Main Spring Boot API server, security filters, JWT auth, and Actuator metrics |
| **Backend WebSocket** | STOMP / SockJS | `8080` | `8080` | `WebSocketConfig.java` (`/ws`), `frontend/src/lib/websocket.js` (`VITE_WS_URL: 8080`) | Real-time disaster alerts, rescue updates, and verification streams |
| **Frontend Web App** | HTTP (Dev) | `5173` | N/A | `frontend/vite.config.js` (`server.port: 5173`), `frontend/package.json` | Vite React development server |
| **Frontend Web App** | HTTP (Prod/Nginx)| `80` | `80` | `frontend/nginx.conf` (`listen 80`), `docker-compose.yml` (`80:80`) | Nginx serving production bundle and reverse-proxying `/api/` & `/ws/` to `backend:8080` |
| **Python NLP & SACHET** | HTTP / REST | `8000` | `8000` | `docker-compose.yml` (`8000:8000`), `python-service/sachet_poller.py` | FastAPI NLP disaster extractor and NDMA SACHET RSS background poller |
| **Google Earth Engine (GEE)** | HTTP / REST | `5050` | `5050` | `gee-service/Dockerfile` (`EXPOSE 5050`), `gee-service/app.py`, `application.yml` (`GEE_SERVICE_URL: 5050`) | FastAPI microservice fetching Earth Engine satellite imagery and precipitation tiles |
| **MongoDB** | TCP (Mongo wire) | `27017` | `27017` | `docker-compose.yml` (`27017:27017`), `application.yml` (`MONGODB_URI: 27017`) | Primary database storing alerts, shelters, users, and geospatial indexes |

---

## 2. In-Depth Configuration Matrix

| Component | File Path | Parameter / Expression | Target Value |
| :--- | :--- | :--- | :--- |
| **Backend Server** | `backend/src/main/resources/application.yml` | `server.port` | `${SERVER_PORT:8080}` |
| **Backend CORS** | `backend/src/main/resources/application.yml` | `app.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}` |
| **Backend GEE Client**| `backend/src/main/resources/application.yml` | `app.gee.service-url` | `${GEE_SERVICE_URL:http://localhost:5050}` |
| **Backend Actuator** | `backend/src/main/resources/application.yml` | `management.endpoints.web.exposure.include` | `health` |
| **Backend Docker** | `backend/Dockerfile` | `EXPOSE` | `8080` |
| **Docker Compose** | `docker-compose.yml` | `services.backend.ports` | `"8080:8080"` |
| **Docker Compose** | `docker-compose.yml` | `services.backend.environment.SERVER_PORT` | `8080` |
| **Docker Compose** | `docker-compose.yml` | `services.backend.environment.CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:80,http://localhost` |
| **Docker Compose** | `docker-compose.yml` | `services.python-nlp.environment.BACKEND_INGEST_URL` | `http://backend:8080/api/events/ingest` |
| **Nginx Reverse Proxy**| `frontend/nginx.conf` | `location /api/` proxy_pass | `http://backend:8080/api/;` |
| **Nginx Reverse Proxy**| `frontend/nginx.conf` | `location /ws/` proxy_pass | `http://backend:8080/ws/;` |
| **Frontend API Fallback**| `frontend/src/lib/api.js` | `API_URL` default | `http://localhost:8080` |
| **Frontend WS Fallback**| `frontend/src/lib/websocket.js` | `WS_URL` default | `http://localhost:8080` |
| **Vite Dev Proxy** | `frontend/vite.config.js` | `server.proxy['/api']` & `server.proxy['/ws']` | `http://localhost:8080` |
| **Python Poller Service**| `python-service/sachet_poller.py` | `SPRING_BOOT_INGEST_URL` default | `http://localhost:8080/api/events/ingest` |
| **Root Environment** | `.env.example` | `SERVER_PORT` / `BACKEND_INGEST_URL` | `8080` / `http://localhost:8080/api/events/ingest` |
| **Frontend Environment**| `frontend/.env.example` | `VITE_API_URL` / `VITE_WS_URL` | `http://localhost:8080` / `http://localhost:8080` |
