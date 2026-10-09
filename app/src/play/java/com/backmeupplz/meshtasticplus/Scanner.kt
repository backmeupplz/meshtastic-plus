package com.backmeupplz.meshtasticplus

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// Google's scanner: no camera permission, but needs Play services
@Composable
fun rememberQrScanner(onLink: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    return {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(ctx, options).startScan()
            .addOnSuccessListener { it.rawValue?.let(onLink) }
            .addOnFailureListener { Toast.makeText(ctx, "Couldn't open the QR scanner", Toast.LENGTH_SHORT).show() }
    }
}
