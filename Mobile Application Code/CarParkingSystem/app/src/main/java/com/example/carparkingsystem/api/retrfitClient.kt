package com.example.carparkingsystem.api

import com.example.carparkingsystem.SharedPrefManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var BASE_URL = SharedPrefManager.getBaseUrl()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    fun getApiService(): ApiService {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)

        if (getTokenFromStorage().isNotEmpty()) {
            val headerInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer "+getTokenFromStorage())
                    .build()
                chain.proceed(request)
            }
            clientBuilder.addInterceptor(headerInterceptor)
        }

        val okHttpClient = clientBuilder.build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun getTokenFromStorage(): String {
        return SharedPrefManager.getApiToken()
    }
}
