import os
import base64
from flask import Blueprint, request, jsonify, current_app, url_for
from werkzeug.security import generate_password_hash, check_password_hash
from flask_jwt_extended import get_jwt, create_access_token
from utils.decorators import handle_exceptions, jwt_required_with_login_check, role_required
from utils.db_utils import execute_query, build_query_and_execute
import random
import time
import smtplib
from email.message import EmailMessage
from utils.common_utils import get_razorpay_client, check_required_fields
from utils.notification_utils import send_notification
from werkzeug.utils import secure_filename
from utils.s3_utils import upload_fileobj, upload_bytes
from utils.mqtt_manager import *
from utils.anpr_ocr import detect_and_ocr

from datetime import datetime, time, date, timedelta
from math import ceil, radians, sin, cos, sqrt, atan2

apis_bp = Blueprint('apis', __name__)

#<------------------------ AUTH APIS START ----------------------->#

@apis_bp.route('/auth/signup', methods=['POST'])
@handle_exceptions
def signup():
    data = request.get_json()
    user_name = data.get('user_name')
    email = data.get('email')
    password = data.get('password')
    conf_password = data.get('conf_password')
    if not user_name or user_name == "":
        return jsonify({"msg": "Name is required!"}), 502
    
    if not email or email == "":
        return jsonify({"msg": "Email is required!"}), 502
    
    if not password or password == "":
        return jsonify({"msg": "Password is required!"}), 502
    
    if password != conf_password:
        return jsonify({"msg": "Password mismatch"}), 502

    password = generate_password_hash(password)
    role_id = int(data.get('role_id', 2))
    if execute_query("SELECT * FROM users WHERE email = %s", args=(email,), is_fetchone=True):
        return jsonify({"msg": "User already exists"}), 400
    if execute_query("INSERT INTO users (user_name, email, password, role_id) VALUES (%s, %s, %s, %s)", args=(user_name, email, password, role_id)):
        return jsonify({"msg": "User created successfully"}), 201
    

@apis_bp.route('/auth/login', methods=['POST'])
@handle_exceptions
def login():
    data = request.get_json()
    email = data.get('email')
    password = data.get('password')
    device_id = data.get('device_id')
    user_type = data.get('user_type', 'user')
    if not email or not password:
        return jsonify({"msg": "Email and password are required!!!"}), 400
    
    if not device_id:
        return jsonify({"msg":"Something went wrong!"}), 502
    
    if (user := execute_query("SELECT a.ID, user_name, email, password, role_name FROM users as a INNER JOIN roles as b on a.role_id = b.ID WHERE email = %s", args=(email,), is_fetchone=True)):
        u_password = user.pop("password")
    if not user or not check_password_hash(u_password, password):
        return jsonify({"msg": "Invalid credentials"}), 404
    if user_type != user.get('role_name'):
        return jsonify({"msg": "Login not allowed for this user!"}), 404
    user_id = user.pop('ID')
    login_data = execute_query("SELECT * FROM login_access WHERE user_id = %s ;", args = (user_id,), is_fetchone=True)
    # if login_data and login_data.get('active', 0) == 1 and login_data.get('device_id') != device_id:
    #     return jsonify({"msg": "Already loggedin in another device!!!"}), 502
    token = create_access_token(identity=email, expires_delta=False, additional_claims={"ID":user_id, "user_name": user['user_name'], "role_name": user['role_name'], "device_id":device_id})
    if login_data:
        execute_query("UPDATE login_access SET updated_on = now(), access_token = %s, device_id = %s, active = %s WHERE user_id = %s ;", args=(token, device_id, 1, user_id))
    else:
        execute_query("INSERT INTO login_access (user_id, access_token, device_id, active, updated_on) VALUES (%s, %s, %s, %s, now());", args=(user_id, token, device_id, 1))
    return jsonify({"token": token, "data":user}), 200


