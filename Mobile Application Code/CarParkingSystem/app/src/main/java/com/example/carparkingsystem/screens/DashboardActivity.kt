package com.example.carparkingsystem.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.carparkingsystem.DialogUtil.showAlertDialog
import com.example.carparkingsystem.LoaderUtil.hideLoader
import com.example.carparkingsystem.LoaderUtil.showLoader
import com.example.carparkingsystem.R
import com.example.carparkingsystem.SharedPrefManager
import com.example.carparkingsystem.adapters.ParkingLotAdapter
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.ErrorModel
import com.example.carparkingsystem.models.LoginModel
import com.example.carparkingsystem.models.MsgModel
import com.example.carparkingsystem.models.ParkingLotResponse
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DashboardActivity : AppCompatActivity() {
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var nameView:TextView
    private lateinit var emailView:TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var parkingList:RecyclerView
    private lateinit var noDataFound:TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        onBackPressedDispatcher.addCallback(backPressedCallback)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.navigation_view)
        toolbar = findViewById(R.id.toolbar)
        parkingList = findViewById(R.id.parkingLotsList)
        noDataFound = findViewById(R.id.noDataFound)

        val headerView = navView.getHeaderView(0)
        nameView = headerView.findViewById(R.id.nameView)
        emailView = headerView.findViewById(R.id.emailView)

        val userJson = SharedPrefManager.getString("user_data")
        val user = Gson().fromJson(userJson, LoginModel::class.java)
        user?.let {
            nameView.text = buildString {
                append("Hello, ")
                append(user.data!!.user_name)
            }
            emailView.text = user.data!!.email
        }

        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_history -> {
                    startActivity(Intent(this, BookingHistoryActivity::class.java))
                }
                R.id.nav_vehicles -> {
                    startActivity(Intent(this, VehiclesListActivity::class.java))
                }
                R.id.nav_logout-> {
                    showAlertDialog(this,  "Alert", "Are you sure you want to logout?") {

                        RetrofitClient.getApiService().logoutApi().enqueue(object :
                            Callback<MsgModel> {
                            override fun onResponse(
                                call: Call<MsgModel>,
                                response: Response<MsgModel>
                            ) {
                                if (response.isSuccessful){
                                    Toast.makeText(this@DashboardActivity, response.body()!!.msg, Toast.LENGTH_LONG).show()
                                    SharedPrefManager.clearAll()
                                    startActivity(Intent(this@DashboardActivity, LoginActivity::class.java))
                                    finishAffinity()
                                }else{
                                    val errorBody = response.errorBody()?.string()?.trim().toString()
                                    if (errorBody.isEmpty()) {
                                        Toast.makeText(this@DashboardActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                                    } else {
                                        try {
                                            val errorResponse = Gson().fromJson(errorBody, ErrorModel::class.java)
                                            Toast.makeText(this@DashboardActivity, errorResponse.msg, Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(this@DashboardActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }

                            }

                            override fun onFailure(call: Call<MsgModel>, t: Throwable) {
                                Toast.makeText(this@DashboardActivity, "Something went wrong!", Toast.LENGTH_LONG).show()

                            }
                        })
                    }
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        loadParkingLots()
        swipeRefreshLayout.setOnRefreshListener {
            loadParkingLots()
        }
    }

    private fun loadParkingLots(){
        showLoader(this)
        RetrofitClient.getApiService().parkingLotsApi().enqueue(
            object : Callback<ParkingLotResponse>{
                override fun onResponse(
                    call: Call<ParkingLotResponse>,
                    response: Response<ParkingLotResponse>
                ) {
                    hideLoader()
                    if(response.isSuccessful){
                        val parkingListData = response.body()!!.data.orEmpty()
                        noDataFound.isVisible = parkingListData.isEmpty()
                        val adapter = ParkingLotAdapter(parkingListData)
                        parkingList.adapter = adapter
                        swipeRefreshLayout.isRefreshing = false
                    }else{
                        Toast.makeText(this@DashboardActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ParkingLotResponse>, t: Throwable) {
                    hideLoader()
                    Toast.makeText(this@DashboardActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                }

            }
        )
    }

    val backPressedCallback = object : OnBackPressedCallback(true){
        override fun handleOnBackPressed() {
            showAlertDialog(this@DashboardActivity,  "Exit App", "Are you sure you want to exit?") {
                finishAffinity()
            }
        }
    }
}