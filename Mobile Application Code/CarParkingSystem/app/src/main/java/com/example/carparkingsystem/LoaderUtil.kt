package com.example.carparkingsystem
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toDrawable
import com.bumptech.glide.Glide

object LoaderUtil {

    private var dialog: Dialog? = null

    fun showLoader(activity: Activity) {
        if (dialog?.isShowing == true) return

        dialog = Dialog(activity)
        val view: View = LayoutInflater.from(activity).inflate(R.layout.dailog_loader, null)
        dialog?.setContentView(view)
        dialog?.setCancelable(false)
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val loaderImage = view.findViewById<ImageView>(R.id.loaderImage)
        Glide.with(activity)
            .asGif()
            .load(R.drawable.loading_gif)
            .into(loaderImage)

        dialog?.show()
    }

    fun hideLoader() {
        dialog?.dismiss()
        dialog = null
    }
}
