# 🚨 Smart Disaster Alert & Resource Coordination System

A production-grade, full-stack disaster response platform that combines real-time emergency alerts, geo-targeted user notification, AI-powered intelligence mapping, and organisation rescue coordination into a single unified system.

> Built as a hackathon project — engineered to behave like a real national disaster management infrastructure.

---

## 📌 Table of Contents

- [Overview](#overview)
- [Live Demo](#live-demo)
- [Core Features](#core-features)
- [System Architecture](#system-architecture)
- [Tech Stack](#tech-stack)
- [Module Breakdown](#module-breakdown)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
- [WebSocket Channels](#websocket-channels)
- [Database Models](#database-models)
- [Folder Structure](#folder-structure)
- [Screenshots](#screenshots)
- [Contributing](#contributing)
- [Author](#author)

---

## Overview

The Smart Disaster Alert & Resource Coordination System is a full-stack platform built to coordinate emergency response during natural disasters. When a disaster is detected or simulated, the system:

1. Identifies affected users by GPS radius using MongoDB geospatial queries
2. Dispatches real HTML email alerts to only those users via Gmail SMTP
3. Activates nearby registered shelters automatically
4. Notifies rescue organisations and volunteers in the affected zone
5. Broadcasts realtime updates to all connected clients via Spring WebSocket
6. Updates a unified AI intelligence map with flood zones, risk heatmaps, SOS beacons, and shelter markers

The platform supports two separate user types — **Citizens** and **Organisations** — each with their own authentication flow, dashboard, and workflows.

---

## Live Demo

| Service | URL |
|---|---|
| Frontend | https://your-frontend.vercel.app |
| Backend API | https://your-backend.onrender.com |
| API Health | https://your-backend.onrender.com/actuator/health |

> **Demo credentials**
> Citizen — `citizen@demo.com` / `Demo@1234`
> Organisation — `org@demo.com` / `Demo@1234`
> Admin — `admin@demo.com` / `Admin@1234`

---

## Core Features

### 🗺️ Unified AI Intelligence Map
- Single fullscreen geospatial command map replacing all separate tab-based maps
- 14 toggle-able live overlay layers: rainfall heatmap, humidity, flood zones, AI risk, shelters, SOS beacons, rescue teams, volunteers, safe zones, evacuation corridors, active disasters, and AI expansion prediction
- Dark CartoDB base tile for military-grade visual aesthetic
- Animated markers: pulsing user location, expanding SOS beacons, blinking disaster zones
- Atmospheric radar sweep and geospatial grid overlay effects

### ⚡ Geo-Targeted Disaster Simulation Engine
- Event-driven backend pipeline: trigger → detect region → notify users → activate shelters → dispatch teams
- MongoDB `$near` geospatial query finds all users within disaster radius
- Real HTML email alerts sent only to affected users (not broadcast to everyone)
- Supports 5 disaster types: Flood, Fire, Earthquake, Cyclone, Landslide
- Progressive disaster escalation timeline
- Full audit log of every action taken per disaster event

### 🏢 Organisation Rescue & Relief Network
- Separate auth system for NGOs, medical teams, food suppliers, shelter providers, rescue teams
- Email OTP verification on signup
- Organisation profile with logo, operating locations, service types, and capacities
- Public showcase on homepage with live availability status
- Organisation dashboard: rescue requests, shelter management, resource inventory, volunteer assignments
- Admin-issued verification badges: VERIFIED, TRUSTED, GOVERNMENT VERIFIED

### 🏠 Shelter Registration & Activation
- Organisations register shelters with coordinates, bed count, food and medical availability
- Shelters auto-appear on disaster map, shelter finder, and emergency dashboard
- Automatic shelter activation when a disaster occurs nearby
- Realtime bed count updates via WebSocket

### 🚨 SOS Request System
- Citizens submit geo-tagged SOS requests from the map with urgency level
- SOS beacons appear instantly on all connected dashboards via WebSocket
- Nearby organisations and rescue teams receive automatic notifications
- Rescue status tracking: PENDING → ACCEPTED → IN_PROGRESS → COMPLETED

### 📧 Email Alert System
- Real HTML email alerts with disaster type, severity, affected location, shelter info, and emergency helplines
- Async email dispatch using `@Async` thread pool — does not block API response
- Colour-coded email templates per severity (LOW / MEDIUM / HIGH / CRITICAL)
- Email sent to organisations with required resource list per disaster type

### 🔴 Realtime Alert Stream
- Bottom ticker showing live events: SOS, shelter activation, rescue deployment, org notifications
- Right panel with AI threat metrics: confidence score, flood probability, affected population, evacuation urgency
- All data pushed via Spring WebSocket — no polling

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        FRONTEND (React)                      │
│  Unified Map  │  Citizen Dashboard  │  Organisation Dashboard│
│  Leaflet.js   │  Alert Stream       │  Shelter Management   │
└──────────────────────────┬──────────────────────────────────┘
                           │  REST API + WebSocket (STOMP)
┌──────────────────────────▼──────────────────────────────────┐
│                     BACKEND (Spring Boot)                    │
│  SimulationController  │  OrganisationController            │
│  DisasterService       │  EmailAlertService                 │
│  WebSocketAlertService │  ShelterActivationService          │
│  UserGeoQueryEngine    │  VolunteerDispatchService          │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    DATABASE (MongoDB Atlas)                  │
│  users  │  disasters  │  organisations  │  shelters         │
│  sos_requests  │  disaster_logs  │  rescue_requests         │
└─────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

### Frontend
| Technology | Purpose |
|---|---|
| React 18 + Vite | Frontend framework and build tool |
| Tailwind CSS | Utility-first styling |
| Framer Motion | Animations and transitions |
| Leaflet.js + react-leaflet | Interactive geospatial map |
| leaflet-heat | Heatmap overlays (rainfall, AI risk) |
| CartoDB Dark Matter | Dark futuristic map tiles |
| @stomp/stompjs + sockjs-client | WebSocket client |
| Axios | HTTP API calls |

### Backend
| Technology | Purpose |
|---|---|
| Spring Boot 3.x | REST API and application server |
| Spring Security + JWT | Authentication and role-based access |
| Spring WebSocket (STOMP) | Realtime bidirectional communication |
| Spring Mail (JavaMailSender) | Email alert dispatch |
| Spring Data MongoDB | Database ORM and geo queries |
| Lombok | Boilerplate reduction |

### Database & Infrastructure
| Technology | Purpose |
|---|---|
| MongoDB Atlas | Primary database with geospatial indexing |
| Gmail SMTP | Email notification service |
| Docker + Docker Compose | Containerisation and local deployment |
| Vercel | Frontend deployment |
| Render | Backend deployment |

---

## Module Breakdown

### 1. Disaster Simulation Engine
- `SimulationController.java` — REST endpoints for all 5 disaster types
- `DisasterService.java` — Core `processDisaster()` pipeline
- `EmailAlertService.java` — HTML email dispatch with async thread pool
- `WebSocketAlertService.java` — Broadcasts to all WebSocket topics
- `ShelterActivationService.java` — Finds and activates nearby shelters
- `OrgNotificationService.java` — Notifies organisations in affected zone
- `VolunteerDispatchService.java` — Assigns nearby volunteers

### 2. Organisation Module
- `OrganisationController.java` — Signup, login, profile, public listing
- `OrganisationService.java` — OTP verification, profile management
- `ShelterController.java` — Shelter registration and status management
- `RescueController.java` — SOS submission, accept, status updates

### 3. Unified Map (Frontend)
- `UnifiedDisasterMap.jsx` — Root map component with all layer orchestration
- `OverlayTogglePanel.jsx` — Left panel with 14 layer toggles
- `AiThreatPanel.jsx` — Right panel with live AI metrics
- `AlertStreamBar.jsx` — Bottom scrolling realtime ticker
- `SimulationControls.jsx` — Admin disaster trigger buttons
- `layers/` — Individual Leaflet layer components per overlay type

---

## Getting Started

### Prerequisites
- Java 17+
- Node.js 18+
- MongoDB Atlas account (free tier works)
- Gmail account with App Password enabled

### 1. Clone the repository

```bash
git clone https://github.com/yourusername/smart-disaster-system.git
cd smart-disaster-system
```

### 2. Backend setup

```bash
cd backend
cp src/main/resources/application.properties.example src/main/resources/application.properties
# Fill in your MongoDB URI, Gmail credentials, and JWT secret
mvn clean install
mvn spring-boot:run
```

Backend starts on `http://localhost:8080`

### 3. Frontend setup

```bash
cd frontend
npm install
cp .env.example .env
# Set VITE_API_BASE_URL=http://localhost:8080
npm run dev
```

Frontend starts on `http://localhost:5173`

### 4. Using Docker Compose (recommended)

```bash
docker-compose up --build
```

This starts both the backend and frontend together.

### 5. Seed demo data

```bash
# Seed test users, shelters, and an organisation in Mumbai
curl -X POST http://localhost:8080/api/admin/seed-demo \
  -H "Authorization: Bearer YOUR_ADMIN_JWT"
```

### 6. Test the simulation engine

```bash
curl -X POST http://localhost:8080/api/simulate/flood \
  -H "Content-Type: application/json" \
  -d '{
    "state": "Maharashtra",
    "city": "Mumbai",
    "severity": "HIGH",
    "latitude": 19.0760,
    "longitude": 72.8777,
    "radiusKm": 10,
    "message": "Mithi river overflow detected"
  }'
```

---

## Environment Variables

### Backend — `application.properties`

```properties
# MongoDB
spring.data.mongodb.uri=mongodb+srv://USERNAME:PASSWORD@cluster.mongodb.net/disaster_db

# JWT (generate using scripts/gen-secrets.ps1 or scripts/gen-secrets.sh)
jwt.secret=YOUR_32_PLUS_CHARACTER_SECRET_KEY
jwt.expiration=86400000

# SMTP (Brevo recommended or custom SMTP)
spring.mail.host=smtp-relay.brevo.com
spring.mail.port=587
spring.mail.username=your-user@example.org
spring.mail.password=YOUR_SMTP_PASSWORD
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# CORS
app.cors.allowed-origins=http://localhost:5173,https://your-frontend.vercel.app
```

> **SMTP / App Password setup:** Configure your SMTP server host and credentials. For Brevo, use `smtp-relay.brevo.com` with your Brevo API key as the password. For Google Workspace/custom SMTP, generate an app-specific password.

### Frontend — `.env`

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=http://localhost:8080/ws
VITE_OPENWEATHER_API_KEY=YOUR_OPENWEATHERMAP_KEY
```

> **OpenWeatherMap API key:** Register free at openweathermap.org/api. Used for rainfall and humidity overlay data.

---

## API Reference

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Citizen registration |
| POST | `/api/auth/login` | Citizen login → returns JWT |
| POST | `/api/org/register` | Organisation registration |
| POST | `/api/org/verify-otp` | OTP email verification |
| POST | `/api/org/login` | Organisation login → returns JWT |

### Disaster Simulation

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/simulate/flood` | Trigger flood simulation |
| POST | `/api/simulate/fire` | Trigger fire simulation |
| POST | `/api/simulate/earthquake` | Trigger earthquake simulation |
| POST | `/api/simulate/cyclone` | Trigger cyclone simulation |
| POST | `/api/simulate/landslide` | Trigger landslide simulation |
| GET | `/api/simulate/active` | Get all active disasters |
| PATCH | `/api/simulate/resolve/{id}` | Resolve a disaster |

### Organisations & Shelters

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/org/public/all` | All verified active organisations |
| GET | `/api/org/dashboard` | Organisation dashboard data (auth required) |
| POST | `/api/shelters/register` | Register a new shelter |
| GET | `/api/shelters/public/all` | All active shelters for map |
| PATCH | `/api/shelters/{id}/update` | Update shelter capacity / status |

### Rescue & SOS

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/rescue/request` | Submit SOS request (citizen) |
| GET | `/api/rescue/nearby` | Get SOS requests near coordinates |
| PATCH | `/api/rescue/{id}/accept` | Accept a rescue request (org) |
| PATCH | `/api/rescue/{id}/status` | Update rescue status |

### AI & Map Data

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/ai/threat-analysis` | AI threat metrics for map panel |
| GET | `/api/disasters/active` | Active disaster markers for map |

---

## WebSocket Channels

Connect to: `ws://localhost:8080/ws` (SockJS)

| Topic | Direction | Payload |
|---|---|---|
| `/topic/disaster-alerts` | Server → Client | Full `DisasterEvent` object |
| `/topic/alert-stream` | Server → Client | Ticker item: type, icon, message, timestamp |
| `/topic/shelter-updates` | Server → Client | Shelter object with updated status |
| `/topic/rescue-requests` | Server → Client | New `SosRequest` object |
| `/topic/rescue-updates` | Server → Client | Rescue status change |
| `/topic/ai-risk-update` | Server → Client | Risk coordinates and level |
| `/topic/org/{orgId}` | Server → Client | Targeted notification to specific org |
| `/app/sos` | Client → Server | Submit SOS from frontend |

---

## Database Models

### Key MongoDB Collections

```
disasters         — Disaster events with geo coordinates, severity, status, counts
users             — Citizens and volunteers with GeoJsonPoint location field
organisations     — NGOs, medical teams, rescue orgs with 2dsphere geo index
shelters          — Registered shelters with capacity and coordinates
sos_requests      — Citizen SOS submissions with urgency and status
rescue_requests   — Organisation-initiated rescue missions
disaster_logs     — Full audit trail of every action per disaster event
```

### Critical Index

Both `users.location` and `organisations.coordinates` must have a `2dsphere` index for geo radius queries to work:

```javascript
db.users.createIndex({ "location": "2dsphere" })
db.organisations.createIndex({ "coordinates": "2dsphere" })
db.shelters.createIndex({ "coordinates": "2dsphere" })
```

---

## Folder Structure

```
smart-disaster-system/
├── backend/
│   └── src/main/java/com/disaster/relief/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── model/
│       └── config/
├── frontend/
│   └── src/
│       ├── components/map/
│       │   ├── layers/
│       │   └── effects/
│       ├── hooks/
│       ├── services/
│       └── pages/
├── docker-compose.yml
└── README.md
```

---

## Screenshots

> *(Add screenshots of the unified map, simulation trigger, email alert, and organisation dashboard here)*

| Unified Intelligence Map | Disaster Simulation | Email Alert |
|---|---|---|
| ![map](screenshots/map.png) | ![sim](screenshots/simulation.png) | ![email](screenshots/email.png) |

---

## Contributing

This project was built as a hackathon submission. Contributions, bug reports, and feature suggestions are welcome.

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add: your feature description'`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

## Author

**Gaurang**
Computer Science Engineering — PIEMR, Indore
[GitHub](https://github.com/gaurangchouhan) · [LinkedIn](https://www.linkedin.com/in/gaurang-chouhan-68054630b/)

---

> *"Every government sends alerts after disasters are already underway. This system detects a disaster, identifies only the affected population, sends targeted real alerts, activates shelters, and coordinates rescue — all in under 5 seconds."*
