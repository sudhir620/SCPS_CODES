package com.example.carparkingsystem.models

data class VehicleResponse(val msg:String?, val data:List<Vehicle>?)

data class AddVehicleResponse(val msg:String?, val data:VehicleDataRes?)
data class VehicleDataRes(val id: Int?)


data class Vehicle(val ID:Int, val image:String?, val owner_name:String?, val owner_mobile:String?, val number:String?, val extracted_number:String?)


