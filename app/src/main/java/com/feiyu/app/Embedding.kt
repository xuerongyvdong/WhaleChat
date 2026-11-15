package com.feiyu.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * 本地语义向量：BAAI/bge-large-zh-v1.5（ONNX int8，1024 维）
 *
 * - 模型不进 APK，首次使用时从 hf-mirror 下载到 filesDir/models/bge-large-zh
 * - 分词器：BERT WordPiece（vocab.txt）
 * - 池化：取 [CLS] 后 L2 归一（BGE 官方用法）
 */
object LocalEmbedder {

    const val DIM = 1024
    const val ENGINE = "bge-large-zh"

    private const val MIRROR = "https://hf-mirror.com/Xenova/bge-large-zh-v1.5/resolve/main"
    private const val MODEL_URL = "$MIRROR/onnx/model_int8.onnx"
    private const val VOCAB_URL = "$MIRROR/vocab.txt"
    private const val MAX_LEN = 512

    private const val ASSET_MODEL = "models/bge/model_int8.onnx"
    private const val ASSET_VOCAB = "models/bge/vocab.txt"

    data class State(
        val downloaded: Boolean = false,
        val loading: Boolean = false,
        val ready: Boolean = false,
        val progress: Float = 0f,
        val error: String = ""
    )

    @Volatile
    var state: State = State()
        private set

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var clsId = 101
    private var sepId = 102
    private var unkId = 100

    fun modelDir(ctx: Context) = File(ctx.filesDir, "models/bge-large-zh")
    fun modelFile(ctx: Context) = File(modelDir(ctx), "model_int8.onnx")
    fun vocabFile(ctx: Context) = File(modelDir(ctx), "vocab.txt")

    /** APK 里是否自带模型 */
    fun hasBundled(ctx: Context): Boolean = runCatching {
        ctx.assets.open(ASSET_MODEL).use { it.available() > 50_000_000 }
    }.getOrDefault(false)

