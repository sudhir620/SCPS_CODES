from utils.mqtt_manager import NOTIFICATION_TOPIC


def send_notification(app, message):
    try:
        with app.app_context():
            mqtt_manager = app.extensions["mqtt_manager"]

            mqtt_manager.publish(
                topic=NOTIFICATION_TOPIC,
                message=message,
                qos=1
            )

    except Exception as e:
        print(f"Error sending notification: {e}")