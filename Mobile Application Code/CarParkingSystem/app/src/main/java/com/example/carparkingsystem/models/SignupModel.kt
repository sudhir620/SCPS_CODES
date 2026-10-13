package com.example.carparkingsystem.models

data class SignupModel(val msg:String?,)

data class SignupRequest(
    val user_name: String,
    val email: String,
    val password: String,
    val conf_password: String
)
