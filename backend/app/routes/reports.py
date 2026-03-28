import uuid
from datetime import datetime, timezone

from flask import Blueprint, current_app, jsonify, request

from ..db import get_db
from .auth import require_auth

reports_bp = Blueprint("reports", __name__)

# ---------------------------------------------------------------------------
# Locked enum sets — must stay in sync with 02-feature-specification.md §3
# and 11-api-contract-mvp.md §2.3
# ---------------------------------------------------------------------------
_ENUMS = {
    "impediment_duration": {"short_term", "long_term"},
    "traffic_level": {"light", "moderate", "heavy", "gridlock"},
    "hazard_types": {
        "pothole", "cracked_surface", "loose_gravel", "sand", "glass",
        "debris", "flooding", "construction", "vehicle_blocking", "other",
    },
    "conditions": {"dry", "wet", "icy", "snowy", "muddy"},
    "infrastructure": {
        "painted_lane", "protected_lane", "shared_road", "shared_path",
        "contraflow_lane", "door_zone_lane", "none", "other",
    },
    "bike_lane_availability": {"clear", "partial", "blocked", "absent"},
}


def _check_scalar(value, field):
    if value is not None and value not in _ENUMS[field]:
        return f"Unknown value '{value}'. Allowed: {sorted(_ENUMS[field])}"
    return None


def _check_array(values, field):
    if values is not None:
        for v in values:
            if v not in _ENUMS[field]:
                return f"Unknown value '{v}'. Allowed: {sorted(_ENUMS[field])}"
    return None


# ---------------------------------------------------------------------------
# POST /reports
# ---------------------------------------------------------------------------
@reports_bp.post("/reports")
@require_auth
def create_report():
    data = request.get_json(silent=True)
    if not data:
        return jsonify({"error": "invalid_json"}), 400

    # Required fields
    if not data.get("segment_id"):
        return jsonify({"error": "validation_failed", "details": {"segment_id": "required"}}), 400
    if not data.get("captured_at"):
        return jsonify({"error": "validation_failed", "details": {"captured_at": "required"}}), 400

    # At least one qualifying field
    hazard_types = data.get("hazard_types") or []
    conditions = data.get("conditions") or []
    has_qualifier = (
        len(hazard_types) > 0
        or len(conditions) > 0
        or data.get("cycling_experience_rating") is not None
        or data.get("pleasantness_rating") is not None
        or bool((data.get("note") or "").strip())
    )
    if not has_qualifier:
        return jsonify({
            "error": "required_field_missing",
            "details": "At least one of hazard_types, conditions, cycling_experience_rating, pleasantness_rating, or note is required",
        }), 422

    # Validate segment UUID and existence
    try:
        segment_uuid = uuid.UUID(str(data["segment_id"]))
    except ValueError:
        return jsonify({"error": "validation_failed", "details": {"segment_id": "must be a UUID"}}), 400

    db = get_db()
    with db.cursor() as cur:
        cur.execute("SELECT id FROM segment WHERE id = %s", (str(segment_uuid),))
        if cur.fetchone() is None:
            return jsonify({"error": "validation_failed", "details": {"segment_id": "unknown segment"}}), 400

    # Validate enums
    errors = {}
    for field in ("impediment_duration", "traffic_level", "infrastructure", "bike_lane_availability"):
        err = _check_scalar(data.get(field), field)
        if err:
            errors[field] = err
    for field in ("hazard_types", "conditions"):
        err = _check_array(data.get(field), field)
        if err:
            errors[field] = err
    if errors:
        return jsonify({"error": "validation_failed", "details": errors}), 400

    # Validate ratings
    for field in ("cycling_experience_rating", "pleasantness_rating"):
        val = data.get(field)
        if val is not None:
            if not isinstance(val, int) or not (1 <= val <= 10):
                return jsonify({
                    "error": "validation_failed",
                    "details": {field: "must be an integer between 1 and 10"},
                }), 400

    # Parse captured_at
    try:
        captured_at = datetime.fromisoformat(data["captured_at"].replace("Z", "+00:00"))
    except (ValueError, AttributeError):
        return jsonify({"error": "validation_failed", "details": {"captured_at": "must be ISO 8601"}}), 400

    # Insert
    with db.cursor() as cur:
        cur.execute(
            """
            INSERT INTO report (
                segment_id, captured_at, pct_along_segment,
                impediment_duration, traffic_level,
                hazard_types, conditions,
                infrastructure, bike_lane_availability,
                cycling_experience_rating, pleasantness_rating,
                client_lat, client_lon, note, raw_payload
            ) VALUES (
                %s, %s, %s,
                %s, %s,
                %s, %s,
                %s, %s,
                %s, %s,
                %s, %s, %s, %s
            )
            RETURNING id, created_at
            """,
            (
                str(segment_uuid),
                captured_at,
                data.get("pct_along_segment"),
                data.get("impediment_duration"),
                data.get("traffic_level"),
                data.get("hazard_types") or None,
                data.get("conditions") or None,
                data.get("infrastructure"),
                data.get("bike_lane_availability"),
                data.get("cycling_experience_rating"),
                data.get("pleasantness_rating"),
                data.get("client_lat"),
                data.get("client_lon"),
                (data.get("note") or "").strip() or None,
                data.get("raw_payload"),
            ),
        )
        row = cur.fetchone()
        db.commit()

    return jsonify({
        "id": str(row["id"]),
        "created_at": row["created_at"].isoformat(),
    }), 201


