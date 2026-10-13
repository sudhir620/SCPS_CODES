"""
S3 utility for uploading and generating URLs for vehicle plate images and ANPR captures.
"""
import os
import uuid
import boto3
from botocore.exceptions import ClientError
from flask import current_app
from werkzeug.datastructures import FileStorage
from io import BytesIO


def get_s3_client():
    return boto3.client(
        "s3",
        aws_access_key_id=current_app.config.get("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=current_app.config.get("AWS_SECRET_ACCESS_KEY"),
        region_name=current_app.config.get("AWS_REGION", "ap-south-1"),
    )


def upload_fileobj(file_obj, folder: str = "vehicles", content_type: str = "image/jpeg") -> str | None:
    return None
    """
    Upload a file-like object to S3 and return the public URL.
    folder examples: vehicles, anpr, plates
    """
    try:
        bucket = current_app.config["S3_BUCKET"]
        base_url = current_app.config.get("S3_BASE_URL") or f"https://{bucket}.s3.{current_app.config.get('AWS_REGION', 'ap-south-1')}.amazonaws.com"

        ext = "jpg"
        if hasattr(file_obj, "filename") and file_obj.filename:
            name = file_obj.filename.lower()
            if name.endswith(".png"):
                ext = "png"
            elif name.endswith(".jpeg") or name.endswith(".jpg"):
                ext = "jpg"
            elif name.endswith(".webp"):
                ext = "webp"

        key = f"{folder}/{uuid.uuid4().hex}.{ext}"
        client = get_s3_client()

        extra_args = {"ContentType": content_type}
        # Uncomment if you want objects public-read (or use bucket policy / CloudFront)
        # extra_args["ACL"] = "public-read"

        if isinstance(file_obj, FileStorage):
            file_obj.seek(0)
            client.upload_fileobj(file_obj, bucket, key, ExtraArgs=extra_args)
        else:
            client.upload_fileobj(file_obj, bucket, key, ExtraArgs=extra_args)

        return f"{base_url.rstrip('/')}/{key}"
    except ClientError as e:
        print("S3 Upload Error:", e)
        return None
    except Exception as e:
        print("S3 Upload Exception:", e)
        return None


def upload_bytes(data: bytes, folder: str = "anpr", content_type: str = "image/jpeg") -> str | None:
    """Upload raw bytes (e.g. from ESP32-CAM base64 decoded image)."""
    return upload_fileobj(BytesIO(data), folder=folder, content_type=content_type)


def delete_object(url_or_key: str) -> bool:
    """Delete an object from S3 given full URL or key."""
    try:
        bucket = current_app.config["S3_BUCKET"]
        key = url_or_key
        if "amazonaws.com/" in url_or_key:
            key = url_or_key.split("amazonaws.com/")[-1]
        get_s3_client().delete_object(Bucket=bucket, Key=key)
        return True
    except Exception as e:
        print("S3 Delete Error:", e)
        return False
