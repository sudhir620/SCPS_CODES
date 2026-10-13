import os
import pymysql
from datetime import timedelta
from dotenv import load_dotenv

load_dotenv()  # Load variables from .env


class Config:
    SECRET_KEY = os.getenv("SECRET_KEY")
    JWT_SECRET_KEY = os.getenv("JWT_SECRET_KEY")
    JWT_ACCESS_TOKEN_EXPIRES = timedelta(
        days=int(os.getenv("JWT_ACCESS_TOKEN_EXPIRES_DAYS", 1))
    )

    # SMTP (Email OTP)
    SMTP_EMAIL_ADDRESS = os.getenv("EMAIL_ADDRESS")
    SMTP_EMAIL_PASSWORD = os.getenv("EMAIL_PASSWORD")

    # Razorpay
    RAZORPAY_KEY_ID = os.getenv("RAZORPAY_KEY_ID")
    RAZORPAY_KEY_SECRET = os.getenv("RAZORPAY_KEY_SECRET")

    # MySQL
    MYSQL_CONFIG = {
        "host": os.getenv("MYSQL_HOST"),
        "port": int(os.getenv("MYSQL_PORT", 3306)),
        "user": os.getenv("MYSQL_USER"),
        "password": os.getenv("MYSQL_PASSWORD"),
        "db": os.getenv("MYSQL_DB"),
        "cursorclass": pymysql.cursors.DictCursor,
        "autocommit": False,
    }

    # AWS S3
    AWS_ACCESS_KEY_ID = os.getenv("AWS_ACCESS_KEY_ID")
    AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY")
    AWS_REGION = os.getenv("AWS_REGION", "ap-south-1")
    S3_BUCKET = os.getenv("S3_BUCKET")
    S3_BASE_URL = os.getenv("S3_BASE_URL")  # optional custom CDN / public URL

    # IoT / Gate
    IOT_API_KEY = os.getenv("IOT_API_KEY", "change-this-iot-secret-key")


# create secret-key using this below command
"""
python -c "import secrets; print(secrets.token_urlsafe(64))"
"""
