# Live Demo Script (5–7 minutes)

## 1. Landing (30 sec)
- Scroll through Earth → India cinematic sequence
- Highlight: realtime stats, organisation cards

## 2. Organisation (1 min)
- Org Login: `relief@example.org` / `${DEV_ORG_PASSWORD}` (or password logged on dev boot)
- Show rescue requests panel + shelter registration

## 3. Admin simulation (2 min)
- Login: `admin` / `${DEV_ADMIN_PASSWORD}` (or password logged on dev boot)
- Dashboard → **Simulate FLOOD**
- Point out: live alert card, shelter ACTIVE status, map markers
- Check backend logs for email (if SMTP configured)

## 4. Citizen flow (1 min)
- Signup → OTP email → verify → dashboard
- Press **SOS — Request Help**
- Switch to org dashboard → Accept Mission

## 5. Architecture (30 sec)
- All sources → `DisasterEvent` → `processEvent()`
- WebSocket topics for realtime coordination

## Talking Points
- Event-driven architecture (simulation = same pipeline as real APIs)
- Geo-based organisation matching
- Government-grade UI with hackathon-ready simulation mode