@apis_bp.route('/auth/sent_otp', methods=['POST'])
@handle_exceptions
def send_otp():
    """
    Send SMTP OTP.
    Body: { "email": "...", "purpose": "forgot" | "register" | "login" }
    """
    EMAIL_ADDRESS = current_app.config['SMTP_EMAIL_ADDRESS']
    EMAIL_PASSWORD = current_app.config['SMTP_EMAIL_PASSWORD']
    data = request.get_json() or {}
    to_email = data.get('email')
    purpose = (data.get('purpose') or 'forgot').lower()

    if not to_email:
        return jsonify({"msg": "Email is required"}), 400

    # For forgot-password, ensure user exists
    if purpose == "forgot":
        if not execute_query("SELECT ID FROM users WHERE email = %s", args=(to_email,), is_fetchone=True):
            return jsonify({"msg": "No account found with this email"}), 404

    otp = random.randint(100000, 999999)  # 6-digit
    subject_map = {
        "forgot": "Password Reset OTP - Smart Parking",
        "register": "Verify your email - Smart Parking",
        "login": "Login OTP - Smart Parking",
    }
    subject = subject_map.get(purpose, "OTP - Smart Parking")
    message_body = f"""Hello,
                        Your OTP is: {otp}
                        This OTP is valid for 10 minutes.
                        Purpose: {purpose}
                        If you did not request this, please ignore this email.
                        - Smart Parking System
                        """

    msg = EmailMessage()
    msg['Subject'] = subject
    msg['From'] = EMAIL_ADDRESS
    msg['To'] = to_email
    msg.set_content(message_body)

    try:
        with smtplib.SMTP_SSL('smtp.gmail.com', 465) as smtp:
            smtp.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
            smtp.send_message(msg)

        data_exist = execute_query(
            "SELECT * FROM otp_verify WHERE email = %s",
            args=(to_email,),
            is_fetchone=True,
        )
        if data_exist:
            execute_query(
                "UPDATE otp_verify SET updated_on = NOW(), otp = %s WHERE email = %s",
                args=(otp, to_email),
            )
        else:
            execute_query(
                "INSERT INTO otp_verify (email, otp) VALUES (%s, %s)",
                args=(to_email, otp),
            )
        return jsonify({"msg": "OTP sent successfully!"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    
@apis_bp.route('/auth/verify_otp', methods=['POST'])
@handle_exceptions
def verify_otp():
    data = request.get_json()
    email = data.get('email')
    otp = data.get('otp')
    new_pass = data.get('new_password')
    password = generate_password_hash(new_pass)

    if not email or not otp:
        return jsonify({"error": "Email and OTP are required"}), 400
    
    data_exist = execute_query("SELECT * FROM otp_verify WHERE email = %s ;", args = (email,), is_fetchone=True)
    if data_exist and int(data_exist.get('otp')) == int(otp):
        execute_query("UPDATE login_access SET updated_on = now(), access_token = NULL, device_id = NULL, active = 0 WHERE user_id = (SELECT ID FROM users where email = %s);", args=(email,))
        execute_query("UPDATE users SET password = %s where email = %s;", args=(password, email))
        return jsonify({"message": "OTP verified and password reset successfully"}), 200
    else:
        return jsonify({"error": "Invalid OTP"}), 401
    
    

@apis_bp.route('/auth/logout', methods=['GET'])
@jwt_required_with_login_check
@handle_exceptions
def logout():
    user_data = get_jwt()
    user_id = user_data['ID']
    device_id = user_data.get("device_id")
    if (login_data := execute_query("SELECT * FROM login_access WHERE user_id = %s ;", args = (user_id,), is_fetchone=True)) and login_data.get('active', 0) == 1 and device_id == login_data.get("device_id"):
        execute_query("UPDATE login_access SET active=%s, updated_on = now() WHERE user_id = %s ;", args=(0, user_id))
        return jsonify({"msg": "Logout successfully!!!"})
    else:
        return jsonify({"msg": "Please login first!!!"})

#<------------------------ AUTH APIS END ----------------------->#


#<------------------------ DASHBOARD APIS START ----------------------->#

@apis_bp.route("/dashboard", methods=["GET"])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def dashboard_data():
    user_data = get_jwt()
    user_id = user_data['ID']

    result = {}

    query = "SELECT COUNT(DISTINCT d.id) AS total_bookings FROM users a INNER JOIN parking_lots b ON a.id = b.user_id INNER JOIN parking_slots c ON c.lot_id = b.id LEFT JOIN booking d ON d.slot_id = c.id WHERE a.id = %s;"
    result['total_bookings']=execute_query(query=query, args=user_id, is_fetchone=True)["total_bookings"]

    query = "select count(*) as active_site from parking_lots where user_id = %s;"
    result['parking_sites']=execute_query(query=query, args=user_id, is_fetchone=True)["active_site"]

    query = "select count(*) as active_parking from parking_lots as a inner join parking_slots as b on a.id = b.lot_id where user_id = %s and a.is_active = 1 and b.is_active = 1;"
    result['active_parking_slots']=execute_query(query=query, args=user_id, is_fetchone=True)["active_parking"]

    query = "select count(*) as in_active_parking from parking_lots as a inner join parking_slots as b on a.id = b.lot_id where user_id = %s and a.is_active = 1 and b.is_active = 0;"
    result['inactive_parking_slots']=execute_query(query=query, args=user_id, is_fetchone=True)["in_active_parking"]

    return jsonify({"msg":"Data fetch successfully", "data": result}), 200

#<------------------------ DASHBOARD APIS END ----------------------->#


#<------------------------ BOOKING APIS START ----------------------->#

@apis_bp.route("/booking/create", methods=["POST"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def takeBooking():
    one_hour_amount = 20
    user_data = get_jwt()
    user_id = user_data['ID']
    rq_data = request.get_json()
    vehicle_id = rq_data["vehicle_id"]
    slot_id = rq_data["slot_id"]
    booking_hours = rq_data['booking_hours']
    start_time = rq_data.get('start_time', None)
    end_time = rq_data.get('end_time', None)
    print("rq_data", rq_data)

    query = f"SELECT * FROM booking where slot_id = {slot_id} and start_time < CURRENT_TIMESTAMP + INTERVAL 15 MINUTE and status = 1;"
    if not execute_query(query, is_fetchone=True):
        query1 = f"SELECT * FROM parking_slots where id = {slot_id};"
        result = execute_query(query=query1, is_fetchone=True)
        if result:
            if int(result["is_available"]) == 0:
                return jsonify({"msg":"Slot not available"}), 400
            if int(result["is_available"]) == 2:
                return jsonify({"msg":"Someone start booking"}), 400

        print("result", result)
        
        one_hour_amount = float(result["fare"])

        query2 = f"INSERT INTO `booking` (`user_id`, `vehicle_id`, `slot_id`, `start_time`, `end_time`, `amount_paid_1`, `status`) VALUES ({user_id}, {vehicle_id}, {slot_id}, '{start_time}:00', '{end_time}:00', {booking_hours*one_hour_amount}, '1');"
        booking_id = execute_query(query2)

        query11 = f"UPDATE `parking_slots` SET `is_available` = '0' WHERE (`id` = '{slot_id}');"

        execute_query(query=query11)

        order = get_razorpay_client().order.create({
            "amount": booking_hours*one_hour_amount * 100,  # rupees to paise
            "currency": "INR",
            "receipt": f"receipt#{booking_id}",
            "payment_capture": 1,
            'notes':{"booking_id":booking_id}
        })

        order.update({"booking_id":booking_id})
        execute_query(query=f'''UPDATE booking SET payment_id_1 = "{order["id"]}" where id = {booking_id};''')
        return jsonify({"msg":"booking created successfully", "data": order}), 200
    else:
        return jsonify({"msg":"someone start booking before you, try another slot!!!"}), 400


@apis_bp.route("/booking/update", methods=["POST"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def completeBooking():
    user_data = get_jwt()
    user_id = user_data['ID']
    rq_data = request.get_json()
    slot_id = rq_data["slot_id"]
    booking_id = rq_data['booking_id']
    status = rq_data['status']
    if status=="success":
        query11 = f"UPDATE parking_slots set is_available = 0 where id = {slot_id}"
        execute_query(query=query11)
        query11 = f'''UPDATE booking set status = 2 where id = {booking_id}'''
        execute_query(query=query11)
        send_notification(current_app, f"Booking created successfully, You can check in booking section.")
    else:
        query11 = f"UPDATE parking_slots set is_available = 1 where id = {slot_id}"
        execute_query(query=query11)

    return jsonify({"msg":"Update successfully"}), 200


@apis_bp.route("/booking/get", methods=["GET"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def getBookingHostory():
    user_data = get_jwt()
    user_id = user_data['ID']
    query = f'''select a.ID, a.time_over, TIME_FORMAT(start_time, '%%h:%%i:%%s %%p') AS start_time, TIME_FORMAT(end_time, '%%h:%%i:%%s %%p') AS end_time, slot_number, (amount_paid_1 + amount_paid_2) AS amount_paid, status, name, address, lat, lng, IF(d.extracted_number IS NULL, d.number, d.extracted_number) as vehicle_number  from booking as a inner join parking_slots as b on b.id= a.slot_id inner join parking_lots as c on c.id = b.lot_id inner join vehicle_details as d on a.vehicle_id = d.ID where a.user_id = {user_id} order by a.ID desc;'''

    results = execute_query(query=query)
    print("results", results)
    return jsonify({"msg":"Successfully", "data":results}), 200

def format_mysql_time(value):
    if isinstance(value, timedelta):
        total_seconds = int(value.total_seconds())

        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60

        return f"{hours:02d}:{minutes:02d}:{seconds:02d}"

    return value

def format_mysql_datetime(value):
    if isinstance(value, datetime):
        return value.strftime("%d-%m-%Y %I:%M %p")
    return value

@apis_bp.route("/booking/get_admin", methods=["GET"])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def getBookingHostoryAdmin():
    user_data = get_jwt()
    user_id = user_data['ID']
    query = f'''select a.id, a.booking_date, b.user_name, c.number, d.slot_number, a.start_time, a.end_time, a.amount_paid_1, a.payment_id_1, a.amount_paid_2, a.payment_id_2 from booking as a inner join users as b on a.user_id = b.ID inner join vehicle_details as c on a.vehicle_id = c.ID inner join parking_slots as d on a.slot_id = d.id inner join parking_lots as e on e.id = d.lot_id WHERE e.user_id = {user_id};'''

    results = execute_query(query=query)
    for row in results:
        row["start_time"] = format_mysql_time(row.get("start_time"))
        row["end_time"] = format_mysql_time(row.get("end_time"))
        row["booking_date"] = format_mysql_datetime(row.get("booking_date"))
    return jsonify({"msg":"Successfully", "data":results}), 200

#<------------------------ BOOKING APIS END ----------------------->#


#<------------------------ PARKING LOT APIS START ----------------------->#

@apis_bp.route("/parking_lot/add", methods=['POST'])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def add_parking_lot():
    user_data = get_jwt()
    user_id = user_data['ID']
    rq_data = request.get_json()
    lot_id = int(rq_data.pop("lot_id", -1))
    if lot_id == -1:
        status, msg = check_required_fields(data = rq_data, required_fields = {"name", "address", "total_slots", "lat", "lng", "is_dynamic_pricing"})
        if not status:
            return jsonify({"msg":msg}), 502
    rq_data.update({"user_id":user_id})

    if lot_id != -1:
        if execute_query("SELECT * FROM parking_lots as a inner join parking_slots as b on a.id = b.lot_id where a.id = %s and a.user_id = %s;", args=(lot_id, user_id), is_fetchone=True):
            build_query_and_execute(data=rq_data, isInsert=False, table_name="parking_lots", where_condition={"id":lot_id})
            return jsonify({"msg": "Data update successfully"}), 200
        else:
            return jsonify({"msg": "No data found!!!"}), 400
    else:
        total_parking_slots = rq_data.get('total_slots', 0)
        if total_parking_slots == 0:
            return jsonify({"msg": "Please enter total parking slots!!!"}), 400
        if (inserted_id := build_query_and_execute(data=rq_data, isInsert=True, table_name="parking_lots")):
            return jsonify({"msg": "Data created successfully", "data": inserted_id}), 201
    raise Exception("Something went wrong!")


@apis_bp.route("/parking_lot/get", methods=['GET'])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def get_parking_lot():
    user_data = get_jwt()
    user_id = user_data['ID']
    lot_id = int(request.args.get("lot_id", 0))
    if lot_id:
        query = "SELECT `id`, `name`, `address`, `total_slots`, `lat`, `lng`, `is_dynamic_pricing`, `is_active`, `created_at`, `updated_at` FROM parking_lots WHERE user_id = %s AND id = %s and is_deleted = 0;"
        args, is_fetchone = (user_id, lot_id), True
    else:
        query = "select a.id, a.name, a.address, a.total_slots, a.lat, a.lng, a.is_dynamic_pricing, a.is_active, a.created_at, a.updated_at, coalesce(sum(b.is_available), 0) as total_available_slots, coalesce((count(b.id)-sum(b.is_available)), 0) as total_not_available_slots from parking_lots as a left join parking_slots as b on a.id = b.lot_id WHERE a.user_id = %s and a.is_deleted = 0 group by a.id;"
        args, is_fetchone = (user_id,), False
    results = execute_query(query, args=args, is_fetchone=is_fetchone)
    if not results:
        return jsonify({"msg": "No data found!!!"}), 404
    return jsonify({"data": results}), 200

@apis_bp.route("/parking_lot/parking_lots", methods=['GET'])
@jwt_required_with_login_check
@role_required(['user', 'admin'])
def get_parking_lots():
    query = "SELECT *, (select count(id) from parking_slots where lot_id = parking_lots.id and is_available = 1) as available_count FROM parking_lots WHERE is_active = 1;"
    results = execute_query(query)
    if not results:
        return jsonify({"msg": "No data found!!!"}), 400
    return jsonify({"data": results}), 200

#<------------------------ PARKING LOT APIS END ----------------------->#


#<------------------------ PARKING SLOT APIS START ----------------------->#

@apis_bp.route("/parking_slot/add", methods=['POST'])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def add_parking_slot():
    user_data = get_jwt()
    user_id = user_data['ID']
    rq_data = request.get_json()
    if rq_data.get('fare', 0)==0:
        rq_data['fare'] = 20
    print("rq_data", rq_data)
    slot_id = int(rq_data.pop("slot_id", -1))
    if slot_id == -1:
        status, msg = check_required_fields(data = rq_data, required_fields = {"lot_id", "slot_number","is_available", "vehicle_type"})
        if not status:
            return jsonify({"msg":msg}), 502

    if slot_id > 0:
        lot_id = int(rq_data.pop("lot_id", -1))
        if execute_query("SELECT * FROM parking_slots where id = %s and lot_id = %s;", args=(slot_id, lot_id), is_fetchone=True):
            build_query_and_execute(data=rq_data, isInsert=False, table_name="parking_slots", where_condition={"id":slot_id})
            return jsonify({"msg": "Data update successfully"}), 200
        else:
            return jsonify({"msg": "No data found!!!"}), 400
    else:
        lot_id = rq_data['lot_id']
        result = execute_query("SELECT * FROM parking_lots where id = %s and user_id = %s;", args=(lot_id, user_id), is_fetchone=True)
        if not result:
            return jsonify({"msg": "Parking lot not found or not in working!!"}), 404
        slot_result =  execute_query("SELECT count(*) as count FROM parking_slots where lot_id = %s;", args=(lot_id, ), is_fetchone=True)
        if slot_result and slot_result.get("count") >= result["total_slots"]:
            return jsonify({"msg": f"Parking lot having only {result["total_slots"]} parking slots!!"}), 502     

        if (inserted_id := build_query_and_execute(data=rq_data, isInsert=True, table_name="parking_slots")):
            return jsonify({"msg": "Data created successfully", "data": inserted_id}), 201
    raise Exception("Something went wrong!")


@apis_bp.route("/parking_slot/get", methods=['GET'])
@jwt_required_with_login_check
@role_required(['admin', 'user'])
@handle_exceptions
def get_parking_slot():
    user_data = get_jwt()
    lot_id = int(request.args.get("lot_id", 0))
    slot_id = int(request.args.get("slot_id", 0))
    if slot_id and user_data.get("role_name") == "admin":
        query = "SELECT * FROM parking_slots WHERE id = %s;"
        args, is_fetchone = (slot_id,), True
    if slot_id and user_data.get("role_name") != "admin":
        return jsonify({"msg": "No data found!!!"}), 400
    if lot_id:
        query = "SELECT * FROM parking_slots WHERE lot_id = %s order by slot_number;"
        args, is_fetchone = (lot_id,), False
    results = execute_query(query, args=args, is_fetchone=is_fetchone)
    if not results and slot_id:
        return jsonify({"msg": "No data found!!!"}), 400
    return jsonify({"data": results}), 200

@apis_bp.route("/parking_slot/update_parking_slot", methods=['POST'])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def update_parking_slot():
    rq_data = request.get_json()
    print(rq_data)
    slot_id = int(rq_data.get("slot_id", 0))
    status = int(rq_data.get("is_available", 0))
    if slot_id == 0:
        return jsonify({"msg": "slot id is required!!!"}), 502
    else:
        query = "UPDATE parking_slots SET is_available = %s, is_active=%s WHERE id=%s;"
        if execute_query(query, args=(status, status, slot_id)):
            return jsonify({"msg": "Data update successfully!"}), 200 
        else:
            return jsonify({"msg": "Slot not found!"}), 404 

#<------------------------ PARKING SLOT APIS END ----------------------->#


#<------------------------ PARKING LOG APIS START ----------------------->#
@apis_bp.route('/parking/logs', methods=['GET'])
@jwt_required_with_login_check
@role_required(['admin'])
@handle_exceptions
def parking_logs():
    user_data = get_jwt()
    user_id = user_data['ID']
    results = execute_query("SELECT b.ID, b.EXTRACTED_NUMBER, a.name, b.DATENTIME, B.GATE FROM parking_lots as a inner join entry_exit_log as b on a.id =b.PARKING_LOT_ID WHERE a.user_id = %s;", args=(user_id))
    if not results:
        return jsonify({"msg": "No data found!!!"}), 400
    if results:
        for row in results:
            if row.get("DATENTIME"):
                row["DATENTIME"] = row["DATENTIME"].strftime('%d %b, %I:%M %p')
    return jsonify({"data": results}), 200

#<------------------------ PARKING LOG APIS END ----------------------->#


#<------------------------ VEHICLE APIS START ----------------------->#

def make_image_url(vehicle_row_or_id):
    """Return S3 image URL stored in DB, or empty string."""
    if isinstance(vehicle_row_or_id, dict):
        return vehicle_row_or_id.get("image") or ""
    row = execute_query("SELECT image FROM vehicle_details WHERE ID = %s", args=(vehicle_row_or_id,), is_fetchone=True)
    return (row or {}).get("image") or ""

@apis_bp.route('/vehicle/details', methods=['GET'])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def get_vehicles():
    user_data = get_jwt()
    user_id = user_data['ID']
    vehicle_id = int(request.args.get("vehicle_id", 0))
    if vehicle_id:
        query = "SELECT * FROM vehicle_details WHERE user_id = %s AND id = %s;"
        args, is_fetchone = (user_id, vehicle_id), True
    else:
        query = "SELECT * FROM vehicle_details WHERE user_id = %s;"
        args, is_fetchone = (user_id,), False
    results = execute_query(query, args=args, is_fetchone=is_fetchone)

    if not results:
        return jsonify({"msg": "No data found!!!"}), 400

    # image column already stores S3 URL (or legacy local path)
    if vehicle_id:
        if not results.get("image"):
            results["image"] = ""
    else:
        for data in results:
            if not data.get("image"):
                data["image"] = ""
    return jsonify({"data": results}), 200


ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def save_image(file, vehicle_id):
    """Upload vehicle plate image to S3 and store URL in DB."""
    s3_url = upload_fileobj(file, folder="vehicles")
    if not s3_url:
        # Fallback to local storage if S3 is not configured
        filename = secure_filename(f"vehicle_image_{vehicle_id}.jpeg")
        upload_folder = current_app.config.get("UPLOAD_FOLDER", os.path.join("static", "images"))
        os.makedirs(upload_folder, exist_ok=True)
        filepath = os.path.join(upload_folder, filename)
        file.seek(0)
        file.save(filepath)
        s3_url = url_for("static", filename=f"images/{filename}", _external=True)

    extracted_number = detect_and_ocr(image_path=filepath)
    
    execute_query(
        "UPDATE vehicle_details SET image = %s, extracted_number = %s WHERE ID = %s",
        args=(s3_url, extracted_number, vehicle_id),
    )
    return s3_url


@apis_bp.route('/vehicle/add_details', methods=['POST'])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def add_vehicles():
    user_data = get_jwt()
    user_id = user_data['ID']

    if 'image' not in request.files:
        return jsonify({'error': 'No image part in the request'}), 400
    
    file = request.files['image']

    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400
    
    if file and not allowed_file(file.filename):
        return jsonify({'error': 'Image not allowed!!!'}), 400
        
    vehicle_id = request.form.get('vehicle_id', -1)
    owner_name = request.form.get('owner_name')
    owner_mobile = request.form.get('owner_mobile')
    number = request.form.get('number')
    data = {
        'owner_name': owner_name,
        'owner_mobile': owner_mobile,
        'number': number
    }
    if vehicle_id>0:
        if execute_query("SELECT * FROM vehicle_details where id = %s and user_id %s;", args=(vehicle_id, user_id), is_fetchone=True):
            if len(values := [f"{key} = '{value}'" for key, value in data.items()])>0:
                execute_query("UPDATE vehicle_details SET {values} WHERE (ID = %s);".format(values=",".join(values)), args=(vehicle_id, ))
                save_image(file, vehicle_id)
                return jsonify({"msg":"Data update successfully"}), 200
            else:
                return jsonify({"msg": "Please provide data to update!!!"}), 500
        else:
            return jsonify({"msg": "No data found!!!"}), 400
    else:
        if (v_data:=execute_query("SELECT * FROM vehicle_details where number = %s;", args=(data['number'], ), is_fetchone=True)):
            vehicle_id = v_data.get("ID")
            if len(values := [f"{key} = '{value}'" for key, value in data.items()])>0:
                execute_query("UPDATE vehicle_details SET {values} WHERE (ID = %s);".format(values=",".join(values)), args=(vehicle_id, ))
                save_image(file, vehicle_id)
                return jsonify({"msg":"Data update successfully"}), 200
        else:
            args = (user_id, data['owner_name'], data['owner_mobile'], data['number'])
            inserted_id = execute_query("INSERT INTO vehicle_details (user_id, owner_name, owner_mobile, number) VALUES (%s, %s, %s, %s);", args=args)
            save_image(file, inserted_id)
            return jsonify({"msg":"Data inserted successfully", "data":{"id": inserted_id}}), 200
        
#<------------------------ VEHICLE APIS END ----------------------->#


#<------------------------ PAYMENT APIS START ----------------------->#

@apis_bp.route("/payment/create_order", methods=["POST"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def create_order():
    data = request.get_json()
    amount = data.get('amount')
    booking_id = data.get('booking_id')
    order = get_razorpay_client().order.create({
        "amount": amount * 100,  # rupees to paise
        "currency": "INR",
        "receipt": f"receipt#{booking_id}",
        "payment_capture": 1,
        'notes':{"booking_id":booking_id}
    })
    return jsonify({"msg": "Order created successfully!", 'data':order}), 200

@apis_bp.route("/payment/verify_payment", methods=["POST"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def verify_payment():
    data = request.get_json()
    try:
        get_razorpay_client().utility.verify_payment_signature({
            'razorpay_order_id': data['order_id'],
            'razorpay_payment_id': data['payment_id'],
            'razorpay_signature': data['signature']
        })
        return jsonify({"msg": "Payment verified successfully!"}), 200
    except Exception:
        return jsonify({"msg": "Payment verification failed!"}), 400

@apis_bp.route("/payment/cancel_order_id", methods=["POST"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def cancel_order():
    data = request.get_json()
    get_razorpay_client().order.edit(data['order_id'], {"status": "cancelled"})
    return jsonify({"msg": "Order cancelled successfully!"}), 200


@apis_bp.route("/payment/fetch_order_id", methods=["POST"])
@jwt_required_with_login_check
@role_required(['user'])
@handle_exceptions
def verify_order_id():
    data = request.get_json()
    order = get_razorpay_client().order.fetch(data['order_id'])
    return jsonify({"msg": "Order id data fetched successfully!", "data":order}), 200


################################# Payment Link #########################################

# Create Payment Link
@apis_bp.route('/payment/create_payment_link', methods=['POST'])
def create_payment_link():
    data = request.get_json()
    try:
        # Get the current time and add 72 hours (in seconds)
        # expire_time = int(time.time()) + (72 * 60 * 60)  # 72 hours from now
        expire_time = int(time.time()) + (15 * 60)  # 2 minutes from now

        # Create the payment link with an expiration time
        payment_link = get_razorpay_client().payment_link.create({
            "amount": data['amount'],  # in paise (e.g., 100 rupees = 10000 paise)
            "currency": "INR",
            "description": data.get('description', 'Test Payment'),
            "customer": {
                "name": data['name'],
                "email": data['email'],
                "contact": data['contact'],
            },
            "notify": {
                "sms": True,
                "email": False,
            },
            "callback_url": data.get('callback_url', 'https://yourdomain.com/payment-callback'),
            "callback_method": "get",
            "expire_by": expire_time  # Set the expiration time here (in Unix timestamp)
        })

        return jsonify({"payment_link": payment_link}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400

# Cancel Payment Link
@apis_bp.route('/payment/cancel_payment_link', methods=['POST'])
def cancel_payment_link():
    data = request.get_json()
    try:
        payment_link_id = data['payment_link_id']
        response = get_razorpay_client().payment_link.cancel(payment_link_id)
        return jsonify({"status": "Cancelled", "response": response}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400
    
# Check Payment Link Status
@apis_bp.route('/payment/check_payment_link_status', methods=['POST'])
def check_payment_link_status():
    data = request.get_json()
    try:
        payment_link_id = data['payment_link_id']
        payment_link_status = get_razorpay_client().payment_link.fetch(payment_link_id)
        
        # Return the status of the payment link
        return jsonify({
            "status": payment_link_status['status'],
            "payment_link": payment_link_status
        }), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400

#<------------------------ PAYMENT APIS END ----------------------->#

#<------------------------ IMAGE APIS START ----------------------->#

ALLOWED_EXTENSIONS = {'png', 'jpg', 'jpeg', 'gif'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

@apis_bp.route("/image/upload", methods=['POST'])
# @jwt_required_with_login_check
# @role_required(['user'])
@handle_exceptions
def upload_image():
    """Upload image (to S3) and optionally run plate recognition."""
    if 'image' not in request.files:
        return jsonify({'error': 'No image part in the request'}), 400

    file = request.files['image']

    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    if file and allowed_file(file.filename):
        s3_url = upload_fileobj(file, folder="uploads")
        if not s3_url:
            # local fallback
            filename = secure_filename(file.filename)
            upload_folder = current_app.config.get('UPLOAD_FOLDER', os.path.join('static', 'images'))
            os.makedirs(upload_folder, exist_ok=True)
            filepath = os.path.join(upload_folder, filename)
            file.seek(0)
            file.save(filepath)
            s3_url = url_for('static', filename=f'images/{filename}', _external=True)

        # Plate recognition is optional – keep existing behaviour if extract_number exists
        vehicle_numbers = []
        try:
            vehicle_numbers = detect_and_ocr(image_path=filepath)
        except Exception:
            pass

        return jsonify({
            "msg": "Image uploaded",
            "image_url": s3_url,
            "data": vehicle_numbers
        }), 200
    else:
        return jsonify({'error': 'Invalid file type'}), 400
#<------------------------ IMAGE APIS END ----------------------->#

#<------------------------ IOT & GATE APIS START ----------------------->#

def _check_iot_api_key():
    return True
    """Simple API-key auth for ESP32 / NodeMCU devices."""
    key = request.headers.get("X-IOT-API-KEY") or request.args.get("api_key")
    expected = current_app.config.get("IOT_API_KEY")
    return key and expected and key == expected


@apis_bp.route("/iot/anpr", methods=["POST"])
@handle_exceptions
def iot_anpr():
    """
    Called by ESP32-CAM.
    Body (JSON or form):
      parking_id, gate (entry|exit), plate_text (optional),
      image (base64) or file upload under key 'image'
    """
    if not _check_iot_api_key():
        return jsonify({"msg": "Unauthorized IoT device"}), 401

    parking_id = request.form.get("parking_id") or (request.json or {}).get("parking_id")
    gate = (request.form.get("gate") or (request.json or {}).get("gate") or "entry").lower()
    plate_text = request.form.get("plate_text") or (request.json or {}).get("plate_text")

    image_url = None
    if "image" in request.files:
        image_url = upload_fileobj(request.files["image"], folder="anpr")
    else:
        b64 = (request.json or {}).get("image")
        if b64:
            if "," in b64:
                b64 = b64.split(",", 1)[1]
            try:
                image_url = upload_bytes(base64.b64decode(b64), folder="anpr")
            except Exception as e:
                print("ANPR base64 decode error:", e)

    # Log the event
    execute_query(
        "INSERT INTO entry_exit_log (PARKING_LOT_ID, EXTRACTED_NUMBER, GATE) VALUES (%s, %s, %s)",
        args=(parking_id, plate_text, gate.upper()),
    )

    # Try to match an active booking by plate number
    matched_booking = None
    open_gate = False
    if plate_text:
        matched_booking = execute_query(
            """
            SELECT b.id, b.status, b.end_time, b.amount_paid_2, v.number
            FROM booking b
            INNER JOIN vehicle_details v ON v.ID = b.vehicle_id
            INNER JOIN parking_slots s ON s.id = b.slot_id
            WHERE v.number = %s AND s.lot_id = %s
              AND b.status IN (1, 2)
            ORDER BY b.id DESC LIMIT 1
            """,
            args=(plate_text.replace(" ", "").upper(), parking_id),
            is_fetchone=True,
        )
        # fallback without normalizing
        if not matched_booking:
            matched_booking = execute_query(
                """
                SELECT b.id, b.status, b.end_time, b.amount_paid_2, v.number
                FROM booking b
                INNER JOIN vehicle_details v ON v.ID = b.vehicle_id
                INNER JOIN parking_slots s ON s.id = b.slot_id
                WHERE REPLACE(UPPER(v.number), ' ', '') = REPLACE(UPPER(%s), ' ', '')
                  AND s.lot_id = %s AND b.status IN (1, 2)
                ORDER BY b.id DESC LIMIT 1
                """,
                args=(plate_text, parking_id),
                is_fetchone=True,
            )

        if matched_booking:
            open_gate = True
            # On exit, check overstay (simple: if now > end_time and no extra payment)
            if gate == "exit":
                from datetime import datetime
                end_time = matched_booking.get("end_time")
                if end_time and datetime.now() > end_time and matched_booking.get("amount_paid_2") == 0:
                    # create new order id to take extra payment for overstay
                    open_gate = False  # force extra payment first

    return jsonify({
        "msg": "ANPR processed",
        "plate_text": plate_text,
        "image_url": image_url,
        "matched_booking_id": matched_booking["id"] if matched_booking else None,
        "open_gate": open_gate,
    }), 200


@apis_bp.route("/iot/slot-status", methods=["POST"])
@handle_exceptions
def iot_slot_status():
    """
    Called by NodeMCU with IR sensor readings.
    Body: { "parking_id": 13, "slots": [ {"slot_id": 6, "occupied": true}, ... ] }
    or simple: { "slot_id": 6, "is_available": 0 }
    """
    if not _check_iot_api_key():
        return jsonify({"msg": "Unauthorized IoT device"}), 401

    data = request.get_json() or {}
    slots = data.get("slots")
    if slots and isinstance(slots, list):
        for item in slots:
            sid = item.get("slot_id")
            occupied = item.get("occupied")
            if sid is None:
                continue
            is_available = 0 if occupied else 1
            execute_query(
                "UPDATE parking_slots SET is_available = %s WHERE id = %s",
                args=(is_available, sid),
            )
        return jsonify({"msg": "Slots updated"}), 200

    # Single slot update
    slot_id = data.get("slot_id")
    is_available = data.get("is_available")
    if slot_id is not None and is_available is not None:
        execute_query(
            "UPDATE parking_slots SET is_available = %s WHERE id = %s",
            args=(is_available, slot_id),
        )
        return jsonify({"msg": "Slot updated"}), 200

    return jsonify({"msg": "Invalid payload"}), 400


@apis_bp.route("/iot/gate-command", methods=["GET"])
@handle_exceptions
def iot_gate_command():
    """
    NodeMCU can poll this endpoint to know whether to open a gate.
    Query: parking_id, gate=entry|exit
    Returns: { "open": true/false }
    (In production prefer MQTT or push; polling is simplest for Arduino IDE.)
    """
    if not _check_iot_api_key():
        return jsonify({"msg": "Unauthorized IoT device"}), 401

    # Simple implementation: look for latest pending command in a temp table
    # For now return false – extend later with a gate_commands table
    return jsonify({"open": False, "msg": "No pending command"}), 200


@apis_bp.route('/payment-callback')
def payment_callback():
    booking_id = request.args.get('booking_id')
    
    if not booking_id:
        return jsonify({"msg": "booking_id is required"}), 400

    # payment_link_status = get_razorpay_client().payment_link.fetch(execute_query("SELECT payment_id_2 from booking where id = %s", args=(booking_id,), is_fetchone=True)["payment_id_2"])
    # if payment_link_status.get("status") == "paid":
    with current_app.app_context():
        mqtt_manager = current_app.extensions.get("mqtt_manager")
        if mqtt_manager:
            mqtt_manager.publish(topic=ESP32_CAM_EXIT_SERVO_TOPIC,message="OPEN")
            mqtt_manager.publish(topic = NOTIFICATION_TOPIC,message="Extra amount paid gate is opening...")
            execute_query("update booking set status = 3 where id = %s", args=(booking_id,))
            execute_query("update parking_slots set is_available = 1 where id = (select slot_id from booking where id = %s limit 1)", args=(booking_id, ))
            execute_query("INSERT INTO entry_exit_log(PARKING_LOT_ID, EXTRACTED_NUMBER, GATE) select c.id, IF(d.extracted_number is not null, d.extracted_number, d.number) as number, 'EXIT' as gate from booking as a inner join parking_slots as b on a.slot_id = b.id inner join parking_lots as c on b.lot_id=c.id inner join vehicle_details as d on a.vehicle_id = d.ID where a.id = %s; ", args=(booking_id,))
    # Your payment success logic here
    print(f"Payment successful for booking_id: {booking_id}")

    return jsonify({
        "msg": "Payment received successfully",
        "booking_id": booking_id
    }), 200


def calculate_overtime(end_time, parking_rate):
    now = datetime.now()
    if isinstance(end_time, timedelta):
        total_seconds = int(end_time.total_seconds())
        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60

        end_time = time(
            hour=hours,
            minute=minutes,
            second=seconds
        )
    elif isinstance(end_time, time):
        pass

    else:
        raise ValueError(
            f"Unsupported end_time type: {type(end_time)}"
        )

    end_datetime = datetime.combine(
        now.date(),
        end_time
    )

    if now <= end_datetime:
        return 0, 0, 0

    overtime_minutes = int(
        (now - end_datetime).total_seconds() / 60
    )

    overtime_hours = ceil(
        overtime_minutes / 60
    )

    amount = overtime_hours * float(parking_rate)

    return overtime_minutes, overtime_hours, amount

def create_payment_link_for_overtime(booking_id, amount, user_name, user_contact):
    try:
        print(booking_id, amount, user_contact, user_name)
        import time as time_module
        expire_time = int(time_module.time()) + (16 * 60)  # 15 minutes from now
        ip = get_system_ip()
        payment_link = get_razorpay_client().payment_link.create({
            "amount": amount * 100,  # in paise
            "currency": "INR",
            "description": f"Overtime payment for booking {booking_id}",
            "customer": {
                "name": user_name,
                "contact": user_contact
            },
            "notify": {
                "sms": False,
            },
            "callback_url": f'http://{ip}:5000/api/payment-callback?booking_id={booking_id}',
            "callback_method": "get",
            "expire_by": expire_time
        })
        return payment_link
    except Exception as e:
        print("Error creating payment link:", e)
        return None 


def send_overtime_payment_email(
    vehicle_number,
    amount,
    user_name,
    user_email,
    payment_url
):
    try:
        EMAIL_ADDRESS = current_app.config[
            'SMTP_EMAIL_ADDRESS'
        ]

        EMAIL_PASSWORD = current_app.config[
            'SMTP_EMAIL_PASSWORD'
        ]

        subject = (
            f"Additional Parking Payment - "
            f"Booking #{vehicle_number}"
        )

        message_body = f"""Hello, {user_name},

Your parking booking #{vehicle_number} has exceeded
the scheduled parking time.

Additional parking amount: ₹{amount:.2f}

Please complete the additional payment using
the payment link below:

{payment_url}

The payment link is valid for a limited time.

If you have already made this payment, please
ignore this email.

Regards,
Smart Parking System
"""

        msg = EmailMessage()

        msg['Subject'] = subject
        msg['From'] = EMAIL_ADDRESS
        msg['To'] = user_email

        msg.set_content(message_body)

        with smtplib.SMTP_SSL(
            'smtp.gmail.com',
            465
        ) as smtp:

            smtp.login(
                EMAIL_ADDRESS,
                EMAIL_PASSWORD
            )

            smtp.send_message(msg)

        print(
            f"Overtime payment email sent to {user_email}"
        )

        return True

    except Exception as e:
        print(
            f"Error sending overtime payment email: {e}"
        )
        return False


@apis_bp.route("/booking/open-gate", methods=["POST"])
@jwt_required_with_login_check
@role_required(["user"])
@handle_exceptions
def open_gate_from_app():
    """
    Manual open-gate from mobile app when user is inside geo-fence
    and ANPR failed.
    Body: { "booking_id": 28, "gate": "entry"|"exit", "lat": ..., "lng": ... }
    """
    user_data = get_jwt()
    user_id = user_data["ID"]
    data = request.get_json() or {}
    print(data)

    booking_id = data.get("booking_id")
    gate = (data.get("gate") or "entry").lower()
    user_lat = data.get("lat")
    user_lng = data.get("lng")

    if not booking_id:
        return jsonify({"msg": "booking_id is required"}), 400

    booking = execute_query(
        """
        SELECT b.*, s.lot_id, s.fare, p.lat, p.lng, p.name,
               b.booking_date, b.start_time, b.end_time, IF(vd.extracted_number is null, vd.number, vd.extracted_number) as number, u.user_name, vd.owner_mobile as user_contact, u.email
        FROM booking b
        INNER JOIN parking_slots s ON s.id = b.slot_id
        INNER JOIN parking_lots p ON p.id = s.lot_id
        INNER JOIN vehicle_details vd ON vd.ID = b.vehicle_id
        INNER JOIN users u ON u.ID = b.user_id
        WHERE b.id = %s AND b.user_id = %s
        """,
        args=(booking_id, user_id),
        is_fetchone=True,
    )

    if not booking:
        return jsonify({"msg": "Booking not found"}), 404


    # ---------- Geo-fence Check ----------
    if user_lat is not None and user_lng is not None and booking.get("lat") and booking.get("lng"):
        R = 6371000
        lat1, lon1 = radians(float(user_lat)), radians(float(user_lng))
        lat2, lon2 = radians(float(booking["lat"])), radians(float(booking["lng"]))
        dlat, dlon = lat2 - lat1, lon2 - lon1
        a = sin(dlat/2)**2 + cos(lat1)*cos(lat2)*sin(dlon/2)**2
        dist = 2 * R * atan2(sqrt(a), sqrt(1-a))
        print("Distance:", dist)

        if dist > 200:  # metres
            send_notification(current_app, f"User {booking['user_name']} tried to open gate for booking {booking['number']} but is too far away ({dist:.1f} m).")
            return jsonify({"msg": "You are too far from the parking gate"}), 403
    # ---------- End Geo-fence ----------

    # ---------- Time Validation ----------
    try:
        booking_date = booking["booking_date"]          # date object or string
        start_time = booking["start_time"]              # timedelta or time
        end_time = booking["end_time"]

        # Convert booking_date to date object if it's string
        if isinstance(booking_date, str):
            booking_date = datetime.strptime(booking_date, "%Y-%m-%d").date()
        elif isinstance(booking_date, datetime):
            booking_date = booking_date.date()

        # Convert timedelta → time
        if isinstance(start_time, timedelta):
            start_time = (datetime.min + start_time).time()
        if isinstance(end_time, timedelta):
            end_time = (datetime.min + end_time).time()

        # Combine date + time
        start_datetime = datetime.combine(booking_date, start_time)
        end_datetime = datetime.combine(booking_date, end_time)

        current_datetime = datetime.now()

        print("Current Time :", current_datetime)
        print("Start Time   :", start_datetime)
        print("End Time     :", end_datetime)

        if current_datetime < start_datetime:
            send_notification(current_app, f"{'Entry' if gate == "entry" else 'Exit'} not allowed. Booking has not started yet.")
            return jsonify({
                "msg": f"{'Entry' if gate == "entry" else 'Exit'} not allowed. Booking has not started yet."
            }), 403
        
        if gate == "entry":
            # ENTRY allowed only between start_time and end_time
            if not (start_datetime <= current_datetime <= end_datetime):
                send_notification(current_app, f"Entry not allowed. Current time is outside your booking slot.")
                return jsonify({
                    "msg": "Entry not allowed. Current time is outside your booking slot."
                }), 403

        elif gate == "exit":
            if booking.get("amount_paid_2") !=0 and booking.get("payment_id_2") != 0:
                pass
            # we can not check the payment link on local machine
            
            overtime_minutes, overtime_hours, amount = calculate_overtime(booking["end_time"], booking.get("fare", 20))
            print("overtime_minutes:", overtime_minutes)
            if overtime_minutes > 0:
                if booking.get("payment_id_2", 0) != 0:
                    try:
                        get_razorpay_client().payment_link.cancel(booking.get("payment_id_2"))
                    except Exception as e:
                        print("payment link cancel error", e)

                payment_link = create_payment_link_for_overtime(
                    booking_id=booking_id,
                    amount=amount,
                    user_name=booking['user_name'],
                    user_contact=booking.get('user_contact')
                )
                print(payment_link)
                if not payment_link:
                    send_notification(current_app, f"User {booking['user_name']} tried to exit for booking {booking['number']} but failed to create payment link for overstay of {overtime_hours} hours. Extra payment required: ₹{amount}.")
                    return jsonify({"msg": "Failed to create payment link for overstay"}), 500
                execute_query(
                    "UPDATE booking SET amount_paid_2 = %s, payment_id_2 = %s WHERE id = %s",
                    args=(amount, payment_link.get("id"), booking_id),
                )
                
                send_notification(current_app, f"User {booking['user_name']} tried to exit for booking {booking['number']} but has an overstay of {overtime_hours} hours. Extra payment required: ₹{amount}. Check your email for payment link.")

        
                payment_url = payment_link['short_url']

                send_overtime_payment_email(
                    vehicle_number=booking["number"],
                    amount=amount,
                    user_name=booking['user_name'],
                    user_email=booking['email'],
                    payment_url=payment_url
                )

                return jsonify({
                    "msg": f"Overstay detected. Please pay extra ₹{amount} for {overtime_hours} hours.",
                    "overtime_hours": overtime_hours,
                    "extra_amount": amount
                }), 200


            # EXIT allowed only if current time <= end_time
            if current_datetime > end_datetime:
                send_notification(current_app, f"Exit not allowed. Booking time has already ended.")
                return jsonify({
                    "msg": "Exit not allowed. Booking time has already ended."
                }), 403

        else:
            send_notification(current_app, f"Invalid gate type. Use 'entry' or 'exit'.")
            return jsonify({"msg": "Invalid gate type. Use 'entry' or 'exit'"}), 400

    except Exception as e:
        print(f"Time validation error:{e.__traceback__.tb_lineno}", str(e))
        send_notification(current_app, f"Error occurred while validating booking time.")
        return jsonify({"msg": "Invalid booking time data"}), 400
    # ---------- End Time Validation ----------

    # Log manual open
    execute_query(
        "INSERT INTO entry_exit_log (PARKING_LOT_ID, EXTRACTED_NUMBER, GATE) VALUES (%s, %s, %s)",
        args=(booking["lot_id"], booking['number'], gate.upper()),
    )
    send_notification(current_app, f"User {booking['user_name']} opened gate {gate} for booking {booking['number']}.")

    if gate == "exit":
        # Mark booking as completed
        execute_query(
            "UPDATE booking SET status = 3 WHERE id = %s",
            args=(booking_id,),
        )
        with current_app.app_context():
            mqtt_manager = current_app.extensions.get("mqtt_manager")
            if mqtt_manager:
                mqtt_manager.publish(
                    topic=ESP32_CAM_EXIT_SERVO_TOPIC,
                    message="OPEN"
                )
                print(f"Published MQTT message to open gate for lot {booking['lot_id']}")

    if gate == "entry":
        with current_app.app_context():
            mqtt_manager = current_app.extensions.get("mqtt_manager")
            if mqtt_manager:
                mqtt_manager.publish(
                    topic=ESP32_CAM_ENTRY_SERVO_TOPIC,
                    message="OPEN"
                )
                print(f"Published MQTT message to open gate for lot {booking['lot_id']}")

    return jsonify({
        "msg": "Gate open command accepted"
    }), 200


#<------------------------ IOT & GATE APIS END ----------------------->#
