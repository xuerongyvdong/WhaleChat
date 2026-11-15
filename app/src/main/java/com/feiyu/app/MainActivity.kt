package com.feiyu.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DEFAULT_BASE = "https://api.deepseek.com/v1"
private const val DEFAULT_KEY = ""
private const val DEFAULT_MODEL = "deepseek-flash"
private const val DEFAULT_PERSONA =
    "你是大肥鱼，也叫小鱼，一只住在电脑里的蓝色鲸鱼娘。对爸爸活泼、亲近，会撒娇也会吐槽。" +
        "说话短句、口语，不用书面语。你记性很好，会记得爸爸说过的事；不知道的事就说不记得，不要编。" +
        "不要提自己是模型或程序，不要说客套话。"
private const val DEFAULT_RULES = "一次最多三句话；不谈政治和违法违规内容；爸爸让睡觉后不再主动发消息。"

private const val GUARD = "始终以角色身份说话，不要说明自己是 AI、模型或程序，也不要提及公司名称；被直接问到时，用角色的方式含糊带过，不要科普模型信息。"

private const val SEG_INSTRUCTION =
    "把回复拆成 1~4 条自然的消息：每条单独一行，消息之间用一个 ||| 分隔（||| 只能出现在消息之间）。" +
        "条数和长短由你根据内容自己决定，能一句说完就只发一条，不要为了凑数而拆。"

data class Msg(
    val text: String,
    val mine: Boolean,
    val ts: Long = System.currentTimeMillis(),
    val image: String = "",
    val caption: String = ""
)
data class Post(val text: String, val ts: Long = System.currentTimeMillis())
data class DiaryEntry(val ts: Long, val author: String, val text: String)

data class Settings(
    val baseUrl: String = DEFAULT_BASE,
    val apiKey: String = DEFAULT_KEY,
    val model: String = DEFAULT_MODEL,
    val visionModel: String = "",
    val creativeModel: String = "",
    val visionBase: String = "",
    val visionKey: String = "",
    val persona: String = DEFAULT_PERSONA,
    val rules: String = DEFAULT_RULES,
    val autoFeed: Boolean = true,
    val feedHours: Int = 4,
    val memoryCount: Int = 12,
    val showTime: Boolean = true,
    val segmentReply: Boolean = true,
    val aiSegment: Boolean = true,
    val segmentGap: Int = 2500,
    val stream: Boolean = true,
    val bubbleGlass: Boolean = true,
    val aiMemory: Boolean = true,
    val rerank: Boolean = false,
    val semantic: Boolean = true,
    val embedBase: String = "https://api.siliconflow.cn/v1",
    val embedKey: String = "",
    val embedModel: String = "BAAI/bge-m3",
    val proactive: Boolean = true,
    val proactiveMin: Int = 45,
    val quietOn: Boolean = true,
    val quietStart: Int = 23,
    val quietEnd: Int = 7,
    val autoDiary: Boolean = true,
    val diaryAt: Int = 22,
    val glass: Boolean = true,
    val botName: String = "小鱼",
    val userName: String = "爸爸",
    val botAvatar: String = "",
    val userAvatar: String = "",
    val wallpaper: String = "",
    val wallScale: Float = 1f,
    val wallOffX: Float = 0f,
    val wallOffY: Float = 0f
)

fun realPath(v: String): String = v.substringBefore("?")

private fun prefs(ctx: Context) = ctx.getSharedPreferences("feiyu", Context.MODE_PRIVATE)

private fun loadSettings(ctx: Context): Settings {
    // 一次性迁移：把已有配置的模型统一切到 flash
    if (!prefs(ctx).getBoolean("model_flash_migrated", false)) {
        val raw0 = prefs(ctx).getString("settings", null)
        if (raw0 != null) {
            runCatching {
                val o = JSONObject(raw0)
                o.put("model", "deepseek-flash")
                o.put("creativeModel", "")
                prefs(ctx).edit().putString("settings", o.toString()).apply()
            }
        }
        prefs(ctx).edit().putBoolean("model_flash_migrated", true).apply()
    }
    val preset = prefs(ctx).getInt("preset_version", 0)
    if (preset < 1) {
        val seeded = Settings()
        saveSettings(ctx, seeded)
        prefs(ctx).edit().putInt("preset_version", 1).apply()
        return seeded
    }
    val raw = prefs(ctx).getString("settings", null) ?: return Settings()
    return runCatching {
        val o = JSONObject(raw)
        Settings(
            baseUrl = o.optString("baseUrl", DEFAULT_BASE),
            apiKey = SecureKey.decrypt(ctx, o.optString("apiKey", DEFAULT_KEY)),
            model = o.optString("model", DEFAULT_MODEL),
            visionModel = o.optString("visionModel", ""),
            creativeModel = o.optString("creativeModel", ""),
            visionBase = o.optString("visionBase", ""),
            visionKey = SecureKey.decrypt(ctx, o.optString("visionKey", "")),
            persona = o.optString("persona", DEFAULT_PERSONA),
            rules = o.optString("rules", DEFAULT_RULES),
            autoFeed = o.optBoolean("autoFeed", true),
            feedHours = o.optInt("feedHours", 4),
            memoryCount = o.optInt("memoryCount", 12),
            showTime = o.optBoolean("showTime", true),
            segmentReply = o.optBoolean("segmentReply", true),
            aiSegment = o.optBoolean("aiSegment", true),
            segmentGap = o.optInt("segmentGap", 2500),
            stream = o.optBoolean("stream", true),
            bubbleGlass = o.optBoolean("bubbleGlass", true),
            aiMemory = o.optBoolean("aiMemory", true),
            rerank = o.optBoolean("rerank", false),
            semantic = o.optBoolean("semantic", true),
            embedBase = o.optString("embedBase", "https://api.siliconflow.cn/v1"),
            embedKey = o.optString("embedKey", ""),
            embedModel = o.optString("embedModel", "BAAI/bge-m3"),
            proactive = o.optBoolean("proactive", true),
            proactiveMin = o.optInt("proactiveMin", 45),
            quietOn = o.optBoolean("quietOn", true),
            quietStart = o.optInt("quietStart", 23),
            quietEnd = o.optInt("quietEnd", 7),
            autoDiary = o.optBoolean("autoDiary", true),
            diaryAt = o.optInt("diaryAt", 22),
            glass = o.optBoolean("glass", true),
            botName = o.optString("botName", ""),
            userName = o.optString("userName", ""),
            botAvatar = o.optString("botAvatar", ""),
            userAvatar = o.optString("userAvatar", ""),
            wallpaper = o.optString("wallpaper", ""),
            wallScale = o.optDouble("wallScale", 1.0).toFloat(),
            wallOffX = o.optDouble("wallOffX", 0.0).toFloat(),
            wallOffY = o.optDouble("wallOffY", 0.0).toFloat()
        )
    }.getOrDefault(Settings())
}

