# API Access Control & Security Matrix

This document defines the authentication and role-based authorization matrix enforced by Spring Security in the backend service.

---

## 1. Identity & Role Model

The system uses JWTs (JSON Web Tokens) with standard HMAC-SHA256 signatures.

### Roles
- **`CITIZEN`**: Registered and verified community members.
- **`ORGANISATION`**: Registered and verified disaster-relief organizations, shelters, NGOs, and emergency coordinators.
- **`ADMIN`**: System administrators and disaster command center controllers.

> **Token vs. Spring Security Convention:**
> - JWT tokens store bare role names (e.g. `"role": "CITIZEN"`, `"role": "ORGANISATION"`, `"role": "ADMIN"`).
> - `JwtAuthenticationFilter` inspects the claim, safely normalizes any existing `ROLE_` prefixes, and assigns a single `SimpleGrantedAuthority("ROLE_" + role)`.
> - Controllers and security matchers evaluate `.hasRole("ADMIN")` (matches authority `ROLE_ADMIN`) or `.hasAnyRole("ORGANISATION", "ADMIN")`.

---

## 2. API Security Access Matrix

| HTTP Method | Path Pattern | Access Level | Description |
|:---|:---|:---|:---|
| **OPTIONS** | `/**` | **Public** (`permitAll()`) | CORS preflight requests |
| **ALL** | `/ws/**`, `/ws/info/**` | **Public** (`permitAll()`) | STOMP WebSocket connection & info handshakes |
| **GET** | `/api/health`, `/actuator/health` | **Public** (`permitAll()`) | Liveness, readiness, uptime monitoring |
| **GET** | `/api/public/organisations`, `/api/org/public/**` | **Public** (`permitAll()`) | Public verified organisation roster & cards |
| **GET** | `/api/public/**` | **Public** (`permitAll()`) | Public stats, alert summaries, shelter overview |
| **GET** | `/api/events`, `/api/events/active`, `/api/events/{id}` | **Public** (`permitAll()`) | Public disaster event feeds & markers |
| **GET** | `/api/shelters`, `/api/shelters/public/**` | **Public** (`permitAll()`) | Public shelter locator & bed counts |
| **GET** | `/api/sachet/**` | **Public** (`permitAll()`) | NDMA SACHET public emergency broadcast feed |
| **GET** | `/api/simulate/active` | **Public** (`permitAll()`) | Active drill/simulation status for map overlays |
| **POST** | `/api/auth/**` | **Public** (`permitAll()`) | Citizen registration, login, OTP verification |
| **POST** | `/api/org/auth/**`, `/api/org/register`, `/api/org/login`, `/api/org/verify-otp`, `/api/org/resend-otp` | **Public** (`permitAll()`) | Organisation registration, login, OTP verification |
| **POST** | `/api/rescue/request` | **Public** (`permitAll()`) | Emergency SOS broadcast beacon submission |
| **POST** | `/api/simulate/**` | **ADMIN only** (`hasRole('ADMIN')`) | Trigger disaster drill simulations (flood, fire, etc.) |
| **PATCH** | `/api/simulate/**` | **ADMIN only** (`hasRole('ADMIN')`) | Resolve active disaster drill events |
| **POST** | `/api/events/simulate` | **ADMIN only** (`hasRole('ADMIN')`) | Trigger simulation disaster event pipeline |
| **POST** | `/api/events/ingest` | **ADMIN only** (`hasRole('ADMIN')`) | Internal / authoritative event ingestion |
| **POST** | `/api/verification/simulate/**` | **ADMIN only** (`hasRole('ADMIN')`) | Rumor/cyclone/flood verification simulations |
| **POST** | `/api/verification/reset` | **ADMIN only** (`hasRole('ADMIN')`) | Reset verification pipeline state & metrics |
| **ALL** | `/api/integrations/**` | **ADMIN only** (`hasRole('ADMIN')`) | External integration status (FIRMS, OpenWeather, Twilio) |
| **GET** | `/api/rescue`, `/api/rescue/pending`, `/api/rescue/**` | **ORGANISATION or ADMIN** | Triage and monitor active rescue SOS requests |
| **PATCH**| `/api/rescue/*/status`, `/api/rescue/{id}/status` | **ORGANISATION or ADMIN** | Update rescue mission status (in progress, resolved) |
| **POST** | `/api/shelters` | **ORGANISATION or ADMIN** | Register and provision emergency shelters |
| **PATCH**| `/api/shelters/**` | **ORGANISATION or ADMIN** | Update shelter capacity, occupancy, and status |
| **ALL** | `/api/volunteers/**` | **ORGANISATION or ADMIN** | Register responders, list volunteers, update status |
| **PUT** | `/api/org/profile` | **ORGANISATION or ADMIN** | Update authenticated organisation profile details |
| **ALL** | `/api/climate/**` | **Authenticated** | Climate intelligence, satellite indices, weather risk |
| **ALL** | `/api/ai/**` | **Authenticated** | AI-driven threat analysis and damage evaluation |
| **ALL** | `/api/verification/**` (read/stream) | **Authenticated** | Verification pipeline monitoring, metrics, and streams |
| **ALL** | Any other / unlisted endpoint | **Deny / Authenticated** (`anyRequest().authenticated()`) | Defense-in-depth default protection |

---

## 3. Standardized Error Handling

All security rejection responses return standardized JSON with HTTP response headers:

### HTTP 401 Unauthorized (`CustomAuthenticationEntryPoint`)
Returned when an anonymous user or a request with an invalid/expired token attempts to access a protected endpoint:
```json
{
  "timestamp": "2026-10-04T18:00:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/verification/reset"
}
```

### HTTP 403 Forbidden (`CustomAccessDeniedHandler`)
Returned when an authenticated user does not have sufficient role privileges for the requested endpoint (e.g., `CITIZEN` attempting to call an `ADMIN` simulation or organisation rescue triage endpoint):
```json
{
  "timestamp": "2026-10-04T18:00:00.000Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: insufficient permissions",
  "path": "/api/simulate/flood"
}
```

---

## 4. Verification

1. **Unauthenticated access to ADMIN-only endpoint:**
   ```bash
   curl -i -X POST http://localhost:8080/api/verification/reset
   # Output: HTTP/1.1 401 Unauthorized
   # Body: {"timestamp":"...","status":401,"error":"Unauthorized",...}
   ```

2. **Citizen token accessing ADMIN-only endpoint:**
   ```bash
   curl -i -X POST http://localhost:8080/api/simulate/flood \
     -H "Authorization: Bearer <CITIZEN_TOKEN>" \
     -H "Content-Type: application/json" \
     -d '{}'
   # Output: HTTP/1.1 403 Forbidden
   # Body: {"timestamp":"...","status":403,"error":"Forbidden",...}
   ```

3. **Public endpoints:**
   ```bash
   curl -i http://localhost:8080/api/health
   # Output: HTTP/1.1 200 OK
   ```

