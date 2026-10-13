package com.example.carparkingsystem.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.carparkingsystem.R
import com.example.carparkingsystem.models.ParkingSlot

class ParkingSlotAdapter(
    private val parkingSlots: List<ParkingSlot>,
    private val onClick: (ParkingSlot) -> Unit
) : RecyclerView.Adapter<ParkingSlotAdapter.ParkingLotViewHolder>() {

    inner class ParkingLotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val parkingNameText: TextView = itemView.findViewById(R.id.parkingNameView)
        val parkingImage: ImageView = itemView.findViewById(R.id.parkingImage)
        val isAvailable: TextView = itemView.findViewById(R.id.isAvailable)
        val mainLayout:LinearLayout = itemView.findViewById(R.id.mainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingLotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.parking_slot_item, parent, false)
        return ParkingLotViewHolder(view)
    }

    @SuppressLint("SetTextI18n", "ResourceAsColor")
    override fun onBindViewHolder(holder: ParkingLotViewHolder, position: Int) {
        val slot = parkingSlots[position]
        holder.parkingNameText.text = slot.slot_number + " (Fare - ${slot.fare}/hr)"
        if (slot.is_available==1){
            holder.isAvailable.text = "Available"
            Glide.with(holder.parkingImage.context)
                .load(R.drawable.parking)
                .into(holder.parkingImage)
            holder.itemView.setOnClickListener {
                onClick(slot)
            }
        }else{
            holder.isAvailable.text = "Not Available"
            Glide.with(holder.parkingImage.context)
                .load(R.drawable.full_parking)
                .into(holder.parkingImage)
            holder.itemView.isClickable = false
            holder.mainLayout.isClickable = false
        }

    }

    override fun getItemCount() = parkingSlots.size
}
