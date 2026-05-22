package com.soreng.tunnel.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.soreng.tunnel.ui.viewmodel.ConfigsViewModel
import com.soreng.tunnel.ui.theme.*

@Composable
fun AddConfigScreen(nav: NavController, vm: ConfigsViewModel = hiltViewModel()) {
    var rawUri   by remember { mutableStateOf("") }
    val feedback by vm.importFeedback.collectAsState()

    LaunchedEffect(feedback) {
        if (feedback != null) {
            kotlinx.coroutines.delay(2000)
            vm.clearFeedback()
            if (feedback?.startsWith("Imported") == true) nav.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize().background(Black).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ nav.popBackStack() }) {
                Text("← BACK", style = MaterialTheme.typography.labelSmall,
                    color = GrayPale, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("ADD CONFIG", style = MaterialTheme.typography.titleMedium,
                color = White, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))

        Text("PASTE URI / JSON", style = MaterialTheme.typography.labelSmall,
            color = GrayMid, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = rawUri, onValueChange = { rawUri = it },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = { Text("vmess://...\nvless://...\ntrojan://...\nss://...",
                color = GrayDark, style = MaterialTheme.typography.bodySmall) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor=GrayDark, unfocusedBorderColor=BlackBorder,
                cursorColor=White, focusedTextColor=White, unfocusedTextColor=GrayPale,
                focusedContainerColor=BlackCard, unfocusedContainerColor=BlackCard),
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))

        Button(onClick = { if (rawUri.isNotBlank()) vm.importUris(rawUri) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GrayDark, contentColor = White)) {
            Text("IMPORT", style = MaterialTheme.typography.labelLarge, letterSpacing = 3.sp)
        }

        feedback?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Imported")) GreenOk else RedAlert)
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { nav.navigate("qr_scan") },
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GrayDark),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GrayPale)) {
                Text("⊡ QR SCAN", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            }
            OutlinedButton(onClick = { vm.importFromClipboard() },
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GrayDark),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GrayPale)) {
                Text("◫ PASTE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            }
        }
    }
}
