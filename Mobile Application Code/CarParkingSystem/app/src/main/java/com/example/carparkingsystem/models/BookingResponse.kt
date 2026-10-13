package com.example.carparkingsystem.models

data class BookingResponse(val msg: String, val data: BookingData?)

data class BookingData(val id:String, val booking_id:Int)

data class BookingHistoryResponse(val msg: String, val data: List<BookingHistory>?)

data class BookingHistory(val ID:Int, val start_time:String, val end_time:String, val slot_number:String, val amount_paid:Int,val status:Int, val time_over:Int, val name:String, val address:String, val lat:Double, val lng:Double, val vehicle_number:String)
