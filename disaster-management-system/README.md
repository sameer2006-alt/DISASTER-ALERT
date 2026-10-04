# Smart Disaster Alert & Resource Coordination System

Event-driven disaster response platform with realtime WebSocket alerts, OTP authentication, organisation relief network, shelter coordination, and cinematic Earth scroll landing page.

## Stack

- **Frontend:** React, Vite, Tailwind CSS, GSAP, Mapbox, STOMP WebSocket
- **Backend:** Java 17, Spring Boot 3, Spring Security (JWT), MongoDB, WebSocket
- **Deploy:** Docker Compose (local), Vercel + Render + MongoDB Atlas (production)

## Quick Start (Development)

### 1. MongoDB

```bash
docker run -d -p 27017:27017 --name disaster-mongo mongo:7
```

Or use MongoDB Atlas and set `MONGODB_URI`.

### 2. Backend

```bash
cd backend
export MONGODB_URI=mongodb://localhost:27017/disaster_db
# Generate cryptographically secure JWT_SECRET via scripts/gen-secrets.ps1 or scripts/gen-secrets.sh
export JWT_SECRET=<generated-secret-min-32-chars>
export SPRING_PROFILES_ACTIVE=dev
# Optional: customize dev passwords (randomly generated and logged if omitted)
# export DEV_ADMIN_PASSWORD=...
# export DEV_USER_PASSWORD=...
# export DEV_ORG_PASSWORD=...
# Optional for real emails:
# export MAIL_USERNAME=...
# export MAIL_PASSWORD=...
mvn spring-boot:run
```

API: http://localhost:8080

**Demo accounts (seeded only when `SPRING_PROFILES_ACTIVE=dev`):**
- Admin: `admin` / `${DEV_ADMIN_PASSWORD}` (check logs on boot if not set)
- Organisation: `relief@example.org` / `${DEV_ORG_PASSWORD}` (check logs on boot if not set)

### 3. Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

App: http://localhost:5173

## Docker (full stack)

```bash
cp .env.example .env
# Set secrets generated via scripts/gen-secrets.ps1 or scripts/gen-secrets.sh
docker compose up --build
```

- Frontend: http://localhost
- Backend: http://localhost:8080

## Hackathon Demo Flow

1. Open landing → scroll Earth cinematic sequence
2. Login as `admin` (using configured or generated dev admin password) → Dashboard
3. Click **Simulate FLOOD** → alerts, shelters activate, WebSocket updates
4. Open org login `relief@example.org` (using configured or generated dev org password) → see rescue requests
5. Citizen signup with OTP (requires mail credentials) or use admin flow

## Event-Driven Core

All sources normalize to `DisasterEvent` → `processEvent()`:

1. Save disaster
2. Broadcast `/topic/alerts`
3. Email users (if Gmail configured)
4. Activate nearby shelters
5. Assign volunteers
6. Match organisations geospatially

## API Overview

| Endpoint | Description |
|----------|-------------|
| `POST /api/auth/register` | Start signup, send OTP |
| `POST /api/auth/verify-otp` | Complete signup, get JWT |
| `POST /api/auth/login` | Citizen login |
| `POST /api/org/auth/register` | Organisation signup |
| `POST /api/events/simulate` | Admin disaster drill |
| `GET /api/public/organisations` | Homepage org cards |
| `GET /api/public/stats` | Live stats |
| WebSocket `/ws` | STOMP topics: alerts, shelters, rescue |

## Project Structure

```
disaster-management-system/
├── backend/          Spring Boot API
├── frontend/         React app + earth-frames/
├── docker-compose.yml
├── docs/
└── README.md
```

## Environment Variables

See `.env.example` and `frontend/.env.example`.

## Deployment

- **Frontend:** Vercel — set `VITE_API_URL`, `VITE_WS_URL`, `VITE_MAPBOX_TOKEN`
- **Backend:** Render/Railway — set `MONGODB_URI`, `JWT_SECRET`, `GMAIL_*`, `CORS_ORIGINS`
- **DB:** MongoDB Atlas
