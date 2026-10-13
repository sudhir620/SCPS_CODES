package com.example.carparkingsystem.models

data class BookingRequest(val start_time: String, val end_time:String, val vehicle_id: Int, val slot_id: Int, val booking_hours:Int)

data class UpdateBookingRequest(val slot_id: Int, val booking_id:Int, val status:String)