# ID Card Printer for Android v1.0.0

A lightweight, modern Android application and embeddable SDK for scanning, perspective-correcting, flat-field cleaning, and composing physical ID cards onto plain 300 DPI A4 sheets for direct wireless printing and document submission.

---

## 🌟 What's New in v1.0.0

- **Minimalistic Dashboard**: Clean dual-slot layout for **Front** and **Back (Optional)** cards. Supports single-side (front-only) and dual-side printing.
- **Dedicated Fullscreen Crop Editor**:
  - **2.5× Magnifying Loupe**: Offset above touch point with high-contrast crosshairs so fingers never obscure card corners.
  - **Rotate 90°**: Instant clockwise rotation.
  - **Auto-Detect**: Pure-Kotlin gradient profile boundary detection to snap corners automatically.
  - **Clean Card Preview**: Real-time flat-fielding and paper-whitening preview dialog.
- **Zero-Bloat Image Pipeline**:
  - Pure Kotlin homography warp via Skia `Matrix.setPolyToPoly` (avoids 30–40 MB OpenCV C++ binary bloat).
  - Illumination flat-fielding removes harsh shadows, uneven gradients, and camera flash glares.
  - 256-LUT S-curve tone mapping suppresses yellow incandescent casts and whitens paper borders to pure `#FFFFFF`.
- **Plain 300 DPI A4 Output**:
  - Front at top, Back at bottom.
  - Clean, distraction-free page with zero clutter (no unwanted text, rulers, or fold lines).
- **Direct Wireless Printing & Export**:
  - Direct wireless Wi-Fi printing via native Android `PrintManager`.
  - Export to high-resolution 300 DPI A4 **PDF** and **PNG**.
  - Output file tracker with quick "Open" and "Share" actions.
- **Embeddable SDK for Other Apps**:
  - `:idcard-core` (**26 KB** AAR): Headless engine for background / KYC pipelines.
  - `:idcard-ui` (**113 KB** AAR): Turnkey cropper & `ActivityResultContract`.

---

## 📦 Downloads & Checksums

| Asset | Size | Description |
| :--- | :--- | :--- |
| `app-debug.apk` | 7.5 MB | Standalone runnable Android application (API 24+) |
| `idcard-core-release.aar` | 26 KB | Embeddable headless image engine library |
| `idcard-ui-release.aar` | 113 KB | Embeddable crop editor and camera UI library |
| `SHA256SUMS.txt` | 258 B | SHA-256 verification checksums |

### SHA-256 Checksums
```
e2fa3da31e9c20aa1982b6be1a8c0ef026046e2fcb43a910ecb29cb256b1a38a  app-debug.apk
60fe489e21160d5b51dd588e7dd9fbfe87ebcf35f79571ff213f3aeaf71f92e0  idcard-core-release.aar
404975734bc14d6428e930ef5ea7314d10f6ce1bb8bdfdd5eb23b0a68d097c02  idcard-ui-release.aar
```

---

## 📱 Quick Install

```bash
adb install -r app-debug.apk
```