    /** 把内置模型复制到 filesDir（首次启动一次） */
    fun installFromAssets(ctx: Context, onProgress: (Float) -> Unit = {}): Boolean {
        val dir = modelDir(ctx)
        dir.mkdirs()
        state = state.copy(loading = true, progress = 0f, error = "")
        return runCatching {
            ctx.assets.open(ASSET_VOCAB).use { input ->
                vocabFile(ctx).outputStream().use { input.copyTo(it) }
            }
            val total = runCatching { ctx.assets.openFd(ASSET_MODEL).use { it.length } }.getOrDefault(0L)
            val tmp = File(dir, "model.tmp")
            ctx.assets.open(ASSET_MODEL).use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var read = 0L
                    var last = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pr = read.toFloat() / total
                            if (pr - last > 0.01f) {
                                last = pr
                                state = state.copy(progress = pr)
                                onProgress(pr)
                            }
                        }
                    }
                }
            }
            val target = modelFile(ctx)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            state = state.copy(downloaded = true, loading = false, progress = 1f)
            true
        }.getOrElse { e ->
            state = state.copy(loading = false, error = e.message ?: "初始化失败")
            false
        }
    }

    fun isDownloaded(ctx: Context): Boolean =
        modelFile(ctx).length() > 50_000_000L && vocabFile(ctx).exists()

    fun isReady(): Boolean = session != null

    fun download(ctx: Context, onProgress: (Float) -> Unit = {}): Boolean {
        val dir = modelDir(ctx)
        dir.mkdirs()
        state = state.copy(loading = true, progress = 0f, error = "")
        return runCatching {
            if (!vocabFile(ctx).exists() || vocabFile(ctx).length() < 1000) {
                fetch(VOCAB_URL, vocabFile(ctx)) { }
            }
            val tmp = File(dir, "model.tmp")
            fetch(MODEL_URL, tmp) { p ->
                state = state.copy(progress = p)
                onProgress(p)
            }
            if (tmp.length() < 50_000_000L) error("模型文件不完整")
            val target = modelFile(ctx)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
            state = state.copy(downloaded = true, loading = false, progress = 1f)
            true
        }.getOrElse { e ->
            state = state.copy(loading = false, error = e.message ?: "下载失败")
            false
        }
    }

    private fun fetch(url: String, target: File, onProgress: (Float) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "WhaleChat")
        conn.connect()
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var last = 0f
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) {
                        val p = read.toFloat() / total
                        if (p - last > 0.01f) {
                            last = p
                            onProgress(p)
                        }
                    }
                }
            }
        }
        conn.disconnect()
    }

    @Synchronized
    fun load(ctx: Context): Boolean {
        if (session != null) return true
        if (!isDownloaded(ctx)) return false
        state = state.copy(loading = true, error = "")
        return runCatching {
            vocab = buildVocab(vocabFile(ctx))
            clsId = vocab["[CLS]"] ?: 101
            sepId = vocab["[SEP]"] ?: 102
            unkId = vocab["[UNK]"] ?: 100
            env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env!!.createSession(modelFile(ctx).absolutePath, opts)
            state = state.copy(ready = true, loading = false, downloaded = true)
            true
        }.getOrElse { e ->
            state = state.copy(loading = false, error = e.message ?: "加载失败", ready = false)
            false
        }
    }

    fun embed(text: String): FloatArray? {
        val s = session ?: return null
        val e = env ?: return null
        if (text.isBlank()) return null
        val ids = tokenize(text)
        if (ids.size < 2) return null
        val seq = ids.size

        return runCatching {
            val idArr = LongArray(seq) { ids[it].toLong() }
            val maskArr = LongArray(seq) { 1L }
            val typeArr = LongArray(seq) { 0L }
            val shape = longArrayOf(1, seq.toLong())

            OnnxTensor.createTensor(e, LongBuffer.wrap(idArr), shape).use { t1 ->
                OnnxTensor.createTensor(e, LongBuffer.wrap(maskArr), shape).use { t2 ->
                    OnnxTensor.createTensor(e, LongBuffer.wrap(typeArr), shape).use { t3 ->
                        val inputs = HashMap<String, OnnxTensor>()
                        val names = s.inputNames
                        if ("input_ids" in names) inputs["input_ids"] = t1
                        if ("attention_mask" in names) inputs["attention_mask"] = t2
                        if ("token_type_ids" in names) inputs["token_type_ids"] = t3
                        s.run(inputs).use { out ->
                            @Suppress("UNCHECKED_CAST")
                            val hidden = out[0].value as Array<Array<FloatArray>>
                            val cls = hidden[0][0].copyOf()
                            normalize(cls)
                            cls
                        }
                    }
                }
            }
        }.getOrNull()
    }

    private fun normalize(v: FloatArray): FloatArray {
        var n = 0f
        for (x in v) n += x * x
        n = sqrt(n)
        if (n > 1e-6f) for (i in v.indices) v[i] /= n
        return v
    }

    private fun buildVocab(f: File): Map<String, Int> {
        val map = HashMap<String, Int>(40000)
        var idx = 0
        f.forEachLine { line ->
            val token = line.trimEnd('\n', '\r')
            if (token.isNotEmpty()) {
                map.putIfAbsent(token, idx)
                idx++
            }
        }
        return map
    }

    private fun tokenize(text: String): IntArray {
        if (vocab.isEmpty()) return IntArray(0)
        val ids = ArrayList<Int>()
        ids.add(clsId)

        val cleaned = text.lowercase().trim()
        var i = 0
        while (i < cleaned.length && ids.size < MAX_LEN - 1) {
            val c = cleaned[i]
            when {
                c.isWhitespace() -> i++
                isCjk(c) || isPunct(c) -> {
                    addWord(c.toString(), ids)
                    i++
                }
                else -> {
                    var j = i
                    while (j < cleaned.length && !cleaned[j].isWhitespace() &&
                        !isCjk(cleaned[j]) && !isPunct(cleaned[j])
                    ) j++
                    addWord(cleaned.substring(i, j), ids)
                    i = j
                }
            }
        }
        ids.add(sepId)
        return ids.toIntArray()
    }

    private fun addWord(word: String, ids: ArrayList<Int>) {
        if (word.isEmpty()) return
        vocab[word]?.let { ids.add(it); return }
        var start = 0
        val pieces = ArrayList<Int>()
        while (start < word.length) {
            var end = word.length
            var found: String? = null
            while (end > start) {
                val sub = word.substring(start, end)
                val candidate = if (start > 0) "##$sub" else sub
                if (vocab.containsKey(candidate)) {
                    found = candidate
                    break
                }
                end--
            }
            if (found == null) {
                pieces.add(unkId)
                ids.addAll(pieces)
                return
            }
            pieces.add(vocab[found]!!)
            start = end
        }
        ids.addAll(pieces)
    }

    private fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF || c.code in 0x3400..0x4DBF || c.code in 0xF900..0xFAFF

    private fun isPunct(c: Char): Boolean =
        c in "，。！？、；：（）【】《》“”‘’…—～·,.!?;:()[]<>\"'`~@#$%^&*-_=+/\\|"

    fun clearModel(ctx: Context) {
        runCatching {
            session?.close()
            session = null
        }
        modelDir(ctx).deleteRecursively()
        state = State()
    }
}


