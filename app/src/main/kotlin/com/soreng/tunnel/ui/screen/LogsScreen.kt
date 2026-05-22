package com.soreng.tunnel.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.soreng.tunnel.ui.viewmodel.LogsViewModel
import com.soreng.tunnel.ui.theme.*

@Composable
fun LogsScreen(vm: LogsViewModel = hiltViewModel()) {
    val logs by vm.logs.collectAsState()
    val lsState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) lsState.animateScrollToItem(logs.size - 1)
    }

    Column(Modifier.fillMaxSize().background(Black).padding(horizontal=16.dp, vertical=12.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("LOGS", style = MaterialTheme.typography.headlineMedium,
                color = White, letterSpacing = 3.sp)
            TextButton(onClick = vm::clearLogs) {
                Text("CLEAR", style = MaterialTheme.typography.labelSmall,
                    color = GrayMid, letterSpacing = 2.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("No logs", style = MaterialTheme.typography.bodySmall, color = GrayMid)
            }
        } else {
            LazyColumn(state = lsState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(logs) { entry ->
                    Text(entry, style = MaterialTheme.typography.labelSmall,
                        color = when {
                            entry.contains("ERROR",true) -> RedAlert
                            entry.contains("WARN", true) -> YellowWarn
                            entry.contains("OK",   true) -> GreenOk
                            else -> GrayPale
                        },
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 10.sp, lineHeight = 14.sp)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
