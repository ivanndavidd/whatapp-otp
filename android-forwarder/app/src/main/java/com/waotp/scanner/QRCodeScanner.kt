package com.waotp.scanner

import android.app.Activity

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions

object QRCodeScanner {

    fun scan(
        activity: Activity,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit,
        onCanceled: () -> Unit = {}
    ) {

        val options =
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE
                )
                .enableAutoZoom()
                .build()

        val scanner =
            GmsBarcodeScanning.getClient(
                activity,
                options
            )

        scanner.startScan()

            .addOnSuccessListener { barcode ->

                val value =
                    barcode.rawValue

                if (!value.isNullOrBlank()) {

                    onSuccess(value)

                } else {

                    onError(
                        IllegalStateException(
                            "QR code tidak memiliki teks didalamnya"
                        )
                    )
                }
            }

            .addOnFailureListener { exception ->

                onError(exception)
            }

            .addOnCanceledListener {

                onCanceled()
            }
    }
}