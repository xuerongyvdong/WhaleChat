package com.feiyu.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 多重记忆系统（Squire 2004 脑区分区 + Hebbian 1975/1995 权重更新）
 *
 * - 脑区标签：hippocampus（情景）/ neocortex（语义）/ flash（情绪闪光）
 * - 编码：候选池 → 判断，惊异度 surprise = 1 - max Jaccard
 * - 权重：w0 = w_base * (1 + surprise)，w_base = 40（主人来源）/ 15（其他）
 * - 存储：有向加权图，节点带 512 维语义向量
 * - 检索：图扩散激活 + 向量模式补全，按意图分配注意力预算
 */
data class MemNode(
    val id: String,
    val text: String,
    val region: String,
    val weight: Float,
    val surprise: Float,
    val valence: Float,
    val arousal: Float,
    val ts: Long,
    val source: String,
    val vec: FloatArray
)

data class MemEdge(val from: String, val to: String, val w: Float, val kind: String)

class MemoryStore(ctx: Context) {

    companion object {
        const val DIM = 512
        const val REGION_HIPPO = "hippocampus"
        const val REGION_NEO = "neocortex"
        const val REGION_FLASH = "flash"

        private val POSITIVE = listOf(
            "开心", "高兴", "喜欢", "爱", "好棒", "厉害", "谢谢", "温暖", "幸福", "可爱",
            "哈哈", "嘿嘿", "期待", "放心", "满意", "舒服"
        )
        private val NEGATIVE = listOf(
            "难过", "伤心", "生气", "烦", "累", "痛", "怕", "害怕", "焦虑", "委屈",
            "失望", "孤独", "讨厌", "滚", "哭", "崩溃"
        )
        private val INTENSE = listOf("！", "!", "超", "特别", "非常", "最", "太", "真的", "巨")

        /** 512 维特征哈希向量（字符二元组），L2 归一 */
        fun embed(text: String): FloatArray {
            val v = FloatArray(DIM)
            val t = text.lowercase().filter { it.isLetterOrDigit() }
            if (t.length < 2) return v
            for (i in 0 until t.length - 1) {
                val h = ("" + t[i] + t[i + 1]).hashCode()
                val idx = (h and 0x7fffffff) % DIM
                val sign = if ((h ushr 8) and 1 == 0) 1f else -1f
                v[idx] += sign
            }
            var norm = 0f
            for (x in v) norm += x * x
            norm = sqrt(norm)
            if (norm > 1e-6f) for (i in v.indices) v[i] /= norm
            return v
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }

        fun tokens(text: String): Set<String> {
            val t = text.lowercase().filter { it.isLetterOrDigit() }
            val out = HashSet<String>()
            for (i in 0 until t.length - 1) out.add("" + t[i] + t[i + 1])
            return out
        }

        fun jaccard(a: Set<String>, b: Set<String>): Float {
            if (a.isEmpty() || b.isEmpty()) return 0f
            var inter = 0
            for (x in a) if (x in b) inter++
            return inter.toFloat() / (a.size + b.size - inter).toFloat()
        }
    }

    val nodes = LinkedHashMap<String, MemNode>()
    val edges = ArrayList<MemEdge>()
    private val file = File(ctx.filesDir, "memory_graph.json")
    private var lastId = 0L

    init {
        load()
    }

    val size: Int get() = nodes.size

    fun regionCounts(): Map<String, Int> {
        val m = HashMap<String, Int>()
        for (n in nodes.values) m[n.region] = (m[n.region] ?: 0) + 1
        return m
    }

    // ---------------- 编码 ----------------

