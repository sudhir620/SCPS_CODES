from flask import Blueprint, request, jsonify, session, redirect, render_template, url_for
import uuid
import requests

auth_view = Blueprint('auth_view', __name__)


@auth_view.route("/")
def index():
    token = session.get('token')
    if not token:
        return redirect('login')
    else:
        return render_template('index.html', active_nav='dashboard', token= token)

@auth_view.route('/login', methods=['GET', 'POST'])
def login():
    token = session.get('token')
    if token:
        return redirect('/')
    if request.method == 'POST':
        data = request.get_json() or request.form

        email = data.get('email')
        password = data.get('password')

        if 'device_id' not in session or session["device_id"] == "":
            session['device_id'] = str(uuid.uuid4())[:20]

        device_id = session['device_id']

        payload = {
            'email': email,
            'password': password,
            'device_id': device_id,
            'user_type':'admin'
        }

        try:
            response = requests.post(url_for('apis.login', _external=True), json=payload)

            response_data = response.json()
            if response.status_code == 200:
                session['token'] = response_data.get('token')
                return jsonify({'success': True, 'redirect': '/'})
            else:
                error_msg = response_data.get('msg', 'Something went wrong!!! Please try again.')
                return jsonify({'success': False, 'error': error_msg}), 401
        except Exception as e:
            return jsonify({'success': False, 'error': 'Something went wrong!!! Please try again.'}), 502
    return render_template('auth_login.html')

@auth_view.route('/signup', methods=['GET', 'POST'])
def signup():
    token = session.get('token')
    if token:
        return redirect('/')
    if request.method == 'POST':
        data = request.get_json() or request.form

        user_name = data.get('name')
        email = data.get('email')
        password = data.get('password')
        conf_password = data.get('password2')

        payload = {
            'user_name': user_name,
            'email': email,
            'password': password,
            'conf_password': conf_password,
            'role_id': 1
        }

        try:
            response = requests.post(url_for('apis.signup', _external=True), json=payload)
            response_data = response.json()
            if response.status_code == 201:
                return jsonify({'success': True, 'redirect': '/login'})
            else:
                error_msg = response_data.get('msg', 'Something went wrong!!! Please try again.')
                return jsonify({'success': False, 'error': error_msg}), 401
        except Exception as e:
            return jsonify({'success': False, 'error': 'Something went wrong!!! Please try again.'}), 502
    
    return render_template('auth_register.html')

@auth_view.route('/logout', methods=['GET'])
def logout():
    token = session.get('token')
    if token:
        try:
            response = requests.get(url_for('apis.logout', _external=True), headers={"Authorization" : "Bearer "+token})
            response_data = response.json()
            if response.status_code == 200:
                session.pop('token', None)
                return jsonify({
                    'success': True,
                    'message': response_data.get('msg', 'Logged out successfully.'),
                    'redirect': '/login'
                })
            else:
                error_msg = response_data.get('msg', 'Something went wrong. Please try again.')
                session['token'] = ""
                return jsonify({'success': False, 'error': error_msg, 'redirect': '/login'}), 401

        except Exception as e:
            return jsonify({'success': False, 'error': 'Something went wrong. Please try again.'}), 500

    return jsonify({'success': False, 'error': 'User not logged in.'}), 401