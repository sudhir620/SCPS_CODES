package com.example.carparkingsystem.models

data class ParkingLotResponse(val data: List<ParkingLot>?)

data class ParkingLot(val id:Int?, val name:String?, val address:String, val is_dynamic_pricing:Int, val total_slots:Int?, val is_active:Int?, val available_count:Int?)
