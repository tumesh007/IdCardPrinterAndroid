# ID Card Printer & Scanner for Android

A modern, ultra-lightweight Android SDK and standalone application for capturing, perspective-correcting, flat-field cleaning, and composing physical ID cards (Aadhaar, Voter ID, Driving License, PAN, Employee IDs) onto 300 DPI A4 sheets for 1:1 true-size wallet printing or official KYC document submission.

---

## 🌟 Key Features

- **Decoupled 3-Tier Architecture**:
  - `:idcard-core`: Pure Kotlin image engine with **zero native C++ OpenCV dependencies**. Matrix homography warp, Auto-WB, downsampled illumination flat-fielding, and 300 DPI A4 PDF composer (~30 KB AAR).
  - `:idcard-ui`: Interactive corner selection with **Magnifying Loupe (2.5x touch-offset zoom glass with crosshairs)**, live CameraX card frame guide, gallery import, 90° rotation, real-time enhancement preview, and turnkey `ActivityResultContract` (~72 KB AAR).
  - `:app`: Standalone demonstration application and launcher utility.
- **Precision 300 DPI Output**:
  - **Wallet 1:1 Cut & Fold**: Exact physical credit-card dimensions (85.6 × 54.0 mm) with centerline fold guide, corner cut marks, and a 5 cm calibration ruler.
  - **KYC Document Copy**: Enlarged front & back card copies positioned on the upper half of an A4 page, leaving the lower half for signature and date.
- **Integrated Android Wireless Printing**:
  - Built-in `PrintDocumentAdapter` hook directly opens Android's native Print Spooler (`PrintManager`) to print via Wi-Fi/Mopria/AirPrint without third-party printer drivers.

---

## 📦 Project Structure

```
IdCardPrinterAndroid/
├── idcard-core/         # Pure headless processing engine (30 KB AAR)
│   └── src/main/java/com/idcardprinter/core/
│       ├── model/       # Models: CardPoint, CardQuad, ProcessingConfig, PrintLayout
│       ├── detector/    # Pure Kotlin gradient boundary detection
│       ├── warp/        # Hardware-accelerated Skia Matrix homography warp
│       ├── enhance/     # Flat-field lighting correction & tone mapping
│       ├── composer/    # 300 DPI A4 sheet composer (Wallet & KYC)
│       ├── pdf/         # Native android.graphics.pdf.PdfDocument generator
│       └── IdCardEngine.kt # High-level facade for headless pipelines
│
├── idcard-ui/           # Ready-to-use Scanner & Cropper UI (72 KB AAR)
│   └── src/main/java/com/idcardprinter/ui/
│       ├── crop/        # CardCropView with 2.5x Magnifying Loupe
│       ├── contract/    # IdCardScannerContract (1-line integration)
│       └── IdCardScanActivity.kt # Full capture, crop & preview flow
│
├── app/                 # Standalone sample & launch application
│   └── src/main/java/com/idcardprinter/app/MainActivity.kt
│
└── release/             # Prebuilt artifacts (APKs and AARs)
    ├── app-debug.apk
    ├── idcard-core-release.aar
    ├── idcard-ui-release.aar
    └── SHA256SUMS.txt
```

---

## 🚀 1-Minute Integration Guide

### Option 1: Using Gradle Subproject / Module

Add the modules to your host project's `settings.gradle.kts`:
```kotlin
include(":idcard-core")
include(":idcard-ui")
project(":idcard-core").projectDir = file("path/to/IdCardPrinterAndroid/idcard-core")
project(":idcard-ui").projectDir = file("path/to/IdCardPrinterAndroid/idcard-ui")
```

In your host app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":idcard-ui"))
    implementation(project(":idcard-core"))
}
```

---

### Option 2: Using Prebuilt AAR Files

Copy `idcard-core-release.aar` and `idcard-ui-release.aar` into your host app's `libs/` directory:
```kotlin
dependencies {
    implementation(files("libs/idcard-core-release.aar"))
    implementation(files("libs/idcard-ui-release.aar"))

    // Required AndroidX runtime dependencies
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
}
```

---

### Launching the Scanner (1 Line of Code)

In your Host `Activity` or `Fragment`:

```kotlin
import com.idcardprinter.core.model.PrintLayout
import com.idcardprinter.ui.contract.IdCardScannerConfig
import com.idcardprinter.ui.contract.IdCardScannerContract

class KycVerificationActivity : AppCompatActivity() {

    // 1. Register the contract
    private val scanCardLauncher = registerForActivityResult(IdCardScannerContract()) { result ->
        if (result.isSuccess) {
            val pdfFilePath = result.pdfPath
            val frontCardBmpPath = result.frontCardPath
            val backCardBmpPath = result.backCardPath

            // Use the 300 DPI PDF for printing or upload to KYC backend
            uploadDocumentToKyc(File(pdfFilePath!!))
        } else {
            val error = result.errorMessage ?: "Cancelled by user"
            Log.w("KYC", error)
        }
    }

    private fun onStartScanClicked() {
        // 2. Launch with desired configuration
        val config = IdCardScannerConfig(
            layout = PrintLayout.WALLET_1TO1, // or PrintLayout.DOCUMENT_KYC
            autoWhiteBalance = true,
            autoFlatField = true
        )
        scanCardLauncher.launch(config)
    }
}
```

---

## 🛠️ Headless Engine Usage (No UI)

If your app captures images through custom camera pipelines or background jobs, you can use `IdCardEngine` directly:

```kotlin
val engine = IdCardEngine(context)

// 1. Detect corners (gradient profile detection)
val quad = engine.detectCorners(rawBitmap)

// 2. Perspective warp, flat-field illumination, and tone curve
val cleanedCard = engine.processCard(
    rawBitmap = rawBitmap,
    quad = quad,
    config = ProcessingConfig(autoWhiteBalance = true, autoFlatField = true),
    isFront = true
)

// 3. Compose 300 DPI A4 page and write PDF
val a4Composer = A4LayoutComposer()
val a4Bitmap = a4Composer.composeWallet1to1(frontCard = cleanedCard, backCard = cleanedBackCard)
val pdfGenerator = PdfGenerator(context)
val pdfFile = pdfGenerator.createPdfFromBitmap(a4Bitmap, "ID_Card_Print.pdf")
```

---

## 🔨 Building from Source

Ensure JDK 17+ and Android SDK with platform 34 are installed:

```bash
# Clone and enter project
cd IdCardPrinterAndroid

# Build all libraries and APKs
./gradlew assemble

# Run unit tests
./gradlew test

# Install debug APK on connected Android phone/emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
MIT License. Free for personal and commercial use.
