package com.feiyu.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class NodePos(val x: Float, val y: Float)

private fun regionColorOf(region: String): Color = when (region) {
    MemoryStore.REGION_HIPPO -> Color(0xFF6FAEFF)
    MemoryStore.REGION_NEO -> Color(0xFF63D6A0)
    MemoryStore.REGION_FLASH -> Color(0xFFFF7B93)
    else -> Color(0xFFB9C6D8)
}

@Composable
fun MemoryMapScreen(memory: MemoryStore, onClose: () -> Unit) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selected by remember { mutableStateOf<MemNode?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val intro = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        intro.animateTo(1f, androidx.compose.animation.core.tween(600))
    }

    // 布局：按脑区分簇，权重决定离簇心的距离（越重越靠内）
    val positions = remember(canvasSize, tick) {
        val map = HashMap<String, NodePos>()
        if (canvasSize.width == 0) return@remember map
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        val centers = mapOf(
            MemoryStore.REGION_HIPPO to Offset(w * 0.28f, h * 0.34f),
            MemoryStore.REGION_NEO to Offset(w * 0.72f, h * 0.34f),
            MemoryStore.REGION_FLASH to Offset(w * 0.5f, h * 0.70f)
        )
        val maxW = memory.nodes.values.maxOfOrNull { it.weight } ?: 1f
        val byRegion = memory.nodes.values.groupBy { it.region }
        for ((region, list) in byRegion) {
            val c = centers[region] ?: Offset(w / 2f, h / 2f)
            list.forEachIndexed { i, n ->
                val rnd = Random(n.id.hashCode())
                val angle = (i.toFloat() / list.size) * 6.2831853f + rnd.nextFloat() * 0.5f
                val norm = (n.weight / maxW).coerceIn(0f, 1f)
                val radius = (1.1f - norm) * (w * 0.16f) + w * 0.05f
                map[n.id] = NodePos(c.x + cos(angle) * radius, c.y + sin(angle) * radius)
            }
        }
        map
    }

    val regionCounts = remember(tick) { memory.regionCounts() }

    Column(Modifier.fillMaxSize().background(Color(0xFF070B16))) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                "返回",
                style = TextStyle(fontSize = 14.sp, color = Color(0xFF7FB2FF)),
                modifier = Modifier.tapGesture(onClose)
            )
            Spacer(Modifier.weight(1f))
            BasicText("记忆星图", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
            Spacer(Modifier.weight(1f))
            BasicText(
                "重置",
                style = TextStyle(fontSize = 14.sp, color = Color(0xFF9AA6B8)),
                modifier = Modifier.tapGesture {
                    scale = 1f
                    offset = Offset.Zero
                    tick++
                    selected = null
                }
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            LegendDot("海马 ${regionCounts[MemoryStore.REGION_HIPPO] ?: 0}", Color(0xFF6FAEFF))
            Spacer(Modifier.width(12.dp))
            LegendDot("新皮层 ${regionCounts[MemoryStore.REGION_NEO] ?: 0}", Color(0xFF63D6A0))
            Spacer(Modifier.width(12.dp))
            LegendDot("闪光 ${regionCounts[MemoryStore.REGION_FLASH] ?: 0}", Color(0xFFFF7B93))
            Spacer(Modifier.weight(1f))
            BasicText(
                "节点 ${memory.size} · 边 ${memory.edges.size}",
                style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA6B8))
            )
        }

        Box(
            Modifier.fillMaxWidth().weight(1f)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 4f)
                        offset += pan
                    }
                }
                .pointerInput(positions) {
                    detectTapGestures { tap ->
                        val local = (tap - offset) / scale
                        var best: MemNode? = null
                        var bestD = Float.MAX_VALUE
                        for (n in memory.nodes.values) {
                            val p = positions[n.id] ?: continue
                            val d = (Offset(p.x, p.y) - local).getDistance()
                            if (d < bestD) {
                                bestD = d
                                best = n
                            }
                        }
                        selected = if (best != null && bestD < 60f) best else null
                    }
                }
        ) {
            val selId = selected?.id
            Canvas(Modifier.fillMaxSize()) {
                // 星空背景
                val rnd = Random(42)
                repeat(120) {
                    val x = rnd.nextFloat() * size.width
                    val y = rnd.nextFloat() * size.height
                    val r = rnd.nextFloat() * 1.2f + 0.3f
                    drawCircle(Color.White.copy(alpha = 0.15f + rnd.nextFloat() * 0.3f), r, Offset(x, y))
                }

                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    // 边
                    for (e in memory.edges) {
                        val a = positions[e.from] ?: continue
                        val b = positions[e.to] ?: continue
                        val alpha = (0.06f + e.w * 0.22f).coerceAtMost(0.35f) * intro.value
                        drawLine(
                            color = Color(0xFF7FB2FF).copy(alpha = alpha),
                            start = Offset(a.x, a.y),
                            end = Offset(b.x, b.y),
                            strokeWidth = 1f
                        )
                    }
                    // 节点
                    val maxW = memory.nodes.values.maxOfOrNull { it.weight } ?: 1f
                    for (n in memory.nodes.values) {
                        val p = positions[n.id] ?: continue
                        val norm = (n.weight / maxW).coerceIn(0.1f, 1f)
                        val r = (3.5f + norm * 9f) * (0.4f + 0.6f * intro.value)
                        val c = regionColorOf(n.region)
                        val isSel = n.id == selId
                        // 光晕
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    c.copy(alpha = (if (isSel) 0.55f else 0.30f) * intro.value),
                                    Color.Transparent
                                ),
                                center = Offset(p.x, p.y),
                                radius = r * 3.2f
                            ),
                            radius = r * 3.2f,
                            center = Offset(p.x, p.y)
                        )
                        drawCircle(c, r, Offset(p.x, p.y))
                        if (isSel) {
                            drawCircle(
                                Color.White.copy(alpha = 0.9f),
                                r + 3f,
                                Offset(p.x, p.y),
                                style = Stroke(width = 1.6f)
                            )
                        }
                    }
                }
            }
        }

        // 选中卡片
        val sel = selected
        if (sel != null) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF101826))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        memory.regionLabel(sel.region),
                        style = TextStyle(fontSize = 10.sp, color = regionColorOf(sel.region))
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        "强度 ${sel.weight.toInt()} · 惊异 ${"%.2f".format(sel.surprise)}",
                        style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA6B8))
                    )
                    Spacer(Modifier.weight(1f))
                    BasicText(
                        "关闭",
                        style = TextStyle(fontSize = 11.sp, color = Color(0xFF7FB2FF)),
                        modifier = Modifier.tapGesture { selected = null }
                    )
                }
                Spacer(Modifier.height(8.dp))
                BasicText(sel.text, style = TextStyle(fontSize = 14.sp, color = Color(0xFFE6ECF5)))
            }
        } else {
            BasicText(
                "双指缩放 · 单指拖动 · 点节点看内容",
                style = TextStyle(fontSize = 11.sp, color = Color(0xFF9AA6B8)),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        BasicText(label, style = TextStyle(fontSize = 10.sp, color = Color(0xFF9AA6B8)))
    }
}

private fun Modifier.tapGesture(onTap: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(Unit) { detectTapGestures { onTap() } }
    )
