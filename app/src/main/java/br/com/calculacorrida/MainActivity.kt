package br.com.calculacorrida

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var txtKmValue: TextView
    private lateinit var txtHourValue: TextView
    private lateinit var txtRatingValue: TextView
    private lateinit var txtMinTripValue: TextView
    private lateinit var txtMinProfitValue: TextView

    private lateinit var seekKm: SeekBar
    private lateinit var seekHour: SeekBar
    private lateinit var seekRating: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtKmValue = findViewById(R.id.txtKmValue)
        txtHourValue = findViewById(R.id.txtHourValue)
        txtRatingValue = findViewById(R.id.txtRatingValue)
        txtMinTripValue = findViewById(R.id.txtMinTripValue)
        txtMinProfitValue = findViewById(R.id.txtMinProfitValue)

        seekKm = findViewById(R.id.seekKm)
        seekHour = findViewById(R.id.seekHour)
        seekRating = findViewById(R.id.seekRating)

        updateKmText(seekKm.progress)
        updateHourText(seekHour.progress)
        updateRatingText(seekRating.progress)

        seekKm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateKmText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekHour.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateHourText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekRating.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateRatingText(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateKmText(progress: Int) {
        val value = progress / 100.0
        txtKmValue.text = "R$ %.2f".format(value).replace(".", ",")
    }

    private fun updateHourText(progress: Int) {
        txtHourValue.text = "R$ $progress,00"
    }

    private fun updateRatingText(progress: Int) {
        val value = progress / 10.0
        txtRatingValue.text = "%.1f".format(value).replace(".", ",")
    }
}
