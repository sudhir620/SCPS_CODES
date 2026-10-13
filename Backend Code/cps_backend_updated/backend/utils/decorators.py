from functools import wraps
from flask import jsonify, request, session, redirect, url_for
from flask_jwt_extended import jwt_required, get_jwt, verify_jwt_in_request
from utils.db_utils import execute_query
import traceback

def role_required(allowed_roles):
    def wrapper(fn):
        @wraps(fn)
        def decorator(*args, **kwargs):
            verify_jwt_in_request()
            if get_jwt().get("role_name") not in allowed_roles:
                return jsonify({"message": "Unauthorized"}), 403
            return fn(*args, **kwargs)
        return decorator
    return wrapper


def handle_exceptions(func):
    @wraps(func)
    def wrapper(*args, **kwargs):
        try:
            return func(*args, **kwargs)
        except Exception as e:
            print("Error >>> ", e.__traceback__.tb_lineno, " >>> ", traceback.format_exc())
            return jsonify({"msg": "Something went wrong!!!"}), 500
    return wrapper

def jwt_required_with_login_check(fn):
    @jwt_required()
    @wraps(fn)
    def wrapper(*args, **kwargs):
        if not (login_data := execute_query("SELECT * FROM login_access WHERE user_id = %s and active = 1;", args=(get_jwt().get("ID", 0)), is_fetchone=True)):
            print("RRRRRRRRRRRRRRRRR", login_data)
            return jsonify({"msg": "You are not logged in, Please login first!!!"}), 400
        
        if (auth_header := request.headers.get('Authorization')) and auth_header.startswith("Bearer "):
            if auth_header.split(" ")[1] != login_data["access_token"]:
                return jsonify({"message": "Token miss matched!!!"}), 403
        else:
            return jsonify({"message": "Unauthorized"}), 403
        return fn(*args, **kwargs)
    return wrapper

def is_loggedin(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            token = session.get('token')
            if not token:
                return redirect(url_for('auth_view.login'))
            return fn(*args, **kwargs)
        except Exception as e:
            return redirect(url_for('auth_view.login'))
    return wrapper
