package com.example.phishingdetectorapp // Replace with your actual package name

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ManualUrlActivity : AppCompatActivity() {

    private lateinit var editTextManualUrlInput: EditText
    private lateinit var buttonManualCheck: Button
    private lateinit var textViewManualStatus: TextView

    private var phishingDetector: PhishingDetector? = null
    private lateinit var executor: ExecutorService

    companion object {
        private const val TAG = "ManualUrlActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_url)

        editTextManualUrlInput = findViewById(R.id.editTextManualUrlInput)
        buttonManualCheck = findViewById(R.id.buttonManualCheck)
        textViewManualStatus = findViewById(R.id.textViewManualStatus)

        executor = Executors.newSingleThreadExecutor()

        // Initialize detector (could also pass instance or use Singleton/DI)
        initializeDetector()

        buttonManualCheck.setOnClickListener {
            checkAndProcessManualUrl()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        // Avoid closing detector here if it's shared/singleton
        // phishingDetector?.close()
    }

    private fun initializeDetector() {
        setUiLoading(true, "Initializing detector...")
        executor.execute {
            try {
                // Assuming PhishingDetector is accessible or re-initialized here
                // For simplicity, re-initializing. Consider Singleton or DI for efficiency.
                phishingDetector = PhishingDetector(applicationContext)
                runOnUiThread { setUiLoading(false, "") }
            } catch (e: Exception) {
                Log.e(TAG, "Initialization failed", e)
                runOnUiThread {
                    setUiLoading(false, "Error initializing detector")
                    Toast.makeText(this, "Detector initialization failed.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkAndProcessManualUrl() {
        val url = editTextManualUrlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            return
        }

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            AlertDialog.Builder(this)
                .setTitle("Missing Scheme")
                .setMessage("The URL is missing \"http://\" or \"https://\". Add \"https://\" and proceed?")
                .setPositiveButton("Add HTTPS and Check") { _, _ ->
                    val correctedUrl = "https://$url"
                    editTextManualUrlInput.setText(correctedUrl)
                    if (isValidUrl(correctedUrl)) {
                        performPrediction(correctedUrl)
                    } else {
                        Toast.makeText(this, "URL still invalid after adding https://", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else if (isValidUrl(url)) {
            performPrediction(url)
        } else {
            Toast.makeText(this, "Please enter a valid URL format", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return android.util.Patterns.WEB_URL.matcher(url).matches()
    }

    private fun performPrediction(url: String) {
        if (phishingDetector == null) {
            Toast.makeText(this, "Detector not ready.", Toast.LENGTH_SHORT).show()
            initializeDetector() // Try re-initializing
            return
        }
        setUiLoading(true, "Checking URL...")
        executor.execute {
            try {
                val (predictedLabel, probability) = phishingDetector!!.predict(url)
                Log.i(TAG, "Prediction for $url: $predictedLabel ($probability)")
                runOnUiThread {
                    navigateToResultScreen(url, predictedLabel, probability)
                    // Optionally finish this activity after navigating
                    // finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed for $url", e)
                runOnUiThread {
                    setUiLoading(false, "Error during prediction")
                    Toast.makeText(this@ManualUrlActivity, "Prediction failed. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToResultScreen(url: String, label: String, probability: Float) {
        // Reset UI before navigating
        setUiLoading(false, "")

        val intent = Intent(this, ResultActivity::class.java).apply {
            // Use constants defined in MainActivity or a shared constants file
            putExtra(MainActivity.EXTRA_URL, url)
            putExtra(MainActivity.EXTRA_LABEL, label)
            putExtra(MainActivity.EXTRA_PROBABILITY, probability)
        }
        startActivity(intent)
    }

    private fun setUiLoading(isLoading: Boolean, statusText: String) {
        editTextManualUrlInput.isEnabled = !isLoading
        buttonManualCheck.isEnabled = !isLoading
        if (statusText.isNotEmpty()) {
            textViewManualStatus.text = statusText
            textViewManualStatus.visibility = View.VISIBLE
        } else {
            textViewManualStatus.visibility = View.GONE
        }
    }
}

