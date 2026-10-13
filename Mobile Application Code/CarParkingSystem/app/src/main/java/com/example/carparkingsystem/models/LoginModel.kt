package com.example.carparkingsystem.models

data class LoginModel(val msg:String?, val data:UserData?, val token:String?)

data class UserData(val email:String?, val role_name:String?, val user_name:String?)

data class LoginRequest(
    val email: String,
    val password: String,
    val device_id: String
)
data class ErrorModel(val msg:String?)

