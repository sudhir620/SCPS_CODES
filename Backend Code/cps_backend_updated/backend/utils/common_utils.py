def check_required_fields(data: dict, required_fields: list) -> tuple[bool, str]:
    if isinstance(data, dict):
        na_fields = required_fields
        for key, value in data.items():
            if key in required_fields and value is not None:
                na_fields.remove(key)
        if len(na_fields) >0:
            return False, f"{", ".join(na_fields)} {"are" if len(na_fields)>1 else "is"} required!"
        return True, "All fileds available"
    return False, "Something went wrong!"

#<------------------------ RAZORPAY CLIENT SETUP START ----------------------->#

from flask import current_app
import razorpay

razorpay_client = None

def create_razorpay_client():
    key_id = current_app.config.get("RAZORPAY_KEY_ID")
    key_secret = current_app.config.get("RAZORPAY_KEY_SECRET")

    if not key_id or not key_secret:
        raise RuntimeError("Razorpay API keys not configured!")
    
    return razorpay.Client(auth=(key_id, key_secret))

def get_razorpay_client():
    global razorpay_client

    if razorpay_client is None:
        razorpay_client = create_razorpay_client()

    return razorpay_client

#<------------------------ RAZORPAY CLIENT SETUP END ----------------------->#
from datetime import datetime, timedelta, time
def timedelta_to_time(value):
    total_seconds = int(value.total_seconds())

    hours = total_seconds // 3600
    minutes = (total_seconds % 3600) // 60
    seconds = total_seconds % 60

    return time(
        hour=hours,
        minute=minutes,
        second=seconds
    )