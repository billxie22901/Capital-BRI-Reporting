import os


class Config:
    DATABASE_URL = os.environ["DATABASE_URL"]
    SECRET_KEY = os.environ.get("SECRET_KEY", "dev-secret-key")
    FLASK_ENV = os.environ.get("FLASK_ENV", "development")

    # Graph version served by /segments endpoints.
    # Update this after each OSMnx precalc ingest run.
    CURRENT_GRAPH_VERSION = os.environ.get("GRAPH_VERSION", "dc-2026-03-28")
