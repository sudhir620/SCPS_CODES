package com.example.carparkingsystem
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color

object DialogUtil {

    @SuppressLint("ResourceAsColor")
    fun showAlertDialog(
        activity: Activity,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        val alertDialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                onConfirm()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.setOnShowListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.WHITE)
        }
        alertDialog.show()
    }
}
