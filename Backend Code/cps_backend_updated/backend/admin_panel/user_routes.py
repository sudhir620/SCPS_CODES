from flask import Blueprint, request, jsonify, session, redirect, render_template, current_app, url_for
import uuid
from utils.decorators import is_loggedin


user_routes_view = Blueprint('user_routes_view', __name__)
    
@user_routes_view.route("/bookings")
@is_loggedin
def bookings():
    return render_template('bookings.html', active_nav='bookings', token= session.get('token'))
    
@user_routes_view.route("/parkings")
@is_loggedin
def parkings():
    return render_template('parkings.html', active_nav='parkings', token= session.get('token'))
    
@user_routes_view.route("/parking_logs")
@is_loggedin
def parking_logs():
    return render_template('parking_logs.html', active_nav='parking_logs', token= session.get('token'))



