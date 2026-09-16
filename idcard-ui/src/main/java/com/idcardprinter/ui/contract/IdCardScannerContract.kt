package com.idcardprinter.ui.contract

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.idcardprinter.core.model.PrintLayout
import com.idcardprinter.ui.IdCardScanActivity
import java.io.Serializable

/**
 * Configuration options when launching the ID Card Scanner flow from a host app.
 */
data class IdCardScannerConfig(
    val layout: PrintLayout = PrintLayout.DOCUMENT_KYC,
    val autoWhiteBalance: Boolean = true,
    val autoFlatField: Boolean = true,
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f
) : Serializable

/**
 * Result returned to the host app containing file paths to the generated print-ready PDF
 * and the enhanced individual front and back card images.
 */
data class IdCardScannerResult(
    val isSuccess: Boolean,
    val pdfPath: String? = null,
    val frontCardPath: String? = null,
    val backCardPath: String? = null,
    val errorMessage: String? = null
) : Serializable

/**
 * Standard Android ActivityResultContract for 1-line integration into external Android apps.
 *
 * Example Usage in Host Activity:
 * ```kotlin
 * val launcher = registerForActivityResult(IdCardScannerContract()) { result ->
 *     if (result.isSuccess) {
 *         val pdfFile = File(result.pdfPath!!)
 *         // Use PDF in host app (upload to KYC server, print, or attach to document)
 *     }
 * }
 * launcher.launch(IdCardScannerConfig(layout = PrintLayout.WALLET_1TO1))
 * ```
 */
class IdCardScannerContract : ActivityResultContract<IdCardScannerConfig, IdCardScannerResult>() {

    override fun createIntent(context: Context, input: IdCardScannerConfig): Intent {
        return Intent(context, IdCardScanActivity::class.java).apply {
            putExtra(EXTRA_CONFIG, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): IdCardScannerResult {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            return IdCardScannerResult(
                isSuccess = true,
                pdfPath = intent.getStringExtra(EXTRA_PDF_PATH),
                frontCardPath = intent.getStringExtra(EXTRA_FRONT_PATH),
                backCardPath = intent.getStringExtra(EXTRA_BACK_PATH)
            )
        }
        val err = intent?.getStringExtra(EXTRA_ERROR)
        return IdCardScannerResult(
            isSuccess = false,
            errorMessage = err ?: "Scanning cancelled by user."
        )
    }

    companion object {
        const val EXTRA_CONFIG = "extra_scanner_config"
        const val EXTRA_PDF_PATH = "extra_pdf_path"
        const val EXTRA_FRONT_PATH = "extra_front_path"
        const val EXTRA_BACK_PATH = "extra_back_path"
        const val EXTRA_ERROR = "extra_error"
    }
}
