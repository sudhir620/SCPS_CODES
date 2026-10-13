import os
from flask import Flask
from flask_jwt_extended import JWTManager
from utils.booking_scheduler import start_booking_scheduler
from config import Config
from db import get_db, close_db
from admin_panel.auth import auth_view
from admin_panel.user_routes import user_routes_view
from apis.routes import apis_bp

from utils.mqtt_manager import MQTTManager

app = Flask(__name__)
app.config.from_object(Config)

mqtt_manager = MQTTManager(app)
mqtt_manager.start()
app.extensions["mqtt_manager"] = mqtt_manager

jwt = JWTManager(app)

# Local fallback folder (used only if S3 is not configured)
UPLOAD_FOLDER = os.path.join("static", "images")
app.config["UPLOAD_FOLDER"] = UPLOAD_FOLDER
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


@app.before_request
def before_request():
    get_db()


@app.teardown_appcontext
def teardown(exception):
    if exception:
        print("DB_ERROR ::>>> ", exception)
    close_db()


app.register_blueprint(auth_view, url_prefix="/")
app.register_blueprint(user_routes_view, url_prefix="/admin")
app.register_blueprint(apis_bp, url_prefix="/api")

start_booking_scheduler(app, mqtt_manager)

if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=5000, use_reloader=False)
