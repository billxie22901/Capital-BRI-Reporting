from flask import Flask
from .config import Config
from .db import close_db
from .routes.health import health_bp
from .routes.auth import auth_bp
from .routes.reports import reports_bp
from .routes.segments import segments_bp


def create_app():
    app = Flask(__name__)
    app.config.from_object(Config)

    app.teardown_appcontext(close_db)

    app.register_blueprint(health_bp)
    app.register_blueprint(auth_bp)
    app.register_blueprint(reports_bp)
    app.register_blueprint(segments_bp)

    return app
