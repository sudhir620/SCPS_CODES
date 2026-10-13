package com.example.carparkingsystem

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.carparkingsystem.api.MqttManager
import com.example.carparkingsystem.models.ParkingSlot
import com.example.carparkingsystem.models.Vehicle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil


class BookingBottomSheet(
    private val slot: ParkingSlot, private val vehicleList: List<Vehicle>, private val onClick: (
        startTime: String, endTime: String, hours: Int, selectedVehicle: Vehicle
    ) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var titleText: TextView
    private lateinit var dateText: TextView
    private lateinit var hourText: TextView

    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var proceedButton: Button
    private lateinit var closeBtn: ImageView

    private lateinit var vehicleSpinner: Spinner

    private var selectedVehicle: Vehicle? = null

    private var startTime: Calendar? = null
    private var endTime: Calendar? = null

    private var isConfirmed = false

    private var hours: Int = 0

    override fun onStart() {
        super.onStart()

        MqttManager.publish(
            MqttManager.UPDATE_PARKING_SLOT, "start=${slot.id}=${SharedPrefManager.getDeviceId()}", qos = 1
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.bottomsheet_booking, container, false
        )

        closeBtn = view.findViewById(R.id.close_button)

        titleText = view.findViewById(R.id.titleText)

        dateText = view.findViewById(R.id.dateText)

        vehicleSpinner = view.findViewById(R.id.vehicleSpinner)

        startTimeButton = view.findViewById(R.id.startTimeButton)

        endTimeButton = view.findViewById(R.id.endTimeButton)

        hourText = view.findViewById(R.id.hourText)

        proceedButton = view.findViewById(R.id.proceedButton)

        titleText.text = "Booking for Slot : ${slot.slot_number}"

        val currentDate = Calendar.getInstance()

        val dateFormat = SimpleDateFormat(
            "EEEE, dd MMMM yyyy", Locale.getDefault()
        )

        dateText.text = dateFormat.format(currentDate.time)

        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, vehicleList.map {

                it.extracted_number?.takeIf { number ->
                    number.isNotBlank()
                } ?: it.number
            })

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        vehicleSpinner.adapter = adapter


        vehicleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {

                selectedVehicle = vehicleList[position]
            }


            override fun onNothingSelected(
                parent: AdapterView<*>?
            ) {

                selectedVehicle = null
            }
        }

        startTimeButton.setOnClickListener {

            showTimePicker(
                isStartTime = true
            )
        }

        endTimeButton.setOnClickListener {

            showTimePicker(
                isStartTime = false
            )
        }

        proceedButton.setOnClickListener {
            if (selectedVehicle == null) {

                Toast.makeText(
                    requireContext(), "Please select a vehicle", Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }
            if (startTime == null) {

                Toast.makeText(
                    requireContext(), "Please select start time", Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }
            if (endTime == null) {

                Toast.makeText(
                    requireContext(), "Please select end time", Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }
            val now = Calendar.getInstance()
            if (!startTime!!.after(now)) {
                Toast.makeText(
                    requireContext(), "Start time must be greater than current time", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (!endTime!!.after(startTime)) {

                Toast.makeText(
                    requireContext(), "End time must be greater than start time", Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }
            val durationMillis = endTime!!.timeInMillis - startTime!!.timeInMillis

            val totalMinutes = durationMillis / (1000 * 60)

            if (totalMinutes <= 0) {
                Toast.makeText(
                    requireContext(), "Please select a valid time interval", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            val chargedHours = ceil(
                totalMinutes / 60.0
            ).toInt()

            val timeFormat = SimpleDateFormat(
                "HH:mm", Locale.getDefault()
            )

            val start = timeFormat.format(
                startTime!!.time
            )

            val end = timeFormat.format(
                endTime!!.time
            )
            onClick.invoke(
                start, end, chargedHours, selectedVehicle!!
            )
            isConfirmed = true

            dismiss()
        }

        closeBtn.setOnClickListener {
            isConfirmed = false
            dismiss()
        }

        return view
    }
    private fun showTimePicker(
        isStartTime: Boolean
    ) {

        val calendar = Calendar.getInstance()


        val timePicker = TimePickerDialog(

            requireContext(),

            { _, hourOfDay, minute ->

                val selectedTime = Calendar.getInstance()

                selectedTime.set(
                    Calendar.HOUR_OF_DAY, hourOfDay
                )

                selectedTime.set(
                    Calendar.MINUTE, minute
                )

                selectedTime.set(
                    Calendar.SECOND, 0
                )

                selectedTime.set(
                    Calendar.MILLISECOND, 0
                )

                if (isStartTime) {

                    val now = Calendar.getInstance()
                    if (!selectedTime.after(now)) {

                        Toast.makeText(
                            requireContext(), "Please select a future start time", Toast.LENGTH_SHORT
                        ).show()

                        return@TimePickerDialog
                    }

                    startTime = selectedTime


                    startTimeButton.text = formatTime(
                        selectedTime
                    )

                    if (endTime != null && !endTime!!.after(startTime)) {

                        endTime = null

                        endTimeButton.text = "Select End Time"
                    }
                }

                else {
                    if (startTime == null) {

                        Toast.makeText(
                            requireContext(), "Please select start time first", Toast.LENGTH_SHORT
                        ).show()

                        return@TimePickerDialog
                    }

                    if (!selectedTime.after(startTime)) {

                        Toast.makeText(
                            requireContext(), "End time must be greater than start time", Toast.LENGTH_SHORT
                        ).show()

                        return@TimePickerDialog
                    }

                    endTime = selectedTime


                    endTimeButton.text = formatTime(
                        selectedTime
                    )
                }

                calculateFare()
            },

            calendar.get(
                Calendar.HOUR_OF_DAY
            ),

            calendar.get(
                Calendar.MINUTE
            ),

            false
        )

        timePicker.setOnShowListener {
            timePicker.getButton(
                DialogInterface.BUTTON_POSITIVE
            ).setTextColor(
                resources.getColor(android.R.color.white, null)
            )
            timePicker.getButton(
                DialogInterface.BUTTON_NEGATIVE
            ).setTextColor(
                resources.getColor(android.R.color.white, null)
            )
        }
        timePicker.show()
    }

    private fun formatTime(
        calendar: Calendar
    ): String {

        return SimpleDateFormat(
            "hh:mm a", Locale.getDefault()
        ).format(
            calendar.time
        )
    }

    @SuppressLint("SetTextI18n")
    private fun calculateFare() {
        if (startTime == null || endTime == null) {

            hourText.text = "Select time"

            hours = 0

            return
        }

        val durationMillis = endTime!!.timeInMillis - startTime!!.timeInMillis


        val totalMinutes = durationMillis / (1000 * 60)

        if (totalMinutes <= 0) {

            hourText.text = "Invalid time interval"

            hours = 0

            return
        }

        val chargedHours = ceil(
            totalMinutes / 60.0
        ).toInt()

        val fare = (slot.fare?.toFloat() ?: 0f) * chargedHours

        val formattedFare = String.format(
            Locale.getDefault(), "%.2f", fare
        )

        hours = chargedHours

        hourText.text = "$chargedHours Hour${
            if (chargedHours > 1) "s" else ""
        } • ₹$formattedFare"
    }

    override fun onDismiss(
        dialog: DialogInterface
    ) {
        super.onDismiss(dialog)

        MqttManager.publish(
            MqttManager.UPDATE_PARKING_SLOT, "end=${slot.id}=${SharedPrefManager.getDeviceId()}", qos = 1
        )

        if (!isConfirmed) {
            // User closed the bottom sheet
        }
    }
}