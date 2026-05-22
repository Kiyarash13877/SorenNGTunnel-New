package com.soreng.tunnel.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.soreng.tunnel.config.ConfigProfile
import com.soreng.tunnel.config.Protocol
import com.soreng.tunnel.ui.viewmodel.ConfigsViewModel
import com.soreng.tunnel.ui.theme.*

@Composable
fun ConfigsScreen(nav: NavController, vm: ConfigsViewModel = hiltViewModel()) {
    val configs  by vm.configs.collectAsState()
    val search   by vm.search.collectAsState()
    val feedback by vm.importFeedback.collectAsState()
    var showAdd  by remember { mutableStateOf(false) }
    var showSub  by remember { mutableStateOf(false) }
    var subUrl   by remember { mutableStateOf("") }
    var subName  by remember { mutableStateOf("") }

    LaunchedEffect(feedback) {
        if (feedback != null) { kotlinx.coroutines.delay(3000); vm.clearFeedback() }
    }

    Column(Modifier.fillMaxSize().background(Black)) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 16.dp),
            Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("CONFIGS", style = MaterialTheme.typography.headlineMedium, color = White, letterSpacing = 3.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { nav.navigate("qr_scan") }) {
                    Text("⊡", fontSize = 20.sp, color = GrayPale)
                }
                IconButton(onClick = { showAdd = !showAdd }) {
                    Text("+", fontSize = 24.sp, color = White, fontWeight = FontWeight.Bold)
                }
            }
        }

        OutlinedTextField(value = search, onValueChange = vm::setSearch,
            placeholder = { Text("search...", color = GrayMid, style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor=GrayDark, unfocusedBorderColor=BlackBorder,
                cursorColor=White, focusedTextColor=White, unfocusedTextColor=GrayPale,
                focusedContainerColor=BlackCard, unfocusedContainerColor=BlackCard),
            shape = RoundedCornerShape(8.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall)

        AnimatedVisibility(showAdd) {
            Row(Modifier.fillMaxWidth().padding(20.dp, 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionBtn("PASTE", "◫", Modifier.weight(1f)) { vm.importFromClipboard() }
                ActionBtn("MANUAL","✎", Modifier.weight(1f)) { nav.navigate("add_config") }
                ActionBtn("SUB",   "↓", Modifier.weight(1f)) { showSub = true }
            }
        }

        feedback?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Import")) GreenOk else RedAlert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }

        if (configs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◎", fontSize = 48.sp, color = GrayDark)
                    Spacer(Modifier.height(12.dp))
                    Text("NO CONFIGS", style = MaterialTheme.typography.headlineSmall,
                        color = GrayDark, letterSpacing = 3.sp)
                    Text("add a config to begin", style = MaterialTheme.typography.bodySmall, color = GrayMid)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)) {
                items(configs, key = { it.id }) { p ->
                    ConfigCard(p,
                        onSelect    = { vm.selectConfig(p) },
                        onDelete    = { vm.delete(p) },
                        onFavorite  = { vm.toggleFavorite(p.id) },
                        onPing      = { vm.testLatency(p) }
                    )
                }
            }
        }
    }

    if (showSub) AlertDialog(
        onDismissRequest = { showSub = false },
        containerColor   = BlackCard,
        title = { Text("Add Subscription", color = White, style = MaterialTheme.typography.titleMedium) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = subName, onValueChange = { subName = it },
                    label = { Text("Name", color = GrayMid) }, singleLine = true,
                    colors = outlinedColors(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = subUrl, onValueChange = { subUrl = it },
                    label = { Text("URL", color = GrayMid) }, singleLine = true,
                    colors = outlinedColors(), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = {
            if (subUrl.isNotBlank()) { vm.importSubscription(subUrl, subName); showSub = false }
        }) { Text("IMPORT", color = White) } },
        dismissButton = { TextButton(onClick = { showSub = false }) { Text("CANCEL", color = GrayMid) } }
    )
}

@Composable
private fun ActionBtn(label: String, icon: String, mod: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier = mod.height(48.dp), shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GrayDark),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GrayPale, containerColor = BlackCard)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 14.sp); Text(label, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigCard(p: ConfigProfile, onSelect: ()->Unit, onDelete: ()->Unit, onFavorite: ()->Unit, onPing: ()->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()
        .border(1.dp, if (p.isFavorite) GrayDark else BlackBorder, RoundedCornerShape(10.dp))
        .background(BlackCard, RoundedCornerShape(10.dp))
        .combinedClickable(onClick = onSelect, onLongClick = { expanded = !expanded })
        .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name.ifBlank { p.address },
                    style = MaterialTheme.typography.titleSmall, color = White, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.protocol.name, style = MaterialTheme.typography.labelSmall, color = GrayMid,
                        modifier = Modifier.border(1.dp,GrayDark,RoundedCornerShape(4.dp))
                            .padding(horizontal=5.dp, vertical=1.dp))
                    Text("${p.address}:${p.port}",
                        style = MaterialTheme.typography.bodySmall, color = GrayMid, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (p.isFavorite) Text("★", color = White, fontSize = 12.sp)
                if (p.latencyMs > 0) Text("${p.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = when { p.latencyMs < 100 -> GreenOk; p.latencyMs < 300 -> YellowWarn; else -> RedAlert })
            }
        }
        AnimatedVisibility(expanded) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((lbl, action) in listOf("★ FAV" to onFavorite, "⏱ PING" to onPing, "✕ DEL" to onDelete)) {
                    TextButton(onClick = { action(); expanded = false },
                        modifier = Modifier.weight(1f).border(1.dp, GrayDark, RoundedCornerShape(4.dp)),
                        colors = ButtonDefaults.textButtonColors(contentColor = GrayPale)) {
                        Text(lbl, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor=GrayDark, unfocusedBorderColor=BlackBorder,
    cursorColor=White, focusedTextColor=White, unfocusedTextColor=GrayPale,
    focusedContainerColor=BlackCard, unfocusedContainerColor=BlackCard)
