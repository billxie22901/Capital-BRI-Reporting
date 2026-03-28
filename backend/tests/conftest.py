"""
pytest fixtures shared across the Capital BRI integration test suite.

All tests hit the live Docker stack (Flask on :8080, Postgres on :5432).
No mocks are used.
"""

import pytest
import requests


# ---------------------------------------------------------------------------
# Base URL
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def api_base() -> str:
    """Return the root URL of the running Flask container."""
    return "http://localhost:8080"


# ---------------------------------------------------------------------------
# Auth token (session-scoped so we only log in once per test run)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def auth_token(api_base: str) -> str:
    """
    POST /auth/login with demo/demo credentials and return the raw JWT string.

    Raises AssertionError if the server does not respond with 200 + a token,
    which would indicate the Docker stack is not running or the endpoint is
    broken at setup time.
    """
    resp = requests.post(
        f"{api_base}/auth/login",
        json={"username": "demo", "password": "demo"},
        timeout=10,
    )
    assert resp.status_code == 200, (
        f"auth/login setup failed (status {resp.status_code}): {resp.text}"
    )
    body = resp.json()
    assert "access_token" in body, f"No access_token in login response: {body}"
    return body["access_token"]


# ---------------------------------------------------------------------------
# A real segment ID from the seed data
# ---------------------------------------------------------------------------

# DC bounding box that comfortably covers all five seeded segments.
_DC_BBOX = "-77.06,38.88,-76.99,38.92"


@pytest.fixture(scope="session")
def a_segment_id(api_base: str) -> str:
    """
    GET /segments with a DC bounding box and return the first segment's id.

    This gives every test that needs a valid segment UUID a real value from
    the seeded database rather than a hard-coded constant.
    """
    resp = requests.get(
        f"{api_base}/segments",
        params={"bbox": _DC_BBOX},
        timeout=10,
    )
    assert resp.status_code == 200, (
        f"segments setup failed (status {resp.status_code}): {resp.text}"
    )
    segments = resp.json().get("segments", [])
    assert segments, "No segments returned for DC bbox — seed data may be missing"
    return segments[0]["id"]
