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
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.ErrorModel
import com.example.carparkingsystem.models.SignupModel
import com.example.carparkingsystem.models.SignupRequest
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {
    private lateinit var nameView: TextInputEditText
    private lateinit var emailView: TextInputEditText
    private lateinit var passwordView: TextInputEditText
    private lateinit var confPasswordView: TextInputEditText
    private lateinit var signupBtn: AppCompatButton
    private lateinit var loginTexView: TextView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        nameView = findViewById(R.id.nameView)
        emailView = findViewById(R.id.emailView)
        passwordView = findViewById(R.id.passwordView)
        confPasswordView = findViewById(R.id.confPasswordView)
        signupBtn = findViewById(R.id.signUpBtn)
        loginTexView = findViewById(R.id.tvLogin)

        loginTexView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        signupBtn.setOnClickListener {
            LoaderUtil.showLoader(this)
            RetrofitClient.getApiService().signupApi(
                SignupRequest(
                    user_name = nameView.text.toString(),
                    email = emailView.text.toString(),
                    password = passwordView.text.toString(),
                    conf_password = confPasswordView.text.toString()
                )
            ).enqueue(object : Callback<SignupModel> {
                override fun onResponse(call: Call<SignupModel>, response: Response<SignupModel>) {
                    LoaderUtil.hideLoader()
                    if (response.code() == 201) {
                        response.body()?.let {
                            Toast.makeText(this@SignupActivity, it.msg, Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@SignupActivity, LoginActivity::class.java))
                            finishAffinity()
                        }?: run {
                            Toast.makeText(this@SignupActivity, "Something went wrong. Please try again!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()?.trim().toString()
                        if (errorBody.isEmpty()) {
                            Toast.makeText(this@SignupActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                        } else {
                            try {
                                val errorResponse = Gson().fromJson(errorBody, ErrorModel::class.java)
                                Toast.makeText(this@SignupActivity, errorResponse.msg, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(this@SignupActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<SignupModel>, t: Throwable) {
                    LoaderUtil.hideLoader()
                    Toast.makeText(this@SignupActivity, "Invalid credentials!", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        showAlertDialog(this,  "Exit App", "Are you sure you want to exit?") {
            finishAffinity()
        }
    }
}