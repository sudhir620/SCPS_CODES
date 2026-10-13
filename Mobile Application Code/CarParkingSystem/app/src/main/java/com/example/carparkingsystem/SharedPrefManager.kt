package com.example.carparkingsystem


import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

object SharedPrefManager {

    private const val PREF_NAME = "MyAppPrefs"
    private lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveString(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    fun getString(key: String): String? {
        return preferences.getString(key, null)
    }

    fun clearAll() {
        preferences.edit {
            saveString("is_logged_in", "NO")
            saveString("user_data", "")
            saveApiToken("")
        }
    }

    fun isLoggedIn():Boolean{
        return getString("is_logged_in").orEmpty() == "YES"
    }

    fun saveApiToken(token:String){
        saveString("api_token", token)
    }

    fun getApiToken():String{
        return getString("api_token").orEmpty()
    }

    fun getDeviceId():String{
        val deviceId = getString("device_id")
        if(deviceId!=null){
            return deviceId
        }else{
            val token = UUID.randomUUID().toString()
            saveString("device_id", token)
            return token
        }
    }

    fun setBaseUrl(baseUrl: String) {
        saveString("base_url", baseUrl.trim('"'))
    }

    fun getBaseUrl(): String {
        return getString("base_url").orEmpty()
    }
}