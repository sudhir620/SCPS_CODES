package com.example.carparkingsystem.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.carparkingsystem.DialogUtil.showAlertDialog
import com.example.carparkingsystem.LoaderUtil
import com.example.carparkingsystem.R
import com.example.carparkingsystem.SharedPrefManager
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.ErrorModel
import com.example.carparkingsystem.models.LoginModel
import com.example.carparkingsystem.models.LoginRequest
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : AppCompatActivity() {
    private lateinit var loginBtn: AppCompatButton
    private lateinit var emailView: TextInputEditText
    private lateinit var passwordView: TextInputEditText
    private lateinit var signupText: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        loginBtn = findViewById(R.id.loginBtn)
        emailView = findViewById(R.id.emailView)
        passwordView = findViewById(R.id.passwordView)
        signupText = findViewById(R.id.tvSignup)


        signupText.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            finish()
        }

        loginBtn.setOnClickListener {
            LoaderUtil.showLoader(this)
            RetrofitClient.getApiService().loginApi(
                LoginRequest(
                    email = emailView.text.toString(),
                    password = passwordView.text.toString(),
                    device_id = SharedPrefManager.getDeviceId()
                )
            ).enqueue(object : Callback<LoginModel> {
                override fun onResponse(call: Call<LoginModel>, response: Response<LoginModel>) {
                    LoaderUtil.hideLoader()
                    if (response.isSuccessful) {
                        response.body()?.let {
                            SharedPrefManager.saveApiToken(it.token.orEmpty())
                            SharedPrefManager.saveString("user_data", Gson().toJson(it))
                            SharedPrefManager.saveString("is_logged_in", "YES")
                            startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                            finish()
                        }?: run {
                            Toast.makeText(this@LoginActivity, "Something went wrong. Please try again!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()?.trim().toString()
                        if (errorBody.isEmpty()) {
                            Toast.makeText(this@LoginActivity, "Invalid credentials!", Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                val errorResponse = Gson().fromJson(errorBody, ErrorModel::class.java)
                                Toast.makeText(this@LoginActivity, errorResponse.msg, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@LoginActivity, "Invalid credentials!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<LoginModel>, t: Throwable) {
                    LoaderUtil.hideLoader()
                    Toast.makeText(this@LoginActivity, "Invalid credentials!", Toast.LENGTH_LONG).show()
                }
            })
        }

    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        showAlertDialog(this,  "Exit App", "Are you sure you want to exit?") {
            finishAffinity()
        }
    }
}