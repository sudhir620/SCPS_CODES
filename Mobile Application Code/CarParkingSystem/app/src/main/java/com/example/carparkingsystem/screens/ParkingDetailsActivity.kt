package com.example.carparkingsystem.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.carparkingsystem.BookingBottomSheet
import com.example.carparkingsystem.LoaderUtil.hideLoader
import com.example.carparkingsystem.LoaderUtil.showLoader
import com.example.carparkingsystem.R
import com.example.carparkingsystem.SharedPrefManager
import com.example.carparkingsystem.adapters.ParkingSlotAdapter
import com.example.carparkingsystem.api.MqttManager
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.BookingRequest
import com.example.carparkingsystem.models.BookingResponse
import com.example.carparkingsystem.models.ErrorModel
import com.example.carparkingsystem.models.MsgModel
import com.example.carparkingsystem.models.ParkingSlot
import com.example.carparkingsystem.models.ParkingSlotResponse
import com.example.carparkingsystem.models.UpdateBookingRequest
import com.example.carparkingsystem.models.Vehicle
import com.example.carparkingsystem.models.VehicleResponse
import com.example.carparkingsystem.socket.IParkingUpdate
import com.google.android.material.appbar.MaterialToolbar
import com.google.gson.Gson
import com.razorpay.Checkout
import com.razorpay.PaymentResultListener
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ParkingDetailsActivity : AppCompatActivity(), PaymentResultListener, IParkingUpdate {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var parkingSlotList: RecyclerView
    private lateinit var noDataFound: TextView

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private var lotId: Int = 0
    private var bookingId: Int = -1
    private var slotId: Int = -1
    private var data: List<ParkingSlot> = emptyList()
    private var vehicleList: List<Vehicle> = emptyList()
    private var adapter: ParkingSlotAdapter? = null


    override fun onStart() {
        super.onStart()
        lotId = intent.getIntExtra("parkingLotId", 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parking_details)
        MqttManager.subscribe(MqttManager.UPDATE_PARKING_SLOT, 1)
        MqttManager.addListener(this)

        toolbar = findViewById(R.id.toolbar)
        parkingSlotList = findViewById(R.id.parkingSlotsList)
        noDataFound = findViewById(R.id.noDataFound)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)


        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        loadParkingSlots()
        loadVehicles()
        swipeRefreshLayout.setOnRefreshListener {
            loadParkingSlots()
            loadVehicles()
        }
    }

    private fun loadVehicles() {
        showLoader(this)
        RetrofitClient.getApiService().getVehicleDetails().enqueue(object : Callback<VehicleResponse> {
            override fun onResponse(
                call: Call<VehicleResponse>, response: Response<VehicleResponse>
            ) {
                swipeRefreshLayout.isRefreshing = false
                hideLoader()
                if (response.isSuccessful) {
                    val vehiclesListData = response.body()!!.data.orEmpty()
                    vehicleList = vehiclesListData
                    if (vehicleList.isEmpty()) {
                        Toast.makeText(this@ParkingDetailsActivity, "No vehicle found!", Toast.LENGTH_LONG).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this@ParkingDetailsActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<VehicleResponse>, t: Throwable) {
                hideLoader()
                Toast.makeText(this@ParkingDetailsActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
            }

        })
    }

    private fun loadParkingSlots() {
        showLoader(this)
        RetrofitClient.getApiService().parkingSlotsApi(intent.getIntExtra("parkingLotId", 0)).enqueue(object : Callback<ParkingSlotResponse> {
            override fun onResponse(
                call: Call<ParkingSlotResponse>, response: Response<ParkingSlotResponse>
            ) {
                swipeRefreshLayout.isRefreshing = false
                hideLoader()
                if (response.isSuccessful) {
                    data = response.body()!!.data.orEmpty()
                    loadAdapter()
                } else {
                    Toast.makeText(
                        this@ParkingDetailsActivity, "Something went wrong!", Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onFailure(call: Call<ParkingSlotResponse>, t: Throwable) {
                hideLoader()
                Toast.makeText(
                    this@ParkingDetailsActivity, "Something went wrong!", Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun loadAdapter() {
        noDataFound.isVisible = data.isEmpty()
        adapter = ParkingSlotAdapter(data, onClick = { slot ->
            slotId = slot.id!!
            val bottomSheet = BookingBottomSheet(slot, vehicleList) { startTime, endTime, hours, vehicle ->
                Log.d("BOOKING", "Start: $startTime")
                Log.d("BOOKING", "End: $endTime")
                Log.d("BOOKING", "Hours: $hours")
                Log.d("BOOKING", "Vehicle: ${vehicle.number}")
                createBooking(startTime, endTime, hours, vehicle.ID, slot.id)

            }
            bottomSheet.isCancelable = false
            bottomSheet.show(supportFragmentManager, "BookingBottomSheet")
        })
        parkingSlotList.adapter = adapter
    }

    private fun createBooking(startTime: String, endTime: String, hours: Int, vehicleId: Int, slotId: Int) {
        showLoader(this)
        RetrofitClient.getApiService().createBookingApi(
            BookingRequest(
                start_time = startTime, end_time = endTime, vehicle_id = vehicleId, booking_hours = hours, slot_id = slotId
            )
        ).enqueue(object : Callback<BookingResponse> {
            override fun onResponse(
                call: Call<BookingResponse>, response: Response<BookingResponse>
            ) {
                hideLoader()
                if (response.isSuccessful) {
                    response.body()?.let {
                        bookingId = it.data!!.booking_id
                        startPayment(it.data.id)
                    } ?: run {
                        Toast.makeText(
                            this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()?.trim().toString()
                    if (errorBody.isEmpty()) {
                        Toast.makeText(
                            this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_LONG
                        ).show()
                    } else {
                        try {
                            val errorResponse = Gson().fromJson(errorBody, ErrorModel::class.java)
                            Toast.makeText(
                                this@ParkingDetailsActivity, errorResponse.msg, Toast.LENGTH_LONG
                            ).show()
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            override fun onFailure(call: Call<BookingResponse>, t: Throwable) {
                hideLoader()
                Toast.makeText(
                    this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_LONG
                ).show()
            }
        })

    }

    private fun startPayment(orderId: String) {
        val checkout = Checkout()
        checkout.setKeyID("rzp_test_your_id")

        try {
            val options = JSONObject()
            options.put("name", "Car Park")
            options.put("description", "Test Payment")
            options.put(
                "image", "https://png.pngtree.com/png-vector/20230327/ourmid/pngtree-car-parking-sign-design-template-vector-png-image_6671099.png"
            )
            options.put("theme.color", "#3399cc")
            options.put("currency", "INR")
            options.put("order_id", orderId)
            checkout.open(this, options)

        } catch (e: Exception) {
            Toast.makeText(this, "Error in starting payment: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        MqttManager.removeListener()
    }

    override fun onPaymentSuccess(p0: String?) {
        updateBooking("success")
    }

    override fun onPaymentError(p0: Int, p1: String?) {
        Toast.makeText(this, "Payment failed: $p1", Toast.LENGTH_LONG).show()
        updateBooking("failed")
    }

    private fun updateBooking(status: String) {
        showLoader(this)
        RetrofitClient.getApiService().updateBookingApi(
            UpdateBookingRequest(
                booking_id = bookingId, slot_id = slotId, status = status
            )
        ).enqueue(object : Callback<MsgModel> {
            override fun onResponse(call: Call<MsgModel>, response: Response<MsgModel>) {
                hideLoader()
                if (response.isSuccessful) {
                    response.body()?.let {
                        Toast.makeText(
                            this@ParkingDetailsActivity, "Your booking has been confirmed", Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } ?: run {
                        Toast.makeText(
                            this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()?.trim().toString()
                    if (errorBody.isEmpty()) {
                        Toast.makeText(
                            this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_LONG
                        ).show()
                    } else {
                        try {
                            val errorResponse = Gson().fromJson(errorBody, ErrorModel::class.java)
                            Toast.makeText(
                                this@ParkingDetailsActivity, errorResponse.msg, Toast.LENGTH_LONG
                            ).show()
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            override fun onFailure(call: Call<MsgModel>, t: Throwable) {
                hideLoader()
                Toast.makeText(
                    this@ParkingDetailsActivity, "Something went wrong. Please try again!", Toast.LENGTH_LONG
                ).show()
            }
        })
        startActivity(Intent(this, DashboardActivity::class.java))
        finishAffinity()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun message(message: String) {
        if (message.isEmpty() or message.isBlank()) return
        message.split("=").let {
            if (it.isEmpty() || it.size < 3) return
            val slotId = it[1].trim().toInt()
            if (it[2].trim() == SharedPrefManager.getDeviceId()) return
            runOnUiThread {
                val finalData: ArrayList<ParkingSlot> = ArrayList()
                for (item in data) {
                    if (item.id == slotId) {
                        if (message.startsWith("start")) {
                            item.is_available = 0
                        } else if (message.startsWith("end")) {
                            item.is_available = 1
                        }
                    }
                    finalData.add(item)
                }
                data = finalData
                adapter?.notifyDataSetChanged()
            }
        }
    }
}
