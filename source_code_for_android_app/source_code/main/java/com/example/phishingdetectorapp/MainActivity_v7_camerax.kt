package com.example.phishingdetectorapp // Replace with your actual package name

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var buttonGoToManualUrl: Button
    private lateinit var buttonGoToScanQr: Button
    private lateinit var buttonGoToUploadQr: Button
    private lateinit var textViewInitStatus: TextView

    private var phishingDetector: PhishingDetector? = null
    private lateinit var executor: ExecutorService

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_URL = "com.example.phishingdetectorapp.URL"
        const val EXTRA_LABEL = "com.example.phishingdetectorapp.LABEL"
        const val EXTRA_PROBABILITY = "com.example.phishingdetectorapp.PROBABILITY"
    }

    // --- ActivityResultLaunchers ---

    // Launcher for Camera Permission (used before launching QRScannerActivity)
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) launchQrScannerActivity()
        else Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
    }

    // Launcher for Storage Permission (used before launching image picker)
    private val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) launchImagePicker()
        else Toast.makeText(this, "Storage permission is required to upload QR images", Toast.LENGTH_LONG).show()
    }

    // *** Launcher for QRScannerActivity ***
    private val qrScannerActivityLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // Get the scanned URL from the result Intent
                val scannedUrl = result.data?.getStringExtra(QRScannerActivity.EXTRA_SCANNED_URL)
                if (!scannedUrl.isNullOrEmpty()) {
                    Log.i(TAG, "Received URL from QRScannerActivity: $scannedUrl")
                    handleScannedUrl(scannedUrl = scannedUrl)
                } else {
                    Log.w(TAG, "QRScannerActivity returned OK but URL was null or empty")
                    Toast.makeText(this, "QR Scan failed: No data received", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "QRScannerActivity cancelled or failed with resultCode: ${result.resultCode}")
                // No toast needed if user just backed out
            }
        }

    // Launcher for Image Picker Intent (remains the same)
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val imageUri: Uri = result.data!!.data!!
            Log.i(TAG, "Image selected: $imageUri")
            decodeQrFromImage(imageUri)
        } else {
            Log.w(TAG, "Image selection cancelled or failed")
        }
    }

    // --- Lifecycle Methods ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_buttons_only)

        buttonGoToManualUrl = findViewById(R.id.buttonGoToManualUrl)
        buttonGoToScanQr = findViewById(R.id.buttonGoToScanQr)
        buttonGoToUploadQr = findViewById(R.id.buttonGoToUploadQr)
        textViewInitStatus = findViewById(R.id.textViewInitStatus)

        executor = Executors.newSingleThreadExecutor()
        initializeDetector()

        buttonGoToManualUrl.setOnClickListener {
            val intent = Intent(this, ManualUrlActivity::class.java)
            startActivity(intent)
        }

        // *** Updated Scan Button Listener ***
        buttonGoToScanQr.setOnClickListener { checkCameraPermissionAndLaunchScanner() }

        buttonGoToUploadQr.setOnClickListener { checkStoragePermissionAndPickImage() }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        phishingDetector?.close()
    }

    // --- Feature Logic ---

    private fun initializeDetector() {
        textViewInitStatus.visibility = View.GONE
        executor.execute {
            try {
                if (phishingDetector == null) {
                    phishingDetector = PhishingDetector(applicationContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Detector Initialization failed", e)
                runOnUiThread {
                    textViewInitStatus.text = "Error: Detector failed to initialize."
                    textViewInitStatus.visibility = View.VISIBLE
                    buttonGoToManualUrl.isEnabled = false
                    buttonGoToScanQr.isEnabled = false
                    buttonGoToUploadQr.isEnabled = false
                }
            }
        }
    }

    // --- QR Code Scanning (Camera) ---
    // *** Renamed function to clarify purpose ***
    private fun checkCameraPermissionAndLaunchScanner() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                launchQrScannerActivity()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Needed")
                    .setMessage("Camera access is required to scan QR codes directly.")
                    .setPositiveButton("Request Permission") { _, _ ->
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // *** Launches our custom QRScannerActivity ***
    private fun launchQrScannerActivity() {
        val intent = Intent(this, QRScannerActivity::class.java)
        try {
            qrScannerActivityLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch QRScannerActivity", e)
            Toast.makeText(this, "Could not launch QR Scanner activity.", Toast.LENGTH_LONG).show()
        }
    }

    // --- QR Code Decoding (Image Upload) ---
    // (This section remains largely the same as before)
    private fun checkStoragePermissionAndPickImage() {
        when {
            ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED -> {
                launchImagePicker()
            }
            shouldShowRequestPermissionRationale(storagePermission) -> {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Needed")
                    .setMessage("Storage access is required to select a QR code image.")
                    .setPositiveButton("Request Permission") { _, _ ->
                        requestStoragePermissionLauncher.launch(storagePermission)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                requestStoragePermissionLauncher.launch(storagePermission)
            }
        }
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        try {
            imagePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Image picker intent failed", e)
            Toast.makeText(this, "Could not launch image picker.", Toast.LENGTH_LONG).show()
        }
    }

    private fun decodeQrFromImage(imageUri: Uri) {
        setUiLoading(true, "Decoding QR...")
        try {
            val inputImage = InputImage.fromFilePath(this, imageUri)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val decodedUrl = barcodes[0].rawValue
                        Log.i(TAG, "ML Kit QR Decode successful: $decodedUrl")
                        if (decodedUrl != null) {
                            handleScannedUrl(decodedUrl)
                        } else {
                            Log.w(TAG, "ML Kit decoded null URL from QR")
                            showQrErrorPopup("QR code content is empty.")
                            setUiLoading(false, "")
                        }
                    } else {
                        Log.w(TAG, "ML Kit: No QR codes found in image.")
                        showQrErrorPopup("No valid QR code found in the selected image.")
                        setUiLoading(false, "")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit QR decoding failed", e)
                    showQrErrorPopup("Failed to process image for QR code.")
                    setUiLoading(false, "")
                }
        } catch (e: IOException) {
            Log.e(TAG, "ML Kit: Failed to create InputImage from URI", e)
            showQrErrorPopup("Failed to read image file.")
            setUiLoading(false, "")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during QR decoding setup", e)
            showQrErrorPopup("An unexpected error occurred during QR decoding.")
            setUiLoading(false, "")
        }
    }

    private fun showQrErrorPopup(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("QR Code Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // --- Common URL Handling & Prediction Trigger ---
    // (This section remains largely the same, handling URLs from both sources)
    private fun handleScannedUrl(scannedUrl: String) {
        if (!scannedUrl.startsWith("http://", ignoreCase = true) && !scannedUrl.startsWith("https://", ignoreCase = true)) {
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Missing Scheme in QR Code")
                    .setMessage("The scanned URL is missing \"http://\" or \"https://\". Add \"https://\" and check?")
                    .setPositiveButton("Add HTTPS and Check") { _, _ ->
                        val correctedUrl = "https://$scannedUrl"
                        if (isValidUrl(correctedUrl)) {
                            performPrediction(correctedUrl)
                        } else {
                            Toast.makeText(this, "QR content still invalid after adding https://", Toast.LENGTH_LONG).show()
                            setUiLoading(false, "")
                        }
                    }
                    .setNegativeButton("Cancel") { _, _ -> setUiLoading(false, "") }
                    .show()
            }
        } else if (isValidUrl(scannedUrl)) {
            performPrediction(scannedUrl)
        } else {
            showQrErrorPopup("QR code content is not a valid URL format: $scannedUrl")
            setUiLoading(false, "")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return android.util.Patterns.WEB_URL.matcher(url).matches()
    }

    private fun performPrediction(url: String) {
        if (phishingDetector == null) {
            Toast.makeText(this, "Detector not ready.", Toast.LENGTH_SHORT).show()
            initializeDetector()
            setUiLoading(false, "")
            return
        }
        setUiLoading(true, "Checking URL...")
        executor.execute {
            try {
                val (predictedLabel, probability) = phishingDetector!!.predict(url)
                Log.i(TAG, "Prediction for $url: $predictedLabel ($probability)")
                runOnUiThread {
                    navigateToResultScreen(url, predictedLabel, probability)
                    setUiLoading(false, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Prediction failed for $url", e)
                runOnUiThread {
                    setUiLoading(false, "Error during prediction")
                    Toast.makeText(this@MainActivity, "Prediction failed. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun navigateToResultScreen(url: String, label: String, probability: Float) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_PROBABILITY, probability)
        }
        startActivity(intent)
    }

    private fun setUiLoading(isLoading: Boolean, statusText: String) {
        buttonGoToManualUrl.isEnabled = !isLoading
        buttonGoToScanQr.isEnabled = !isLoading
        buttonGoToUploadQr.isEnabled = !isLoading
        if(isLoading) {
            Toast.makeText(this, statusText, Toast.LENGTH_SHORT).show()
        }
    }
}

// --- PhishingDetector Class (Should be the same as previous version) ---
// (Ensure this class is included in your project)
class PhishingDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var charIndex: Map<Char, Int> = mapOf()
    private var maxLen: Int = 0
    private val modelFileName = "phishing_model_with_tf_ops.tflite"
    private val tokenizerFileName = "tokenizer_config.json"

    init {
        loadTokenizerConfig()
        val options = Interpreter.Options()
        interpreter = Interpreter(loadModelFile(), options)
        println("PhishingDetector: Model and tokenizer loaded successfully.")
    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    @Throws(IOException::class, org.json.JSONException::class)
    private fun loadTokenizerConfig() {
        context.assets.open(tokenizerFileName).bufferedReader().use { reader ->
            val jsonString = reader.readText()
            val jsonObject = JSONObject(jsonString)
            maxLen = jsonObject.getInt("max_len")
            val charIndexObject = jsonObject.getJSONObject("char_index")
            val tempMap = mutableMapOf<Char, Int>()
            charIndexObject.keys().forEach {
                if (it.length == 1) {
                    tempMap[it[0]] = charIndexObject.getInt(it)
                } else {
                    println("Warning: Unexpected key format in char_index: $it")
                }
            }
            charIndex = tempMap
            println("PhishingDetector: Tokenizer config loaded: maxLen=$maxLen, vocabSize=${charIndex.size}")
        }
    }

    private fun preprocessUrl(url: String): ByteBuffer {
        val processedUrl = url.lowercase()
        val sequence = processedUrl.map { charIndex.getOrDefault(it, 0) }.toMutableList()
        while (sequence.size < maxLen) sequence.add(0)
        val finalSequence = if (sequence.size > maxLen) sequence.subList(0, maxLen) else sequence
        val inputBuffer = ByteBuffer.allocateDirect(maxLen * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        finalSequence.forEach { index ->
            inputBuffer.putFloat(index.toFloat())
        }
        inputBuffer.rewind()
        return inputBuffer
    }

    fun predict(url: String): Pair<String, Float> {
        val currentInterpreter = interpreter ?: run {
            throw IllegalStateException("Interpreter not initialized")
        }
        if (charIndex.isEmpty()) {
            throw IllegalStateException("Tokenizer not initialized")
        }
        val inputBuffer = preprocessUrl(url)
        val outputBuffer = ByteBuffer.allocateDirect(1 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())
        currentInterpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        val probability = outputBuffer.float
        val threshold = 0.5f
        val label = if (probability >= threshold) "phishing" else "legitimate"
        return Pair(label, probability)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        println("PhishingDetector: Interpreter closed.")
    }
}

