package com.idcardprinter.ui.crop

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.idcardprinter.core.IdCardEngine
import com.idcardprinter.core.model.CardQuad
import com.idcardprinter.core.model.ProcessingConfig
import com.idcardprinter.ui.databinding.ActivityCardCropBinding
import com.idcardprinter.ui.databinding.DialogCleanCardPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

class CardCropActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_INITIAL_QUAD = "extra_initial_quad"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IS_FRONT = "extra_is_front"

        const val EXTRA_RESULT_QUAD = "extra_result_quad"
        const val EXTRA_RESULT_IMAGE_PATH = "extra_result_image_path"
    }

    private lateinit var binding: ActivityCardCropBinding
    private val engine = IdCardEngine()

    private var currentBitmap: Bitmap? = null
    private var currentImagePath: String? = null
    private var isFront: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Crop ID Card"
        isFront = intent.getBooleanExtra(EXTRA_IS_FRONT, true)
        binding.tvToolbarTitle.text = title

        setupListeners()
        loadImage()
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.btnDone.setOnClickListener {
            onDoneClicked()
        }

        binding.btnRotate.setOnClickListener {
            rotateCard()
        }

        binding.btnAutoDetect.setOnClickListener {
            autoDetectCard()
        }

        binding.btnCleanPreview.setOnClickListener {
            previewCleanCard()
        }

        binding.btnResetCrop.setOnClickListener {
            resetCrop()
        }
    }

    private fun loadImage() {
        val path = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
        val initialQuad = intent.getSerializableExtra(EXTRA_INITIAL_QUAD) as? CardQuad

        showLoading("Loading image...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var bmp: Bitmap? = null
                var finalPath: String? = path

                if (path != null && File(path).exists()) {
                    bmp = decodeSampledBitmap(path, 2500, 2500)
                    bmp = fixOrientation(path, bmp)
                } else if (uriStr != null) {
                    val uri = Uri.parse(uriStr)
                    bmp = decodeSampledBitmapFromUri(uri, 2500, 2500)
                    bmp = fixOrientationFromUri(uri, bmp)

                    // Save to local cache so we have a persistent path
                    val cacheFile = File(cacheDir, "cached_card_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(cacheFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    finalPath = cacheFile.absolutePath
                }

                if (bmp == null) {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@CardCropActivity, "Unable to load image", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                val detectedQuad = initialQuad ?: engine.detectCorners(bmp)

                withContext(Dispatchers.Main) {
                    hideLoading()
                    currentBitmap = bmp
                    currentImagePath = finalPath
                    binding.cropView.setImage(bmp, detectedQuad)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@CardCropActivity, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun rotateCard() {
        val bmp = currentBitmap ?: return
        showLoading("Rotating...")
        lifecycleScope.launch(Dispatchers.Default) {
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

            // Save rotated image
            val file = File(cacheDir, "card_rotated_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            val currentQuad = binding.cropView.getCorners()
            val newCorners = currentQuad?.rotated90(bmp.width, bmp.height) ?: engine.detectCorners(rotated)

            withContext(Dispatchers.Main) {
                hideLoading()
                currentBitmap = rotated
                currentImagePath = file.absolutePath
                binding.cropView.setImage(rotated, newCorners)
            }
        }
    }

    private fun autoDetectCard() {
        val bmp = currentBitmap ?: return
        showLoading("Detecting card boundary...")
        lifecycleScope.launch(Dispatchers.Default) {
            val detected = engine.detectCorners(bmp)
            withContext(Dispatchers.Main) {
                hideLoading()
                binding.cropView.setCorners(detected)
                Toast.makeText(this@CardCropActivity, "Corners auto-aligned!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun previewCleanCard() {
        val bmp = currentBitmap ?: return
        val quad = binding.cropView.getCorners() ?: return

        showLoading("Enhancing Card...")
        lifecycleScope.launch(Dispatchers.Default) {
            val config = ProcessingConfig(
                autoWhiteBalance = true,
                autoFlatField = true
            )
            val cleaned = engine.processCard(bmp, quad, config, isFront)

            withContext(Dispatchers.Main) {
                hideLoading()
                showCleanDialog(cleaned)
            }
        }
    }

    private fun showCleanDialog(card: Bitmap) {
        val dialog = Dialog(this)
        val dialogBinding = DialogCleanCardPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.tvPreviewTitle.text = if (isFront) "Front Side Preview" else "Back Side Preview"
        dialogBinding.ivCleanCard.setImageBitmap(card)
        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun resetCrop() {
        val bmp = currentBitmap ?: return
        val defaultQuad = CardQuad.defaultFor(bmp.width, bmp.height)
        binding.cropView.setCorners(defaultQuad)
    }

    private fun onDoneClicked() {
        val quad = binding.cropView.getCorners()
        if (quad == null || !quad.isConvex()) {
            Toast.makeText(this, "Please select 4 valid corner points", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent().apply {
            putExtra(EXTRA_RESULT_QUAD, quad)
            putExtra(EXTRA_RESULT_IMAGE_PATH, currentImagePath)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while (halfH / inSampleSize >= reqHeight && halfW / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap {
        contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfH = options.outHeight / 2
                val halfW = options.outWidth / 2
                while (halfH / inSampleSize >= reqHeight && halfW / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use { s2 ->
                return BitmapFactory.decodeStream(s2, null, decodeOptions)
                    ?: throw RuntimeException("Failed to decode image from uri")
            }
        }
        throw RuntimeException("Could not open uri stream")
    }

    private fun fixOrientation(path: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun fixOrientationFromUri(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun showLoading(msg: String) {
        binding.tvLoadingText.text = msg
        binding.loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }
}