    /** returns null if judged as noise */
    fun encode(
        text: String,
        source: String,
        aiWeight: Float? = null,
        aiRegion: String? = null,
        aiValence: Float? = null,
        aiArousal: Float? = null,
        vecOverride: FloatArray? = null
    ): MemNode? {
        val clean = text.trim()
        if (clean.length < 4) return null
        val tk = tokens(clean)
        if (tk.size < 2) return null

        // 1) 惊异度：与既有记忆的最大 Jaccard 相似度取补
        var maxSim = 0f
        for (n in nodes.values) {
            val j = jaccard(tk, tokens(n.text))
            if (j > maxSim) maxSim = j
        }
        val surprise = (1f - maxSim).coerceIn(0f, 1f)

        // 2) 候选池 → 判断：过于重复且无情绪则不写入
        val valence = valenceOf(clean)
        val arousal = arousalOf(clean)
        val emotional = abs(valence) >= 0.6f || arousal >= 0.6f
        if (maxSim > 0.92f && !emotional) return null

        // 3) 权重：AI 判定优先，否则用 Hebbian 初值 w0 = w_base * (1 + surprise)
        val base = if (source == "owner") 40f else 15f
        val w0 = aiWeight?.coerceIn(1f, 100f) ?: (base * (1f + surprise))
        val v = aiValence?.coerceIn(-1f, 1f) ?: valence
        val a = aiArousal?.coerceIn(0f, 1f) ?: arousal

        // 4) 脑区判定：AI 指定优先
        val region = when {
            aiRegion == REGION_HIPPO || aiRegion == REGION_NEO || aiRegion == REGION_FLASH -> aiRegion
            emotional -> REGION_FLASH
            source == "owner" && w0 >= 60f -> REGION_HIPPO
            w0 >= 45f -> REGION_HIPPO
            else -> REGION_NEO
        }

        val id = "m" + (++lastId) + "_" + System.currentTimeMillis().toString(36)
        val vec = vecOverride ?: embed(clean)
        val node = MemNode(id, clean, region, w0, surprise, v, a, System.currentTimeMillis(), source, vec)
        nodes[id] = node

        // 5) 建边：语义近邻 + 时间邻近
        val scored = nodes.values.filter { it.id != id }
            .map { it to cosine(vec, it.vec) }
            .sortedByDescending { it.second }
            .take(3)
        for ((other, sim) in scored) {
            if (sim > 0.15f) {
                edges.add(MemEdge(id, other.id, sim, "semantic"))
                edges.add(MemEdge(other.id, id, sim * 0.8f, "semantic"))
            }
        }
        val prev = nodes.values.lastOrNull { it.id != id }
        if (prev != null) {
            edges.add(MemEdge(prev.id, id, 0.5f, "temporal"))
        }

        save()
        return node
    }

    private fun valenceOf(t: String): Float {
        var pos = 0
        var neg = 0
        for (w in POSITIVE) if (t.contains(w)) pos++
        for (w in NEGATIVE) if (t.contains(w)) neg++
        if (pos + neg == 0) return 0f
        return (pos - neg).toFloat() / (pos + neg).toFloat()
    }

    private fun arousalOf(t: String): Float {
        var n = 0
        for (w in INTENSE) if (t.contains(w)) n++
        val ex = t.count { it == '!' || it == '！' }
        return (n * 0.25f + ex * 0.2f).coerceIn(0f, 1f)
    }

    // ---------------- 检索 ----------------

    enum class Intent { EPISODIC, SEMANTIC, EMOTIONAL, MIXED }

    fun intentOf(q: String): Intent {
        val s = q
        return when {
            listOf("之前", "上次", "记得", "那天", "以前", "刚才").any { s.contains(it) } -> Intent.EPISODIC
            listOf("难过", "开心", "难受", "害怕", "生气", "委屈").any { s.contains(it) } -> Intent.EMOTIONAL
            listOf("为什么", "怎么", "是什么", "区别", "原理").any { s.contains(it) } -> Intent.SEMANTIC
            else -> Intent.MIXED
        }
    }

    private fun budget(intent: Intent): Map<String, Float> = when (intent) {
        Intent.EPISODIC -> mapOf(REGION_HIPPO to 0.6f, REGION_NEO to 0.2f, REGION_FLASH to 0.2f)
        Intent.SEMANTIC -> mapOf(REGION_NEO to 0.6f, REGION_HIPPO to 0.3f, REGION_FLASH to 0.1f)
        Intent.EMOTIONAL -> mapOf(REGION_FLASH to 0.55f, REGION_HIPPO to 0.3f, REGION_NEO to 0.15f)
        Intent.MIXED -> mapOf(REGION_HIPPO to 0.4f, REGION_NEO to 0.35f, REGION_FLASH to 0.25f)
    }

    fun retrieve(query: String, topK: Int = 6, queryVec: FloatArray? = null): List<Pair<MemNode, Float>> {
        if (nodes.isEmpty() || (query.isBlank() && queryVec == null)) return emptyList()
        val qv = queryVec ?: embed(query)
        val intent = intentOf(query)
        val bud = budget(intent)

        val cos = HashMap<String, Float>()
        for (n in nodes.values) cos[n.id] = cosine(qv, n.vec)

        // 图通道：从种子扩散激活
        val activation = HashMap<String, Float>()
        val seeds = cos.entries.sortedByDescending { it.value }.take(5)
        for (s in seeds) if (s.value > 0.1f) activation[s.key] = s.value
        for (e in edges) {
            val a = activation[e.from] ?: continue
            val add = a * e.w * 0.45f
            val old = activation[e.to] ?: 0f
            if (add > old) activation[e.to] = add
        }

        val maxW = nodes.values.maxOf { it.weight }.coerceAtLeast(1f)
        val scored = nodes.values.map { n ->
            val c = cos[n.id] ?: 0f
            val act = activation[n.id] ?: 0f
            val base = 0.5f * c + 0.3f * act + 0.2f * (n.weight / maxW)
            val bonus = bud[n.region] ?: 0.2f
            n to (base * (0.75f + 0.5f * bonus))
        }.filter { it.second > 0.12f }
            .sortedByDescending { it.second }
            .take(topK)
        return scored
    }