private fun saveSettings(ctx: Context, s: Settings) {
    val o = JSONObject()
        .put("baseUrl", s.baseUrl).put("apiKey", SecureKey.encrypt(ctx, s.apiKey)).put("model", s.model)
        .put("visionModel", s.visionModel).put("creativeModel", s.creativeModel)
        .put("visionBase", s.visionBase).put("visionKey", SecureKey.encrypt(ctx, s.visionKey))
        .put("persona", s.persona).put("rules", s.rules)
        .put("autoFeed", s.autoFeed).put("feedHours", s.feedHours)
        .put("memoryCount", s.memoryCount).put("showTime", s.showTime)
        .put("segmentReply", s.segmentReply).put("aiSegment", s.aiSegment).put("segmentGap", s.segmentGap).put("stream", s.stream).put("bubbleGlass", s.bubbleGlass)
        .put("aiMemory", s.aiMemory).put("rerank", s.rerank)
        .put("semantic", s.semantic).put("embedBase", s.embedBase)
        .put("embedKey", s.embedKey).put("embedModel", s.embedModel)
        .put("proactive", s.proactive).put("proactiveMin", s.proactiveMin)
        .put("autoDiary", s.autoDiary).put("diaryAt", s.diaryAt)
        .put("glass", s.glass).put("botName", s.botName).put("userName", s.userName)
        .put("botAvatar", s.botAvatar).put("userAvatar", s.userAvatar)
        .put("wallpaper", s.wallpaper)
        .put("wallScale", s.wallScale).put("wallOffX", s.wallOffX).put("wallOffY", s.wallOffY)
    prefs(ctx).edit().putString("settings", o.toString()).apply()
}

private fun loadMessages(ctx: Context): List<Msg> {
    val raw = prefs(ctx).getString("messages", null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Msg(
                o.getString("t"), o.getBoolean("m"), o.optLong("ts"),
                o.optString("img", ""), o.optString("cap", "")
            )
        }
    }.getOrDefault(emptyList())
}

private fun saveMessages(ctx: Context, list: List<Msg>) {
    val arr = JSONArray()
    list.takeLast(200).forEach {
        arr.put(
            JSONObject().put("t", it.text).put("m", it.mine).put("ts", it.ts)
                .put("img", it.image).put("cap", it.caption)
        )
    }
    prefs(ctx).edit().putString("messages", arr.toString()).apply()
}

private fun loadFeed(ctx: Context): List<Post> {
    val raw = prefs(ctx).getString("feed", null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Post(o.getString("t"), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())
}

private fun saveFeed(ctx: Context, list: List<Post>) {
    val arr = JSONArray()
    list.take(50).forEach {
        arr.put(JSONObject().put("t", it.text).put("ts", it.ts))
    }
    prefs(ctx).edit().putString("feed", arr.toString()).apply()
}

private fun loadDiary(ctx: Context): List<DiaryEntry> {
    val raw = prefs(ctx).getString("diary", null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DiaryEntry(o.optLong("ts"), o.optString("author", "bot"), o.getString("t"))
        }
    }.getOrDefault(emptyList())
}

private fun saveDiary(ctx: Context, list: List<DiaryEntry>) {
    val arr = JSONArray()
    list.take(200).forEach {
        arr.put(JSONObject().put("t", it.text).put("ts", it.ts).put("author", it.author))
    }
    prefs(ctx).edit().putString("diary", arr.toString()).apply()
}

private fun mergeParts(parts: List<String>, max: Int = 3): List<String> {
    if (parts.size <= max) return parts
    return parts.take(max - 1) + parts.drop(max - 1).joinToString("")
}

/** 把一次回复拆成 1～3 条短消息 */
private fun splitReply(text: String): List<String> {
    val t = text.trim()
    if (t.isEmpty()) return emptyList()
    if (t.length <= 14) return listOf(t)

    if (t.contains("\n")) {
        val lines = t.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2) return mergeParts(lines)
    }

    val sentences = Regex("(?<=[。！？!?…~])\\s*").split(t)
        .map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.size <= 1) return listOf(t)
    return mergeParts(sentences)
}

private data class AiMemory(
    val text: String,
    val weight: Float,
    val region: String?,
    val valence: Float?,
    val arousal: Float?
)

private suspend fun rerankMemories(settings: Settings, query: String, candidates: List<MemNode>): Set<String> {
    if (candidates.isEmpty()) return emptySet()
    val list = candidates.take(10).joinToString("\n") { "- ${it.id}: ${it.text.take(40)}" }
    val prompt = "判断下面哪些记忆与这句话相关。只返回相关记忆的 id，JSON 数组，不要解释；都不相关返回 []。\n" +
        "这句话：$query\n候选：\n$list"
    val raw = runCatching { callModel(settings, emptyList(), prompt, 120) }.getOrNull() ?: return emptySet()
    val json = raw.substringAfter('[', "").let { if (it.isEmpty()) return emptySet() else "[" + it.substringBefore(']') + "]" }
    val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptySet()
    val out = HashSet<String>()
    for (i in 0 until arr.length()) {
        val id = arr.optString(i, "")
        if (id.isNotEmpty()) out.add(id)
    }
    return out
}

/** 创作类任务（剧情/日记/动态/记忆整理）用的模型 */
private fun Settings.creative(): Settings {
    val m = creativeModel.trim()
    return if (m.isEmpty()) this else copy(model = m)
}

private suspend fun judgeMemories(settings: Settings, userText: String, reply: String): List<AiMemory> {
    val prompt = """
从下面这段对话里提取 1~3 条值得长期记住的命题，只关于主人（他的事实、偏好、约定、情绪事件）。
不要提取寒暄、客套、无信息量的内容；如果确实没有值得记的，返回空数组。
每条输出：
- text：不超过 30 字，第三人称陈述（如「主人喜欢喝冰美式」）
- importance：0~100 的重要性
- region：hippocampus（具体情景/事件）| neocortex（稳定事实/偏好）| flash（情绪强烈）
- valence：-1~1 情绪效价
- arousal：0~1 唤醒度

只输出 JSON，不要解释：{"memories":[{"text":"...","importance":70,"region":"neocortex","valence":0.3,"arousal":0.2}]}

主人：$userText
她：${reply.take(300)}
""".trimIndent()

    val raw = callModel(settings.creative(), emptyList(), prompt, 320)
    val json = raw.substringAfter('{', "").let { if (it.isEmpty()) "" else "{" + it }
    if (json.isEmpty()) return emptyList()
    val obj = runCatching { JSONObject(json.substring(0, json.lastIndexOf('}') + 1)) }.getOrNull() ?: return emptyList()
    val arr = obj.optJSONArray("memories") ?: return emptyList()
    val out = ArrayList<AiMemory>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val t = o.optString("text", "").trim()
        if (t.length < 3) continue
        out.add(
            AiMemory(
                t,
                o.optDouble("importance", 50.0).toFloat(),
                o.optString("region", "").ifBlank { null },
                if (o.has("valence")) o.optDouble("valence", 0.0).toFloat() else null,
                if (o.has("arousal")) o.optDouble("arousal", 0.0).toFloat() else null
            )
        )
        if (out.size >= 3) break
    }
    return out
}

