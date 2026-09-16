# Project Handoff Document: ID Card Printer & Scanner Ecosystem
**Desktop Application & Modular Android SDK**

---

## 1. Executive Summary

The **ID Card Printer Ecosystem** is an end-to-end solution for capturing, perspective-correcting, flat-field cleaning, and composing physical identity cards (Aadhaar, Voter ID, Driving License, PAN, Employee IDs) onto 300 DPI A4 print-ready sheets.

The ecosystem consists of two fully functional, self-contained implementations:

1. **Desktop Application (`IdCardPrinter`)**:
   - Python-based GUI utility with live preview, 4-corner perspective correction, lighting normalization, and single-page A4 PDF composition.
   - Distributed as a standalone Linux binary (PyInstaller ELF), standalone ZipApp (`.pyz`), and packaged tarball with system menu integration (`.desktop`).
   - GitHub release `v1.0.0` published.

2. **Android Port & Embeddable SDK (`IdCardPrinterAndroid`)**:
   - Native Android modular library and standalone application targeting API 24–34.
   - Ultra-lightweight with **zero native C++ / OpenCV dependencies** (~100 KB total SDK size vs 30–40 MB typical scanner SDKs).
   - Features an interactive **Magnifying Loupe (2.5× touch-offset zoom glass with crosshairs)**, live CameraX scanning, and a turnkey 1-line `ActivityResultContract` for integration into third-party apps (KYC, banking, document scanners).
   - Direct wireless printing via Android's native `PrintManager` (Wi-Fi, Mopria, AirPrint).

---

## 2. Repository Index & Locations

| Component | Path | Language / Stack | Primary Deliverables |
| :--- | :--- | :--- | :--- |
| **Desktop App** | `/home/pnb/Projects/IdCardPrinter` | Python 3, Tkinter, OpenCV, Pillow, PyMuPDF | `release/id-card-printer`<br>`release/id-card-printer.pyz`<br>`release/id-card-printer-v1.0.0-linux-x86_64.tar.gz` |
| **Android SDK & App** | `/home/pnb/Projects/IdCardPrinterAndroid` | Kotlin, Gradle 8.7, AndroidX, CameraX | `release/app-debug.apk` (6.5 MB)<br>`release/idcard-core-release.aar` (30 KB)<br>`release/idcard-ui-release.aar` (72 KB) |

---

## 3. Core Image Processing & Mathematics

Both desktop and Android implementations share the same algorithmic specifications:

### 3.1. 4-Corner Quadrilateral Homography
- **Input**: 4 user-selected or auto-detected corners `[TL, TR, BR, BL]`.
- **Target Dimensions**: Physical card standard ISO/IEC 7810 ID-1 ($85.60 \times 53.98\text{ mm} \approx 1.586$ aspect ratio, rendered at $1184 \times 758\text{ px}$ target).
- **Transformation**:
  - **Desktop**: `cv2.getPerspectiveTransform(src, dst)` and `cv2.warpPerspective()`.
  - **Android**: Hardware-accelerated Skia `android.graphics.Matrix.setPolyToPoly(src, 0, dst, 0, 4)` and `Canvas.drawBitmap()`.

### 3.2. Illumination Flat-Fielding (Shadow & Glare Removal)
Natural camera captures have uneven lighting gradients and hand shadows.
1. The image is downsampled to a low-frequency $40 \times 26$ mesh.
2. A large-radius spatial box filter smooths the mesh to isolate illumination background intensity $B(x, y)$ while ignoring high-frequency card text and photos.
3. The original image $I(x, y)$ is divided by $B(x, y)$ and scaled:
   $$I_{\text{corrected}}(x, y) = \min\left(255, \frac{I(x, y)}{B(x, y)} \times \bar{B}\right)$$
4. Result: Uniform lighting across the card without burning out text or photos.

### 3.3. Tone Curve Paper Whitening
- A 256-entry S-curve Lookup Table (LUT) expands midrange contrast.
- Pixels with luminance $L > 220$ and low chroma are gently mapped to pure white (`#FFFFFF`), removing yellowish incandescent room light casts from paper borders while keeping photo portraits natural.

### 3.4. 300 DPI A4 Canvas Geometry
- **Sheet Dimensions**: $210 \times 297\text{ mm} \to 2480 \times 3508\text{ px}$ at 300 DPI.
- **Wallet 1:1 Layout**:
  - Card Size: $85.6 \times 54.0\text{ mm} = 1011 \times 638\text{ px}$.
  - Front and Back cards placed horizontally adjacent separated by a central dashed fold line.
  - Corner crop ticks for cutting.
  - Exactly calibrated **5.0 cm check ruler** ($50\text{ mm} = 591\text{ px}$) printed on the sheet so users can verify 100% scale before cutting.
