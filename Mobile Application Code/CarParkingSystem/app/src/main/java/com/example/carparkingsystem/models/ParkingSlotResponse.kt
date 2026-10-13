package com.example.carparkingsystem.models

data class ParkingSlot(
    val id:Int?,
    var is_available: Int?,
    val lot_id: Int?,
    val slot_number: String?,
    val vehicle_type: String?,
    val fare: Int?
)

data class ParkingSlotResponse(
    val data: List<ParkingSlot>?,
    val msg:String?
)