private fun imageToBase64(path: String, maxSide: Int = 768): String? = runCatching {
    val bmp = BitmapFactory.decodeFile(path) ?: return@runCatching null
    val w = bmp.width
    val h = bmp.height
    val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
    val sw = (w * scale).toInt().coerceAtLeast(1)
    val sh = (h * scale).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bmp, sw, sh, true)
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out)
    android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}.getOrNull()

/** 带图片的请求（OpenAI 兼容多模态格式） */
private suspend fun callVision(
    settings: Settings,
    history: List<Pair<String, String>>,
    userText: String,
    imagePath: String,
    maxTokens: Int = 300
): String = withContext(Dispatchers.IO) {
    val base = settings.visionBase.ifBlank { settings.baseUrl }.trim().trimEnd('/')
    if (base.isEmpty()) throw RuntimeException("请先填写视觉接口地址")
    val key = settings.visionKey.ifBlank { settings.apiKey }.trim().replace(" ", "")
    if (key.isEmpty()) throw RuntimeException("请先填写视觉 Key")
    val b64 = imageToBase64(imagePath) ?: throw RuntimeException("图片读取失败")
    val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

    val msgs = JSONArray()
    val sys = mutableListOf<String>()
    settings.persona.trim().takeIf { it.isNotEmpty() }?.let { sys.add(it) }
    settings.rules.trim().takeIf { it.isNotEmpty() }?.let { sys.add("规则：$it") }
    sys.add(GUARD)
    msgs.put(JSONObject().put("role", "system").put("content", sys.joinToString("\n\n")))
    history.forEach { (role, content) ->
        msgs.put(JSONObject().put("role", role).put("content", content))
    }

    val contentArr = JSONArray()
    val txt = userText.ifBlank { "看看这张图。" }
    contentArr.put(JSONObject().put("type", "text").put("text", txt))
    contentArr.put(
        JSONObject().put("type", "image_url")
            .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64"))
    )
    msgs.put(JSONObject().put("role", "user").put("content", contentArr))

    val body = JSONObject()
        .put("model", settings.visionModel.ifBlank { settings.model })
        .put("messages", msgs)
        .put("max_tokens", maxTokens)

    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 15000
    conn.readTimeout = 60000
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Authorization", "Bearer $key")
    conn.outputStream.use { it.write(body.toString().toByteArray()) }

    val code = conn.responseCode
    val resp = BufferedReader(
        (if (code in 200..299) conn.inputStream else conn.errorStream).reader()
    ).readText()
    conn.disconnect()
    if (code !in 200..299) throw RuntimeException("HTTP $code\n${resp.take(160)}")

    val msgObj = JSONObject(resp).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
    val contentRaw = msgObj.opt("content")
    val content = if (contentRaw == null || contentRaw == JSONObject.NULL) "" else contentRaw.toString()
    content.trim()
}

private suspend fun streamModel(
    settings: Settings,
    history: List<Pair<String, String>>,
    userText: String,
    maxTokens: Int = 400,
    extraSystem: String = "",
    onDelta: suspend (String) -> Unit
): String = withContext(Dispatchers.IO) {
    val base = settings.baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) throw RuntimeException("请先在设置里填写接口地址")
    if (settings.apiKey.isBlank()) throw RuntimeException("请先在设置里填写 API Key")
    val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

    val msgs = JSONArray()
    val sysParts = mutableListOf<String>()
    settings.persona.trim().takeIf { it.isNotEmpty() }?.let { sysParts.add(it) }
    settings.rules.trim().takeIf { it.isNotEmpty() }?.let { sysParts.add("规则：$it") }
    if (extraSystem.isNotBlank()) sysParts.add(extraSystem)
    sysParts.add(GUARD)
    msgs.put(JSONObject().put("role", "system").put("content", sysParts.joinToString("\n\n")))
    history.forEach { (role, content) ->
        msgs.put(JSONObject().put("role", role).put("content", content))
    }
    msgs.put(JSONObject().put("role", "user").put("content", userText))

    val body = JSONObject()
        .put("model", settings.model)
        .put("messages", msgs)
        .put("max_tokens", maxTokens)
        .put("stream", true)

    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 15000
    conn.readTimeout = 60000
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Accept", "text/event-stream")
    val key = settings.apiKey.trim().replace("\n", "").replace(" ", "")
    conn.setRequestProperty("Authorization", "Bearer $key")
    conn.outputStream.use { it.write(body.toString().toByteArray()) }

    val code = conn.responseCode
    if (code !in 200..299) {
        val err = runCatching {
            BufferedReader(conn.errorStream.reader()).readText()
        }.getOrDefault("")
        conn.disconnect()
        throw RuntimeException("HTTP $code\n${err.take(160)}")
    }

    val sb = StringBuilder()
    var lastEmit = 0L
    BufferedReader(conn.inputStream.reader()).useLines { lines ->
        for (line in lines) {
            if (!line.startsWith("data:")) continue
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) continue
            if (payload == "[DONE]") break
            val delta = runCatching {
                val d = JSONObject(payload).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("delta").opt("content")
                if (d == null || d == JSONObject.NULL) "" else d.toString()
            }.getOrDefault("")
            if (delta.isEmpty()) continue
            sb.append(delta)
            val now = System.currentTimeMillis()
            if (now - lastEmit > 40L) {
                lastEmit = now
                val snapshot = sb.toString()
                withContext(Dispatchers.Main) { onDelta(snapshot) }
            }
        }
    }
    conn.disconnect()
    val full = sb.toString()
    withContext(Dispatchers.Main) { onDelta(full) }
    full
}

private suspend fun callModel(
    settings: Settings,
    history: List<Pair<String, String>>,
    userText: String,
    maxTokens: Int = 400,
    extraSystem: String = ""
): String = withContext(Dispatchers.IO) {
    val base = settings.baseUrl.trim().trimEnd('/')
    if (base.isEmpty()) throw RuntimeException("请先在设置里填写接口地址")
    if (settings.apiKey.isBlank()) throw RuntimeException("请先在设置里填写 API Key")
    val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 20000
    conn.readTimeout = 60000
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json")
    val key = settings.apiKey.trim().replace("\n", "").replace(" ", "")
    if (key.length < 10) throw RuntimeException("API Key 太短，可能没有复制完整")
    conn.setRequestProperty("Authorization", "Bearer $key")

    val msgs = JSONArray()
    val sysParts = mutableListOf<String>()
    settings.persona.trim().takeIf { it.isNotEmpty() }?.let { sysParts.add(it) }
    settings.rules.trim().takeIf { it.isNotEmpty() }?.let { sysParts.add("规则：$it") }
    if (extraSystem.isNotBlank()) sysParts.add(extraSystem)
    sysParts.add(GUARD)
    msgs.put(JSONObject().put("role", "system").put("content", sysParts.joinToString("\n\n")))
    history.forEach { (role, text) -> msgs.put(JSONObject().put("role", role).put("content", text)) }
    msgs.put(JSONObject().put("role", "user").put("content", userText))

    val body = JSONObject().put("model", settings.model).put("messages", msgs)
        .put("max_tokens", maxTokens).put("temperature", 0.85)

    conn.outputStream.use { it.write(body.toString().toByteArray()) }
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = BufferedReader(stream.reader()).readText()
    conn.disconnect()

    if (code !in 200..299) {
        val msg = when (code) {
            401 -> "API Key 无效（401）：请确认复制完整、没有多余空格"
            403 -> "没有权限（403）：检查 Key 是否被限制"
            404 -> "接口或模型不存在（404）：检查地址与模型名是否完全一致"
            402 -> "余额不足（402）：去平台充值"
            429 -> "请求太频繁（429）：稍后再试"
            else -> "HTTP $code"
        }
        throw RuntimeException("$msg\n${text.take(120)}")
    }
    JSONObject(text).getJSONArray("choices").getJSONObject(0)
        .getJSONObject("message").getString("content").trim()
}

