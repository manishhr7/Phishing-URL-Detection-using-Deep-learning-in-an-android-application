package com.example.phishingdetectorapp // Replace with your actual package name

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class ResultActivity : AppCompatActivity() {

    private lateinit var textViewCheckedUrl: TextView
    private lateinit var textViewPredictionResult: TextView
    private lateinit var textViewProbabilityValue: TextView
    private lateinit var buttonOpenUrl: Button
    private lateinit var buttonBack: Button

    private var currentUrl: String? = null

    companion object {
        private const val TAG = "ResultActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result) // Use the result layout file

        textViewCheckedUrl = findViewById(R.id.textViewCheckedUrl)
        textViewPredictionResult = findViewById(R.id.textViewPredictionResult)
        textViewProbabilityValue = findViewById(R.id.textViewProbabilityValue)
        buttonOpenUrl = findViewById(R.id.buttonOpenUrl)
        buttonBack = findViewById(R.id.buttonBack)

        // Retrieve data from Intent
        val url = intent.getStringExtra(MainActivity.EXTRA_URL)
        val label = intent.getStringExtra(MainActivity.EXTRA_LABEL)
        val probability = intent.getFloatExtra(MainActivity.EXTRA_PROBABILITY, -1.0f)

        currentUrl = url // Store URL for the open button

        if (url == null || label == null || probability < 0) {
            // Handle error case where data is missing
            Log.e(TAG, "Error: Missing data in Intent extras.")
            textViewCheckedUrl.text = "Error: Data missing"
            textViewPredictionResult.text = "Error"
            textViewProbabilityValue.text = "N/A"
            buttonOpenUrl.isEnabled = false
            Toast.makeText(this, "Error displaying results.", Toast.LENGTH_LONG).show()
        } else {
            // Display the results
            textViewCheckedUrl.text = url
            textViewPredictionResult.text = label
            textViewProbabilityValue.text = String.format("%.4f", probability)

            // Change text color based on prediction (optional visual cue)
            if (label.equals("phishing", ignoreCase = true)) {
                textViewPredictionResult.setTextColor(Color.RED)
            } else {
                textViewPredictionResult.setTextColor(Color.parseColor("#006400")) // Dark Green
            }

            // Enable/disable Open URL button based on whether it looks like a valid URL
            buttonOpenUrl.isEnabled = android.util.Patterns.WEB_URL.matcher(url).matches()
        }

        // Set up button listeners
        buttonBack.setOnClickListener {
            // Simply finish this activity to go back to the previous one (MainActivity)
            finish()
        }

        buttonOpenUrl.setOnClickListener {
            currentUrl?.let { urlToOpen ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen))
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening URL: $urlToOpen", e)
                    Toast.makeText(this, "Could not open URL. No app found?", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // TODO: Implement writing result to a single file if needed (Step 017)
        // Example: appendResultToFile(url, label, probability)
    }


}

