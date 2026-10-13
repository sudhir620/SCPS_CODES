package com.example.carparkingsystem.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.carparkingsystem.R
import com.example.carparkingsystem.SharedPrefManager
import com.example.carparkingsystem.models.Vehicle

class VehicleAdapter(
    private val vehicleList: List<Vehicle>, private val onClick: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.ParkingLotViewHolder>() {

    inner class ParkingLotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv1: TextView = itemView.findViewById(R.id.tv1)
        val tv2: TextView = itemView.findViewById(R.id.tv2)
        val tv3: TextView = itemView.findViewById(R.id.tv3)
        val tv4: TextView = itemView.findViewById(R.id.tv4)
        val vehicleImage: ImageView = itemView.findViewById(R.id.vehicleImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingLotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vehicle_item_layout, parent, false)
        return ParkingLotViewHolder(view)
    }

    fun getImageUrl(image: String?): String? {
        if (image.isNullOrBlank()) return null

        // Already a complete URL
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return image
        }
        return SharedPrefManager.getBaseUrl()
            .removeSuffix("api/")
            .removeSuffix("/")
            .plus("/")
            .plus(image.removePrefix("/"))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ParkingLotViewHolder, position: Int) {
        val vehicle = vehicleList[position]
        holder.tv1.text = "Owner Name :" + vehicle.owner_name
        holder.tv2.text = "Owner Mobile:" + vehicle.owner_mobile
        holder.tv3.text = "Vehicle Number: " + vehicle.number
        holder.tv4.text = "Extracted No.: " + vehicle.extracted_number
        holder.tv4.visibility = if (!vehicle.extracted_number.isNullOrEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        Glide.with(holder.itemView).load(getImageUrl(vehicle.image)).into(holder.vehicleImage)

        holder.itemView.setOnClickListener {
            onClick(vehicle)
        }
    }

    override fun getItemCount() = vehicleList.size
}
