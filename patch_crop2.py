import re

with open("idcard-ui/src/main/java/com/idcardprinter/ui/crop/CardCropActivity.kt", "r") as f:
    code = f.read()

# bmp is captured by closure. We can just use a local final val or use ?.let
replace_from = """
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
"""

replace_to = """
                    val oriented = fixOrientation(path, bmp!!)
                    if (oriented !== bmp) {
                        bmp = oriented
                        // Save the rotated image so the quad coordinates match
                        val cacheFile = File(cacheDir, "cached_card_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(cacheFile).use { out ->
                            oriented.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        finalPath = cacheFile.absolutePath
                    }
"""

code = code.replace(replace_from.strip(), replace_to.strip())

with open("idcard-ui/src/main/java/com/idcardprinter/ui/crop/CardCropActivity.kt", "w") as f:
    f.write(code)
