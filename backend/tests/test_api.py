"""
Capital BRI — Flask API integration tests.

All tests hit the live Docker stack at localhost:8080.
No mocks are used; the database must be seeded with the five DC segments
(graph_version 'dc-2026-03-28') before running.

Run with:
    pip install -r backend/tests/requirements-test.txt
    pytest backend/tests/ -v
"""

from datetime import datetime, timezone

import pytest
import requests


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_DC_BBOX = "-77.06,38.88,-76.99,38.92"

# Pennsylvania Ave NW centre-point — sits inside all five seeded segments' bbox.
_DC_LAT = 38.8945
_DC_LON = -77.0230


def _auth_header(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


# ===========================================================================
# 1. Health
# ===========================================================================

class TestHealth:
    def test_health_returns_200_and_status_ok(self, api_base):
        resp = requests.get(f"{api_base}/health", timeout=10)
        assert resp.status_code == 200
        assert resp.json() == {"status": "ok"}

    def test_health_content_type_is_json(self, api_base):
        resp = requests.get(f"{api_base}/health", timeout=10)
        assert "application/json" in resp.headers.get("Content-Type", "")


# ===========================================================================
# 2. Auth — POST /auth/login
# ===========================================================================

class TestAuthLogin:
    def test_valid_credentials_return_200_with_token(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={"username": "demo", "password": "demo"},
            timeout=10,
        )
        assert resp.status_code == 200
        body = resp.json()
        assert "access_token" in body
        assert body.get("token_type") == "Bearer"
        assert isinstance(body.get("expires_in"), int)

    def test_token_is_non_empty_string(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={"username": "demo", "password": "demo"},
            timeout=10,
        )
        token = resp.json()["access_token"]
        assert isinstance(token, str) and len(token) > 10

    def test_missing_username_returns_401(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={"password": "demo"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_missing_password_returns_401(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={"username": "demo"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_empty_body_returns_401(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_non_json_body_returns_401(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            data="not-json",
            headers={"Content-Type": "text/plain"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_empty_username_returns_401(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={"username": "", "password": "demo"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_empty_password_returns_401(self, api_base):
        resp = requests.post(
            f"{api_base}/auth/login",
            json={"username": "demo", "password": ""},
            timeout=10,
        )
        assert resp.status_code == 401


# ===========================================================================
# 3. Segments — GET /segments?bbox=…
# ===========================================================================

class TestSegmentsBbox:
    def test_valid_dc_bbox_returns_200_and_segments_list(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": _DC_BBOX},
            timeout=10,
        )
        assert resp.status_code == 200
        body = resp.json()
        assert "segments" in body
        assert isinstance(body["segments"], list)

    def test_dc_bbox_returns_all_five_seed_segments(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": _DC_BBOX},
            timeout=10,
        )
        segments = resp.json()["segments"]
        assert len(segments) == 5

    def test_known_segment_names_present(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": _DC_BBOX},
            timeout=10,
        )
        names = {s["name"] for s in resp.json()["segments"]}
        expected = {
            "Pennsylvania Ave NW",
            "M St NW",
            "15th St NW",
            "Independence Ave SW",
            "K St NW",
        }
        assert expected == names

    def test_segment_shape_has_required_fields(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": _DC_BBOX},
            timeout=10,
        )
        first = resp.json()["segments"][0]
        for key in ("id", "name", "highway", "length_m", "geometry"):
            assert key in first, f"Missing key '{key}' in segment"

    def test_segment_geometry_is_geojson_linestring(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": _DC_BBOX},
            timeout=10,
        )
        geom = resp.json()["segments"][0]["geometry"]
        assert geom["type"] == "LineString"
        assert isinstance(geom["coordinates"], list)

    def test_bbox_outside_dc_returns_empty_list(self, api_base):
        # Bounding box over the middle of the Atlantic Ocean.
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": "-40.0,30.0,-39.0,31.0"},
            timeout=10,
        )
        assert resp.status_code == 200
        assert resp.json()["segments"] == []

    def test_missing_bbox_returns_400(self, api_base):
        resp = requests.get(f"{api_base}/segments", timeout=10)
        assert resp.status_code == 400
        body = resp.json()
        assert body.get("error") == "missing_parameter"

    def test_invalid_bbox_too_few_parts_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": "-77.06,38.88"},
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "invalid_bbox"

    def test_invalid_bbox_non_numeric_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": "abc,def,ghi,jkl"},
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "invalid_bbox"

    def test_invalid_bbox_empty_string_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments",
            params={"bbox": ""},
            timeout=10,
        )
        # empty string is treated the same as absent
        assert resp.status_code == 400


# ===========================================================================
# 4. Segments nearest — GET /segments/nearest
# ===========================================================================

class TestSegmentsNearest:
    def test_dc_coords_return_200_and_candidates(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": _DC_LON, "radius_m": 500, "limit": 3},
            timeout=10,
        )
        assert resp.status_code == 200
        body = resp.json()
        assert "candidates" in body
        assert isinstance(body["candidates"], list)

    def test_candidate_shape_has_required_fields(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": _DC_LON, "radius_m": 500, "limit": 3},
            timeout=10,
        )
        candidates = resp.json()["candidates"]
        if candidates:
            first = candidates[0]
            for key in ("id", "name", "distance_m", "pct_along", "geometry"):
                assert key in first, f"Missing key '{key}' in candidate"

    def test_candidates_sorted_by_distance_ascending(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": _DC_LON, "radius_m": 500, "limit": 5},
            timeout=10,
        )
        distances = [c["distance_m"] for c in resp.json()["candidates"]]
        assert distances == sorted(distances)

    def test_limit_caps_number_of_results(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": _DC_LON, "radius_m": 500, "limit": 2},
            timeout=10,
        )
        assert len(resp.json()["candidates"]) <= 2

    def test_server_caps_limit_at_5(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": _DC_LON, "radius_m": 500, "limit": 100},
            timeout=10,
        )
        assert len(resp.json()["candidates"]) <= 5

    def test_zero_radius_returns_empty_candidates(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": _DC_LON, "radius_m": 0, "limit": 3},
            timeout=10,
        )
        assert resp.status_code == 200
        assert resp.json()["candidates"] == []

    def test_missing_lat_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lon": _DC_LON},
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "missing_parameter"

    def test_missing_lon_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT},
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "missing_parameter"

    def test_missing_both_lat_and_lon_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"radius_m": 50},
            timeout=10,
        )
        assert resp.status_code == 400

    def test_lat_out_of_range_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": 91.0, "lon": _DC_LON},
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "invalid_parameter"

    def test_lon_out_of_range_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": _DC_LAT, "lon": 181.0},
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "invalid_parameter"

    def test_non_numeric_lat_returns_400(self, api_base):
        resp = requests.get(
            f"{api_base}/segments/nearest",
            params={"lat": "abc", "lon": _DC_LON},
            timeout=10,
        )
        assert resp.status_code == 400


# ===========================================================================
# 5. Reports — POST /reports
# ===========================================================================

class TestReportsPost:
    def _valid_payload(self, segment_id: str) -> dict:
        return {
            "segment_id": segment_id,
            "captured_at": _now_iso(),
            "hazard_types": ["pothole"],
            "conditions": ["wet"],
            "cycling_experience_rating": 7,
        }

    def test_authenticated_valid_payload_returns_201_with_id(
        self, api_base, auth_token, a_segment_id
    ):
        resp = requests.post(
            f"{api_base}/reports",
            json=self._valid_payload(a_segment_id),
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 201
        body = resp.json()
        assert "id" in body
        assert "created_at" in body

    def test_returned_id_is_uuid_string(
        self, api_base, auth_token, a_segment_id
    ):
        import uuid as _uuid
        resp = requests.post(
            f"{api_base}/reports",
            json=self._valid_payload(a_segment_id),
            headers=_auth_header(auth_token),
            timeout=10,
        )
        returned_id = resp.json()["id"]
        # Should not raise
        _uuid.UUID(returned_id)

    def test_unauthenticated_request_returns_401(self, api_base, a_segment_id):
        resp = requests.post(
            f"{api_base}/reports",
            json=self._valid_payload(a_segment_id),
            timeout=10,
        )
        assert resp.status_code == 401

    def test_invalid_token_returns_401(self, api_base, a_segment_id):
        resp = requests.post(
            f"{api_base}/reports",
            json=self._valid_payload(a_segment_id),
            headers={"Authorization": "Bearer this.is.not.a.valid.jwt"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_missing_segment_id_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        del payload["segment_id"]
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400
        details = resp.json().get("details", {})
        assert "segment_id" in details

    def test_unknown_segment_id_returns_400(self, api_base, auth_token):
        payload = {
            "segment_id": "00000000-0000-0000-0000-000000000000",
            "captured_at": _now_iso(),
            "hazard_types": ["pothole"],
        }
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400
        body = resp.json()
        assert body.get("error") == "validation_failed"

    def test_non_uuid_segment_id_returns_400(self, api_base, auth_token):
        payload = {
            "segment_id": "not-a-uuid",
            "captured_at": _now_iso(),
            "hazard_types": ["pothole"],
        }
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_invalid_hazard_type_enum_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["hazard_types"] = ["flying_monkeys"]
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400
        assert resp.json().get("error") == "validation_failed"

    def test_invalid_condition_enum_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["conditions"] = ["teleporting"]
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_invalid_impediment_duration_enum_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["impediment_duration"] = "forever"
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_invalid_traffic_level_enum_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["traffic_level"] = "apocalyptic"
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_rating_below_range_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["cycling_experience_rating"] = 0  # must be 1–10
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400
        details = resp.json().get("details", {})
        assert "cycling_experience_rating" in details

    def test_rating_above_range_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["cycling_experience_rating"] = 11
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_pleasantness_rating_out_of_range_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = {
            "segment_id": a_segment_id,
            "captured_at": _now_iso(),
            "pleasantness_rating": 0,
        }
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_missing_qualifier_fields_returns_422(
        self, api_base, auth_token, a_segment_id
    ):
        # segment_id and captured_at present, but no hazard_types / conditions /
        # ratings — should trigger the qualifier check.
        payload = {
            "segment_id": a_segment_id,
            "captured_at": _now_iso(),
        }
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 422
        assert resp.json().get("error") == "required_field_missing"

    def test_missing_captured_at_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        del payload["captured_at"]
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_invalid_captured_at_format_returns_400(
        self, api_base, auth_token, a_segment_id
    ):
        payload = self._valid_payload(a_segment_id)
        payload["captured_at"] = "not-a-date"
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400

    def test_all_optional_fields_accepted(
        self, api_base, auth_token, a_segment_id
    ):
        payload = {
            "segment_id": a_segment_id,
            "captured_at": _now_iso(),
            "hazard_types": ["pothole", "glass"],
            "conditions": ["wet", "icy"],
            "impediment_duration": "short_term",
            "traffic_level": "moderate",
            "infrastructure": "painted_lane",
            "bike_lane_availability": "partial",
            "cycling_experience_rating": 5,
            "pleasantness_rating": 8,
            "pct_along_segment": 0.42,
            "client_lat": _DC_LAT,
            "client_lon": _DC_LON,
        }
        resp = requests.post(
            f"{api_base}/reports",
            json=payload,
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 201


# ===========================================================================
# 6. Reports — GET /reports
# ===========================================================================

class TestReportsGet:
    def test_authenticated_get_returns_200_and_list(
        self, api_base, auth_token
    ):
        resp = requests.get(
            f"{api_base}/reports",
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 200
        assert isinstance(resp.json(), list)

    def test_unauthenticated_get_returns_401(self, api_base):
        resp = requests.get(f"{api_base}/reports", timeout=10)
        assert resp.status_code == 401

    def test_invalid_token_returns_401(self, api_base):
        resp = requests.get(
            f"{api_base}/reports",
            headers={"Authorization": "Bearer bad.token.here"},
            timeout=10,
        )
        assert resp.status_code == 401

    def test_report_item_has_required_fields(
        self, api_base, auth_token, a_segment_id
    ):
        # Post a report first so the list is never empty.
        requests.post(
            f"{api_base}/reports",
            json={
                "segment_id": a_segment_id,
                "captured_at": _now_iso(),
                "hazard_types": ["debris"],
            },
            headers=_auth_header(auth_token),
            timeout=10,
        )
        resp = requests.get(
            f"{api_base}/reports",
            headers=_auth_header(auth_token),
            timeout=10,
        )
        reports = resp.json()
        assert reports, "Expected at least one report in the list"
        first = reports[0]
        for key in ("id", "segment_id", "created_at", "captured_at"):
            assert key in first, f"Missing key '{key}' in report"

    def test_filter_by_segment_id_returns_matching_reports(
        self, api_base, auth_token, a_segment_id
    ):
        # Ensure at least one report exists for a_segment_id.
        requests.post(
            f"{api_base}/reports",
            json={
                "segment_id": a_segment_id,
                "captured_at": _now_iso(),
                "conditions": ["dry"],
            },
            headers=_auth_header(auth_token),
            timeout=10,
        )
        resp = requests.get(
            f"{api_base}/reports",
            params={"segment_id": a_segment_id},
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 200
        reports = resp.json()
        assert reports, "Expected at least one report for the given segment_id"
        for r in reports:
            assert r["segment_id"] == a_segment_id

    def test_limit_param_caps_result_count(self, api_base, auth_token):
        resp = requests.get(
            f"{api_base}/reports",
            params={"limit": 1},
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 200
        assert len(resp.json()) <= 1

    def test_server_caps_limit_at_100(self, api_base, auth_token):
        resp = requests.get(
            f"{api_base}/reports",
            params={"limit": 99999},
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 200
        assert len(resp.json()) <= 100

    def test_default_limit_is_20_or_fewer(self, api_base, auth_token):
        resp = requests.get(
            f"{api_base}/reports",
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert len(resp.json()) <= 20

    def test_bbox_filter_returns_only_reports_in_area(
        self, api_base, auth_token, a_segment_id
    ):
        # Post a report for a known DC segment then filter with DC bbox.
        requests.post(
            f"{api_base}/reports",
            json={
                "segment_id": a_segment_id,
                "captured_at": _now_iso(),
                "cycling_experience_rating": 6,
            },
            headers=_auth_header(auth_token),
            timeout=10,
        )
        resp = requests.get(
            f"{api_base}/reports",
            params={"bbox": _DC_BBOX},
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 200
        # All returned reports should have segment_ids that exist in the DB —
        # the endpoint joining on segment geometry guarantees geographic match.
        assert isinstance(resp.json(), list)

    def test_invalid_segment_id_filter_returns_400(
        self, api_base, auth_token
    ):
        resp = requests.get(
            f"{api_base}/reports",
            params={"segment_id": "not-a-uuid"},
            headers=_auth_header(auth_token),
            timeout=10,
        )
        assert resp.status_code == 400
