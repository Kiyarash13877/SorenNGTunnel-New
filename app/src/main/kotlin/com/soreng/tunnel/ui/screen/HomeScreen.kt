package com.soreng.tunnel.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.soreng.tunnel.ui.viewmodel.HomeViewModel
import com.soreng.tunnel.vpn.VpnConnectionState
import com.soreng.tunnel.ui.theme.*

@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = hiltViewModel()) {
    val state    by vm.state.collectAsState()
    val ulSpeed  by vm.uploadSpeed.collectAsState()
    val dlSpeed  by vm.downloadSpeed.collectAsState()
    val ping     by vm.ping.collectAsState()
    val ulTotal  by vm.uploadTotal.collectAsState()
    val dlTotal  by vm.downloadTotal.collectAsState()
    val selected by vm.selected.collectAsState()
    val history  by vm.history.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("SOREN NG", style = MaterialTheme.typography.headlineLarge, color = White,
                    fontWeight = FontWeight.Black)
                Text("TUNNEL v1.0", style = MaterialTheme.typography.labelSmall,
                    color = GrayMid, letterSpacing = 3.sp)
            }
            Box(Modifier.size(8.dp).background(
                if (state is VpnConnectionState.Connected) GreenOk else GrayDark,
                CircleShape))
        }
        Spacer(Modifier.height(12.dp))

        // Selected config chip
        Box(Modifier.fillMaxWidth()
            .border(1.dp, BlackBorder, RoundedCornerShape(8.dp))
            .background(BlackCard, RoundedCornerShape(8.dp))
            .clickable { nav.navigate("configs") }
            .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(selected?.name?.ifBlank { selected?.address } ?: "— TAP TO SELECT CONFIG —",
                    style = MaterialTheme.typography.bodyMedium, color = GrayPale, maxLines = 1,
                    modifier = Modifier.weight(1f))
                if (selected != null)
                    Text(selected!!.protocol.name, style = MaterialTheme.typography.labelSmall,
                        color = GrayMid, modifier = Modifier
                            .border(1.dp, GrayDark, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(24.dp))

        // Connect button
        ConnectButton(state) {
            if (state.isActive) vm.disconnect()
            else selected?.let { vm.connect(it.id) }
        }
        Spacer(Modifier.height(20.dp))

        // Status
        val statusColor = when (state) {
            is VpnConnectionState.Connected   -> GreenOk
            is VpnConnectionState.Connecting,
            is VpnConnectionState.Disconnecting-> YellowWarn
            is VpnConnectionState.Error        -> RedAlert
            else                               -> GrayMid
        }
        Text(state.label, style = MaterialTheme.typography.labelMedium,
            color = statusColor, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        if (state is VpnConnectionState.Error)
            Text((state as VpnConnectionState.Error).message.take(80),
                style = MaterialTheme.typography.bodySmall, color = RedAlert.copy(alpha=0.8f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(top=4.dp))
        Spacer(Modifier.height(24.dp))

        // Speed row
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            StatChip("▲ UP",   vm.fmt(ulSpeed))
            StatChip("▼ DOWN", vm.fmt(dlSpeed))
            StatChip("◈ PING", if (ping < 0) "—" else "${ping}ms")
        }
        Spacer(Modifier.height(16.dp))

        // Live graph
        if (history.isNotEmpty()) TrafficGraph(history)
        Spacer(Modifier.height(16.dp))

        // Totals
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL ▲", style = MaterialTheme.typography.labelSmall, color = GrayMid, letterSpacing = 1.sp)
                Text(vm.fmtB(ulTotal), style = MaterialTheme.typography.bodyMedium, color = GrayPale)
            }
            Box(Modifier.width(1.dp).height(36.dp).background(BlackBorder))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOTAL ▼", style = MaterialTheme.typography.labelSmall, color = GrayMid, letterSpacing = 1.sp)
                Text(vm.fmtB(dlTotal), style = MaterialTheme.typography.bodyMedium, color = GrayPale)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun ConnectButton(state: VpnConnectionState, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label="ring")
    val rot by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(3000, easing=LinearEasing)), label="rot")
    val pulse by inf.animateFloat(0.95f, 1.05f,
        infiniteRepeatable(tween(1200, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    val glow by inf.animateFloat(0.3f, 0.9f,
        infiniteRepeatable(tween(1500, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="glow")

    val ringColor = when (state) {
        is VpnConnectionState.Connected    -> GreenOk
        is VpnConnectionState.Connecting,
        is VpnConnectionState.Disconnecting-> YellowWarn
        is VpnConnectionState.Error        -> RedAlert
        else -> GrayDark
    }
    val isConnected   = state is VpnConnectionState.Connected
    val isTransitioning = state is VpnConnectionState.Connecting || state is VpnConnectionState.Disconnecting

    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val r = size.minDimension / 2f
            drawCircle(ringColor.copy(alpha = if (isConnected) glow*0.2f else 0.05f),
                radius = r, style = Stroke(width = 24.dp.toPx()))
            if (isTransitioning) {
                drawArc(ringColor, rotationAngle = rot, startAngle = rot,
                    sweepAngle = 120f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            } else if (isConnected) {
                drawCircle(ringColor.copy(alpha = glow*0.5f),
                    radius = r - 12.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            }
        }
        Button(
            onClick = onClick,
            modifier = Modifier.size(160.dp).scale(if (isConnected) pulse else 1f),
            shape  = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = when (state) {
                    is VpnConnectionState.Connected   -> Color(0xFF001A00)
                    is VpnConnectionState.Connecting  -> Color(0xFF1A1A00)
                    is VpnConnectionState.Error       -> Color(0xFF1A0000)
                    else -> BlackCard
                },
                contentColor = White
            ),
            border    = BorderStroke(if (isConnected) 2.dp else 1.dp,
                ringColor.copy(alpha = if (isConnected) glow else 0.5f)),
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (state) {
                    is VpnConnectionState.Connected    -> "◉"
                    is VpnConnectionState.Connecting,
                    is VpnConnectionState.Disconnecting-> "◌"
                    is VpnConnectionState.Error        -> "✗"
                    else -> "◎"
                }, fontSize = 36.sp, color = ringColor)
                Spacer(Modifier.height(4.dp))
                Text(when (state) {
                    is VpnConnectionState.Connected    -> "DISCONNECT"
                    is VpnConnectionState.Connecting   -> "CANCEL"
                    is VpnConnectionState.Disconnecting-> "STOPPING"
                    is VpnConnectionState.Error        -> "RETRY"
                    else -> "CONNECT"
                }, style = MaterialTheme.typography.labelSmall, color = GrayPale, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .border(1.dp, BlackBorder, RoundedCornerShape(8.dp))
            .background(BlackCard, RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GrayMid, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrafficGraph(history: List<Pair<Long,Long>>) {
    val maxVal = history.maxOfOrNull { maxOf(it.first, it.second) }?.coerceAtLeast(1L) ?: 1L
    Box(Modifier.fillMaxWidth().height(90.dp)
        .border(1.dp, BlackBorder, RoundedCornerShape(8.dp))
        .background(BlackCard, RoundedCornerShape(8.dp))
        .padding(8.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val n = history.size
            if (n < 2) return@Canvas
            for (i in 1..3) drawLine(GrayDark.copy(0.25f),
                Offset(0f, h*i/4f), Offset(w, h*i/4f), strokeWidth = 1f)
            val dlPath = Path()
            history.forEachIndexed { i, (_, dl) ->
                val x = i.toFloat()/(n-1)*w; val y = h-(dl.toFloat()/maxVal*h)
                if (i==0) dlPath.moveTo(x,y) else dlPath.lineTo(x,y)
            }
            drawPath(dlPath, White, style = Stroke(2f, cap = StrokeCap.Round))
            val ulPath = Path()
            history.forEachIndexed { i, (ul, _) ->
                val x = i.toFloat()/(n-1)*w; val y = h-(ul.toFloat()/maxVal*h)
                if (i==0) ulPath.moveTo(x,y) else ulPath.lineTo(x,y)
            }
            drawPath(ulPath, GrayMid, style = Stroke(1.5f, cap = StrokeCap.Round))
        }
    }
}