- **KYC Document Copy**:
  - Enlarged front and back card views centered on the upper half of A4, leaving the lower half open for physical or digital customer signature and date.

---

## 4. Desktop Application Details (`IdCardPrinter`)

### 4.1. File Structure
```
IdCardPrinter/
├── app.py                # Tkinter GUI (split preview, zoom loupe, settings, print dispatcher)
├── card_processor.py     # OpenCV engine (detect, warp, flat-field, A4 composition)
├── id-card-printer.spec  # PyInstaller spec file
├── install.sh            # Installs standalone binary & desktop icon into ~/.local
├── uninstall.sh          # Cleans up binary and desktop entry
├── run.sh                # Launcher script for source execution
├── requirements.txt      # Python dependencies (opencv-python-headless, pillow, pymupdf)
└── release/              # Release binaries, checksums, and release notes
```

### 4.2. Common Commands
```bash
# Run from source
cd /home/pnb/Projects/IdCardPrinter
./run.sh

# Rebuild standalone binary (PyInstaller)
pyinstaller id-card-printer.spec --clean

# Build ZipApp (.pyz)
python3 -m zipapp . -m "app:main" -o release/id-card-printer.pyz

# Install to system menu
./install.sh

# Publish / update GitHub release
gh release create v1.0.0 release/id-card-printer release/id-card-printer.pyz release/SHA256SUMS.txt --notes-file release/GITHUB_RELEASE.md
```

---

## 5. Android Architecture & Embeddable SDK (`IdCardPrinterAndroid`)

### 5.1. File Structure
```
IdCardPrinterAndroid/
├── idcard-core/                         # Headless Processing Engine (30 KB AAR)
│   └── src/main/java/com/idcardprinter/core/
│       ├── model/Models.kt              # CardPoint, CardQuad, ProcessingConfig, PrintLayout
│       ├── detector/CornerDetector.kt   # Pure Kotlin gradient boundary detector
│       ├── warp/PerspectiveWarper.kt    # Skia Matrix homography transform
│       ├── enhance/CardEnhancer.kt      # Flat-fielding, Auto-WB, and S-curve LUT
│       ├── layout/A4LayoutComposer.kt   # 300 DPI A4 composer (Wallet 1:1 & KYC)
│       ├── layout/PdfGenerator.kt       # Native android.graphics.pdf.PdfDocument
│       └── IdCardEngine.kt              # High-level facade for headless callers
│
├── idcard-ui/                           # Interactive UI & Turnkey Contract (72 KB AAR)
│   └── src/main/java/com/idcardprinter/ui/
│       ├── crop/CardCropView.kt         # 4-handle cropper with 2.5x Magnifying Loupe
│       ├── contract/
│       │   └── IdCardScannerContract.kt # ActivityResultContract<Config, Result>
│       └── IdCardScanActivity.kt        # Dual-card capture, crop, rotate & preview flow
│
├── app/                                 # Showcase & Standalone App (5.3 MB APK)
│   └── src/main/java/com/idcardprinter/app/
│       └── MainActivity.kt              # Layout selection, PDF share, and PrintManager
│
└── release/                             # Prebuilt distributables & checksums
    ├── app-debug.apk                    # 6.5 MB runnable APK
    ├── idcard-core-release.aar          # 30 KB
    ├── idcard-ui-release.aar            # 72 KB
    └── SHA256SUMS.txt
```

### 5.2. Magnifying Loupe Design (`CardCropView.kt`)
On touchscreen devices, the user's thumb or index finger covers the exact corner they are adjusting. To solve this:
- When a corner handle is pressed, a circular **2.5× Magnifying Loupe** is rendered with a 65dp vertical offset above the finger.
- The loupe displays a high-resolution sub-region of the image centered precisely on the card corner, complete with crosshairs, a circular accent border, and subtle haptic feedback.

---

## 6. Integration Guide for Other Android Apps (KYC, Banking, Scanning)

### 6.1. Turnkey Contract (1 Line of Code)

#### Step 1: Add Dependency
In your host app's `build.gradle.kts`:
```kotlin
// Option A: If included as a git submodule / subproject
dependencies {
    implementation(project(":idcard-ui"))
    implementation(project(":idcard-core"))
}

// Option B: If using prebuilt AAR files in libs/
dependencies {
    implementation(files("libs/idcard-ui-release.aar"))
    implementation(files("libs/idcard-core-release.aar"))
}
```

