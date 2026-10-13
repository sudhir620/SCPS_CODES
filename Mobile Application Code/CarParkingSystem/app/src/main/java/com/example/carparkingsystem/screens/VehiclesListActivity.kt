package com.example.carparkingsystem.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.carparkingsystem.LoaderUtil.hideLoader
import com.example.carparkingsystem.LoaderUtil.showLoader
import com.example.carparkingsystem.R
import com.example.carparkingsystem.adapters.VehicleAdapter
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.VehicleResponse
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class VehiclesListActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var vehicleList: RecyclerView
    private lateinit var noDataFound: TextView
    private lateinit var addButton: ImageView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicles_list)

        vehicleList = findViewById(R.id.vehicleList)
        noDataFound = findViewById(R.id.noDataFound)
        addButton = findViewById(R.id.addButton)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)


        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        addButton.setOnClickListener {
            val intent = Intent(this, AddVehicleActivity::class.java)
            startActivity(intent)
        }

        loadVehicles()
        swipeRefreshLayout.setOnRefreshListener {
            loadVehicles()
        }
    }

    private fun loadVehicles(){
        showLoader(this)
        RetrofitClient.getApiService().getVehicleDetails().enqueue(
            object : Callback<VehicleResponse> {
                override fun onResponse(
                    call: Call<VehicleResponse>,
                    response: Response<VehicleResponse>
                ) {
                    swipeRefreshLayout.isRefreshing = false
                    hideLoader()
                    if(response.isSuccessful){
                        val vehiclesListData = response.body()!!.data.orEmpty()
                        noDataFound.isVisible = vehiclesListData.isEmpty()
                        val adapter = VehicleAdapter(vehiclesListData){
                        }
                        vehicleList.adapter = adapter
                    }else{
                        val errorBody = response.errorBody()?.string()

                        try {
                            val jsonObject = JSONObject(errorBody ?: "")
                            val message = jsonObject.optString("msg", "Something went wrong!")

                            Toast.makeText(
                                this@VehiclesListActivity,
                                message,
                                Toast.LENGTH_LONG
                            ).show()

                        } catch (_: Exception) {
                            Toast.makeText(
                                this@VehiclesListActivity,
                                "Something went wrong!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                override fun onFailure(call: Call<VehicleResponse>, t: Throwable) {
                    hideLoader()
                    Toast.makeText(this@VehiclesListActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                }

            }
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}