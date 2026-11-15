package com.feiyu.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MemoryScreen(memory: MemoryStore, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    val counts = remember(tick) { memory.regionCounts() }

    Column(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEDF4FF), Color(0xFFF8FBFF), Color(0xFFFFFFFF))
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt("返回", size = 14.sp, color = Color(0xFF2F7FE0), modifier = Modifier.tap(onClose))
            Spacer(Modifier.weight(1f))
            Txt("记忆工作台", size = 16.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Txt(
                "清空", size = 14.sp, color = Ui.AccentDeep,
                modifier = Modifier.tap { memory.clear(); tick++ }
            )
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Txt(
                "节点 ${memory.size} · 边 ${memory.edges.size} · " +
                    "海马 ${counts["hippocampus"] ?: 0} · " +
                    "新皮层 ${counts["neocortex"] ?: 0} · " +
                    "闪光 ${counts["flash"] ?: 0}",
                size = 12.sp, color = Color(0xFF5B6B85)
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Ui.ChipRadius))
                    .background(Color.White).padding(10.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF16202C)),
                    maxLines = 2
                )
                if (query.isEmpty()) {
                    Txt("搜一段记忆，或留空按权重浏览", size = 14.sp, color = Color(0xFF9AA6B8))
                }
            }
        }

        val hits: List<Pair<MemNode, Float>> = remember(query, tick) {
            if (query.isBlank()) {
                memory.nodes.values.sortedByDescending { it.weight }.take(40).map { it to it.weight }
            } else {
                memory.retrieve(query, 30)
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(14.dp)) {
            items(hits.size) { i ->
                val (n, score) = hits[i]
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(Ui.ChipRadius))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(regionColor(n.region).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Txt(memory.regionLabel(n.region), size = 10.sp, color = regionColor(n.region))
                        }
                        Spacer(Modifier.width(8.dp))
                        Txt("强度 ${n.weight.toInt()}", size = 10.sp, color = Color(0xFF5B6B85))
                        Spacer(Modifier.width(8.dp))
                        Txt("惊异 ${"%.2f".format(n.surprise)}", size = 10.sp, color = Color(0xFF5B6B85))
                        Spacer(Modifier.weight(1f))
                        Txt("相关 ${"%.2f".format(score)}", size = 10.sp, color = Color(0xFF8A96A8))
                    }
                    Spacer(Modifier.height(6.dp))
                    Txt(n.text, size = 14.sp)
                }
            }
            if (hits.isEmpty()) {
                item { Txt("还没有记忆。先聊几句，系统会自动编码。", size = 13.sp, color = Color(0xFF5B6B85)) }
            }
        }
    }
}

fun regionColor(region: String): Color = when (region) {
    MemoryStore.REGION_HIPPO -> Color(0xFF2F7FE0)
    MemoryStore.REGION_NEO -> Color(0xFF3C9A6E)
    MemoryStore.REGION_FLASH -> Color(0xFFD14343)
    else -> Color(0xFF5B6B85)
}
@Composable
private fun ClearConfirmDialog(
    title: String,
    desc: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Color.White).padding(20.dp)
        ) {
            Txt(title, size = 16.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Txt(desc, size = 13.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Chip("取消") { onDismiss() } }
                Box(Modifier.weight(1f)) { Chip("确认清空", danger = true) { onConfirm() } }
            }
        }
    }
}

