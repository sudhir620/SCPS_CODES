package com.example.carparkingsystem.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.carparkingsystem.LoaderUtil.hideLoader
import com.example.carparkingsystem.LoaderUtil.showLoader
import com.example.carparkingsystem.R
import com.example.carparkingsystem.adapters.BookingHistoryAdapter
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.BookingHistoryResponse
import com.example.carparkingsystem.models.MsgModel
import com.example.carparkingsystem.models.OpenGateRequestBody
import com.google.android.gms.location.LocationServices
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class BookingHistoryActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var bookingHistoryList: RecyclerView

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var noDataFound: TextView
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booking_history)

        getCurrentLocation()

        bookingHistoryList = findViewById(R.id.bookingHistoryList)
        noDataFound = findViewById(R.id.noDataFound)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        loadBookingHistory()
        calculateDistance(lat2 = currentLat, lng2 = currentLng)
        swipeRefreshLayout.setOnRefreshListener {
            loadBookingHistory()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@BookingHistoryActivity)

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    Log.d("Location", "Lat: $latitude, Lng: $longitude")
                    currentLat = latitude
                    currentLng = longitude
                } else {
                    Log.d("Location", "Location is null")
                    currentLat = 0.0
                    currentLng = 0.0
                }
            }
    }

    private fun loadBookingHistory(){
        showLoader(this)
        RetrofitClient.getApiService().getBookingHistory().enqueue(
            object : Callback<BookingHistoryResponse> {

                override fun onResponse(
                    call: Call<BookingHistoryResponse>,
                    response: Response<BookingHistoryResponse>
                ) {
                    swipeRefreshLayout.isRefreshing = false
                    hideLoader()
                    if(response.isSuccessful){
                        val parkingListData = response.body()!!.data.orEmpty()
                        noDataFound.isVisible = parkingListData.isEmpty()
                        val adapter = BookingHistoryAdapter(parkingListData,{
                            openGoogleMaps(it.lat, it.lng)
                        },{
                            getCurrentLocation()

                            showOpenGateDialog(it.ID)
                        })
                        bookingHistoryList.adapter = adapter
                    }else{
                        Toast.makeText(this@BookingHistoryActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<BookingHistoryResponse>, t: Throwable) {
                    hideLoader()
                    Toast.makeText(this@BookingHistoryActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                }

            }
        )
    }

    private fun showOpenGateDialog(bookingId: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Want to manual open gate?")
        builder.setMessage("Please select Entry or Exit")

        builder.setPositiveButton("Entry") { dialog, _ ->
            callOpenGateApi(bookingId, "ENTRY", currentLat, currentLng)
            dialog.dismiss()
        }

        builder.setNegativeButton("Exit") { dialog, _ ->
            callOpenGateApi(bookingId, "EXIT", currentLat, currentLng)
            dialog.dismiss()
        }

        builder.setNeutralButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.setCancelable(false)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(Color.WHITE)
    }

    private fun callOpenGateApi(bookingId: Int, type: String, lat: Double, lng: Double) {
        showLoader(this)

        val request = OpenGateRequestBody(bookingId, type, lat, lng)

        RetrofitClient.getApiService().openGateApi(request)
            .enqueue(object : Callback<MsgModel> {
                override fun onResponse(call: Call<MsgModel?>, response: Response<MsgModel?>) {
                    hideLoader()
                    if (response.isSuccessful) {
                        loadBookingHistory()
                        val message = response.body()?.msg ?: "Gate open command accepted"
                        Toast.makeText(this@BookingHistoryActivity, message, Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMessage = try {
                            val errorBody = response.errorBody()?.string()
                            if (!errorBody.isNullOrEmpty()) {
                                val json = JSONObject(errorBody)
                                json.optString("msg", "Something went wrong")
                            } else {
                                response.body()?.msg ?: "Something went wrong"
                            }
                        } catch (e: Exception) {
                            "You are too far from the parking gate"
                        }
                        Toast.makeText(this@BookingHistoryActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<MsgModel?>, t: Throwable) {
                    hideLoader()
                    Toast.makeText(this@BookingHistoryActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun calculateDistance(lat1: Double = 28.6316563, lng1: Double = 76.2069526, lat2: Double, lng2: Double): Float {
        val startLocation = Location("parkingLocation").apply {
            latitude = lat1
            longitude = lng1
        }

        val endLocation = Location("userLocation").apply {
            latitude = lat2
            longitude = lng2
        }

        val distanceInMeters = startLocation.distanceTo(endLocation)
        Log.d("Distance", "Distance: $distanceInMeters meters")
        return distanceInMeters
    }

    private fun openGoogleMaps(destinationLat: Double, destinationLng: Double) {
        val uri = "http://maps.google.com/maps?daddr=$destinationLat,$destinationLng"
        val intent = Intent(Intent.ACTION_VIEW, uri.toUri())
        intent.setPackage("com.google.android.apps.maps") // Force open in Google Maps app
        startActivity(intent)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}