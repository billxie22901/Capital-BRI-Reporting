"""
load_dc_segments.py

Fetches the Washington DC bike-accessible road network via OSMnx and
upserts every edge as a row in the `segment` table of the Capital BRI
Postgres database.

Usage:
    python load_dc_segments.py

Dependencies (see requirements.txt):
    osmnx>=1.9.0
    psycopg2-binary>=2.9.0
    shapely>=2.0.0
"""

import json
import math
import os

import osmnx as ox
import psycopg2
import psycopg2.extras

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", "5432"))
DB_NAME = os.getenv("DB_NAME", "capital_bri")
DB_USER = os.getenv("DB_USER", "capital_bri")
DB_PASSWORD = os.getenv("DB_PASSWORD", "capital_bri_dev")

GRAPH_VERSION = "dc-2026-03-28"
PLACE = "Washington, DC, USA"
NETWORK_TYPE = "bike"
BATCH_SIZE = 500

USEFUL_TAGS_WAY = [
    "name",
    "highway",
    "cycleway",
    "bicycle",
    "surface",
    "smoothness",
]

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _coerce_null(value):
    """Return None for NaN/None/empty, otherwise the original value."""
    if value is None:
        return None
    try:
        if math.isnan(float(value)):  # works for numeric NaN
            return None
    except (TypeError, ValueError):
        pass
    if isinstance(value, float) and math.isnan(value):
        return None
    return value


def _to_json(value):
    """Serialise an osmid that may be an int, list, or already None."""
    v = _coerce_null(value)
    if v is None:
        return None
    if isinstance(v, list):
        return json.dumps(v)
    return json.dumps(int(v))


def _to_str(value):
    """Return a string column or None."""
    v = _coerce_null(value)
    if v is None:
        return None
    # osmnx sometimes returns lists for tags (e.g. multiple highway values)
    if isinstance(v, list):
        return "; ".join(str(item) for item in v)
    return str(v)


# ---------------------------------------------------------------------------
# Pipeline
# ---------------------------------------------------------------------------


def fetch_graph():
    print(f"Fetching OSMnx bike network for: {PLACE} ...")
    ox.settings.useful_tags_way = USEFUL_TAGS_WAY
    G = ox.graph_from_place(PLACE, network_type=NETWORK_TYPE)
    print(f"  Raw graph: {len(G.nodes):,} nodes, {len(G.edges):,} edges")
    return G


def project_and_extract(G):
    """Project to UTM (for accurate lengths), back to WGS84, return GeoDataFrame."""
    print("Projecting to UTM for length calculation ...")
    G_utm = ox.project_graph(G)          # UTM — lengths in meters
    G_wgs = ox.project_graph(G_utm, to_crs="EPSG:4326")  # back to WGS84

    # to_undirected=False keeps every directed edge
    edges_gdf = ox.graph_to_gdfs(G_wgs, nodes=False, edges=True)
    print(f"  Edges GeoDataFrame: {len(edges_gdf):,} rows")
    return edges_gdf


def build_rows(edges_gdf):
    """Convert GeoDataFrame rows to a list of dicts ready for DB insertion."""
    rows = []
    for (u, v, key), row in edges_gdf.iterrows():
        geometry_wkt = row.geometry.wkt  # LINESTRING in SRID 4326

        rows.append(
            {
                "graph_u": int(u),
                "graph_v": int(v),
                "graph_key": int(key),
                "graph_version": GRAPH_VERSION,
                "geometry_wkt": geometry_wkt,
                "length_m": float(row.get("length", 0.0)),
                "name": _to_str(row.get("name")),
                "highway": _to_str(row.get("highway")),
                "cycleway": _to_str(row.get("cycleway")),
                "bicycle": _to_str(row.get("bicycle")),
                "surface": _to_str(row.get("surface")),
                "smoothness": _to_str(row.get("smoothness")),
                "osmid": _to_json(row.get("osmid")),
            }
        )
    return rows


UPSERT_SQL = """
INSERT INTO segment (
    graph_u, graph_v, graph_key, graph_version,
    geometry, length_m,
    name, highway, cycleway, bicycle, surface, smoothness,
    osmid
)
VALUES (
    %(graph_u)s, %(graph_v)s, %(graph_key)s, %(graph_version)s,
    ST_GeomFromText(%(geometry_wkt)s, 4326), %(length_m)s,
    %(name)s, %(highway)s, %(cycleway)s, %(bicycle)s, %(surface)s, %(smoothness)s,
    %(osmid)s::jsonb
)
ON CONFLICT (graph_u, graph_v, graph_key, graph_version)
DO NOTHING;
"""


def upsert_rows(rows):
    total = len(rows)
    print(f"\nConnecting to database {DB_HOST}:{DB_PORT}/{DB_NAME} ...")
    conn = psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD,
    )

    inserted = 0
    skipped = 0

    try:
        with conn:
            with conn.cursor() as cur:
                for batch_start in range(0, total, BATCH_SIZE):
                    batch = rows[batch_start : batch_start + BATCH_SIZE]
                    batch_end = batch_start + len(batch)

                    # executemany does not give us per-row rowcount for DO NOTHING,
                    # so we execute individually inside the batch to track skips.
                    before = cur.rowcount if cur.rowcount != -1 else 0
                    for row_dict in batch:
                        cur.execute(UPSERT_SQL, row_dict)
                        if cur.rowcount == 1:
                            inserted += 1
                        else:
                            skipped += 1

                    print(
                        f"  Batch {batch_start + 1}–{batch_end} / {total} "
                        f"(running inserted={inserted}, skipped={skipped})"
                    )

    finally:
        conn.close()

    return inserted, skipped


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def main():
    G = fetch_graph()
    edges_gdf = project_and_extract(G)
    rows = build_rows(edges_gdf)

    total = len(rows)
    print(f"\nTotal edges to upsert: {total:,}")

    inserted, skipped = upsert_rows(rows)

    print("\n--- Summary ---")
    print(f"  Total edges found : {total:,}")
    print(f"  Rows inserted     : {inserted:,}")
    print(f"  Rows skipped      : {skipped:,}")
    print("Done.")


if __name__ == "__main__":
    main()