/** 设置页里的「本地语义模型」控制条 */
@Composable
fun LocalModelRow() {
    val ctx = LocalContext.current.applicationContext
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var ready by remember { mutableStateOf(LocalEmbedder.isReady()) }
    var downloaded by remember { mutableStateOf(LocalEmbedder.isDownloaded(ctx)) }
    val scope = rememberCoroutineScope()

    Column {
        val bundled = LocalEmbedder.hasBundled(ctx)
        Txt(
            if (bundled) "本地语义模型（已内置 · bge-large-zh-v1.5 · 1024 维）"
            else "本地语义模型（bge-large-zh-v1.5 · 约 330MB）",
            size = 12.sp, color = Color(0xFF5B6B85)
        )
        Spacer(Modifier.height(6.dp))
        val status = when {
            busy && progress > 0f -> "下载中 ${(progress * 100).toInt()}%"
            busy -> "准备中…"
            ready -> "已启用 · 1024 维"
            downloaded -> "已下载，未加载"
            bundled -> "已内置，待初始化"
            else -> "未启用（目前用轻量哈希向量）"
        }
        Txt(status, size = 12.sp, color = if (ready) Color(0xFF2E7D5B) else Color(0xFF8A96A8))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!ready) {
                Chip(
                    when {
                        downloaded -> "加载模型"
                        bundled -> "初始化内置模型"
                        else -> "下载并启用"
                    }
                ) {
                    if (busy) return@Chip
                    busy = true
                    scope.launch(Dispatchers.IO) {
                        val ok = if (LocalEmbedder.isDownloaded(ctx)) true
                        else if (LocalEmbedder.hasBundled(ctx))
                            LocalEmbedder.installFromAssets(ctx) { p -> progress = p }
                        else LocalEmbedder.download(ctx) { p -> progress = p }
                        if (ok && LocalEmbedder.load(ctx)) {
                            // 旧记忆后台重算
                            runCatching { EMB_STORE?.reembedAll() }
                        }
                        ready = LocalEmbedder.isReady()
                        downloaded = LocalEmbedder.isDownloaded(ctx)
                        busy = false
                        progress = 0f
                    }
                }
            }
            if (downloaded) {
                Chip("删除模型", danger = true) {
                    LocalEmbedder.clearModel(ctx)
                    ready = false
                    downloaded = false
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Txt(
            "启用后记忆用真语义检索（同义词、模糊描述也能召回）；旧记忆会在后台慢慢重算。",
            size = 11.sp, color = Color(0xFF8A96A8)
        )
    }
}

/** 由 App 注入的记忆库实例，方便设置页触发重算 */
var EMB_STORE: MemoryStore? = null

fun bindEmbedder(store: MemoryStore) {
    EMB_STORE = store
    if (LocalEmbedder.isReady()) {
        store.localEmbed = { t -> LocalEmbedder.embed(t) }
    }
}
