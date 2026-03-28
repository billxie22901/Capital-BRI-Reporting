from flask import Blueprint, current_app, jsonify, request

from ..db import get_db

segments_bp = Blueprint("segments", __name__)


# ---------------------------------------------------------------------------
# GET /segments?bbox=minLon,minLat,maxLon,maxLat
# ---------------------------------------------------------------------------
@segments_bp.get("/segments")
def get_segments():
    bbox_raw = request.args.get("bbox")
    if not bbox_raw:
        return jsonify({"error": "missing_parameter", "details": {"bbox": "required"}}), 400

    try:
        parts = bbox_raw.split(",")
        if len(parts) != 4:
            raise ValueError
        min_lon, min_lat, max_lon, max_lat = map(float, parts)
    except ValueError:
        return jsonify({"error": "invalid_bbox", "details": "format: minLon,minLat,maxLon,maxLat"}), 400

    version = current_app.config["CURRENT_GRAPH_VERSION"]
    db = get_db()

    with db.cursor() as cur:
        cur.execute(
            """
            SELECT
                id::text,
                name,
                highway,
                cycleway,
                bicycle,
                length_m,
                ST_AsGeoJSON(geometry)::json AS geometry
            FROM segment
            WHERE graph_version = %s
              AND ST_Intersects(
                  geometry,
                  ST_MakeEnvelope(%s, %s, %s, %s, 4326)
              )
            LIMIT 500
            """,
            (version, min_lon, min_lat, max_lon, max_lat),
        )
        rows = cur.fetchall()

    segments = [
        {
            "id": row["id"],
            "name": row["name"],
            "highway": row["highway"],
            "cycleway": row["cycleway"],
            "bicycle": row["bicycle"],
            "length_m": row["length_m"],
            "geometry": row["geometry"],
        }
        for row in rows
    ]
    return jsonify({"segments": segments})


# ---------------------------------------------------------------------------
# GET /segments/nearest?lat=&lon=&radius_m=&limit=
# ---------------------------------------------------------------------------
@segments_bp.get("/segments/nearest")
def nearest_segments():
    lat_raw = request.args.get("lat")
    lon_raw = request.args.get("lon")

    if not lat_raw or not lon_raw:
        return jsonify({"error": "missing_parameter", "details": "lat and lon are required"}), 400

    try:
        lat = float(lat_raw)
        lon = float(lon_raw)
    except ValueError:
        return jsonify({"error": "invalid_parameter", "details": "lat and lon must be numeric"}), 400

    if not (-90 <= lat <= 90) or not (-180 <= lon <= 180):
        return jsonify({"error": "invalid_parameter", "details": "lat/lon outside WGS84 range"}), 400

    radius_m = min(int(request.args.get("radius_m", 50)), 200)
    limit = min(int(request.args.get("limit", 3)), 5)
    version = current_app.config["CURRENT_GRAPH_VERSION"]

    db = get_db()
    with db.cursor() as cur:
        cur.execute(
            """
            SELECT
                id::text,
                name,
                ST_AsGeoJSON(geometry)::json AS geometry,
                ST_Distance(
                    geometry::geography,
                    ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography
                ) AS distance_m,
                ST_LineLocatePoint(
                    geometry,
                    ST_ClosestPoint(geometry, ST_SetSRID(ST_MakePoint(%s, %s), 4326))
                ) AS pct_along
            FROM segment
            WHERE graph_version = %s
              AND ST_DWithin(
                  geometry::geography,
                  ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography,
                  %s
              )
            ORDER BY distance_m ASC
            LIMIT %s
            """,
            (lon, lat, lon, lat, version, lon, lat, radius_m, limit),
        )
        rows = cur.fetchall()

    candidates = [
        {
            "id": row["id"],
            "name": row["name"],
            "distance_m": round(float(row["distance_m"]), 1),
            "pct_along": round(float(row["pct_along"]), 3),
            "geometry": row["geometry"],
        }
        for row in rows
    ]
    return jsonify({"candidates": candidates})
