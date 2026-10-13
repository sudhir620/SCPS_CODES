package com.example.carparkingsystem.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.carparkingsystem.LoaderUtil.hideLoader
import com.example.carparkingsystem.LoaderUtil.showLoader
import com.example.carparkingsystem.R
import com.example.carparkingsystem.api.RetrofitClient
import com.example.carparkingsystem.models.AddVehicleResponse
import com.google.android.material.appbar.MaterialToolbar
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddVehicleActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var imageView: ImageView
    private lateinit var addBtn: AppCompatButton

    private lateinit var etVehicleNumber: EditText
    private lateinit var etOwnerMobile: EditText
    private  lateinit var etOwnerName: EditText
    private lateinit var photoURI: Uri
    private lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_vehicle)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        imageView = findViewById(R.id.imageView)
        addBtn = findViewById(R.id.addBtn)

        etVehicleNumber = findViewById(R.id.numberView)
        etOwnerMobile = findViewById(R.id.owner_mobile)
        etOwnerName = findViewById(R.id.owner_name)



        imageView.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }
        addBtn.setOnClickListener {
            showLoader(this)
            uploadVehicle(photoURI)
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkCameraPermissionAndOpenCamera() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                dispatchTakePictureIntent()
            }

            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                imageView.setImageURI(photoURI)
            } else {
                Toast.makeText(this, "Image capture failed", Toast.LENGTH_SHORT).show()
            }
        }

    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "upload_image_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)

            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun dispatchTakePictureIntent() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            null
        }

        photoFile?.also {
            photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )

            takePictureLauncher.launch(photoURI)
        }
    }

    fun String.toRequestBody(): RequestBody =
        this.toRequestBody("text/plain".toMediaTypeOrNull())

    private fun prepareFilePart(file: File): MultipartBody.Part {

        val requestFile =
            file.asRequestBody("image/*".toMediaTypeOrNull())

        return MultipartBody.Part.createFormData(
            "image",
            file.name,
            requestFile
        )
    }

    private fun uploadVehicle(uri: Uri) {

        val file = uriToFile(uri)

        if (file == null) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_LONG).show()
            return
        }

        val imagePart = prepareFilePart(file)
        val ownerNameBody = etOwnerName.text.toString().toRequestBody()
        val ownerMobileBody = etOwnerMobile.text.toString().toRequestBody()
        val numberBody = etVehicleNumber.text.toString().toRequestBody()

        RetrofitClient.getApiService().addVehicle(
            imagePart,
            ownerNameBody,
            ownerMobileBody,
            numberBody
        ).enqueue(object : Callback<AddVehicleResponse>{
            override fun onResponse(
                call: Call<AddVehicleResponse?>,
                response: Response<AddVehicleResponse?>
            ) {
                hideLoader()
                if (response.isSuccessful && response.body() != null) {

                    Toast.makeText(
                        this@AddVehicleActivity,
                        response.body()!!.msg,
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this@AddVehicleActivity, DashboardActivity::class.java))
                    finishAffinity()

                } else {

                    val errorBody = response.errorBody()?.string()

                    try {
                        val jsonObject = JSONObject(errorBody ?: "")
                        val message = jsonObject.optString("msg", "Something went wrong!")

                        Toast.makeText(
                            this@AddVehicleActivity,
                            message,
                            Toast.LENGTH_LONG
                        ).show()

                    } catch (e: Exception) {
                        hideLoader()
                        Toast.makeText(this@AddVehicleActivity, "Something went wrong!", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onFailure(
                call: Call<AddVehicleResponse?>,
                t: Throwable
            ) {
                hideLoader()
                Toast.makeText(this@AddVehicleActivity, "Something went wrong!", Toast.LENGTH_LONG).show()

            }
        })
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(imageFileName, ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}