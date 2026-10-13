package com.example.carparkingsystem.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.carparkingsystem.R
import com.example.carparkingsystem.models.BookingHistory

class BookingHistoryAdapter(
    private val bookingList: List<BookingHistory>,
    private val onClick: (BookingHistory) -> Unit,
    private val onOpenGate: (BookingHistory) -> Unit
) : RecyclerView.Adapter<BookingHistoryAdapter.ParkingLotViewHolder>() {

    inner class ParkingLotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tv1: TextView = itemView.findViewById(R.id.tv1)
        val tv2: TextView = itemView.findViewById(R.id.tv2)
        val tv3: TextView = itemView.findViewById(R.id.tv3)
        val tv4: TextView = itemView.findViewById(R.id.tv4)
        val tv5: TextView = itemView.findViewById(R.id.tv5)
        val tv6: TextView = itemView.findViewById(R.id.tv6)
        val tv7: TextView = itemView.findViewById(R.id.tv7)
        val tv8: TextView = itemView.findViewById(R.id.tv8)
        val openMap: ImageView = itemView.findViewById(R.id.openMap)
        val open_gate:Button = itemView.findViewById(R.id.open_gate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingLotViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.booking_item, parent, false)
        return ParkingLotViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ParkingLotViewHolder, position: Int) {
        val booking = bookingList[position]
        holder.tv1.text = "Vehicle Number: "+booking.vehicle_number
        holder.tv2.text = "Parking Name:" +booking.name
        holder.tv3.text = "Parking Address: "+booking.address
        holder.tv4.text = "Slot No.: "+booking.slot_number
        holder.tv5.text = "Total Amount: "+booking.amount_paid
        holder.tv6.text = "Start Time: "+booking.start_time
        holder.tv7.text = "End Time: "+booking.end_time
        holder.tv8.text = when (booking.status) {
            1 -> "Pending"
            3 -> "Completed"
            else -> "Paid"
        }.plus(if(booking.time_over==1 && booking.status==2) "(Time Over, Please Pay Extra)" else "")

        holder.openMap.setOnClickListener {
            onClick.invoke(booking)
        }
        holder.open_gate.setOnClickListener {
            onOpenGate.invoke(booking)
        }
        holder.openMap.visibility = if(booking.status==2) View.VISIBLE else View.GONE
        holder.open_gate.visibility = if(booking.status==2) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = bookingList.size
}
