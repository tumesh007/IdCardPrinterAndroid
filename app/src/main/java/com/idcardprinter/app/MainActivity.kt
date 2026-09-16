package com.idcardprinter.app

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.idcardprinter.app.databinding.ActivityMainBinding
import com.idcardprinter.core.model.PrintLayout
import com.idcardprinter.ui.contract.IdCardScannerConfig
import com.idcardprinter.ui.contract.IdCardScannerContract
import com.idcardprinter.ui.contract.IdCardScannerResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastResult: IdCardScannerResult? = null

    // Register ActivityResultContract - 1 line turnkey integration
    private val scannerLauncher = registerForActivityResult(IdCardScannerContract()) { result ->
        handleScanResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLaunchScanner.setOnClickListener {
            val selectedLayout = if (binding.rbWallet.isChecked) {
                PrintLayout.WALLET_1TO1
            } else {
                PrintLayout.DOCUMENT_KYC
            }

            val config = IdCardScannerConfig(
                layout = selectedLayout,
                autoWhiteBalance = binding.switchWhiteBalance.isChecked,
                autoFlatField = binding.switchFlatField.isChecked
            )

            scannerLauncher.launch(config)
        }

        binding.btnSharePdf.setOnClickListener {
            val path = lastResult?.pdfPath ?: return@setOnClickListener
            sharePdf(File(path))
        }

        binding.btnPrintPdf.setOnClickListener {
            val path = lastResult?.pdfPath ?: return@setOnClickListener
            printPdfDirect(File(path))
        }
    }

    private fun handleScanResult(result: IdCardScannerResult) {
        if (!result.isSuccess || result.pdfPath == null) {
            if (result.errorMessage != null) {
                Toast.makeText(this, "Scan failed: ${result.errorMessage}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
            return
        }

        lastResult = result
        binding.cardResult.visibility = View.VISIBLE

        val pdfPath = result.pdfPath ?: return
        val pdfFile = File(pdfPath)
        val sizeKb = pdfFile.length() / 1024
        binding.tvResultPdfPath.text = "Saved: ${pdfFile.name} ($sizeKb KB)"

        result.frontCardPath?.let { path ->
            val frontBmp = BitmapFactory.decodeFile(path)
            binding.ivResultFront.setImageBitmap(frontBmp)
            binding.ivResultFront.visibility = View.VISIBLE
        } ?: run {
            binding.ivResultFront.visibility = View.GONE
        }

        result.backCardPath?.let { path ->
            val backBmp = BitmapFactory.decodeFile(path)
            binding.ivResultBack.setImageBitmap(backBmp)
            binding.ivResultBack.visibility = View.VISIBLE
        } ?: run {
            binding.ivResultBack.visibility = View.GONE
        }

        Toast.makeText(this, "ID Card PDF Ready for Printing!", Toast.LENGTH_SHORT).show()
    }

    private fun sharePdf(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share ID Card Print PDF"))
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

        val printJobName = "ID_Card_${file.nameWithoutExtension}"
        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()

        printManager.print(printJobName, printAdapter, printAttributes)
    }
}
