package com.example.carparkingsystem.models

data class OpenGateRequestBody(val booking_id: Int, val gate: String, val lat: Double, val lng: Double)