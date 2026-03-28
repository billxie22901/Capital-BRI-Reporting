import datetime
from functools import wraps

import jwt
from flask import Blueprint, current_app, jsonify, request

auth_bp = Blueprint("auth", __name__)


def require_auth(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return jsonify({"error": "missing_token"}), 401
        token = header[len("Bearer "):]
        try:
            jwt.decode(token, current_app.config["SECRET_KEY"], algorithms=["HS256"])
        except jwt.InvalidTokenError:
            return jsonify({"error": "invalid_token"}), 401
        return f(*args, **kwargs)

    return decorated


@auth_bp.post("/auth/login")
def login():
    data = request.get_json(silent=True)
    if not data or not data.get("username") or not data.get("password"):
        return jsonify({"error": "invalid_credentials"}), 401

    # Lab: accept any non-empty username/password (demo/demo or anything).
    token = jwt.encode(
        {
            "sub": data["username"],
            "iat": datetime.datetime.utcnow(),
            "exp": datetime.datetime.utcnow() + datetime.timedelta(days=1),
        },
        current_app.config["SECRET_KEY"],
        algorithm="HS256",
    )
    return jsonify(
        {
            "access_token": token,
            "token_type": "Bearer",
            "expires_in": 86400,
        }
    )
