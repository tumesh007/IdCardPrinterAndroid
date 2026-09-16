package com.idcardprinter.app

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.idcardprinter.app.databinding.ActivityMainBinding
import com.idcardprinter.app.databinding.DialogFullPagePreviewBinding
import com.idcardprinter.core.IdCardEngine
import com.idcardprinter.core.layout.A4LayoutComposer
import com.idcardprinter.core.layout.PdfGenerator
import com.idcardprinter.core.model.CardQuad
import com.idcardprinter.core.model.CardSide
import com.idcardprinter.core.model.ProcessingConfig
import com.idcardprinter.ui.crop.CropInput
import com.idcardprinter.ui.crop.IdCardCropContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val engine = IdCardEngine()

    // Card State
    private var frontRawPath: String? = null
    private var frontQuad: CardQuad? = null
    private var frontEnhanced: Bitmap? = null

    private var backRawPath: String? = null
    private var backQuad: CardQuad? = null
    private var backEnhanced: Bitmap? = null

    // Pending Action Tracking
    private var targetSide: CardSide = CardSide.FRONT
    private var tempCameraFile: File? = null

    // Generated Deliverables
    private var latestPdfFile: File? = null
    private var latestPngFile: File? = null
    private var latestA4Bitmap: Bitmap? = null

    // Launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraInternal()
        } else {
            Toast.makeText(this, "Camera permission is required to capture photos", Toast.LENGTH_LONG).show()
        }
    }

    private val cameraCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            openCropEditor(
                imagePath = tempCameraFile!!.absolutePath,
                initialQuad = null,
                isFront = targetSide == CardSide.FRONT
            )
        }
    }

    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            openCropEditor(
                imageUri = it,
                initialQuad = null,
                isFront = targetSide == CardSide.FRONT
            )
        }
    }

    private val cropLauncher = registerForActivityResult(
        IdCardCropContract()
    ) { result ->
        if (result.isSuccess && result.quad != null && result.imagePath != null) {
            if (targetSide == CardSide.FRONT) {
                frontRawPath = result.imagePath
                frontQuad = result.quad
                updateFrontCardUi()
            } else {
                backRawPath = result.imagePath
                backQuad = result.quad
                updateBackCardUi()
            }
            // Invalidate previously cached A4 exports
            latestA4Bitmap = null
            latestPdfFile = null
            latestPngFile = null
            binding.cardOutputTracker.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateFrontCardUi()
        updateBackCardUi()
    }

    private fun setupListeners() {
        // Front slot listeners
        binding.btnFrontCamera.setOnClickListener {
            targetSide = CardSide.FRONT
            checkCameraPermissionAndLaunch()
        }
        binding.btnFrontGallery.setOnClickListener {
            targetSide = CardSide.FRONT
            galleryPickerLauncher.launch("image/*")
        }
        binding.btnFrontEditCrop.setOnClickListener {
            frontRawPath?.let { path ->
                targetSide = CardSide.FRONT
                openCropEditor(imagePath = path, initialQuad = frontQuad, isFront = true)
            }
        }
        binding.btnFrontRotate.setOnClickListener {
            rotateCardInSlot(CardSide.FRONT)
        }
        binding.btnFrontCleanView.setOnClickListener {
            previewCleanCard(CardSide.FRONT)
        }
        binding.btnFrontRemove.setOnClickListener {
            frontRawPath = null
            frontQuad = null
            frontEnhanced = null
            updateFrontCardUi()
        }

        // Back slot listeners
        binding.btnBackCamera.setOnClickListener {
            targetSide = CardSide.BACK
            checkCameraPermissionAndLaunch()
        }
        binding.btnBackGallery.setOnClickListener {
            targetSide = CardSide.BACK
            galleryPickerLauncher.launch("image/*")
        }
        binding.btnBackEditCrop.setOnClickListener {
            backRawPath?.let { path ->
                targetSide = CardSide.BACK
                openCropEditor(imagePath = path, initialQuad = backQuad, isFront = false)
            }
        }
        binding.btnBackRotate.setOnClickListener {
            rotateCardInSlot(CardSide.BACK)
        }
        binding.btnBackCleanView.setOnClickListener {
            previewCleanCard(CardSide.BACK)
        }
        binding.btnBackRemove.setOnClickListener {
            backRawPath = null
            backQuad = null
            backEnhanced = null
            updateBackCardUi()
        }

        // Main action buttons
        binding.btnPrintDirect.setOnClickListener {
            composeAndExecute(ActionType.PRINT)
        }
        binding.btnSavePdf.setOnClickListener {
            composeAndExecute(ActionType.SHARE_PDF)
        }
        binding.btnSavePng.setOnClickListener {
            composeAndExecute(ActionType.SHARE_PNG)
        }
        binding.btnPreviewPage.setOnClickListener {
            composeAndExecute(ActionType.PREVIEW_PAGE)
        }

        // Output Tracker open buttons
        binding.btnOpenPdf.setOnClickListener {
            latestPdfFile?.let { shareFile(it, "application/pdf") }
        }
        binding.btnOpenPng.setOnClickListener {
            latestPngFile?.let { shareFile(it, "image/png") }
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraInternal()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraInternal() {
        try {
            val photoFile = File(cacheDir, "id_card_cam_${System.currentTimeMillis()}.jpg")
            photoFile.parentFile?.mkdirs()
            photoFile.createNewFile()
            tempCameraFile = photoFile

            val authority = "${applicationContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, photoFile)
            cameraCaptureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initialize camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCropEditor(
        imagePath: String? = null,
        imageUri: Uri? = null,
        initialQuad: CardQuad? = null,
        isFront: Boolean = true
    ) {
        val title = if (isFront) "Crop Front Side" else "Crop Back Side"
        cropLauncher.launch(
            CropInput(
                imagePath = imagePath,
                imageUri = imageUri,
                initialQuad = initialQuad,
                title = title,
                isFront = isFront
            )
        )
    }

    private fun updateFrontCardUi() {
        val path = frontRawPath
        val quad = frontQuad
        if (path != null && quad != null) {
            binding.layoutFrontEmpty.visibility = View.GONE
            binding.layoutFrontLoaded.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                val bmp = decodeSampledBitmap(path, 400, 300)
                withContext(Dispatchers.Main) {
                    binding.ivFrontThumbnail.setImageBitmap(bmp)
                }
            }
        } else {
            binding.layoutFrontEmpty.visibility = View.VISIBLE
            binding.layoutFrontLoaded.visibility = View.GONE
        }
    }

    private fun updateBackCardUi() {
        val path = backRawPath
        val quad = backQuad
        if (path != null && quad != null) {
            binding.layoutBackEmpty.visibility = View.GONE
            binding.layoutBackLoaded.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                val bmp = decodeSampledBitmap(path, 400, 300)
                withContext(Dispatchers.Main) {
                    binding.ivBackThumbnail.setImageBitmap(bmp)
                }
            }
        } else {
            binding.layoutBackEmpty.visibility = View.VISIBLE
            binding.layoutBackLoaded.visibility = View.GONE
        }
    }

    private fun rotateCardInSlot(side: CardSide) {
        val path = if (side == CardSide.FRONT) frontRawPath else backRawPath ?: return
        showLoading("Rotating card...")
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(path) ?: return@launch
            val matrix = Matrix().apply { postRotate(90f) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)

            val file = File(cacheDir, "card_rot_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            val newQuad = engine.detectCorners(rotated)

            withContext(Dispatchers.Main) {
                hideLoading()
                if (side == CardSide.FRONT) {
                    frontRawPath = file.absolutePath
                    frontQuad = newQuad
                    updateFrontCardUi()
                } else {
                    backRawPath = file.absolutePath
                    backQuad = newQuad
                    updateBackCardUi()
                }
            }
        }
    }

    private fun previewCleanCard(side: CardSide) {
        val path = if (side == CardSide.FRONT) frontRawPath else backRawPath
        val quad = if (side == CardSide.FRONT) frontQuad else backQuad
        if (path == null || quad == null) return

        showLoading("Enhancing card...")
        lifecycleScope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeFile(path) ?: return@launch
            val cleaned = engine.processCard(
                bitmap = bmp,
                quad = quad,
                config = ProcessingConfig(autoWhiteBalance = true, autoFlatField = true),
                isFront = side == CardSide.FRONT
            )
            withContext(Dispatchers.Main) {
                hideLoading()
                val dialog = Dialog(this@MainActivity)
                val iv = ImageView(this@MainActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    adjustViewBounds = true
                    setImageBitmap(cleaned)
                    setPadding(24, 24, 24, 24)
                }
                dialog.setContentView(iv)
                dialog.show()
            }
        }
    }

    private enum class ActionType {
        PRINT,
        SHARE_PDF,
        SHARE_PNG,
        PREVIEW_PAGE
    }

    private fun composeAndExecute(action: ActionType) {
        val fPath = frontRawPath
        val fQuad = frontQuad

        if (fPath == null || fQuad == null) {
            Toast.makeText(this, "Please add the Front side of your card first", Toast.LENGTH_SHORT).show()
            return
        }

        // If outputs already prepared, execute directly
        if (latestA4Bitmap != null && latestPdfFile != null && latestPngFile != null) {
            executeAction(action, latestA4Bitmap!!, latestPdfFile!!, latestPngFile!!)
            return
        }

        showLoading("Composing 300 DPI A4 Page...")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Process Front Card
                val frontBmp = BitmapFactory.decodeFile(fPath)
                val frontCleaned = engine.processCard(frontBmp, fQuad, ProcessingConfig(), isFront = true)

                // 2. Process Back Card if provided
                var backCleaned: Bitmap? = null
                val bPath = backRawPath
                val bQuad = backQuad
                if (bPath != null && bQuad != null) {
                    val backBmp = BitmapFactory.decodeFile(bPath)
                    backCleaned = engine.processCard(backBmp, bQuad, ProcessingConfig(), isFront = false)
                }

                // 3. Compose A4 Bitmap
                val a4 = engine.composeA4(frontCleaned, backCleaned)

                // 4. Generate PDF
                val timeTag = System.currentTimeMillis()
                val pdfFile = File(cacheDir, "id_card_print_$timeTag.pdf")
                PdfGenerator.generatePdf(a4, pdfFile)

                // 5. Generate PNG
                val pngFile = File(cacheDir, "id_card_print_$timeTag.png")
                A4LayoutComposer.saveAsPng(a4, pngFile)

                withContext(Dispatchers.Main) {
                    hideLoading()
                    latestA4Bitmap = a4
                    latestPdfFile = pdfFile
                    latestPngFile = pngFile

                    // Update Output Tracker UI
                    binding.cardOutputTracker.visibility = View.VISIBLE
                    val pdfKb = pdfFile.length() / 1024
                    val pngKb = pngFile.length() / 1024
                    binding.tvPdfTrack.text = "📄 PDF: ${pdfFile.name} ($pdfKb KB)"
                    binding.tvPngTrack.text = "🖼 PNG: ${pngFile.name} ($pngKb KB)"

                    executeAction(action, a4, pdfFile, pngFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(this@MainActivity, "Failed to compose page: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun executeAction(action: ActionType, a4: Bitmap, pdf: File, png: File) {
        when (action) {
            ActionType.PRINT -> printPdfDirect(pdf)
            ActionType.SHARE_PDF -> shareFile(pdf, "application/pdf")
            ActionType.SHARE_PNG -> shareFile(png, "image/png")
            ActionType.PREVIEW_PAGE -> showFullPagePreviewDialog(a4, pdf)
        }
    }

    private fun showFullPagePreviewDialog(a4: Bitmap, pdf: File) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogBinding = DialogFullPagePreviewBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.ivFullA4Preview.setImageBitmap(a4)
        dialogBinding.btnClosePreview.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnQuickPrint.setOnClickListener {
            dialog.dismiss()
            printPdfDirect(pdf)
        }
        dialog.show()
    }

    private fun shareFile(file: File, mimeType: String) {
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
    }

    private fun printPdfDirect(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val printManager = getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(this, "Printing service unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        val printAdapter = object : PrintDocumentAdapter() {
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
                val info = PrintDocumentInfo.Builder("ID_Card_Print.pdf")
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

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        printManager.print("ID_Card_${file.nameWithoutExtension}", printAdapter, printAttributes)
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
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(path, decodeOptions)
    }

    private fun showLoading(msg: String) {
        binding.tvGlobalLoadingText.text = msg
        binding.globalLoadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        binding.globalLoadingOverlay.visibility = View.GONE
    }
}
