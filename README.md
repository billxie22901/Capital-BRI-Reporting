# Capital BRI Reporting

Washington, DC bike rider road-condition reporting app — mobile-first, segment-anchored reports on the OSM road network.

**Current state: Prototype 1 complete.** The full loop works: open the app, tap Report, tap a road, adjust the segment boundaries, fill out conditions, submit. Reports are stored in PostGIS and retrievable via API.

---

## Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Docker Desktop | Any recent |
| Android Studio | With an AVD (emulator) configured |
| Python | 3.11+ |
| Gradle | 8+ (or use `./gradlew` wrapper in `mobile/`) |

### 1. Start the backend

```bash
docker-compose up
```

Starts:
- **PostgreSQL + PostGIS** on `localhost:5432`
- **Flask API** on `localhost:8080`

The schema (`backend/init.sql`) is applied automatically on first container start.

### 2. Load DC road segments (first time only)

```bash
cd scripts
python -m venv .venv
source .venv/Scripts/activate   # Windows
# source .venv/bin/activate     # macOS/Linux
pip install "numpy<2" -r requirements.txt
python load_dc_segments.py
```

Fetches the Washington DC bike network via OSMnx (~102,000 edges) and loads it into PostGIS. Takes 3–5 minutes. Only needed once; subsequent `docker-compose up` runs reuse the persisted volume.

> **Note:** `numpy<2` must be pinned before other packages — osmnx/pandas are compiled against NumPy 1.x.

### 3. Build and install the mobile app

```bash
cd mobile
./gradlew assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The app connects to `10.0.2.2:8080` (Android emulator's alias for localhost). For a physical device, update `BASE_URL` in `mobile/shared/src/commonMain/kotlin/com/capitalbri/shared/api/ApiConfig.kt`.

---

## Architecture

```
mobile/
  androidApp/          # Android UI — Jetpack Compose + MapLibre
  shared/              # Kotlin Multiplatform shared logic (models, API client, validators)

backend/
  app/
    routes/            # Flask blueprints: health, auth, segments, reports
    db.py              # psycopg2 connection pool
  init.sql             # Schema (applied by Docker on first start)

scripts/
  load_dc_segments.py  # One-time OSM data loader (osmnx → PostGIS)
```

### Backend (Flask + PostGIS)

Stateless REST API. No ORM — raw psycopg2 with dict cursors. Auth is HTTP Basic with a hardcoded demo user (`demo` / `demo`) returning a JWT; production would replace this.

**Endpoints:**

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/health` | No | Liveness check |
| POST | `/auth/login` | No | Returns JWT access token |
| GET | `/segments?bbox=minLon,minLat,maxLon,maxLat` | No | Segments intersecting viewport (max 500) |
| GET | `/segments/nearest?lat=&lon=&radius_m=&limit=` | No | Nearest segments to a point (max radius 1000 m, max 5 results) |
| POST | `/reports` | JWT | Submit a road condition report |
| GET | `/reports?segment_id=&bbox=&limit=` | JWT | List reports |

### Mobile (Kotlin Multiplatform / Compose)

**Screens:**
- `MapScreen` — full-screen MapLibre map with FABs (Report, Recent Reports)
- `ReportFormScreen` — scrollable form with chip pickers, sliders, and a free-text note field
- `RecentReportsScreen` — list of recent reports from the API

**Map overlay layers (MapLibre):**

| Layer | Color | Purpose |
|---|---|---|
| `segments-layer` | Blue `#2196F3`, 3px | All segments in the current viewport |
| `pending-layer` | Amber `#FF9800`, 6px | Immediate basemap-tile highlight on tap (while backend snaps) |
| `selected-layer` | Orange `#FF5722`, 8px | Confirmed segment from backend |
| `endpoint-layer` | Orange circles, 8px radius | Start/end node markers of selected segment |

**Reporting flow:**

