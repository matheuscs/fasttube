package com.example.ocrtubeml

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.tensorflow.lite.Interpreter
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.nio.ByteBuffer

import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var cameraImage: ImageView
    private lateinit var captureImgBtn: Button
    private lateinit var resultText: TextView

    private var currentPhotoPath: String? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>

    private lateinit var tflite: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tflite = loadTFLiteModel()

        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_gray_status_bar)

        cameraImage = findViewById(R.id.cameraImage)
        captureImgBtn = findViewById(R.id.captureImgBtn)
        resultText = findViewById(R.id.resultText)
        resultText.movementMethod = ScrollingMovementMethod()

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isGranted ->
            if (isGranted) {
                captureImage()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentPhotoPath?.let { path ->
                    val rawBitmap = BitmapFactory.decodeFile(path)
                    val rotatedBitmap = rotateImageIfRequired(rawBitmap, path)
                    val detections = runObjectDetection(rotatedBitmap)  // Run detection and get filtered boxes
                    runOcrOnDetections(rotatedBitmap, detections)  // Run OCR only inside the detected regions
                }
            }
        }
        captureImgBtn.setOnClickListener{
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun captureImage() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error occurred while creating the file", Toast.LENGTH_SHORT).show()
            null
        }
        photoFile?.also {
            val photoUri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
            takePictureLauncher.launch(photoUri)
        }
    }

    private fun loadTFLiteModel(): Interpreter {
        val assetFileDescriptor = assets.openFd("best_float16.tflite")
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        return Interpreter(modelBuffer)
    }

    private fun runObjectDetection(bitmap: Bitmap): List<Detection> {
        val input = preprocessImage(bitmap)
        val output = Array(1) { Array(5) { FloatArray(13125) } }
        tflite.run(input, output)

        val detections = mutableListOf<Detection>()
        for (i in 0 until 13125) {
            val conf = output[0][4][i]
            if (conf > 0.5f) {
                detections.add(
                    Detection(
                        x = output[0][0][i],
                        y = output[0][1][i],
                        w = output[0][2][i],
                        h = output[0][3][i],
                        score = conf,
                        label = -1
                    )
                )
            }
        }

        val filtered = filterOverlappingDetections(detections)
        val drawnBitmap = drawDetections(bitmap, filtered)
        cameraImage.setImageBitmap(drawnBitmap)

        Toast.makeText(this, "Detected ${filtered.size} items", Toast.LENGTH_SHORT).show()

        return filtered
    }
    private fun filterOverlappingDetections(detections: List<Detection>, iouThreshold: Float = 0.4f): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }
        val kept = mutableListOf<Detection>()

        for (candidate in sorted) {
            var shouldKeep = true
            for (keptDet in kept) {
                val iou = calculateIoU(candidate, keptDet)
                if (iou > iouThreshold) {
                    shouldKeep = false
                    break
                }
            }
            if (shouldKeep) kept.add(candidate)
        }
        return kept
    }
    private fun calculateIoU(a: Detection, b: Detection): Float {
        val ax1 = a.x - a.w / 2
        val ay1 = a.y - a.h / 2
        val ax2 = a.x + a.w / 2
        val ay2 = a.y + a.h / 2

        val bx1 = b.x - b.w / 2
        val by1 = b.y - b.h / 2
        val bx2 = b.x + b.w / 2
        val by2 = b.y + b.h / 2

        val x1 = maxOf(ax1, bx1)
        val y1 = maxOf(ay1, by1)
        val x2 = minOf(ax2, bx2)
        val y2 = minOf(ay2, by2)

        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = (ax2 - ax1) * (ay2 - ay1)
        val areaB = (bx2 - bx1) * (by2 - by1)
        val union = areaA + areaB - intersection

        return if (union > 0f) intersection / union else 0f
    }

    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val inputSize = 800
        val inputImage = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(java.nio.ByteOrder.nativeOrder())
        Log.d("YOLO", "Model expects: ${tflite.getInputTensor(0).shape().contentToString()}")  // Should be [1, 640, 640, 3]
        Log.d("YOLO", "Input buffer size: ${byteBuffer.capacity()}")

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = inputImage.getPixel(x, y)
                byteBuffer.putFloat((pixel shr 16 and 0xFF) / 255.0f)  // Red
                byteBuffer.putFloat((pixel shr 8 and 0xFF) / 255.0f)   // Green
                byteBuffer.putFloat((pixel and 0xFF) / 255.0f)         // Blue
            }
        }
        return byteBuffer
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, photoPath: String): Bitmap {
        val exif = ExifInterface(photoPath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap // no rotation needed
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun drawDetections(original: Bitmap, detections: List<Detection>): Bitmap {
        val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutableBitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }

        for (det in detections) {
            val cx = det.x * original.width
            val cy = det.y * original.height
            val w = det.w * original.width
            val h = det.h * original.height

            val left = cx - w / 2
            val top = cy - h / 2
            val right = cx + w / 2
            val bottom = cy + h / 2

            canvas.drawRect(left, top, right, bottom, paint)
        }

        return mutableBitmap
    }

    private fun runOcrOnDetections(bitmap: Bitmap, detections: List<Detection>) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val results = mutableListOf<String>()

        for ((index, det) in detections.withIndex()) {
            val cx = det.x * bitmap.width
            val cy = det.y * bitmap.height
            val w = det.w * bitmap.width
            val h = det.h * bitmap.height

            val left = (cx - w / 2).toInt().coerceIn(0, bitmap.width)
            val top = (cy - h / 2).toInt().coerceIn(0, bitmap.height)
            val right = (cx + w / 2).toInt().coerceIn(0, bitmap.width)
            val bottom = (cy + h / 2).toInt().coerceIn(0, bitmap.height)

            val fullBox = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            val textZoneTop = (fullBox.height * 0.7).toInt()
            val textZoneHeight = fullBox.height - textZoneTop

            val textBox = Bitmap.createBitmap(fullBox, 0, textZoneTop, fullBox.width, textZoneHeight)

            val image = InputImage.fromBitmap(textBox, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.text.trim().lines().filter { it.isNotBlank() }
                    runSummarization(index, lines)
                }
                .addOnFailureListener {
                    Log.e("OCR", "Failed on box $index: ${it.message}")
                }
        }
    }

    private fun runSummarization(index: Int, lines: List<String>) {
        val titleLines = lines.dropLast(2)
        val channel = lines.getOrNull(lines.size - 2) ?: ""
        val stats = lines.getOrNull(lines.size - 1) ?: ""
        val title = titleLines.joinToString(" ")
        val thumbText = lines.firstOrNull() ?: ""
        resultText.text = ""
        Log.i("SUMMARY", "Got data: $channel: $title")
        if (title.isNotBlank() && channel.isNotBlank()) {
            getSummary(thumb = thumbText, title = title, channel = channel) { summary ->
                if (summary != null) {
                    Log.d("SUMMARY", "Box $index: $summary")
                    runOnUiThread {
                        val currentText = resultText.text.toString()
                        val newText = "${currentText}Box $index:\n$summary\n\n"
                        resultText.text = newText
                    }
                } else {
                    Log.w("SUMMARY", "Box $index failed to summarize")
                }
            }
        } else {
            Log.d("SUMMARY", "Box $index skipped due to missing title or channel")
        }
    }

    fun getSummary(thumb: String, title: String, channel: String, callback: (String?) -> Unit) {
        val url = URL("http://192.168.1.14:5000/get-summary")
        val json = """{"thumb":"$thumb","title":"$title","channel":"$channel","debug":"False"}"""

        Thread {
            try {
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true

                conn.outputStream.use { os ->
                    val input = json.toByteArray()
                    os.write(input, 0, input.size)
                }

                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val summary = JSONObject(response).getString("summary")
                runOnUiThread {
                    callback(summary)
                }
            } catch (e: Exception) {
                Log.e("SUMMARY", "Failed to fetch: ${e.message}")
                runOnUiThread {
                    callback(null)
                }
            }
        }.start()
    }
    data class Detection(val x: Float, val y: Float, val w: Float, val h: Float, val score: Float, val label: Int)
}