    fun formatForPrompt(hits: List<Pair<MemNode, Float>>): String {
        if (hits.isEmpty()) return ""
        val lines = hits.joinToString("\n") { (n, s) ->
            "- [${regionLabel(n.region)}] ${n.text.take(80)}（强度${n.weight.toInt()}，相关${"%.2f".format(s)}）"
        }
        return "【长期记忆·按脑区标注，供参考，不要逐字复述】\n$lines"
    }

    fun regionLabel(r: String): String = when (r) {
        REGION_HIPPO -> "海马·情景"
        REGION_NEO -> "新皮层·语义"
        REGION_FLASH -> "杏仁核·闪光"
        else -> r
    }

    fun exportJson(): JSONObject {
        val arr = JSONArray()
        for (n in nodes.values) {
            arr.put(
                JSONObject()
                    .put("id", n.id).put("text", n.text).put("region", n.region)
                    .put("weight", n.weight).put("surprise", n.surprise)
                    .put("valence", n.valence).put("arousal", n.arousal)
                    .put("ts", n.ts).put("source", n.source)
                    .put("vec", vecToString(n.vec))
            )
        }
        val earr = JSONArray()
        for (e in edges) {
            earr.put(
                JSONObject().put("from", e.from).put("to", e.to)
                    .put("w", e.w).put("kind", e.kind)
            )
        }
        return JSONObject().put("nodes", arr).put("edges", earr).put("lastId", lastId)
    }

    fun importJson(root: JSONObject) {
        nodes.clear()
        edges.clear()
        lastId = root.optLong("lastId", 0)
        val arr = root.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            nodes[id] = MemNode(
                id, o.getString("text"), o.getString("region"),
                o.optDouble("weight", 0.0).toFloat(),
                o.optDouble("surprise", 0.0).toFloat(),
                o.optDouble("valence", 0.0).toFloat(),
                o.optDouble("arousal", 0.0).toFloat(),
                o.optLong("ts", 0L), o.optString("source", "other"),
                stringToVec(o.optString("vec", ""))
            )
        }
        val earr = root.optJSONArray("edges") ?: JSONArray()
        for (i in 0 until earr.length()) {
            val o = earr.getJSONObject(i)
            edges.add(
                MemEdge(
                    o.getString("from"), o.getString("to"),
                    o.optDouble("w", 0.0).toFloat(), o.optString("kind", "semantic")
                )
            )
        }
        save()
    }

    fun clear() {
        nodes.clear()
        edges.clear()
        save()
    }

    // ---------------- 持久化 ----------------

    fun save() {
        runCatching {
            val arr = JSONArray()
            for (n in nodes.values) {
                val o = JSONObject()
                    .put("id", n.id).put("text", n.text).put("region", n.region)
                    .put("weight", n.weight).put("surprise", n.surprise)
                    .put("valence", n.valence).put("arousal", n.arousal)
                    .put("ts", n.ts).put("source", n.source)
                    .put("vec", vecToString(n.vec))
                arr.put(o)
            }
            val earr = JSONArray()
            for (e in edges) {
                earr.put(
                    JSONObject().put("from", e.from).put("to", e.to)
                        .put("w", e.w).put("kind", e.kind)
                )
            }
            val root = JSONObject().put("nodes", arr).put("edges", earr).put("lastId", lastId)
            file.writeText(root.toString())
        }
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            lastId = root.optLong("lastId", 0)
            val arr = root.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                nodes[id] = MemNode(
                    id, o.getString("text"), o.getString("region"),
                    o.optDouble("weight", 0.0).toFloat(),
                    o.optDouble("surprise", 0.0).toFloat(),
                    o.optDouble("valence", 0.0).toFloat(),
                    o.optDouble("arousal", 0.0).toFloat(),
                    o.optLong("ts", 0L), o.optString("source", "other"),
                    stringToVec(o.optString("vec", ""))
                )
            }
            val earr = root.optJSONArray("edges") ?: JSONArray()
            for (i in 0 until earr.length()) {
                val o = earr.getJSONObject(i)
                edges.add(
                    MemEdge(
                        o.getString("from"), o.getString("to"),
                        o.optDouble("w", 0.0).toFloat(), o.optString("kind", "semantic")
                    )
                )
            }
        }
    }

    private fun vecToString(v: FloatArray): String =
        v.joinToString(",") { ((it * 10000).toInt()).toString() }

    private fun stringToVec(s: String): FloatArray {
        val out = FloatArray(DIM)
        if (s.isEmpty()) return out
        val parts = s.split(",")
        for (i in 0 until minOf(parts.size, DIM)) {
            out[i] = (parts[i].toIntOrNull() ?: 0) / 10000f
        }
        return out
    }
}