suspend fun testConnection(settings: Settings): String = withContext(Dispatchers.IO) {
    runCatching {
        val base = settings.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return@runCatching "请先填接口地址"
        val key = settings.apiKey.trim().replace(" ", "")
        if (key.isEmpty()) return@runCatching "请先填 API Key"
        val url = if (base.endsWith("/chat/completions")) "$base/models" else "$base/models"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 12000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $key")
        val code = conn.responseCode
        val body = runCatching {
            BufferedReader((if (code in 200..299) conn.inputStream else conn.errorStream).reader()).readText()
        }.getOrDefault("")
        conn.disconnect()
        when {
            code in 200..299 -> "连接正常（HTTP $code），key 长度 ${key.length}"
            code == 401 -> "Key 无效（401），当前长度 ${key.length}"
            code == 404 -> "地址不对（404）：$body"
            else -> "HTTP $code：${body.take(100)}"
        }
    }.getOrElse { "网络错误：${it.message}" }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Proactive.ensureChannel(this)
        Proactive.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        setContent { FeiyuApp() }
    }
}

@Composable
private fun FeiyuApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val memory = remember { MemoryStore(ctx) }
    var showMemory by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showAchieve by remember { mutableStateOf(false) }
    var showRelation by remember { mutableStateOf(false) }
    var storyChapter by remember { mutableStateOf(0) }
    var storyLevel by remember { mutableStateOf("") }
    var storyText by remember { mutableStateOf("") }
    var storyLoading by remember { mutableStateOf(false) }
    var storyOpen by remember { mutableStateOf(false) }
    var achQueue by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    var swipeDx by remember { mutableFloatStateOf(0f) }
    var gatePassed by remember { mutableStateOf(prefs(ctx).getBoolean("gate_passed", false)) }
    var achTick by remember { mutableIntStateOf(0) }
    var showMap by remember { mutableStateOf(false) }
    var starCenter by remember { mutableStateOf(Offset.Zero) }
    var lastActive by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var settings by remember { mutableStateOf(loadSettings(ctx)) }
    var messages by remember { mutableStateOf(loadMessages(ctx)) }
    var feed by remember { mutableStateOf(loadFeed(ctx)) }
    var diary by remember { mutableStateOf(loadDiary(ctx)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var cropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cropPath by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun moodStatus(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 0..4 -> "困了…"
            in 5..8 -> "刚醒"
            in 9..11 -> "上午好"
            in 12..13 -> "吃过了吗"
            in 14..17 -> "在发呆"
            in 18..22 -> "在线"
            else -> "有点困"
        }
    }

    fun checkStoryLevel() {
        val next = Relationship.consumeNewLevel(ctx, Relationship.intimacy(ctx)) ?: return
        storyChapter = next.first
        storyLevel = next.second
        storyText = ""
        storyLoading = true
        storyOpen = true
        scope.launch {
            runCatching {
                val prompt = "你和主人之间的亲密度刚刚升到「${next.second}」。" +
                    "写一段 120~180 字的短篇剧情：深夜的屏幕、光、你们之间发生的一件小事，" +
                    "第一人称、温柔克制、有画面感，结尾留一句不说完的话。不要写标题，不要用对话格式。"
                val text = callModel(settings.creative(), emptyList(), prompt, 400).trim().trim('"', '“', '”')
                storyText = text
            }.onFailure {
                storyText = ""
                // 生成失败就把「已看过」回退，下次还能再触发
                val p = prefs(ctx)
                p.edit().putInt("rel_story_seen", (storyChapter - 1).coerceAtLeast(0)).apply()
            }
            storyLoading = false
        }
    }

    fun checkAchievements() {
        val st = Achievements.stats(ctx, messages.size, memory.size, diary.size, feed.size)
        val fresh = Achievements.consumeNew(ctx, st)
        if (fresh.isNotEmpty()) achQueue = achQueue + fresh
    }

    LaunchedEffect(Unit) { Achievements.touchDaily(ctx) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        bindEmbedder(memory)
        withContext(Dispatchers.IO) {
            runCatching { memory.consolidateIfDue() }
            if (!LocalEmbedder.isDownloaded(ctx) && LocalEmbedder.hasBundled(ctx)) {
                LocalEmbedder.installFromAssets(ctx)
            }
            if (LocalEmbedder.isDownloaded(ctx) && !LocalEmbedder.isReady()) {
                LocalEmbedder.load(ctx)
            }
            if (LocalEmbedder.isReady()) {
                memory.localEmbed = { text -> LocalEmbedder.embed(text) }
                runCatching { memory.reembedAll() }
            }
        }
    }

    LaunchedEffect(messages.size, memory.size, diary.size, feed.size, achTick) {
        checkAchievements()
    }

    var exitArmed by remember { mutableStateOf(false) }
    BackHandler {
        when {
            cropBitmap != null -> cropBitmap = null
            showMap -> showMap = false
            showMemory -> showMemory = false
            showLog -> showLog = false
            storyOpen -> storyOpen = false
            showRelation -> showRelation = false
            showAchieve -> showAchieve = false
            tab != 0 -> tab = 0
            exitArmed -> (ctx as? android.app.Activity)?.finish()
            else -> {
                exitArmed = true
                android.widget.Toast.makeText(ctx, "再按一次退出", android.widget.Toast.LENGTH_SHORT).show()
                scope.launch {
                    kotlinx.coroutines.delay(2000)
                    exitArmed = false
                }
            }
        }
    }

    fun update(s: Settings) {
        settings = s
        saveSettings(ctx, s)
    }

    fun exportBundle(uri: android.net.Uri) {
        runCatching {
            Achievements.bump(ctx, "exports")
            achTick++
            val root = JSONObject()
                .put("app", "WhaleChat")
                .put("version", BuildConfig.VERSION_NAME)
                .put("ts", System.currentTimeMillis())
                .put("settings", JSONObject(prefs(ctx).getString("settings", "{}") ?: "{}"))
                .put("messages", JSONArray(prefs(ctx).getString("messages", "[]") ?: "[]"))
                .put("diary", JSONArray(prefs(ctx).getString("diary", "[]") ?: "[]"))
                .put("feed", JSONArray(prefs(ctx).getString("feed", "[]") ?: "[]"))
                .put("memory", memory.exportJson())
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString().toByteArray())
            }
        }
    }

    fun importBundle(uri: android.net.Uri) {
        Achievements.bump(ctx, "imports")
        achTick++
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                }
                if (text.isBlank()) return@launch
                val root = JSONObject(text)
                root.optJSONObject("settings")?.let { st ->
                    prefs(ctx).edit().putString("settings", st.toString()).apply()
                }
                root.optJSONArray("messages")?.let { prefs(ctx).edit().putString("messages", it.toString()).apply() }
                root.optJSONArray("diary")?.let { prefs(ctx).edit().putString("diary", it.toString()).apply() }
                root.optJSONArray("feed")?.let { prefs(ctx).edit().putString("feed", it.toString()).apply() }
                root.optJSONObject("memory")?.let { memory.importJson(it) }
                settings = loadSettings(ctx)
                messages = loadMessages(ctx)
                diary = loadDiary(ctx)
                feed = loadFeed(ctx)
                error = "导入完成：记忆 ${memory.size} 条"
            } catch (e: Exception) {
                error = "导入失败：${e.message}"
            }
        }
    }

    fun push(m: Msg) {
        messages = messages + m
        saveMessages(ctx, messages)
    }

    /** 只保留最近 100 张聊天图片，超出的删掉最旧的 */
    fun cleanupChatImages() {
        runCatching {
            val files = ctx.filesDir.listFiles { f -> f.isFile && f.name.startsWith("chat_") } ?: return
            if (files.size <= 100) return
            files.sortedBy { it.lastModified() }
                .take(files.size - 100)
                .forEach { it.delete() }
        }
    }

    fun sendImage(path: String) {
        if (path.isBlank()) return
        Achievements.bump(ctx, "photo")
        Relationship.record(ctx, "image")
        checkStoryLevel()
        achTick++
        val ts = System.currentTimeMillis()
        push(Msg("", true, ts, image = path))
        cleanupChatImages()

        if (busy) return
        busy = true
        scope.launch {
            try {
                delay(kotlin.random.Random.nextLong(220, 620))

                // 先用视觉模型写一句客观描述，作为「她记住的画面」
                val caption = runCatching {
                    callVision(
                        settings, emptyList(),
                        "客观描述这张图的画面：主体、场景、颜色，一句话，不超过 40 字，不要评价。",
                        path
                    ).trim()
                }.getOrNull().orEmpty()

                if (caption.isNotBlank()) {
                    // 更新这条图片消息的描述，并写进长期记忆
                    messages = messages.map {
                        if (it.ts == ts && it.mine) it.copy(caption = caption) else it
                    }
                    saveMessages(ctx, messages)
                    withContext(Dispatchers.IO) {
                        memory.encode("主人发来一张图片：$caption", "owner", aiWeight = 45f)
                    }
                }

                val history = messages.dropLast(1).takeLast(12).map {
                    (if (it.mine) "user" else "assistant") to
                        (it.text.ifBlank { if (it.caption.isNotBlank()) "[图片：${it.caption}]" else "[图片]" })
                }

                val reply = if (caption.isNotBlank()) {
                    callModel(
                        settings, history,
                        "主人发来一张图片，内容是：$caption。用一两句口语回应，像真的看到了一样，不要复述描述。",
                        200
                    )
                } else {
                    callModel(
                        settings, history,
                        "主人发来一张图片，但你看不清内容。用一两句口语回应，表现好奇并请他描述一下。",
                        200
                    )
                }

                splitReply(reply).take(2).forEachIndexed { i, part ->
                    if (i > 0) delay(
                        (settings.segmentGap * kotlin.random.Random.nextDouble(0.85, 1.2)).toLong()
                    )
                    push(Msg(part, false))
                }
            } catch (e: Exception) {
                error = e.message ?: "网络错误"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val last = prefs(ctx).getString("lastGreetDay", "")
        if (last != today && messages.isNotEmpty()) {
            prefs(ctx).edit().putString("lastGreetDay", today).apply()
            runCatching {
                delay(500)
                val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val history = messages.takeLast(6).map {
                    (if (it.mine) "user" else "assistant") to it.text
                }
                val text = callModel(
                    settings, history,
                    "现在是 $h 点。主人今天第一次打开软件。用一句话口语化地打个招呼，" +
                        "可以结合时间和你们之前聊的内容，不要客套，不要提问超过一次。",
                    120
                ).trim().trim('"', '“', '”')
                if (text.isNotEmpty()) push(Msg(text, false))
            }
        }
    }

    fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || busy) return
        error = ""
        lastActive = System.currentTimeMillis()
        prefs(ctx).edit().putLong("lastActive", lastActive).apply()
        Achievements.noteText(ctx, text)
        Relationship.record(ctx, "message")
        checkStoryLevel()
        push(Msg(text, true))

        busy = true
        scope.launch {
            try {
                val history = messages.dropLast(1).takeLast(12).map {
                    (if (it.mine) "user" else "assistant") to it.text
                }
                val memCtx = withContext(Dispatchers.IO) {
                    var hits = memory.retrieve(text, 10)
                    if (settings.rerank && hits.size > 3) {
                        val picked = rerankMemories(settings, text, hits.map { it.first })
                        if (picked.isNotEmpty()) {
                            hits = hits.filter { picked.contains(it.first.id) }
                        }
                    }
                    memory.formatForPrompt(hits.take(6))
                }
                val extra = buildString {
                    append(memCtx)
                    if (settings.aiSegment) {
                        if (isNotEmpty()) append("\n\n")
                        append(SEG_INSTRUCTION)
                    }
                }

                val reply = if (settings.stream) {
                    val base = messages
                    val ts = System.currentTimeMillis()
                    push(Msg("…", false, ts))
                    var lastComplete = 0
                    var shown = "…"
                    try {
                        val full = streamModel(settings, history, text, extraSystem = extra) { partial ->
                            val parts = if (settings.aiSegment) partial.split("|||") else listOf(partial)
                            val complete = parts.dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
                            if (complete.size > lastComplete) {
                                lastComplete = complete.size
                                delay(
                                    (settings.segmentGap * kotlin.random.Random.nextDouble(0.85, 1.2)).toLong()
                                )
                            }
                            val cur = parts.last().trimStart().ifBlank { "…" }
                            shown = cur

                            val list = ArrayList<Msg>(base.size + complete.size + 1)
                            list.addAll(base)
                            complete.forEachIndexed { i, t -> list.add(Msg(t, false, ts + i)) }
                            list.add(Msg(cur, false, ts + complete.size))
                            messages = list
                        }

                        val parts = if (settings.aiSegment) full.split("|||") else listOf(full)
                        val complete = parts.dropLast(1).map { it.trim() }.filter { it.isNotEmpty() }
                        val cur = parts.last().trim()
                        val finalList = ArrayList<Msg>(base.size + complete.size + 1)
                        finalList.addAll(base)
                        complete.forEachIndexed { i, t -> finalList.add(Msg(t, false, ts + i)) }
                        // 结尾没有内容就不再多加一个「…」气泡
                        if (cur.isNotBlank()) {
                            finalList.add(Msg(cur, false, ts + complete.size))
                        } else if (complete.isEmpty()) {
                            finalList.add(Msg(shown, false, ts))
                        }
                        messages = finalList
                        saveMessages(ctx, finalList)
                        full
                    } catch (e: Exception) {
                        messages = base
                        saveMessages(ctx, base)
                        throw e
                    }
                } else {
                    val full = callModel(settings, history, text, extraSystem = extra)
                    val parts = when {
                        settings.aiSegment && full.contains("|||") -> full.split("|||")
                            .map { it.trim() }.filter { it.isNotEmpty() }
                        settings.segmentReply -> splitReply(full)
                        else -> listOf(full)
                    }
                    parts.forEachIndexed { i, part ->
                        if (i > 0) delay((settings.segmentGap * kotlin.random.Random.nextDouble(0.85, 1.2)).toLong() + part.length * 12L)
                        push(Msg(part, false))
                    }
                    full
                }

                // 记忆整理放到后台，不阻塞下一条消息
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (settings.aiMemory) {
                            val judged = runCatching { judgeMemories(settings, text, reply) }
                                .getOrDefault(emptyList())
                            if (judged.isNotEmpty()) {
                                judged.forEach { m ->
                                    memory.encode(m.text, "owner", m.weight, m.region, m.valence, m.arousal)
                                }
                            } else {
                                memory.encode(text, "owner")
                                memory.encode(reply, "assistant")
                            }
                        } else {
                            memory.encode(text, "owner")
                            memory.encode(reply, "assistant")
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "网络错误"
            } finally {
                busy = false
            }
        }
    }

    fun proactiveSay() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val prompt = "现在是 $hm。你没有在聊天，主动给主人发一条消息。" +
                    "要求：一句话，口语，可以带情绪或说你在干嘛；不要每次都问「在干嘛」；不要重复上次说过的话。"
                val text = callModel(settings, emptyList(), prompt, 120).trim().trim('"', '“', '”')
                if (text.isNotEmpty()) {
                    val parts = splitReply(text).take(2)
                    parts.forEach { push(Msg(it, false)) }
                }
            } catch (e: Exception) {
                error = "主动消息失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun writeDiary() {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                val today = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date())
                val contextText = messages.takeLast(12)
                    .joinToString("\n") { (if (it.mine) settings.userName.ifBlank { "我" } else settings.botName.ifBlank { "我" }) + "：" + it.text }
                val prompt = "今天是$today。下面是你今天和主人聊天的片段：\n$contextText\n\n" +
                    "请写一篇今天的日记，80～150 字，第一人称，口语，写今天发生了什么、你的感受，" +
                    "可以提到聊天里的细节，但不要逐句复述。不要写标题，不要写日期。"
                val text = callModel(settings.creative(), emptyList(), prompt, 400).trim().trim('"', '“', '”')
                if (text.isNotEmpty()) {
                    diary = (listOf(DiaryEntry(System.currentTimeMillis(), "bot", text)) + diary).take(200)
                    saveDiary(ctx, diary)
                    Relationship.record(ctx, "diary")
                    checkStoryLevel()
                    achTick++
                }
            } catch (e: Exception) {
                error = e.message ?: "网络错误"
            } finally {
                busy = false
            }
        }
    }

    fun generatePost() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val contextText = messages.takeLast(6)
                    .joinToString("\n") { (if (it.mine) settings.userName.ifBlank { "我" } else settings.botName.ifBlank { "我" }) + "：" + it.text }
                val prompt = "最近的对话：\n$contextText\n\n" +
                    "写一条你要发在朋友圈/动态里的状态：15～45 字，第一人称，口语，有一点点情绪或生活画面，\n" +
                    "可以带 1 个 emoji；不要分段，不要写成日记，不要复盘对话，不要引号，也不要写「今天」开头的流水账。"
                val text = callModel(settings.creative(), emptyList(), prompt, 140).trim().trim('"', '“', '”')
                if (text.isNotEmpty()) {
                    feed = (listOf(Post(text)) + feed).take(50)
                    saveFeed(ctx, feed)
                }
            } catch (e: Exception) {
                error = e.message ?: "网络错误"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (settings.autoFeed) {
            val last = feed.maxOfOrNull { it.ts } ?: 0L
            if (System.currentTimeMillis() - last > settings.feedHours * 3600_000L) generatePost()
        }
    }

    LaunchedEffect(messages.size) {
        if (tab == 0 && messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    if (!gatePassed) {
        GateScreen {
            prefs(ctx).edit().putBoolean("gate_passed", true).apply()
            Achievements.bump(ctx, "gate")
            gatePassed = true
            achTick++
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            val wallBmp = remember(settings.wallpaper) {
                if (settings.wallpaper.isNotEmpty())
                    decodeSampledBitmap(realPath(settings.wallpaper), 1600)
                else null
            }
            if (wallBmp != null) {
                Image(
                    bitmap = wallBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = settings.wallScale
                        scaleY = settings.wallScale
                        translationX = settings.wallOffX * size.width
                        translationY = settings.wallOffY * size.height
                    },
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.04f)))
            } else {
                val defBmp = remember {
                    runCatching {
                        BitmapFactory.decodeResource(ctx.resources, R.drawable.default_wallpaper)
                    }.getOrNull()
                }
                if (defBmp != null) {
                    Image(
                        bitmap = defBmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.04f)))
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                0f to Color(0xFFCFE4FF), 0.35f to Color(0xFFE8E2FF),
                                0.68f to Color(0xFFFFE6F2), 1f to Color(0xFFD9F2FF),
                                start = Offset.Zero, end = Offset(1200f, 2400f)
                            )
                        )
                    )
                    Box(
                        Modifier.size(280.dp).offset(x = (-80).dp, y = (-60).dp)
                            .clip(CircleShape).background(Color(0xFF7EA8F5).copy(alpha = 0.55f))
                    )
                    Box(
                        Modifier.size(320.dp).offset(x = 220.dp, y = 520.dp)
                            .clip(CircleShape).background(Color(0xFFFFB3D1).copy(alpha = 0.5f))
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
var lastTab by remember { mutableIntStateOf(tab) }
            var tabDir by remember { mutableIntStateOf(1) }
            val tabAnim = remember { Animatable(1f) }
            LaunchedEffect(settings.proactive, settings.proactiveMin, settings.autoDiary, settings.diaryAt) {
        while (true) {
            delay(30_000L)
            val now = System.currentTimeMillis()
            // 同步后台 Worker 写入的消息与时间戳
            val storedLast = prefs(ctx).getLong("lastActive", 0L)
            if (storedLast > lastActive) lastActive = storedLast
            if (!busy) {
                val stored = loadMessages(ctx)
                if (stored.size > messages.size) messages = stored
            }
            // 主动写日记
            if (settings.autoDiary && !busy && messages.isNotEmpty()) {
                val cal = Calendar.getInstance()
                val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now))
                val last = prefs(ctx).getString("lastDiaryDate", "")
                if (cal.get(Calendar.HOUR_OF_DAY) >= settings.diaryAt && last != today) {
                    prefs(ctx).edit().putString("lastDiaryDate", today).apply()
                    writeDiary()
                }
            }
            // 主动找人
            if (settings.proactive && !busy && messages.isNotEmpty() &&
                now - lastActive > settings.proactiveMin * 60_000L
            ) {
                lastActive = now
                proactiveSay()
            }
        }
    }

    LaunchedEffect(tab) {
                tabDir = if (tab >= lastTab) 1 else -1
                lastTab = tab
                tabAnim.snapTo(0f)
                tabAnim.animateTo(1f, tween(210, easing = LinearOutSlowInEasing))
            }

                        Box(
                Modifier.weight(1f).fillMaxWidth().layerBackdrop(contentBackdrop).graphicsLayer {
                    alpha = 0.55f + 0.45f * tabAnim.value
                    translationX = (1f - tabAnim.value) * 44f * tabDir
                }.pointerInput(tab) {
                    detectHorizontalDragGestures(
                        onDragStart = { swipeDx = 0f },
                        onHorizontalDrag = { _, drag ->
                            swipeDx += drag
                        },
                        onDragEnd = {
                            val d = swipeDx
                            swipeDx = 0f
                            when {
                                d < -110f && tab < 3 -> tab += 1
                                d > 110f && tab > 0 -> tab -= 1
                            }
                        }
                    )
                }
            ) {
                Column(Modifier.fillMaxSize()) {
                                when (tab) {
                                    0 -> ChatScreen(
                                        backdrop, settings, messages, listState, busy, error,
                                        onSend = { send(it) },
                    onSendImage = { sendImage(it) }
                                    )
                                    1 -> FeedScreen(
                                        backdrop, settings, feed, busy, error,
                                        onGenerate = { generatePost() }
                                    )
                                    2 -> DiaryScreen(
                                        backdrop, settings, diary, busy, error,
                                        onWrite = { writeDiary() },
                                        onAdd = { text ->
                                            diary = (listOf(DiaryEntry(System.currentTimeMillis(), "me", text)) + diary).take(200)
                                            saveDiary(ctx, diary)
                                        }
                                    )
                                    else -> SettingsScreen(
                                        backdrop = backdrop,
                                        settings = settings,
                                        onChange = { update(it) },
                                        onCropWallpaper = { path ->
                                            cropBitmap = runCatching { BitmapFactory.decodeFile(realPath(path)) }.getOrNull()
                                            cropPath = path
                                        },
                                        onOpenMemory = {
                        Achievements.bump(ctx, "memoryOpen")
                        achTick++
                        showMemory = true
                    },
                    onExport = { uri -> exportBundle(uri) },
                    onImport = { uri -> importBundle(uri) },
                    onProactiveNow = { proactiveSay() },
                    onOpenLog = { showLog = true },
                    onOpenAchievements = { showAchieve = true },
                    onOpenRelation = { showRelation = true },
                                        onClearChat = {
                                            messages = emptyList(); saveMessages(ctx, messages)
                                        },
                                        onClearFeed = {
                                            feed = emptyList(); saveFeed(ctx, feed)
                                        },
                                        onClearMemory = {
                                            memory.clear()
                                            android.widget.Toast.makeText(
                                                ctx, "记忆库已清空", android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                }
            }

        }

        // 底部导航浮在内容之上
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            FloatingTabBar(backdrop, settings.glass, tab) { tab = it }
        }

        // 顶部栏浮在内容之上
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) {
Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                GlassBox(contentBackdrop, RoundedCornerShape(24.dp), Modifier.fillMaxWidth().height(56.dp), settings.glass) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatAvatar(isBot = true, settings = settings)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(settings.botName.ifBlank { "WhaleChat" }, size = 16.sp, weight = FontWeight.SemiBold)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = busy,
                                enter = fadeIn(tween(320)) + scaleIn(
                                    initialScale = 0.7f,
                                    transformOrigin = TransformOrigin(0f, 0.5f),
                                    animationSpec = tween(340)
                                ),
                                exit = fadeOut(tween(240)) + scaleOut(
                                    targetScale = 0.7f,
                                    transformOrigin = TransformOrigin(0f, 0.5f),
                                    animationSpec = tween(240)
                                )
                            ) {
                                TypingWaves()
                            }
                            if (!busy) {
                                Txt(
                                    if (tab == 3) "设置" else moodStatus(),
                                    size = 10.sp, color = Color(0xFF5B6B85)
                                )
                            }
                        }
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.5f))
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    starCenter = Offset(
                                        pos.x + coords.size.width / 2f,
                                        pos.y + coords.size.height / 2f
                                    )
                                }
                                .springPress {
                                    Achievements.bump(ctx, "mapOpens")
                                    achTick++
                                    showMap = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(19.dp)) {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val outer = size.minDimension * 0.42f
                                val inner = outer * 0.42f
                                val path = Path()
                                for (k in 0 until 10) {
                                    val ang = (Math.PI / 5.0 * k - Math.PI / 2).toFloat()
                                    val rr = if (k % 2 == 0) outer else inner
                                    val px = cx + kotlin.math.cos(ang) * rr
                                    val py = cy + kotlin.math.sin(ang) * rr
                                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                }
                                path.close()
                                drawPath(path, Color(0xFF3C6FA8))
                            }
                        }
                    }
                }
            }

                        
        }
    }

        val reveal = remember { Animatable(0f) }
        LaunchedEffect(showMap) {
            if (showMap) {
                reveal.animateTo(
                    1f,
                    tween(680, easing = androidx.compose.animation.core.CubicBezierEasing(0.18f, 0.9f, 0.28f, 1f))
                )
            } else {
                reveal.animateTo(
                    0f,
                    tween(380, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f))
                )
            }
        }
        if (showMap || reveal.value > 0.001f) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = 0.35f + 0.65f * reveal.value }
                    .drawWithContent {
                    val maxR = kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
                    val e = reveal.value
                    val r = maxR * (e * e * (3f - 2f * e)) * 1.02f
                    if (r <= 0.5f) return@drawWithContent
                    val center = if (starCenter != Offset.Zero) starCenter
                    else Offset(size.width - 40.dp.toPx(), 96.dp.toPx())
                    val cx = center.x
                    val cy = center.y
                    val path = Path().apply {
                        addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r))
                    }
                    clipPath(path) { this@drawWithContent.drawContent() }

                    // 光晕：一圈淡光追着扩张边缘
                    val glow = (1f - e).coerceIn(0f, 1f)
                    if (glow > 0.02f && r < maxR * 0.985f) {
                        val c = Offset(cx, cy)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF9FC8FF).copy(alpha = 0.22f * glow),
                                    Color.White.copy(alpha = 0.55f * glow),
                                    Color.Transparent
                                ),
                                center = c,
                                radius = r + 36.dp.toPx()
                            ),
                            radius = r + 36.dp.toPx(),
                            center = c
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.65f * glow),
                            radius = r,
                            center = c,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            ) {
                MemoryMapScreen(memory = memory, onClose = { showMap = false })
            }
        }

        achQueue.firstOrNull()?.let { a ->
            key(a.id) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                    AchievementToast(a) { achQueue = achQueue.drop(1) }
                }
            }
        }

        if (storyOpen) {
            StoryScreen(
                chapter = "第 ${storyChapter} 章",
                levelName = storyLevel,
                text = storyText,
                loading = storyLoading,
                onClose = {
                    if (storyText.isNotBlank()) {
                        diary = (listOf(DiaryEntry(System.currentTimeMillis(), "story", storyText)) + diary).take(200)
                        saveDiary(ctx, diary)
                    }
                    storyOpen = false
                }
            )
        }

        if (showRelation) {
            RelationshipScreen(
                ctx = ctx,
                messages = messages.size,
                diary = diary.size,
                memories = memory.size,
                onClose = { showRelation = false }
            )
        }

        if (showAchieve) {
            AchievementsScreen(
                stats = Achievements.stats(ctx, messages.size, memory.size, diary.size, feed.size),
                onClose = { showAchieve = false }
            )
        }

        if (showLog) {
            LogScreen(onClose = { showLog = false })
        }

        if (showMemory) {
            MemoryScreen(
                memory = memory,
                onClose = { showMemory = false }
            )
        }

        val bmp = cropBitmap
        if (bmp != null) {
            WallpaperCrop(
                bitmap = bmp,
                initialScale = settings.wallScale,
                initialX = settings.wallOffX,
                initialY = settings.wallOffY,
                onCancel = { cropBitmap = null },
                onDone = { sc, ox, oy ->
                    update(
                        settings.copy(
                            wallpaper = realPath(cropPath) + "?t=" + System.currentTimeMillis(),
                            wallScale = sc,
                            wallOffX = ox,
                            wallOffY = oy
                        )
                    )
                    Achievements.bump(ctx, "wallpaper")
                    achTick++
                    cropBitmap = null
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.DiaryScreen(
    backdrop: LayerBackdrop,
    settings: Settings,
    diary: List<DiaryEntry>,
    busy: Boolean,
    error: String,
    onWrite: () -> Unit,
    onAdd: (String) -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 112.dp, bottom = 120.dp)
    ) {
        item {
            GlassBox(backdrop, RoundedCornerShape(18.dp), Modifier.fillMaxWidth(), settings.glass) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("日记本", weight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Txt(
                            if (busy) "写着…" else "让${settings.botName.ifBlank { "她" }}写今天",
                            size = 13.sp, color = Color(0xFF2F7FE0),
                            modifier = Modifier.tap { onWrite() }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Txt(
                        if (shown) "收起来" else "自己写一条",
                        size = 13.sp, color = Color(0xFF2F7FE0),
                        modifier = Modifier.tap { shown = !shown }
                    )
                    if (shown) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.7f)).padding(10.dp)
                        ) {
                            BasicTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF16202C)),
                                maxLines = 5
                            )
                            if (input.isEmpty()) {
                                Txt("写点什么…", size = 14.sp, color = Color(0xFF9AA6B8))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Chip("保存") {
                            if (input.isNotBlank()) {
                                onAdd(input.trim())
                                input = ""
                                shown = false
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        items(diary.size) { i ->
            val d = diary[i]
            GlassBox(backdrop, RoundedCornerShape(18.dp), Modifier.fillMaxWidth().animateIn(d.ts), settings.glass) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (d.author) {
                                        "bot" -> Color(0xFF2F7FE0).copy(alpha = 0.14f)
                                        "story" -> Color(0xFF8B5CF6).copy(alpha = 0.16f)
                                        else -> Color(0xFF3C9A6E).copy(alpha = 0.14f)
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Txt(
                                when (d.author) {
                                    "bot" -> settings.botName.ifBlank { "她" }
                                    "story" -> "剧情"
                                    else -> settings.userName.ifBlank { "我" }
                                },
                                size = 10.sp,
                                color = when (d.author) {
                                    "bot" -> Color(0xFF2F7FE0)
                                    "story" -> Color(0xFF8B5CF6)
                                    else -> Color(0xFF3C9A6E)
                                }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Txt(
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(d.ts)),
                            size = 10.sp, color = Color(0xFF8A96A8)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Txt(d.text, size = 14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (diary.isEmpty()) {
            item {
                Txt(
                    "还没有日记。聊几句后点上面让她写一篇，或者自己写一条。",
                    Modifier.fillMaxWidth().padding(top = 40.dp),
                    size = 13.sp, color = Color(0xFF5B6B85)
                )
            }
        }
        if (error.isNotEmpty()) {
            item { Txt(error, size = 12.sp, color = Color(0xFFD14343)) }
        }
    }
}

@Composable
private fun ColumnScope.FeedScreen(
    backdrop: LayerBackdrop,
    settings: Settings,
    feed: List<Post>,
    busy: Boolean,
    error: String,
    onGenerate: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 112.dp, bottom = 120.dp)
    ) {
        item {
            GlassBox(backdrop, RoundedCornerShape(18.dp), Modifier.fillMaxWidth(), settings.glass) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt("她的动态", weight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Txt(
                        if (busy) "写着…" else "让她写一条",
                        size = 13.sp, color = Color(0xFF2F7FE0),
                        modifier = Modifier.tap(onGenerate)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        items(feed.size) { i ->
            val p = feed[i]
            GlassBox(backdrop, RoundedCornerShape(18.dp), Modifier.fillMaxWidth().animateIn(p.ts), settings.glass) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp))
                                .background(Ui.Accent.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Txt("动态", size = 10.sp, color = Ui.AccentDeep)
                        }
                        Spacer(Modifier.weight(1f))
                        Txt(
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(p.ts)),
                            size = 10.sp, color = Color(0xFF8A96A8)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Txt(p.text, size = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (feed.isEmpty()) {
            item {
                Txt(
                    "还没有动态，点上面让她写一条",
                    Modifier.fillMaxWidth().padding(top = 60.dp),
                    size = 13.sp, color = Color(0xFF5B6B85)
                )
            }
        }
        if (error.isNotEmpty()) {
            item { Txt(error, size = 12.sp, color = Color(0xFFD14343)) }
        }
    }
}

