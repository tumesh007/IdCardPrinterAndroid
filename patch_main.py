import re

with open("app/src/main/java/com/idcardprinter/app/MainActivity.kt", "r") as f:
    code = f.read()

code = code.replace(
    'class MainActivity : AppCompatActivity() {',
    'class MainActivity : AppCompatActivity() {\n\n    private var isDocumentMode = false'
)

setup_listeners_replacement = """
    private fun setupListeners() {
        binding.toggleMode.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                isDocumentMode = (checkedId == R.id.btnModeDocument)
                if (isDocumentMode) {
                    binding.tvFrontTitle.text = "Document Image"
                    binding.cardBackSlot.visibility = View.GONE
                } else {
                    binding.tvFrontTitle.text = "Front Side (Required)"
                    binding.cardBackSlot.visibility = View.VISIBLE
                }
            }
        }
"""
code = code.replace('    private fun setupListeners() {', setup_listeners_replacement)

# Import View
if 'import android.view.View' not in code:
    code = code.replace('import android.os.Bundle', 'import android.view.View\nimport android.os.Bundle')
    
# Import R
if 'import com.idcardprinter.app.R' not in code:
    code = code.replace('import com.idcardprinter.app.databinding.ActivityMainBinding', 'import com.idcardprinter.app.R\nimport com.idcardprinter.app.databinding.ActivityMainBinding')

preview_clean = """
            val cleaned = if (isDocumentMode) {
                engine.processDocument(bmp, quad, ProcessingConfig(autoWhiteBalance = true, autoFlatField = true))
            } else {
                engine.processCard(
                    bitmap = bmp,
                    quad = quad,
                    config = ProcessingConfig(autoWhiteBalance = true, autoFlatField = true),
                    isFront = side == CardSide.FRONT
                )
            }
"""
# Replace the processCard inside previewCleanCard
code = re.sub(
    r'val cleaned = engine\.processCard\(.*?\n.*?isFront = side == CardSide\.FRONT\n\s*\)',
    preview_clean.strip(),
    code,
    flags=re.DOTALL
)

compose_replace = """
            try {
                val a4: Bitmap
                val pdfFile: File
                val pngFile: File
                val timeTag = System.currentTimeMillis()
                
                if (isDocumentMode) {
                    val frontBmp = BitmapFactory.decodeFile(fPath)
                    val frontCleaned = engine.processDocument(frontBmp, fQuad, ProcessingConfig())
                    a4 = engine.composeDocumentA4(frontCleaned)
                    
                    pdfFile = File(cacheDir, "document_print_$timeTag.pdf")
                    PdfGenerator.generatePdf(a4, pdfFile)
                    
                    pngFile = File(cacheDir, "document_print_$timeTag.png")
                    A4LayoutComposer.saveAsPng(a4, pngFile)
                } else {
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
                    a4 = engine.composeA4(frontCleaned, backCleaned)
    
                    // 4. Generate PDF
                    pdfFile = File(cacheDir, "id_card_print_$timeTag.pdf")
                    PdfGenerator.generatePdf(a4, pdfFile)
    
                    // 5. Generate PNG
                    pngFile = File(cacheDir, "id_card_print_$timeTag.png")
                    A4LayoutComposer.saveAsPng(a4, pngFile)
                }
"""

old_compose = r'try \{\s*// 1\. Process Front Card.*?// 5\. Generate PNG.*?A4LayoutComposer\.saveAsPng\(a4, pngFile\)'

code = re.sub(
    old_compose,
    compose_replace.strip(),
    code,
    flags=re.DOTALL
)

with open("app/src/main/java/com/idcardprinter/app/MainActivity.kt", "w") as f:
    f.write(code)
