package com.idcardprinter.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.idcardprinter.core.IdCardEngine
import com.idcardprinter.core.model.CardQuad
import com.idcardprinter.core.model.PrintLayout
import com.idcardprinter.core.model.ProcessingConfig
import com.idcardprinter.ui.contract.IdCardScannerConfig
import com.idcardprinter.ui.contract.IdCardScannerContract
import com.idcardprinter.ui.databinding.ActivityIdCardScanBinding
import com.idcardprinter.ui.databinding.DialogPrintPreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Reusable full-flow Activity for capturing, perspective cropping, and printing ID cards.
 * Can be invoked by any host application via IdCardScannerContract.
 */
class IdCardScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIdCardScanBinding
    private val engine = IdCardEngine()

    private var scannerConfig = IdCardScannerConfig()
    private var currentSide = Side.FRONT

    // Stored Bitmaps & Quads
    private var frontRawBitmap: Bitmap? = null
    private var frontQuad: CardQuad? = null
    private var frontEnhanced: Bitmap? = null

    private var backRawBitmap: Bitmap? = null
    private var backQuad: CardQuad? = null
    private var backEnhanced: Bitmap? = null

    // Temp camera image URI
    private var tempCameraUri: Uri? = null
    private var tempCameraFile: File? = null

    enum class Side {
        FRONT,
        BACK
    }

    // Gallery Picker Contract
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadBitmapFromUri(it) }
    }

    // Camera Capture Contract
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            tempCameraFile?.let { loadBitmapFromFile(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIdCardScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read configuration from Intent
        (intent.getSerializableExtra(IdCardScannerContract.EXTRA_CONFIG) as? IdCardScannerConfig)?.let {
            scannerConfig = it
        }

        setupToolbar()
        setupListeners()
        updateUiForCurrentSide()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "ID Card Print Utility"
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        binding.toolbar.setNavigationOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun setupListeners() {
        binding.btnCaptureCamera.setOnClickListener { launchCamera() }
        binding.btnPickGallery.setOnClickListener { galleryLauncher.launch("image/*") }

        binding.btnRetake.setOnClickListener {
            if (currentSide == Side.FRONT) {
                frontRawBitmap = null
                frontQuad = null
            } else {
                backRawBitmap = null
                backQuad = null
            }
            updateUiForCurrentSide()
        }

        binding.btnRotate.setOnClickListener { rotateCurrentCard() }
        binding.btnAutoDetect.setOnClickListener { autoDetectCurrentCard() }
        binding.btnCleanPreview.setOnClickListener { previewCleanCurrentCard() }

        binding.btnNextOrDone.setOnClickListener {
            onNextOrDoneClicked()
        }
    }

    private fun updateUiForCurrentSide() {
        val currentBitmap = if (currentSide == Side.FRONT) frontRawBitmap else backRawBitmap
        val currentQuad = if (currentSide == Side.FRONT) frontQuad else backQuad

        if (currentSide == Side.FRONT) {
            binding.tvStepIndicator.text = "Step 1 of 2: Front Side (Photo / Details)"
            binding.btnNextOrDone.text = "Next: Back Side →"
        } else {
            binding.tvStepIndicator.text = "Step 2 of 2: Back Side (Address / Barcode)"
            binding.btnNextOrDone.text = "Generate A4 Print Page"
        }

        if (currentBitmap != null) {
            binding.emptyStateLayout.visibility = View.GONE
            binding.cropView.visibility = View.VISIBLE
            binding.btnRetake.visibility = View.VISIBLE
            binding.btnNextOrDone.visibility = View.VISIBLE
            binding.btnRotate.visibility = View.VISIBLE
            binding.btnAutoDetect.visibility = View.VISIBLE
            binding.btnCleanPreview.visibility = View.VISIBLE

            binding.cropView.setImage(currentBitmap, currentQuad)
            binding.cropView.setOnCornersChangedListener { quad ->
                if (currentSide == Side.FRONT) frontQuad = quad else backQuad = quad
            }
        } else {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.cropView.visibility = View.GONE
            binding.btnRetake.visibility = View.GONE
            binding.btnNextOrDone.visibility = View.GONE
            binding.btnRotate.visibility = View.GONE
            binding.btnAutoDetect.visibility = View.GONE
            binding.btnCleanPreview.visibility = View.GONE
        }
    }

    private fun launchCamera() {
        try {
            val file = File(cacheDir, "temp_id_card_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            val authority = "${packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            // Fallback for apps without FileProvider configuration
            Toast.makeText(this, "Opening gallery...", Toast.LENGTH_SHORT).show()
            galleryLauncher.launch("image/*")
        }
    }

    private fun loadBitmapFromUri(uri: Uri) {
        showLoading("Loading image...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    val oriented = fixExifOrientation(uri, bmp)
                    withContext(Dispatchers.Main) {
                        onImageLoaded(oriented)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@IdCardScanActivity, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadBitmapFromFile(file: File) {
        showLoading("Loading image...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                val oriented = fixExifOrientation(file.absolutePath, bmp)
                withContext(Dispatchers.Main) {
                    onImageLoaded(oriented)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@IdCardScanActivity, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onImageLoaded(bitmap: Bitmap) {
        hideLoading()
        val detected = engine.detectCorners(bitmap)
        if (currentSide == Side.FRONT) {
            frontRawBitmap = bitmap
            frontQuad = detected
        } else {
            backRawBitmap = bitmap
            backQuad = detected
        }
        updateUiForCurrentSide()
    }

    private fun rotateCurrentCard() {
        val currentBitmap = (if (currentSide == Side.FRONT) frontRawBitmap else backRawBitmap) ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        val rotated = Bitmap.createBitmap(currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true)
        val newCorners = engine.detectCorners(rotated)

        if (currentSide == Side.FRONT) {
            frontRawBitmap = rotated
            frontQuad = newCorners
        } else {
            backRawBitmap = rotated
            backQuad = newCorners
        }
        updateUiForCurrentSide()
    }

    private fun autoDetectCurrentCard() {
        val currentBitmap = (if (currentSide == Side.FRONT) frontRawBitmap else backRawBitmap) ?: return
        val detected = engine.detectCorners(currentBitmap)
        if (currentSide == Side.FRONT) frontQuad = detected else backQuad = detected
        binding.cropView.setCorners(detected)
    }

    private fun previewCleanCurrentCard() {
        val currentBitmap = (if (currentSide == Side.FRONT) frontRawBitmap else backRawBitmap) ?: return
        val currentQuad = binding.cropView.getCorners() ?: return

        showLoading("Enhancing Card...")
        lifecycleScope.launch(Dispatchers.Default) {
            val config = ProcessingConfig(
                autoWhiteBalance = scannerConfig.autoWhiteBalance,
                autoFlatField = scannerConfig.autoFlatField,
                brightness = scannerConfig.brightness,
                contrast = scannerConfig.contrast
            )
            val cleaned = engine.processCard(currentBitmap, currentQuad, config, currentSide == Side.FRONT)

            withContext(Dispatchers.Main) {
                hideLoading()
                showSingleCardDialog(cleaned)
            }
        }
    }

    private fun showSingleCardDialog(card: Bitmap) {
        val dialog = Dialog(this)
        val iv = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            adjustViewBounds = true
            setImageBitmap(card)
            setPadding(16, 16, 16, 16)
        }
        dialog.setContentView(iv)
        dialog.show()
    }

    private fun onNextOrDoneClicked() {
        // Save corners from crop view
        binding.cropView.getCorners()?.let {
            if (currentSide == Side.FRONT) frontQuad = it else backQuad = it
        }

        if (currentSide == Side.FRONT) {
            // Move to Back side
            currentSide = Side.BACK
            updateUiForCurrentSide()
        } else {
            // Process both sides and open Print Preview
            generateAndShowPreview()
        }
    }

    private fun generateAndShowPreview() {
        val fBitmap = frontRawBitmap ?: return
        val fQuad = frontQuad ?: engine.detectCorners(fBitmap)

        showLoading("Generating A4 Layout (300 DPI)...")
        lifecycleScope.launch(Dispatchers.Default) {
            val config = ProcessingConfig(
                autoWhiteBalance = scannerConfig.autoWhiteBalance,
                autoFlatField = scannerConfig.autoFlatField,
                brightness = scannerConfig.brightness,
                contrast = scannerConfig.contrast
            )

            frontEnhanced = engine.processCard(fBitmap, fQuad, config, isFront = true)

            // If back card was omitted, duplicate front
            val bBitmap = backRawBitmap ?: fBitmap
            val bQuad = backQuad ?: engine.detectCorners(bBitmap)
            backEnhanced = engine.processCard(bBitmap, bQuad, config, isFront = false)

            withContext(Dispatchers.Main) {
                hideLoading()
                openPrintPreviewDialog()
            }
        }
    }

    private fun openPrintPreviewDialog() {
        val fe = frontEnhanced ?: return
        val be = backEnhanced ?: return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val previewBinding = DialogPrintPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(previewBinding.root)

        var selectedLayout = scannerConfig.layout

        fun updatePreviewImage() {
            showLoading("Composing Layout...")
            lifecycleScope.launch(Dispatchers.Default) {
                val a4Bitmap = engine.composeA4(fe, be, selectedLayout)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    previewBinding.ivA4Preview.setImageBitmap(a4Bitmap)
                }
            }
        }

        previewBinding.rgLayout.setOnCheckedChangeListener { _, checkedId ->
            selectedLayout = when (checkedId) {
                R.id.rbWallet -> PrintLayout.WALLET_1TO1
                R.id.rbAllInOne -> PrintLayout.ALL_IN_ONE
                else -> PrintLayout.DOCUMENT_KYC
            }
            updatePreviewImage()
        }

        // Set initial radio state
        when (scannerConfig.layout) {
            PrintLayout.WALLET_1TO1 -> previewBinding.rbWallet.isChecked = true
            PrintLayout.ALL_IN_ONE -> previewBinding.rbAllInOne.isChecked = true
            else -> previewBinding.rbDocument.isChecked = true
        }
        updatePreviewImage()

        previewBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }

        previewBinding.btnDirectPrint.setOnClickListener {
            printDirectly(selectedLayout)
        }

        previewBinding.btnSavePdf.setOnClickListener {
            savePdfAndFinish(selectedLayout)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun printDirectly(layout: PrintLayout) {
        val fe = frontEnhanced ?: return
        val be = backEnhanced ?: return

        showLoading("Preparing Print Document...")
        lifecycleScope.launch(Dispatchers.Default) {
            val pdfFile = File(cacheDir, "id_card_print_${System.currentTimeMillis()}.pdf")
            engine.generateA4Pdf(fe, be, layout, pdfFile)

            withContext(Dispatchers.Main) {
                hideLoading()
                val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
                val printAdapter = PdfPrintDocumentAdapter(pdfFile)
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .build()

                printManager?.print("ID Card Print", printAdapter, printAttributes)
            }
        }
    }

    private fun savePdfAndFinish(layout: PrintLayout) {
        val fe = frontEnhanced ?: return
        val be = backEnhanced ?: return

        showLoading("Exporting PDF...")
        lifecycleScope.launch(Dispatchers.Default) {
            val outputDir = getExternalFilesDir(null) ?: filesDir
            val pdfFile = File(outputDir, "ID_Card_A4_Print_${System.currentTimeMillis()}.pdf")
            engine.generateA4Pdf(fe, be, layout, pdfFile)

            // Save individual enhanced PNGs
            val frontFile = File(outputDir, "id_front_clean_${System.currentTimeMillis()}.png")
            FileOutputStream(frontFile).use { fe.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val backFile = File(outputDir, "id_back_clean_${System.currentTimeMillis()}.png")
            FileOutputStream(backFile).use { be.compress(Bitmap.CompressFormat.PNG, 100, it) }

            withContext(Dispatchers.Main) {
                hideLoading()
                val resultIntent = Intent().apply {
                    putExtra(IdCardScannerContract.EXTRA_PDF_PATH, pdfFile.absolutePath)
                    putExtra(IdCardScannerContract.EXTRA_FRONT_PATH, frontFile.absolutePath)
                    putExtra(IdCardScannerContract.EXTRA_BACK_PATH, backFile.absolutePath)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                Toast.makeText(this@IdCardScanActivity, "PDF Saved: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun fixExifOrientation(pathOrUri: Any, bitmap: Bitmap): Bitmap {
        try {
            val exif = when (pathOrUri) {
                is String -> ExifInterface(pathOrUri)
                is Uri -> contentResolver.openInputStream(pathOrUri)?.use { ExifInterface(it) }
                else -> null
            } ?: return bitmap

            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap

            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            return bitmap
        }
    }

    private fun showLoading(msg: String) {
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.tvLoadingText.text = msg
    }

    private fun hideLoading() {
        binding.loadingOverlay.visibility = View.GONE
    }
}

/**
 * Native Android PrintDocumentAdapter that streams the generated PDF to Android PrintManager.
 */
class PdfPrintDocumentAdapter(private val file: File) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
