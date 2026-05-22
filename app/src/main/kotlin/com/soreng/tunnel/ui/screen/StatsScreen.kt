package com.soreng.tunnel.ui.screen

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.soreng.tunnel.ui.viewmodel.StatsViewModel
import com.soreng.tunnel.ui.theme.*

@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val ulSpeed  by vm.uploadSpeed.collectAsState()
    val dlSpeed  by vm.downloadSpeed.collectAsState()
    val ping     by vm.ping.collectAsState()
    val ulTotal  by vm.uploadTotal.collectAsState()
    val dlTotal  by vm.downloadTotal.collectAsState()
    val history  by vm.history.collectAsState()
    val duration by vm.duration.collectAsState()

    Column(Modifier.fillMaxSize().background(Black)
        .verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("STATISTICS", style = MaterialTheme.typography.headlineMedium,
            color = White, letterSpacing = 3.sp)
        Spacer(Modifier.height(20.dp))

        // Live graph
        Text("LIVE TRAFFIC", style = MaterialTheme.typography.labelSmall, color = GrayMid, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        LiveGraph(history, Modifier.fillMaxWidth().height(140.dp))
        Spacer(Modifier.height(20.dp))

        // Grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("UPLOAD",   vm.fmt(ulSpeed), "▲", Modifier.weight(1f))
            StatCard("DOWNLOAD", vm.fmt(dlSpeed), "▼", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("PING",    if (ping < 0) "—" else "${ping}ms", "◈", Modifier.weight(1f))
            StatCard("SESSION", duration,                            "⏱", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("TOTAL ▲", vm.fmtB(ulTotal), "∑", Modifier.weight(1f))
            StatCard("TOTAL ▼", vm.fmtB(dlTotal), "∑", Modifier.weight(1f))
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: String, mod: Modifier) {
    Column(mod.border(1.dp, BlackBorder, RoundedCornerShape(10.dp))
        .background(BlackCard, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, fontSize = 11.sp, color = GrayMid)
            Text(label, style = MaterialTheme.typography.labelSmall, color = GrayMid, letterSpacing = 1.sp)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LiveGraph(history: List<Pair<Long,Long>>, modifier: Modifier) {
    Box(modifier.border(1.dp, BlackBorder, RoundedCornerShape(10.dp))
        .background(BlackCard, RoundedCornerShape(10.dp)).padding(10.dp)) {
        if (history.size >= 2) {
            val maxVal = history.maxOfOrNull { maxOf(it.first,it.second) }?.coerceAtLeast(1L) ?: 1L
            Canvas(Modifier.fillMaxSize()) {
                val w=size.width; val h=size.height; val n=history.size
                for (i in 1..3) drawLine(GrayDark.copy(0.3f), Offset(0f,h*i/4f), Offset(w,h*i/4f), 1f)
                val dlFill=Path().also { path ->
                    path.moveTo(0f,h)
                    history.forEachIndexed { i, (_,dl) ->
                        val x=i.toFloat()/(n-1)*w; val y=h-dl.toFloat()/maxVal*h
                        path.lineTo(x,y)
                    }
                    path.lineTo(w,h); path.close()
                }
                drawPath(dlFill, Brush.verticalGradient(0f to White.copy(0.12f), 1f to Color.Transparent))
                val dl=Path()
                history.forEachIndexed{i,(_,d)->{val x=i.toFloat()/(n-1)*w;val y=h-d.toFloat()/maxVal*h;if(i==0)dl.moveTo(x,y)else dl.lineTo(x,y)}}
                drawPath(dl, White, style=Stroke(2f,cap=StrokeCap.Round))
                val ul=Path()
                history.forEachIndexed{i,(u,_)->{val x=i.toFloat()/(n-1)*w;val y=h-u.toFloat()/maxVal*h;if(i==0)ul.moveTo(x,y)else ul.lineTo(x,y)}}
                drawPath(ul, GrayMid, style=Stroke(1.5f,cap=StrokeCap.Round))
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(2.dp), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                Box(Modifier.size(8.dp,2.dp).background(White)); Text("▼",fontSize=9.sp,color=GrayPale)
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){
                Box(Modifier.size(8.dp,2.dp).background(GrayMid)); Text("▲",fontSize=9.sp,color=GrayMid)
            }
        }
    }
}
