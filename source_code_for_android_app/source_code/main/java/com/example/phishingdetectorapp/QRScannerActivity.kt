package com.example.phishingdetectorapp // Replace with your actual package name

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var backButton: Button
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var camera: Camera? = null
    private var isProcessing = false // Flag to prevent multiple results

    companion object {
        private const val TAG = "QRScannerActivity"
        const val EXTRA_SCANNED_URL = "com.example.phishingdetectorapp.SCANNED_URL"
    }

    // --- ActivityResultLaunchers ---
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.i(TAG, "Camera permission granted")
            startCamera()
        } else {
            Log.w(TAG, "Camera permission denied")
            Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            finish() // Close activity if permission denied
        }
    }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner) // Use the new layout

        previewView = findViewById(R.id.previewView)
        backButton = findViewById(R.id.btnBackScanner)
        backButton.setOnClickListener { finish() } // Simple back button

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check for camera permission
        checkCameraPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Shut down camera executor and release resources
        cameraExecutor.shutdown()
        barcodeScanner?.close()
        cameraProvider?.unbindAll()
    }

    // --- Camera and ML Kit Setup ---
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        isProcessing = false // Reset processing flag
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera provider", e)
                Toast.makeText(this, "Error initializing camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val currentCameraProvider = cameraProvider ?: run {
            Log.e(TAG, "Camera provider not available")
            return
        }

        currentCameraProvider.unbindAll() // Unbind previous use cases

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Preview Use Case
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        // Image Analysis Use Case
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        // Setup ML Kit Barcode Scanner
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        // Set Analyzer
        imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            // Bind use cases to camera
            camera = currentCameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalysis
            )
            Log.i(TAG, "Camera use cases bound successfully")
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(this, "Failed to start camera preview", Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close() // Close if already processing a result
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barcodeScanner?.process(image)
                ?.addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty() && !isProcessing) {
                        isProcessing = true // Set flag to prevent multiple triggers
                        val barcode = barcodes[0] // Get the first detected barcode
                        val rawValue = barcode.rawValue
                        Log.i(TAG, "QR Code detected: $rawValue")
                        if (rawValue != null) {
                            returnResultAndFinish(rawValue)
                        } else {
                            Log.w(TAG, "Detected QR code has null value")
                            isProcessing = false // Reset if value is null
                        }
                    }
                    // No barcode found in this frame, continue scanning
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                    // Don't stop scanning on failure, just log
                }
                ?.addOnCompleteListener {
                    // Always close the imageProxy to allow the next frame to be processed
                    imageProxy.close()
                }
        } else {
            imageProxy.close() // Close if mediaImage is null
        }
    }

    // --- Result Handling ---
    private fun returnResultAndFinish(scannedUrl: String) {
        Log.d(TAG, "Returning result: $scannedUrl")
        val resultIntent = Intent().apply {
            putExtra(EXTRA_SCANNED_URL, scannedUrl)
        }
        setResult(RESULT_OK, resultIntent)
        finish() // Close the scanner activity
    }
}

