from apscheduler.schedulers.background import BackgroundScheduler
from datetime import datetime
from utils.mqtt_manager import NOTIFICATION_TOPIC
from utils.db_utils import execute_query


def update_booking_status(mqtt_manager):
    print("Checking booking status...", datetime.now())

    query = """
        SELECT a.*, IF(b.extracted_number is null, b.number, b.extracted_number) as number FROM car_parking.booking as a inner join vehicle_details as b on a.vehicle_id = b.ID where end_time<now() and status = 2;
    """

    bookings = execute_query(query=query)

    for booking in bookings:

        booking_id = booking["id"]
        vehicle_id = booking["vehicle_id"]

        # Check whether vehicle has exited
        exit_query = """
            SELECT * FROM car_parking.entry_exit_log where DATE(DATENTIME) = curdate() and EXTRACTED_NUMBER = %s AND GATE = "EXIT";
        """

        exit_record = execute_query(
            query=exit_query,
            args=(booking["number"],),
            is_fetchone=True
        )

        if exit_record:
            # Vehicle has exited
            update_query = """
                UPDATE car_parking.booking
                SET status = 3
                WHERE id = %s
            """

            execute_query(
                query=update_query,
                args=(booking_id,)
            )

            update_slot_query = """
                UPDATE car_parking.parking_slots
                SET is_available = 1
                WHERE id = %s and is_active = 1
            """
            execute_query(
                            query=update_slot_query,
                            args=(booking["slot_id"],)
                        )

            print(
                f"Booking {booking_id}: "
                f"vehicle exited → COMPLETED"
            )

        else:
            # End time passed but vehicle is still inside
            update_query = """
                UPDATE car_parking.booking
                SET time_over = 1
                WHERE id = %s and time_over = 0
            """

            row_affected = execute_query(
                query=update_query,
                args=(booking_id,)
            )
            if row_affected > 0:
                mqtt_manager.publish(
                            NOTIFICATION_TOPIC,
                        f"Your parking booking for #{booking["number"]} "
                        f"has exceeded the booking time.",
                        qos=1
                    )

            print(
                f"Booking {booking_id}: "
                f"vehicle still inside → EXPIRED"
            )


def start_booking_scheduler(app, mqtt_manager):

    scheduler = BackgroundScheduler()

    scheduler.add_job(
        func=run_with_context,
        trigger="interval",
        seconds=30,
        args=[app, mqtt_manager],
        id="booking_status_checker",
        replace_existing=True
    )

    scheduler.start()

    print("Booking scheduler started")


def run_with_context(app, mqtt_manager):
    with app.app_context():
        try:
            update_booking_status(mqtt_manager)
        except Exception as e:
            print("Booking scheduler error:", e)