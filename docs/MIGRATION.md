# Database & Location Migration Guide

This document describes the one-time data migration utility designed to correct entity coordinates stored in MongoDB.

---

## 1. Problem Overview

In earlier versions of the platform, user and organisation registration flows hardcoded latitude `20.5937` and longitude `78.9629` (the geographic center of India). Consequently, real citizens and rescue organisations were clustered in central Madhya Pradesh/Maharashtra, preventing localized emergency broadcast alerts and geospatial queries (`$geoNear`, `$nearSphere`) from reaching them.

---

## 2. Migration Solution (`LocationMigrationRunner`)

The backend includes a dedicated migration runner:
[`LocationMigrationRunner`](file:///c:/dev/DISASTER-ALERT/disaster-management-system/backend/src/main/java/com/disaster/config/LocationMigrationRunner.java)

- **Condition:** Enabled **only** when the Spring profile **`migrate`** is active (`@Profile("migrate")`).
- **Mechanism:**
  1. Scans all records in the `users` and `organisations` collections.
  2. Detects any records residing at the default India-centre coordinates (`lat â‰ˆ 20.5937, lon â‰ˆ 78.9629`) or with uninitialized coordinates (`(0.0, 0.0)`).
  3. Uses [`GeoLocationLookupService`](file:///c:/dev/DISASTER-ALERT/disaster-management-system/backend/src/main/java/com/disaster/service/GeoLocationLookupService.java) to resolve true city/state centroids from the registered `city` and `state` fields.
  4. Updates `latitude` and `longitude`, recomputes the 2dsphere `geoLocation` point via `syncGeo()`, and persists the document to MongoDB.
  5. Outputs a detailed log report of updated records and unresolved entities.

---

## 3. How to Execute the Migration

### Option A: Via Docker Compose (Recommended)

1. Open your `.env` file in `disaster-management-system/`.
2. Append `migrate` to the `SPRING_PROFILES_ACTIVE` variable:
   ```env
   SPRING_PROFILES_ACTIVE=dev,migrate
   ```
3. Restart the backend container:
   ```bash
   docker compose up -d backend
   ```
4. View the migration log output:
   ```bash
   docker compose logs backend --tail 100
   ```
   You will see logs such as:
   ```
   STARTING LOCATION MIGRATION: Re-deriving coordinates for India-centre entities...
   Migrated user [id=..., username=..., city=Indore, state=Madhya Pradesh] -> lat=22.7196, lon=75.8577 (approx=false)
   LOCATION MIGRATION COMPLETED: Migrated N users, M organisations.
   ```
5. Remove `migrate` from `SPRING_PROFILES_ACTIVE` in `.env` afterwards.

### Option B: One-off Container Run

Run a temporary container instance with the `migrate` profile enabled:
```bash
docker compose run --rm -e SPRING_PROFILES_ACTIVE=dev,migrate backend
```

### Option C: Running via Maven locally

```powershell
cd disaster-management-system\backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev,migrate
```

### Option D: Running via JAR

```powershell
java -Dspring.profiles.active=dev,migrate -jar target/disaster-backend-1.0.0.jar
```

---

## 4. Verification

After running the migration, verify in MongoDB that affected entities now possess coordinates reflecting their actual cities (e.g. Indore users have coordinates near `(22.7196, 75.8577)` rather than `(20.5937, 78.9629)`).
