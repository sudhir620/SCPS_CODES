package com.example.carparkingsystem.adapters

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.carparkingsystem.R
import com.example.carparkingsystem.models.ParkingLot
import com.example.carparkingsystem.screens.ParkingDetailsActivity

class ParkingLotAdapter(
    private val parkingLots: List<ParkingLot>
) : RecyclerView.Adapter<ParkingLotAdapter.ParkingLotViewHolder>() {

    inner class ParkingLotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.nameView)
        val locationText: TextView = itemView.findViewById(R.id.addressView)
        val isDynamicPricingEnabled: TextView = itemView.findViewById(R.id.isDynamicPricingEnabled)
        val spotsText: TextView = itemView.findViewById(R.id.totalParking)
        val mainLayout: LinearLayout = itemView.findViewById(R.id.mainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingLotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.parking_item_layout, parent, false)
        return ParkingLotViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ParkingLotViewHolder, position: Int) {
        val lot = parkingLots[position]
        holder.nameText.text = lot.name
        holder.locationText.text = lot.address
        holder.spotsText.text = "Total Slots : ${lot.total_slots} \nAvailable: ${lot.available_count}"
        holder.isDynamicPricingEnabled.text = if (lot.is_dynamic_pricing == 1) {
            "Dynamic Pricing Enabled"
        } else {
            "Dynamic Pricing Disabled"
        }

        holder.mainLayout.setOnClickListener {
            val intent = Intent(holder.mainLayout.context, ParkingDetailsActivity::class.java)
            intent.putExtra("parkingLotId", lot.id)
            holder.mainLayout.context.startActivity(intent)
        }

    }

    override fun getItemCount() = parkingLots.size
}