@Composable
fun ColumnScope.SettingsScreen(
    backdrop: LayerBackdrop,
    settings: Settings,
    onChange: (Settings) -> Unit,
    onCropWallpaper: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onProactiveNow: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRelation: () -> Unit,
    onClearChat: () -> Unit,
    onClearFeed: () -> Unit,
    onClearMemory: () -> Unit
) {
    var pendingClear by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 112.dp, bottom = 120.dp)
    ) {
        Section(backdrop, settings, "接口 API") {
            Field("接口地址", settings.baseUrl) { onChange(settings.copy(baseUrl = it)) }
            Field("API Key", settings.apiKey, password = true) {
                onChange(settings.copy(apiKey = it.trim()))
            }
            Txt(
                "当前 Key 长度 ${settings.apiKey.trim().length}（DeepSeek 官方一般 35 位，含 sk-）",
                size = 11.sp, color = Color(0xFF8A96A8)
            )
            Spacer(Modifier.height(8.dp))
            var testMsg by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            Chip(if (testMsg.isEmpty()) "测试连接" else "再测一次") {
                scope.launch { testMsg = testConnection(settings) }
            }
            if (testMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Txt(testMsg, size = 11.sp, color = Color(0xFF2F7FE0))
            }
            Spacer(Modifier.height(6.dp))
            Txt(
                "安装包不内置任何接口与密钥，请填写你自己的 OpenAI 兼容地址，例如 https://your-api.example/v1",
                size = 11.sp, color = Color(0xFF5B6B85)
            )
        }

        Section(backdrop, settings, "模型") {
            Field("模型名", settings.model) { onChange(settings.copy(model = it)) }
            Spacer(Modifier.height(4.dp))
            Field("创作模型（留空=与聊天相同）", settings.creativeModel) {
                onChange(settings.copy(creativeModel = it.trim()))
            }
            Txt(
                "留空则与聊天共用。建议聊天用 deepseek-flash，创作填 deepseek-chat。",
                size = 11.sp, color = Color(0xFF8A96A8)
            )
            Spacer(Modifier.height(4.dp))
            Txt("看图配置（可独立于文本接口）", size = 12.sp, weight = FontWeight.Medium, color = Color(0xFF25314A))
            Spacer(Modifier.height(6.dp))
            Field("视觉接口地址", settings.visionBase) {
                onChange(settings.copy(visionBase = it.trim()))
            }
            Field("视觉 Key", settings.visionKey, password = true) {
                onChange(settings.copy(visionKey = it.trim()))
            }
            Field("视觉模型", settings.visionModel) {
                onChange(settings.copy(visionModel = it.trim()))
            }
            Txt(
                "例：地址 https://open.bigmodel.cn/api/paas/v4，模型 glm-4v-flash（免费）。三项留空则复用上面的文本接口。",
                size = 11.sp, color = Color(0xFF8A96A8)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("deepseek-flash") { onChange(settings.copy(model = "deepseek-flash")) }
                Chip("deepseek-reasoner") { onChange(settings.copy(model = "deepseek-reasoner")) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("V4.1-Flash") { onChange(settings.copy(model = "deepseek-ai/DeepSeek-V4.1-Flash")) }
                Chip("Qwen3.5-35B") { onChange(settings.copy(model = "Qwen/Qwen3.5-35B-A3B")) }
            }
            Spacer(Modifier.height(6.dp))
            Txt("模型名区分大小写，必须与平台完全一致（如 deepseek-chat 全小写）", size = 11.sp, color = Color(0xFF8A96A8))
        }

        Section(backdrop, settings, "身份") {
            Field("她的名字", settings.botName) { onChange(settings.copy(botName = it)) }
            Field("你的名字", settings.userName) { onChange(settings.copy(userName = it)) }
            AvatarRow("她的头像", isBot = true, settings = settings, onChange = onChange)
            AvatarRow("你的头像", isBot = false, settings = settings, onChange = onChange)
        }

        Section(backdrop, settings, "外观") {
            WallpaperRow(settings = settings, onChange = onChange, onCrop = onCropWallpaper)
        }

        Section(backdrop, settings, "通用设置") {
            Field("人格", settings.persona, lines = 4) {
                onChange(settings.copy(persona = it))
            }
            Field("规则", settings.rules, lines = 3) {
                onChange(settings.copy(rules = it))
            }
            SwitchRow("显示时间", settings.showTime) {
                onChange(settings.copy(showTime = it))
            }
            SwitchRow("流式输出（逐字显示）", settings.stream) {
                onChange(settings.copy(stream = it))
            }
            SwitchRow("由她决定分段（推荐）", settings.aiSegment) {
                onChange(settings.copy(aiSegment = it))
            }
            SwitchRow("分段回复（1～3 条，仅非流式时生效）", settings.segmentReply) {
                onChange(settings.copy(segmentReply = it))
            }
            if (settings.segmentReply) {
                Spacer(Modifier.height(4.dp))
                Txt(
                    "每条间隔 ${"%.1f".format(settings.segmentGap / 1000f)} 秒",
                    size = 12.sp, color = Color(0xFF5B6B85)
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1500, 2000, 2500, 3000, 4000).forEach { gap ->
                        Chip(
                            "${"%.1f".format(gap / 1000f)}s",
                            danger = settings.segmentGap == gap
                        ) { onChange(settings.copy(segmentGap = gap)) }
                    }
                }
            }
            SwitchRow("AI 判断记忆权重", settings.aiMemory) {
                onChange(settings.copy(aiMemory = it))
            }
            SwitchRow("气泡毛玻璃（关掉更流畅）", settings.bubbleGlass) {
                onChange(settings.copy(bubbleGlass = it))
            }
            SwitchRow("自动生成动态（每 ${settings.feedHours} 小时）", settings.autoFeed) {
                onChange(settings.copy(autoFeed = it))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("- 1 小时") {
                    onChange(settings.copy(feedHours = (settings.feedHours - 1).coerceAtLeast(1)))
                }
                Chip("+ 1 小时") {
                    onChange(settings.copy(feedHours = (settings.feedHours + 1).coerceAtMost(48)))
                }
            }
        }

        Section(backdrop, settings, "插件") {
            SwitchRow("液态玻璃特效", settings.glass) {
                onChange(settings.copy(glass = it))
            }
        }

        Section(backdrop, settings, "主动") {
            val appCtx = LocalContext.current
            SwitchRow("静默时段（不主动打扰）", settings.quietOn) {
                onChange(settings.copy(quietOn = it))
            }
            if (settings.quietOn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("开始", size = 12.sp, color = Color(0xFF5B6B85))
                    Spacer(Modifier.width(6.dp))
                    Chip("-", danger = false) {
                        onChange(settings.copy(quietStart = (settings.quietStart + 23) % 24))
                    }
                    Spacer(Modifier.width(4.dp))
                    Txt("%02d:00".format(settings.quietStart), size = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Chip("+", danger = false) {
                        onChange(settings.copy(quietStart = (settings.quietStart + 1) % 24))
                    }
                    Spacer(Modifier.weight(1f))
                    Txt("结束", size = 12.sp, color = Color(0xFF5B6B85))
                    Spacer(Modifier.width(6.dp))
                    Chip("-", danger = false) {
                        onChange(settings.copy(quietEnd = (settings.quietEnd + 23) % 24))
                    }
                    Spacer(Modifier.width(4.dp))
                    Txt("%02d:00".format(settings.quietEnd), size = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Chip("+", danger = false) {
                        onChange(settings.copy(quietEnd = (settings.quietEnd + 1) % 24))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Txt(
                    "这段时间内她不会主动发消息，跨午夜也支持",
                    size = 11.sp, color = Color(0xFF8A96A8)
                )
            }

            SwitchRow("主动找我聊天", settings.proactive) {
                onChange(settings.copy(proactive = it))
                if (it) Proactive.schedule(appCtx) else Proactive.cancel(appCtx)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("立刻试一条") { onProactiveNow() }
                Chip("测试通知") {
                    Proactive.notify(appCtx, "这是一条测试通知，能看到就说明权限和渠道都正常。")
                }
            }
            if (settings.proactive) {
                Txt("安静 ${settings.proactiveMin} 分钟后主动发一条", size = 11.sp, color = Color(0xFF5B6B85))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 120).forEach { m ->
                        Chip(if (m >= 60) "${m / 60}h" else "${m}m", danger = settings.proactiveMin == m) {
                            onChange(settings.copy(proactiveMin = m))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            SwitchRow("每天自动写日记", settings.autoDiary) {
                onChange(settings.copy(autoDiary = it))
            }
            if (settings.autoDiary) {
                Txt("每天 ${settings.diaryAt}:00 之后自动写", size = 11.sp, color = Color(0xFF5B6B85))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20, 21, 22, 23).forEach { h ->
                        Chip("${h}:00", danger = settings.diaryAt == h) {
                            onChange(settings.copy(diaryAt = h))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Txt("需要 App 在前台或后台存活时生效", size = 11.sp, color = Color(0xFF8A96A8))
        }

        Section(backdrop, settings, "备份") {
            val ctx = LocalContext.current
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri: android.net.Uri? -> if (uri != null) onExport(uri) }
            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: android.net.Uri? -> if (uri != null) onImport(uri) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("导出备份") {
                    exportLauncher.launch("whalechat-backup.json")
                }
                Chip("导入备份") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
            Spacer(Modifier.height(6.dp))
            Txt(
                "备份包含：聊天记录、日记、动态、记忆图谱、全部设置。导入会覆盖当前数据。",
                size = 11.sp, color = Color(0xFF8A96A8)
            )
        }

        Section(backdrop, settings, "记忆") {
            Txt("多重记忆系统：脑区分区 + 有向图 + 双通道检索", size = 11.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(8.dp))
            SwitchRow("相关性重排（官方模型）", settings.rerank) {
                onChange(settings.copy(rerank = it))
            }
            Spacer(Modifier.height(8.dp))
            Chip("打开记忆工作台") { onOpenMemory() }
            Spacer(Modifier.height(10.dp))
            LocalModelRow()
            Spacer(Modifier.height(10.dp))
            Txt("清空数据", size = 12.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("清空聊天", danger = true) { pendingClear = "chat" }
                Chip("清空动态", danger = true) { pendingClear = "feed" }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("清空记忆库", danger = true) { pendingClear = "memory" }
            }
        }

        Section(backdrop, settings, "相处") {
            Txt("活跃度与亲密度", size = 11.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(8.dp))
            Chip("查看相处记录") { onOpenRelation() }
        }

        Section(backdrop, settings, "成就") {
            Txt("记录你和她的点点滴滴", size = 11.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(8.dp))
            Chip("查看成就") { onOpenAchievements() }
        }

        pendingClear?.let { what ->
            val pair: Pair<String, String> = when (what) {
                "chat" -> "清空全部聊天？" to "聊天记录会全部删除，且无法恢复。记忆和日记不受影响。"
                "feed" -> "清空全部动态？" to "她发过的动态会全部删除，且无法恢复。"
                else -> "清空记忆库？" to "她会忘掉记住的所有事情（脑区、权重、图谱），且无法恢复。聊天记录不受影响。"
            }
            val (title, desc) = pair
            ClearConfirmDialog(
                title = title,
                desc = desc,
                onDismiss = { pendingClear = null },
                onConfirm = {
                    when (what) {
                        "chat" -> onClearChat()
                        "feed" -> onClearFeed()
                        else -> onClearMemory()
                    }
                    pendingClear = null
                }
            )
        }

        Section(backdrop, settings, "关于") {
            Txt("WhaleChat", weight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Txt("版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）", size = 12.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(4.dp))
            Txt("开发者：雪绒于冬", size = 12.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(4.dp))
            Txt("联系方式：mahjonged85@gmail.com", size = 12.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.height(10.dp))
            Chip("更新日志") { onOpenLog() }
            Spacer(Modifier.height(6.dp))
            Txt("Kotlin · Jetpack Compose · Liquid Glass", size = 11.sp, color = Color(0xFF8A96A8))
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun AvatarRow(
    label: String,
    isBot: Boolean,
    settings: Settings,
    onChange: (Settings) -> Unit
) {
    val ctx = LocalContext.current
    val path = if (isBot) settings.botAvatar else settings.userAvatar
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val file = File(ctx.filesDir, if (isBot) "avatar_bot.bin" else "avatar_user.bin")
                if (!saveDownscaledImage(ctx, uri, file, 512, 88)) return@runCatching
                onChange(
                    if (isBot) settings.copy(botAvatar = file.absolutePath + "?t=" + System.currentTimeMillis())
                    else settings.copy(userAvatar = file.absolutePath + "?t=" + System.currentTimeMillis()).also {
                        Achievements.bump(ctx, "photo")
                    }
                )
            }
        }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(label, size = 14.sp, modifier = Modifier.weight(1f))
        ChatAvatar(isBot = isBot, settings = settings)
        Spacer(Modifier.width(10.dp))
        Chip(if (path.isEmpty()) "选图片" else "换一张") {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}

@Composable
fun WallpaperCrop(
    bitmap: android.graphics.Bitmap,
    initialScale: Float,
    initialX: Float,
    initialY: Float,
    onCancel: () -> Unit,
    onDone: (Float, Float, Float) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        var scale by remember { mutableFloatStateOf(initialScale.coerceIn(1f, 4f)) }
        var off by remember { mutableStateOf(Offset(initialX * w, initialY * h)) }

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = off.x
                    translationY = off.y
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        off += pan
                    }
                }
        )

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Txt("取消", size = 15.sp, color = Color.White, modifier = Modifier.tap(onCancel))
            Txt(
                "确定", size = 15.sp, weight = FontWeight.SemiBold, color = Color.White,
                modifier = Modifier.tap { onDone(scale, off.x / w, off.y / h) }
            )
        }
        Txt(
            "双指缩放 · 单指拖动",
            size = 12.sp, color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp)
        )
    }
}

@Composable
fun WallpaperRow(settings: Settings, onChange: (Settings) -> Unit, onCrop: (String) -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val file = File(ctx.filesDir, "wallpaper.bin")
                if (!saveDownscaledImage(ctx, uri, file, 1600, 88)) return@runCatching
                Achievements.bump(ctx, "photo")
                onCrop(file.absolutePath)
            }
        }
    }
    val preview = remember(settings.wallpaper) {
        if (settings.wallpaper.isNotEmpty())
            decodeSampledBitmap(realPath(settings.wallpaper), 720)
        else null
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt("自定义壁纸", size = 14.sp, modifier = Modifier.weight(1f))
        if (preview != null) {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(width = 40.dp, height = 64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(8.dp))
        }
        Chip(if (preview == null) "选图片" else "换一张") {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
    if (preview != null) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("调整大小位置") { onCrop(realPath(settings.wallpaper)) }
            Chip("恢复默认背景", danger = true) { onChange(settings.copy(wallpaper = "")) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Txt("壁纸会成为毛玻璃采样的底层，换完立刻生效", size = 11.sp, color = Color(0xFF8A96A8))
}

@Composable
fun LogScreen(onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lines = remember {
        runCatching {
            ctx.assets.open("CHANGELOG.md").bufferedReader().use { it.readLines() }
                .filter { it.startsWith("| ") && it.count { c -> c == '|' } >= 3 }
                .filterNot { it.contains("---") || it.contains("版本 |") }
                .map { row ->
                    val cells = row.trim().trim('|').split("|").map { it.trim() }
                    if (cells.size >= 3) "v${cells[0]}  ${cells[2]}" else row
                }
        }.getOrDefault(listOf("暂无更新日志"))
    }

    Column(
        Modifier.fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFEDF4FF), Color(0xFFFFFFFF))
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt("返回", size = 14.sp, color = Ui.AccentDeep, modifier = Modifier.tap(onClose))
            Spacer(Modifier.weight(1f))
            Txt("更新日志", size = 16.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(28.dp))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            items(lines.size) { i ->
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(Ui.ChipRadius))
                        .background(Color.White)
                        .padding(12.dp)
                ) {
                    Txt(lines[i], size = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AchievementsScreen(stats: Map<String, Int>, onClose: () -> Unit) {
    val unlocked = Achievements.unlockedCount(stats)

    Column(
        Modifier.fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFFEDF4FF), Color(0xFFFFFFFF))
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Txt("返回", size = 14.sp, color = Ui.AccentDeep, modifier = Modifier.tap(onClose))
            Spacer(Modifier.weight(1f))
            Txt("成就", size = 16.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Txt("$unlocked/${Achievements.all.size}", size = 12.sp, color = Color(0xFF5B6B85))
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            items(Achievements.all.size) { i ->
                val a = Achievements.all[i]
                val value = stats[a.key] ?: 0
                val done = value >= a.target
                val ratio = (value.toFloat() / a.target).coerceIn(0f, 1f)

                // 入场：逐条上浮淡入
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(i * 45L)
                    appeared = true
                }
                val enterAlpha by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = tween(260), label = "achAlpha"
                )
                val enterY by animateFloatAsState(
                    targetValue = if (appeared) 0f else 24f,
                    animationSpec = tween(320), label = "achY"
                )
                val barRatio by animateFloatAsState(
                    targetValue = if (appeared) ratio else 0f,
                    animationSpec = tween(700), label = "achBar"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (appeared && done) 1f else if (appeared) 0.92f else 0.6f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
                    label = "achIcon"
                )

                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        .graphicsLayer {
                            alpha = enterAlpha
                            translationY = enterY
                        }
                        .clip(RoundedCornerShape(Ui.ChipRadius))
                        .background(Color.White)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                            .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            .background(
                                if (done) Color(0xFFE3EFFF) else Color(0xFFF0F2F5)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Txt(a.icon, size = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(a.title, size = 14.sp, weight = FontWeight.Medium)
                            Spacer(Modifier.width(6.dp))
                            if (done) {
                                Txt("已解锁", size = 10.sp, color = Ui.AccentDeep)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Txt(a.desc, size = 11.sp, color = Color(0xFF8A96A8))
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier.fillMaxWidth().height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE9EDF2))
                        ) {
                            Box(
                                Modifier.fillMaxWidth(barRatio).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (done) Ui.Accent else Color(0xFFB9CBE5))
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Txt(
                        "${value.coerceAtMost(a.target)}/${a.target}",
                        size = 10.sp, color = Color(0xFF8A96A8)
                    )
                }
            }
        }
    }
}