#### Step 2: Register & Launch in Activity or Fragment
```kotlin
import com.idcardprinter.core.model.PrintLayout
import com.idcardprinter.ui.contract.IdCardScannerConfig
import com.idcardprinter.ui.contract.IdCardScannerContract
import java.io.File

class KycVerificationActivity : AppCompatActivity() {

    // 1. Register contract
    private val scannerLauncher = registerForActivityResult(IdCardScannerContract()) { result ->
        if (result.isSuccess) {
            val pdfPath: String = result.pdfPath!!
            val frontPath: String? = result.frontCardPath
            val backPath: String? = result.backCardPath

            // Ready for KYC document upload, cloud backup, or local printing!
            uploadToKycServer(File(pdfPath))
        } else {
            val err = result.errorMessage ?: "User cancelled scanning"
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Launch scanner
    private fun startScanning() {
        scannerLauncher.launch(
            IdCardScannerConfig(
                layout = PrintLayout.WALLET_1TO1, // or PrintLayout.DOCUMENT_KYC
                autoWhiteBalance = true,
                autoFlatField = true
            )
        )
    }
}
```

---

### 6.2. Headless Processing (Without Any UI)

If your app already captures photos and only needs the perspective transformation, shadow removal, and 300 DPI A4 PDF generation:

```kotlin
import com.idcardprinter.core.IdCardEngine
import com.idcardprinter.core.layout.A4LayoutComposer
import com.idcardprinter.core.layout.PdfGenerator
import com.idcardprinter.core.model.ProcessingConfig

val engine = IdCardEngine(context)

// 1. Gradient boundary auto-detection
val quad = engine.detectCorners(rawBitmap)

// 2. Homography warp + flat-field illumination + paper whitening
val cleanedFront = engine.processCard(
    rawBitmap = rawBitmap,
    quad = quad,
    config = ProcessingConfig(autoWhiteBalance = true, autoFlatField = true),
    isFront = true
)

// 3. Compose 300 DPI A4 page
val composer = A4LayoutComposer()
val a4Bitmap = composer.composeWallet1to1(frontCard = cleanedFront, backCard = cleanedBack)

// 4. Generate native PDF
val pdfGenerator = PdfGenerator(context)
val pdfFile = pdfGenerator.createPdfFromBitmap(a4Bitmap, "ID_Card_Wallet.pdf")
```

---

## 7. Android Build & Testing Cheatsheet

```bash
cd /home/pnb/Projects/IdCardPrinterAndroid

# Run unit tests (CardQuadTest, etc.)
./gradlew test

# Build debug APK (can be installed on any device without signing)
./gradlew :app:assembleDebug

# Build release AARs
./gradlew :idcard-core:assembleRelease
./gradlew :idcard-ui:assembleRelease

# Build everything
./gradlew assemble

# Install directly to USB connected Android device
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 8. Verified Deliverables & Checksums

### Desktop (`/home/pnb/Projects/IdCardPrinter/release`)
```
SHA-256 Checksums:
4dcfd4c06283ff1c4f4ad925ea4cece38ef348f33a921d26ec66373b7a5a8816  id-card-printer
ef57df079717ec2efb612140b904d9b2512f45cc3558c422dd81134a654eb683  id-card-printer-v1.0.0-linux-x86_64.tar.gz
1e4f4ec85c8e31fc5d045d625531d0fd4fbf4aa05d15c7e112aaee0be634fbef  id-card-printer.pyz
```

### Android (`/home/pnb/Projects/IdCardPrinterAndroid/release`)
```
SHA-256 Checksums:
22941df756e1fc3ea73da56b27e6dbcbdb23aafe7bfe2f8d3845b410427c3e1e  app-debug.apk
e05139a039750d4f3b7d34ddb478cf3bb15809ceea5ef43be444dd1a56658dfa  idcard-core-release.aar
404975734bc14d6428e930ef5ea7314d10f6ce1bb8bdfdd5eb23b0a68d097c02  idcard-ui-release.aar
```

---

## 9. Next Steps / Recommendations for Future Integrations

1. **Camera Auto-Focus / Auto-Capture**: If required in future iterations, CameraX `ImageAnalysis` can trigger auto-capture when all 4 card corners are stable for $>500\text{ ms}$.
2. **Barcode / QR Code Extraction**: A lightweight integration with Google ML Kit Barcode Scanning can be added to read the QR code on the back of Aadhaar or PAN cards before rendering.
3. **Maven Central / GitHub Packages**: If distributing the `.aar` libraries across external teams without sharing source folders, configure the `maven-publish` Gradle plugin to push `:idcard-core` and `:idcard-ui` to your organization's internal Maven repository.
