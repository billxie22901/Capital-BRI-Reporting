-- Capital BRI — initial schema
-- Run automatically by Docker on first container start.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS segment (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    graph_u         BIGINT NOT NULL,
    graph_v         BIGINT NOT NULL,
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
    note                        TEXT,
    raw_payload                 JSONB
);

CREATE INDEX IF NOT EXISTS idx_report_segment_id  ON report (segment_id);
CREATE INDEX IF NOT EXISTS idx_report_created_at  ON report (created_at);

