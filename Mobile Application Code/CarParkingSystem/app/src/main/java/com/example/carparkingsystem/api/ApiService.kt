package com.example.carparkingsystem.api

import com.example.carparkingsystem.models.AddVehicleResponse
import com.example.carparkingsystem.models.BookingHistoryResponse
import com.example.carparkingsystem.models.BookingRequest
import com.example.carparkingsystem.models.BookingResponse
import com.example.carparkingsystem.models.LoginModel
import com.example.carparkingsystem.models.LoginRequest
import com.example.carparkingsystem.models.MsgModel
import com.example.carparkingsystem.models.OpenGateRequestBody
import com.example.carparkingsystem.models.ParkingLotResponse
import com.example.carparkingsystem.models.ParkingSlotResponse
import com.example.carparkingsystem.models.SignupModel
import com.example.carparkingsystem.models.SignupRequest
import com.example.carparkingsystem.models.UpdateBookingRequest
import com.example.carparkingsystem.models.VehicleResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface ApiService {
    @POST("auth/login")
    fun loginApi(@Body loginRequest: LoginRequest): Call<LoginModel>

    @POST("auth/signup")
    fun signupApi(@Body signupRequest: SignupRequest): Call<SignupModel>

    @GET("auth/logout")
    fun logoutApi(): Call<MsgModel>

    @GET("parking_lot/parking_lots")
    fun parkingLotsApi(): Call<ParkingLotResponse>

    @GET("parking_slot/get")
    fun parkingSlotsApi(@Query("lot_id") lotId: Int): Call<ParkingSlotResponse>

    @POST("booking/create")
    fun createBookingApi(@Body bookingRequest: BookingRequest): Call<BookingResponse>

    @POST("booking/update")
    fun updateBookingApi(@Body updateBookingRequest: UpdateBookingRequest): Call<MsgModel>

    @GET("booking/get")
    fun getBookingHistory():Call<BookingHistoryResponse>

    @GET("vehicle/details")
    fun getVehicleDetails():Call<VehicleResponse>

    @Multipart
    @POST("vehicle/add_details")
    fun addVehicle(
        @Part image: MultipartBody.Part,
        @Part("owner_name") ownerName: RequestBody,
        @Part("owner_mobile") ownerMobile: RequestBody,
        @Part("number") number: RequestBody
    ): Call<AddVehicleResponse>

    @POST("booking/open-gate")
    fun openGateApi(@Body openGateRequestBody: OpenGateRequestBody): Call<MsgModel>
}