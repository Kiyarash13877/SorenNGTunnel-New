package com.soreng.tunnel.ui.screen

import android.Manifest
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.soreng.tunnel.ui.viewmodel.ConfigsViewModel
import com.soreng.tunnel.ui.theme.*
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScanScreen(nav: NavController, vm: ConfigsViewModel = hiltViewModel()) {
    val camPerm = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) { if (!camPerm.status.isGranted) camPerm.launchPermissionRequest() }

    Box(Modifier.fillMaxSize().background(Black)) {
        if (camPerm.status.isGranted) {
            QrCamera { raw -> vm.importUris(raw); nav.popBackStack() }
        } else {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("CAMERA PERMISSION REQUIRED",
                    style = MaterialTheme.typography.titleMedium, color = White)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { camPerm.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = GrayDark)) {
                    Text("GRANT PERMISSION")
                }
            }
        }
        Box(Modifier.fillMaxWidth().align(Alignment.TopStart).padding(16.dp)) {
            TextButton({ nav.popBackStack() }) {
                Text("← BACK", style = MaterialTheme.typography.labelSmall, color = White, letterSpacing = 2.sp)
            }
        }
        // Scan frame
        Box(Modifier.align(Alignment.Center)) {
            Canvas(Modifier.size(240.dp)) {
                val w=size.width; val h=size.height; val len=40.dp.toPx(); val sw=3f
                listOf(0f to 0f, w to 0f, 0f to h, w to h).forEach { (cx,cy) ->
                    val dx=if(cx==0f)1f else -1f; val dy=if(cy==0f)1f else -1f
                    drawLine(Color.White,Offset(cx,cy),Offset(cx+dx*len,cy),sw)
                    drawLine(Color.White,Offset(cx,cy),Offset(cx,cy+dy*len),sw)
                }
            }
        }
        Text("ALIGN QR CODE IN FRAME",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom=80.dp),
            style = MaterialTheme.typography.labelMedium, color = GrayPale, letterSpacing = 2.sp)
    }
}

@Composable
private fun QrCamera(onDetected: (String) -> Unit) {
    val ctx   = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var done  by remember { mutableStateOf(false) }
    val exec  = remember { Executors.newSingleThreadExecutor() }

    AndroidView(Modifier.fillMaxSize(), factory = { c ->
        PreviewView(c).also { pv ->
            ProcessCameraProvider.getInstance(c).addListener({
                val cp  = ProcessCameraProvider.getInstance(c).get()
                val pre = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                val opt = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                val scn = BarcodeScanning.getClient(opt)
                val ana = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280,720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                ana.setAnalyzer(exec) { proxy ->
                    if (!done) {
                        @androidx.camera.core.ExperimentalGetImage
                        val img = proxy.image
                        if (img != null) {
                            scn.process(InputImage.fromMediaImage(img, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes ->
                                    codes.firstOrNull()?.rawValue?.let { v ->
                                        if (!done) { done=true; onDetected(v) }
                                    }
                                }.addOnCompleteListener { proxy.close() }
                        } else proxy.close()
                    } else proxy.close()
                }
                try {
                    cp.unbindAll()
                    cp.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pre, ana)
                } catch (e: Exception) { e.printStackTrace() }
            }, ContextCompat.getMainExecutor(c))
        }
    })
}
