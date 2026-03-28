-- Capital BRI — initial schema
-- Run automatically by Docker on first container start.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS segment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_u         INTEGER NOT NULL,
    graph_v         INTEGER NOT NULL,
    graph_key       INTEGER NOT NULL,
    graph_version   VARCHAR(50) NOT NULL,
    geometry        GEOMETRY(LINESTRING, 4326) NOT NULL,
    length_m        FLOAT NOT NULL,
    name            TEXT,
    highway         TEXT,
    cycleway        TEXT,
    bicycle         TEXT,
    surface         TEXT,
    smoothness      TEXT,
    osmid           JSONB,
    UNIQUE (graph_u, graph_v, graph_key, graph_version)
);

CREATE INDEX IF NOT EXISTS idx_segment_geometry ON segment USING GIST (geometry);
CREATE INDEX IF NOT EXISTS idx_segment_version  ON segment (graph_version);

CREATE TABLE IF NOT EXISTS report (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    segment_id                  UUID NOT NULL REFERENCES segment(id),
    pct_along_segment           FLOAT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    captured_at                 TIMESTAMPTZ NOT NULL,
    impediment_duration         TEXT,
    traffic_level               TEXT,
    hazard_types                TEXT[],
    conditions                  TEXT[],
    infrastructure              TEXT,
    bike_lane_availability      TEXT,
    cycling_experience_rating   SMALLINT,
    pleasantness_rating         SMALLINT,
    client_lat                  DOUBLE PRECISION,
    client_lon                  DOUBLE PRECISION,
    raw_payload                 JSONB
);

CREATE INDEX IF NOT EXISTS idx_report_segment_id  ON report (segment_id);
CREATE INDEX IF NOT EXISTS idx_report_created_at  ON report (created_at);

-- ---------------------------------------------------------------------------
-- Seed segments (DC, graph_version = 'dc-2026-03-28')
-- Three real-ish segments for immediate testing of /segments and /segments/nearest
-- ---------------------------------------------------------------------------
INSERT INTO segment (graph_u, graph_v, graph_key, graph_version, geometry, length_m, name, highway, cycleway, bicycle)
VALUES
    (
        1001, 1002, 0, 'dc-2026-03-28',
        ST_GeomFromText('LINESTRING(-77.0250 38.8938, -77.0230 38.8945, -77.0210 38.8952)', 4326),
        285.4, 'Pennsylvania Ave NW', 'tertiary', 'lane', 'yes'
    ),
    (
        1003, 1004, 0, 'dc-2026-03-28',
        ST_GeomFromText('LINESTRING(-77.0050 38.9050, -77.0030 38.9055, -77.0010 38.9060)', 4326),
        195.2, 'M St NW', 'secondary', NULL, 'yes'
    ),
    (
        1005, 1006, 0, 'dc-2026-03-28',
        ST_GeomFromText('LINESTRING(-77.0320 38.9090, -77.0300 38.9095, -77.0280 38.9100)', 4326),
        215.8, '15th St NW', 'secondary', 'track', 'yes'
    ),
    (
        1007, 1008, 0, 'dc-2026-03-28',
        ST_GeomFromText('LINESTRING(-77.0450 38.8890, -77.0430 38.8895, -77.0410 38.8900)', 4326),
        189.3, 'Independence Ave SW', 'primary', 'lane', 'yes'
    ),
    (
        1009, 1010, 0, 'dc-2026-03-28',
        ST_GeomFromText('LINESTRING(-77.0150 38.9010, -77.0130 38.9015, -77.0110 38.9020)', 4326),
        201.7, 'K St NW', 'secondary', NULL, 'yes'
    )
ON CONFLICT DO NOTHING;
