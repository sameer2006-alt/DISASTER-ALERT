"""
SACHET NDMA Background Polling Service.
Periodically fetches real-time NDMA disaster alerts, prevents duplicates,
stores in MongoDB, and broadcasts to the platform WebSocket stream.
"""

import os
import time
import logging
import asyncio
from datetime import datetime, timezone
import requests
from pymongo import MongoClient

from sachet_parser import fetch_and_parse_sachet_feed

logger = logging.getLogger("sachet_poller")

# Config from environment
DEFAULT_POLL_INTERVAL = int(os.getenv("SACHET_POLL_INTERVAL_SECONDS", "300"))
MAX_STORED_ALERTS = int(os.getenv("SACHET_MAX_STORED_ALERTS", "500"))
SACHET_ENABLED = os.getenv("SACHET_ENABLED", "true").lower() in ("true", "1", "yes")
SPRING_BOOT_INGEST_URL = os.getenv("BACKEND_INGEST_URL", "http://localhost:8080/api/events/ingest")
MONGODB_URI = os.getenv("MONGODB_URI", "mongodb://localhost:27017/disaster_db")


class SachetPollerService:
    def __init__(self):
        self.poll_interval = DEFAULT_POLL_INTERVAL
        self.enabled = SACHET_ENABLED
        self.processed_ids: set[str] = set()
        self.recent_alerts: list[dict] = []
        self.last_poll_time: str | None = None
        self.total_alerts_processed: int = 0
        self.total_new_alerts: int = 0
        self.is_running: bool = False
        self.last_error: str | None = None
        self._task: asyncio.Task | None = None

        # Direct Mongo client fallback
        self._mongo_client = None
        self._mongo_col = None
        self._init_mongo()

    def _init_mongo(self):
        try:
            self._mongo_client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=2000)
            try:
                db = self._mongo_client.get_default_database()
            except Exception:
                db = None
            if db is None:
                db = self._mongo_client["disaster_db"]
            self._mongo_col = db["disasters"]
            logger.info("Connected to MongoDB for direct disaster event storage.")
        except Exception as e:
            logger.warning(f"Direct MongoDB connection not available: {e}")

    def convert_to_disaster_event(self, alert: dict) -> dict:
        """
        Transform parsed SACHET alert into standard DisasterEvent document.
        """
        sachet_id = alert["sachet_id"]
        event_id = f"sachet-{sachet_id}"

        # Standardize disasterType to match platform enum
        dtype = alert["disaster_type"]
        if dtype not in ["FLOOD", "FIRE", "EARTHQUAKE", "CYCLONE", "LANDSLIDE"]:
            # Fallbacks if backend strictly validates enum
            if dtype == "HEAVY_RAIN":
                dtype = "FLOOD"
            elif dtype == "HEATWAVE":
                dtype = "FIRE"
            else:
                dtype = "FLOOD"

        return {
            "id": event_id,
            "disasterType": dtype,
            "severity": alert["severity_score"],
            "location": f"{alert['state']}, India",
            "latitude": alert["latitude"],
            "longitude": alert["longitude"],
            "geoLocation": {
                "type": "Point",
                "coordinates": [alert["longitude"], alert["latitude"]]
            },
            "timestamp": alert["published_at"],
            "message": alert["title"],
            "affectedRadius": 25.0,
            "source": "SACHET_NDMA",
            "sourceUrl": alert["source_url"],
            "active": True,
            # Extra metadata
            "rawDisasterType": alert["disaster_type"],
            "state": alert["state"],
            "officialSeverity": alert["severity"],
            "author": alert.get("author", "NDMA Control Room"),
            "sachetId": sachet_id
        }

    def broadcast_alert_to_platform(self, event: dict) -> bool:
        """
        Forward event to Spring Boot backend ingest endpoint.
        Spring Boot saves to MongoDB and converts/sends to WebSocket /topic/alerts & /topic/dashboard.
        """
        try:
            res = requests.post(
                SPRING_BOOT_INGEST_URL,
                json=event,
                headers={"Content-Type": "application/json"},
                timeout=3
            )
            if res.status_code in (200, 201):
                logger.info(f"Broadcasted SACHET alert {event['id']} to Spring Boot WebSocket pipeline.")
                return True
            else:
                logger.warning(f"Spring Boot ingest responded with {res.status_code}: {res.text}")
        except Exception as e:
            logger.debug(f"Spring Boot ingest endpoint not reachable ({e}). Direct storage active.")

        # Fallback direct MongoDB insert
        if self._mongo_col is not None:
            try:
                self._mongo_col.update_one(
                    {"id": event["id"]},
                    {"$set": event},
                    upsert=True
                )
                logger.info(f"Directly stored SACHET alert {event['id']} in MongoDB.")
                return True
            except Exception as me:
                logger.error(f"Failed to persist alert in MongoDB: {me}")

        return False

    def poll_once(self) -> dict:
        """
        Execute one sync poll against SACHET RSS feed.
        """
        self.last_poll_time = datetime.now(timezone.utc).isoformat()
        logger.info("Starting SACHET NDMA RSS poll cycle...")

        try:
            parsed_items = fetch_and_parse_sachet_feed()
            if not parsed_items:
                logger.info("No items retrieved from SACHET feed in this cycle.")
                return {"status": "ok", "found": 0, "new": 0, "alerts": []}

            new_alerts = []
            for item in parsed_items:
                sid = item["sachet_id"]
                if sid not in self.processed_ids:
                    self.processed_ids.add(sid)
                    event_doc = self.convert_to_disaster_event(item)

                    # Save in memory collection
                    self.recent_alerts.insert(0, event_doc)
                    if len(self.recent_alerts) > MAX_STORED_ALERTS:
                        self.recent_alerts = self.recent_alerts[:MAX_STORED_ALERTS]

                    # Broadcast to platform
                    self.broadcast_alert_to_platform(event_doc)

                    new_alerts.append(event_doc)
                    self.total_new_alerts += 1

                self.total_alerts_processed += 1

            logger.info(
                f"SACHET Poll complete: {len(parsed_items)} total items, {len(new_alerts)} new alerts broadcast."
            )
            self.last_error = None
            return {
                "status": "ok",
                "found": len(parsed_items),
                "new": len(new_alerts),
                "new_alerts": new_alerts
            }
        except Exception as e:
            self.last_error = str(e)
            logger.error(f"Error during SACHET poll cycle: {e}", exc_info=True)
            return {"status": "error", "error": str(e), "found": 0, "new": 0}

    async def run_loop(self):
        """
        Continuous async background polling loop.
        """
        self.is_running = True
        logger.info(f"SACHET background poller started (interval: {self.poll_interval}s, enabled: {self.enabled})")

        while self.is_running:
            if self.enabled:
                try:
                    # Run poll in thread pool to prevent blocking asyncio loop
                    await asyncio.to_thread(self.poll_once)
                except Exception as e:
                    logger.error(f"Error in SACHET polling loop: {e}", exc_info=True)

            try:
                await asyncio.sleep(self.poll_interval)
            except asyncio.CancelledError:
                break

        self.is_running = False
        logger.info("SACHET background poller stopped.")

    def start(self):
        """Start async background poller task."""
        if not self.is_running:
            self._task = asyncio.create_task(self.run_loop())

    def stop(self):
        """Stop background poller."""
        self.is_running = False
        if self._task:
            self._task.cancel()

    def get_status(self) -> dict:
        """Return diagnostic metrics for the polling service."""
        return {
            "status": "running" if self.is_running else "stopped",
            "enabled": self.enabled,
            "poll_interval_seconds": self.poll_interval,
            "last_poll_time": self.last_poll_time,
            "total_alerts_processed": self.total_alerts_processed,
            "total_new_alerts": self.total_new_alerts,
            "cached_alerts_count": len(self.recent_alerts),
            "last_error": self.last_error
        }

    def get_alerts(self, disaster_type: str | None = None, state: str | None = None, limit: int = 50) -> list[dict]:
        """
        Get recent SACHET alerts with optional filtering.
        """
        results = self.recent_alerts

        if disaster_type:
            dt_upper = disaster_type.strip().upper()
            results = [
                a for a in results 
                if a.get("disasterType") == dt_upper or a.get("rawDisasterType") == dt_upper
            ]

        if state:
            state_lower = state.strip().lower()
            results = [
                a for a in results
                if state_lower in a.get("state", "").lower() or state_lower in a.get("location", "").lower()
            ]

        return results[:limit]


# Global singleton instance
sachet_service = SachetPollerService()