1. Tap the **Report** FAB (⚠ icon).
2. If GPS is good, the backend is queried automatically. Otherwise: "Tap a road on the map."
3. Tapping a road shows an immediate amber glow (basemap tile geometry), then the backend snaps to the nearest OSM segment (orange overlay + endpoint circles, camera fits to segment).
4. The **Segment confirm sheet** appears:
   - `← Extend` / `Extend →` — merges the adjacent OSM segment at that end into the selection.
   - `← Retract` / `Retract →` — undoes the last extend on that end (disabled until an extend has been made; Reset also disabled until extended).
   - **Not this road** — cycles to the next nearest backend candidate.
   - **Cancel** — exits reporting, clears all overlays.
   - **Confirm** — navigates to the report form.
5. Report form: hazard types, surface conditions, traffic level, infrastructure, ratings (1–10 sliders), and an optional free-text note.

---

## Schema

Defined in `backend/init.sql`. Applied automatically by Docker.

### `segment`

Stores OSM road graph edges. Populated by `scripts/load_dc_segments.py`.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `graph_u`, `graph_v` | BIGINT | OSM node IDs (must be BIGINT — exceed 32-bit range) |
| `graph_key` | INTEGER | OSMnx parallel-edge disambiguator |
| `graph_version` | VARCHAR(50) | Dataset tag, e.g. `dc-2026-03-28` |
| `geometry` | GEOMETRY(LINESTRING, 4326) | WGS84 linestring; GIST-indexed |
| `length_m` | FLOAT | Edge length in metres (computed in UTM projection) |
| `name`, `highway`, `cycleway`, `bicycle`, `surface`, `smoothness` | TEXT | OSM tags |
| `osmid` | JSONB | Raw OSM way ID(s) |

Unique constraint: `(graph_u, graph_v, graph_key, graph_version)`.

### `report`

User-submitted road condition reports.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `segment_id` | UUID | FK → `segment.id` |
| `pct_along_segment` | FLOAT | 0–1 position along segment where reporter was |
| `created_at` | TIMESTAMPTZ | Server insert time |
| `captured_at` | TIMESTAMPTZ | Client-reported observation time |
| `impediment_duration` | TEXT | `short_term` / `long_term` |
| `traffic_level` | TEXT | `light` / `moderate` / `heavy` / `gridlock` |
| `hazard_types` | TEXT[] | Array of hazard codes |
| `conditions` | TEXT[] | Array of surface condition codes |
| `infrastructure` | TEXT | Infrastructure type code |
| `bike_lane_availability` | TEXT | `clear` / `partial` / `blocked` / `absent` |
| `cycling_experience_rating` | SMALLINT | 1–10 |
| `pleasantness_rating` | SMALLINT | 1–10 |
| `client_lat`, `client_lon` | DOUBLE PRECISION | GPS position at time of report |
| `note` | TEXT | Optional free-text comment from reporter |
| `raw_payload` | JSONB | Full request body for future-proofing |

---

## Credentials (local dev only)

| Service | Value |
|---|---|
| DB host | `localhost:5432` |
| DB name | `capital_bri` |
| DB user | `capital_bri` |
| DB password | `capital_bri_dev` |
| API | `http://localhost:8080` |
| Demo login | `demo` / `demo` |

---

## Known Limitations / Next Steps

- **MapView lifecycle** — `MapView.onStart/onResume/onPause/onStop/onDestroy` are not forwarded from the Compose lifecycle. This causes the style to reload on every recomposition in some conditions. Production code should wire these via `LocalLifecycleOwner`.
- **Drag-to-snap endpoints** — endpoint circle markers are visual only. Dragging them to snap to adjacent OSM graph nodes requires a backend adjacency query and is not yet implemented.
- **Auth** — JWT tokens are issued from a hardcoded demo user. No registration, no persistence across app restarts.
- **Offline** — no local caching; all segment and report data requires a live backend connection.
- **iOS** — the `shared` KMP module is written for Android only; the iOS target exists in the Gradle config but has no UI layer.
