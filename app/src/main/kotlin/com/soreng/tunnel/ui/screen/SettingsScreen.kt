package com.soreng.tunnel.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.soreng.tunnel.ui.viewmodel.SettingsViewModel
import com.soreng.tunnel.ui.theme.*

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val autoStart    by vm.autoStart.collectAsState()
    val autoReconnect by vm.autoReconnect.collectAsState()
    val killSwitch   by vm.killSwitch.collectAsState()
    val fakeDns      by vm.fakeDns.collectAsState()
    val udp          by vm.udp.collectAsState()
    val ipv6         by vm.ipv6.collectAsState()
    val dnsPrimary   by vm.dnsPrimary.collectAsState()
    val dnsSecondary by vm.dnsSecondary.collectAsState()
    val bypassApps   by vm.bypassApps.collectAsState()
    var showSplit    by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Black)
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("SETTINGS", style = MaterialTheme.typography.headlineMedium,
            color = White, letterSpacing = 3.sp)
        Spacer(Modifier.height(20.dp))

        Section("CONNECTION") {
            Toggle("Auto Start on Boot",  autoStart,     vm::setAutoStart)
            Toggle("Auto Reconnect",       autoReconnect, vm::setAutoReconnect)
            Toggle("Kill Switch",          killSwitch,    vm::setKillSwitch)
        }
        Spacer(Modifier.height(14.dp))
        Section("TUNNEL") {
            Toggle("FakeDNS",     fakeDns, vm::setFakeDns)
            Toggle("UDP Forward", udp,     vm::setUdp)
            Toggle("IPv6",        ipv6,    vm::setIpv6)
        }
        Spacer(Modifier.height(14.dp))
        Section("DNS") {
            DnsInput("Primary DNS",   dnsPrimary,   vm::setDnsPrimary)
            DnsInput("Secondary DNS", dnsSecondary, vm::setDnsSecondary)
        }
        Spacer(Modifier.height(14.dp))
        Section("SPLIT TUNNEL") {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Bypass Apps", style = MaterialTheme.typography.bodyMedium, color = GrayPale)
                    Text("${bypassApps.size} app(s) excluded",
                        style = MaterialTheme.typography.bodySmall, color = GrayMid)
                }
                OutlinedButton(onClick = { showSplit = true },
                    border = BorderStroke(1.dp, GrayDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GrayPale)) {
                    Text("MANAGE", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Section("ABOUT") {
            InfoRow("Version",  "1.0.0")
            InfoRow("Core",     "Xray-core + Psiphon")
            InfoRow("Protocol", "tun2socks + SOCKS5")
        }
        Spacer(Modifier.height(80.dp))
    }

    if (showSplit) SplitTunnelDialog(vm, bypassApps) { showSplit = false }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()
        .border(1.dp, BlackBorder, RoundedCornerShape(10.dp))
        .background(BlackCard, RoundedCornerShape(10.dp))
        .padding(horizontal = 16.dp, vertical = 4.dp)) {
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = GrayMid, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        content()
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GrayPale)
        Switch(checked = value, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor=White, checkedTrackColor=GrayDark,
                uncheckedThumbColor=GrayMid, uncheckedTrackColor=BlackMid,
                uncheckedBorderColor=GrayDark))
    }
}

@Composable
private fun DnsInput(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GrayPale,
            modifier = Modifier.width(110.dp))
        OutlinedTextField(value = value, onValueChange = onChange,
            modifier = Modifier.weight(1f), singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor=GrayDark, unfocusedBorderColor=BlackBorder,
                cursorColor=White, focusedTextColor=White, unfocusedTextColor=GrayPale,
                focusedContainerColor=BlackMid, unfocusedContainerColor=BlackMid),
            shape = RoundedCornerShape(6.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GrayPale)
        Text(value,  style = MaterialTheme.typography.bodyMedium, color = GrayMid)
    }
}

@Composable
private fun SplitTunnelDialog(vm: SettingsViewModel, bypassed: Set<String>, onDismiss: () -> Unit) {
    val apps = remember { vm.getInstalledApps() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = BlackCard,
        title = { Text("Bypass Apps", color = White, style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(apps) { (pkg, name) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (pkg in bypassed) vm.removeBypassApp(pkg) else vm.addBypassApp(pkg)
                    }.padding(vertical = 6.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodySmall, color = White, maxLines = 1)
                            Text(pkg, style = MaterialTheme.typography.labelSmall, color = GrayMid, maxLines = 1)
                        }
                        Checkbox(checked = pkg in bypassed, onCheckedChange = { checked ->
                            if (checked) vm.addBypassApp(pkg) else vm.removeBypassApp(pkg)
                        }, colors = CheckboxDefaults.colors(
                            checkedColor = White, uncheckedColor = GrayMid, checkmarkColor = Black))
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("DONE", color = White) } }
    )
}
