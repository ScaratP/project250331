package com.example.project250311.Game

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlin.also
import kotlin.collections.firstOrNull
import kotlin.text.isNotEmpty

/**
 * 專用於掃描 QR Code 的全螢幕 Composable。
 *
 * @param onQrCodeScanned 當掃描到 QR Code 時被呼叫，回傳掃描到的字串內容。
 */
@Composable
fun QrcodeScreen(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 請求相機權限
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    // 當 Composable 進入畫面時，如果沒有權限，就啟動權限請求
    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 根據權限狀態顯示不同內容
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // 顯示相機預覽和掃描邏輯
            CameraPreview(
                onQrCodeScanned = onQrCodeScanned
            )
            Text(
                text = "請對準集點 QR Code",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
            )
        } else {
            // 顯示權限被拒絕的提示
            Text(
                text = "相機權限已被拒絕，請至設定開啟權限",
                color = Color.Black,
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * 內部 Composable，負責顯示 CameraX 預覽並整合 ML Kit 掃描
 */
@Composable
private fun CameraPreview(
    onQrCodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // 用於 ML Kit 處理影像的執行緒
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // 記住 BarcodeScanner 實例
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE) // 只偵測 QR Code
            .build()
        BarcodeScanning.getClient(options)
    }

    // 記住是否已經掃描到
    var hasScanned by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProvider = cameraProviderFuture.get()

            // 1. 設定預覽 (Preview) UseCase
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // 2. 設定影像分析 (ImageAnalysis) UseCase
            val imageAnalysis = ImageAnalysis.Builder()
//                .setTargetResolution(Size(previewView.width, previewView.height))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER /* ImageAnalysis.STRATEGY_KEEP_LATEST */)
                .build()

            // 設置分析器
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                // 如果已經掃描到了，就不要再分析了
                if (hasScanned) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                processImageProxy(barcodeScanner, imageProxy) { scannedValue ->
                    if (scannedValue.isNotEmpty() && !hasScanned) {
                        hasScanned = true // 標記為已掃描

                        // 在主執行緒上回傳結果並關閉相機
                        ContextCompat.getMainExecutor(context).execute {
                            cameraProvider.unbindAll() // 停止相機
                            onQrCodeScanned(scannedValue) // 回傳結果
                        }
                    }
                }
            }

            // 3. 選擇後置鏡頭
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            // 4. 將 UseCases 綁定到鏡頭
            try {
                cameraProvider.unbindAll() // 先解除所有綁定
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis // 把分析器也綁定
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            previewView
        }
    )
}

/**
 * 輔助函式：處理 ImageProxy，並使用 ML Kit 進行分析
 */
@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    barcodeScanner: BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                // 處理成功
                val scannedValue = barcodes.firstOrNull()?.rawValue
                if (scannedValue != null) {
                    onResult(scannedValue)
                }
            }
            .addOnFailureListener {
                // 處理失敗
                it.printStackTrace()
            }
            .addOnCompleteListener {
                // 無論成功或失敗，最後都要關閉 imageProxy
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}