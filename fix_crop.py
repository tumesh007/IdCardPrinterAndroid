import re

# 1. Update CropInput contract
with open("idcard-ui/src/main/java/com/idcardprinter/ui/crop/IdCardCropContract.kt", "r") as f:
    code = f.read()
code = code.replace(
    'val isFront: Boolean = true',
    'val isFront: Boolean = true,\n    val isDocumentMode: Boolean = false'
)
code = code.replace(
    'putExtra(CardCropActivity.EXTRA_IS_FRONT, input.isFront)',
    'putExtra(CardCropActivity.EXTRA_IS_FRONT, input.isFront)\n            putExtra(CardCropActivity.EXTRA_IS_DOCUMENT_MODE, input.isDocumentMode)'
)
with open("idcard-ui/src/main/java/com/idcardprinter/ui/crop/IdCardCropContract.kt", "w") as f:
    f.write(code)

# 2. Update CardCropActivity
with open("idcard-ui/src/main/java/com/idcardprinter/ui/crop/CardCropActivity.kt", "r") as f:
    code = f.read()

code = code.replace(
    'const val EXTRA_IS_FRONT = "extra_is_front"',
    'const val EXTRA_IS_FRONT = "extra_is_front"\n        const val EXTRA_IS_DOCUMENT_MODE = "extra_is_document_mode"'
)
code = code.replace(
    'private var isFront: Boolean = true',
    'private var isFront: Boolean = true\n    private var isDocumentMode: Boolean = false'
)
code = code.replace(
    'isFront = intent.getBooleanExtra(EXTRA_IS_FRONT, true)',
    'isFront = intent.getBooleanExtra(EXTRA_IS_FRONT, true)\n        isDocumentMode = intent.getBooleanExtra(EXTRA_IS_DOCUMENT_MODE, false)'
)

# Fix orientation save issue
load_image_replacement = """
                if (path != null && File(path).exists()) {
                    bmp = decodeSampledBitmap(path, 2500, 2500)
                    val oriented = fixOrientation(path, bmp)
                    if (oriented !== bmp) {
                        bmp = oriented
                        // Save the rotated image so the quad coordinates match
                        val cacheFile = File(cacheDir, "cached_card_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(cacheFile).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        finalPath = cacheFile.absolutePath
                    }
                } else if (uriStr != null) {
"""
code = code.replace(
    '                if (path != null && File(path).exists()) {\n                    bmp = decodeSampledBitmap(path, 2500, 2500)\n                    bmp = fixOrientation(path, bmp)\n                } else if (uriStr != null) {',
    load_image_replacement.strip()
)

# Fix preview processing
preview_clean_replacement = """
            val config = ProcessingConfig(
                autoWhiteBalance = true,
                autoFlatField = true
            )
            val cleaned = if (isDocumentMode) {
                engine.processDocument(bmp, quad, config)
            } else {
                engine.processCard(bmp, quad, config, isFront)
            }
"""
code = re.sub(
    r'val config = ProcessingConfig\(\s*autoWhiteBalance = true,\s*autoFlatField = true\s*\)\s*val cleaned = engine\.processCard\(bmp, quad, config, isFront\)',
    preview_clean_replacement.strip(),
    code
)

with open("idcard-ui/src/main/java/com/idcardprinter/ui/crop/CardCropActivity.kt", "w") as f:
    f.write(code)

# 3. Update MainActivity to pass isDocumentMode
with open("app/src/main/java/com/idcardprinter/app/MainActivity.kt", "r") as f:
    code = f.read()

code = code.replace(
    'isFront = isFront',
    'isFront = isFront,\n                isDocumentMode = isDocumentMode'
)

with open("app/src/main/java/com/idcardprinter/app/MainActivity.kt", "w") as f:
    f.write(code)