# ---------------------------------------------------------------------------
# GET /reports
# ---------------------------------------------------------------------------
@reports_bp.get("/reports")
@require_auth
def list_reports():
    limit = min(int(request.args.get("limit", 20)), 100)
    segment_id = request.args.get("segment_id")
    bbox_raw = request.args.get("bbox")

    conditions_list = []
    params = []

    if segment_id:
        try:
            uuid.UUID(segment_id)
        except ValueError:
            return jsonify({"error": "invalid_segment_id"}), 400
        conditions_list.append("r.segment_id = %s")
        params.append(segment_id)

    if bbox_raw:
        try:
            parts = bbox_raw.split(",")
            if len(parts) != 4:
                raise ValueError
            min_lon, min_lat, max_lon, max_lat = map(float, parts)
        except ValueError:
            return jsonify({"error": "invalid_bbox", "details": "format: minLon,minLat,maxLon,maxLat"}), 400
        conditions_list.append(
            "ST_Intersects(s.geometry, ST_MakeEnvelope(%s, %s, %s, %s, 4326))"
        )
        params.extend([min_lon, min_lat, max_lon, max_lat])

    where = ("WHERE " + " AND ".join(conditions_list)) if conditions_list else ""
    join = "JOIN segment s ON s.id = r.segment_id" if bbox_raw else ""
    params.append(limit)

    sql = f"""
        SELECT
            r.id, r.segment_id, r.created_at, r.captured_at,
            r.impediment_duration, r.traffic_level,
            r.hazard_types, r.conditions,
            r.infrastructure, r.bike_lane_availability,
            r.cycling_experience_rating, r.pleasantness_rating,
            r.pct_along_segment, r.note
        FROM report r
        {join}
        {where}
        ORDER BY r.created_at DESC
        LIMIT %s
    """

    db = get_db()
    with db.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()

    result = []
    for row in rows:
        result.append({
            "id": str(row["id"]),
            "segment_id": str(row["segment_id"]),
            "created_at": row["created_at"].isoformat(),
            "captured_at": row["captured_at"].isoformat(),
            "impediment_duration": row["impediment_duration"],
            "traffic_level": row["traffic_level"],
            "hazard_types": row["hazard_types"],
            "conditions": row["conditions"],
            "infrastructure": row["infrastructure"],
            "bike_lane_availability": row["bike_lane_availability"],
            "cycling_experience_rating": row["cycling_experience_rating"],
            "pleasantness_rating": row["pleasantness_rating"],
            "pct_along_segment": row["pct_along_segment"],
            "note": row["note"],
            # client_lat/client_lon intentionally omitted
        })

    return jsonify(result)
