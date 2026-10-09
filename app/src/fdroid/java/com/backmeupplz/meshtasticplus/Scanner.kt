package com.backmeupplz.meshtasticplus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// Open-source ZXing scanner for F-Droid (asks for the camera permission)
@Composable
fun rememberQrScanner(onLink: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { it.contents?.let(onLink) }
    return {
        launcher.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("").setBeepEnabled(false).setOrientationLocked(false),
        )
    }
}
