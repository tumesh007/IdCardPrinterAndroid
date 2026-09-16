package com.idcardprinter.ui.crop

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import com.idcardprinter.core.model.CardQuad
import java.io.Serializable

data class CropInput(
    val imagePath: String? = null,
    val imageUri: Uri? = null,
    val initialQuad: CardQuad? = null,
    val title: String = "Crop ID Card",
    val isFront: Boolean = true
) : Serializable

data class CropResult(
    val isSuccess: Boolean,
    val quad: CardQuad? = null,
    val imagePath: String? = null
) : Serializable

class IdCardCropContract : ActivityResultContract<CropInput, CropResult>() {

    override fun createIntent(context: Context, input: CropInput): Intent {
        return Intent(context, CardCropActivity::class.java).apply {
            input.imagePath?.let { putExtra(CardCropActivity.EXTRA_IMAGE_PATH, it) }
            input.imageUri?.let { putExtra(CardCropActivity.EXTRA_IMAGE_URI, it.toString()) }
            input.initialQuad?.let { putExtra(CardCropActivity.EXTRA_INITIAL_QUAD, it) }
            putExtra(CardCropActivity.EXTRA_TITLE, input.title)
            putExtra(CardCropActivity.EXTRA_IS_FRONT, input.isFront)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): CropResult {
        if (resultCode == Activity.RESULT_OK && intent != null) {
            val quad = intent.getSerializableExtra(CardCropActivity.EXTRA_RESULT_QUAD) as? CardQuad
            val path = intent.getStringExtra(CardCropActivity.EXTRA_RESULT_IMAGE_PATH)
            return CropResult(isSuccess = true, quad = quad, imagePath = path)
        }
        return CropResult(isSuccess = false)
    }
}
