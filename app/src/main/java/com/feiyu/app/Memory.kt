package com.feiyu.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 多重记忆系统（Squire 2004 脑区分区 + Hebbian 1975/1995 权重更新）
 *
 * 在基础图上增加了三层「像人」的机制：
 * - 遗忘曲线：每条记忆有 stability（稳定度），召回随时间衰减
 * - 再巩固：被想起一次就更牢一点，同时刷新「最后确认时间」
 * - 睡眠巩固：定期把相似情景合并成一条语义记忆，原条目归档变模糊
 * - 元记忆：每条记忆带来源与置信度，回答时可以「我记得你说过」或「我有点记不清」
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
    val vec: FloatArray,
    /** 来源：user 主人说的 / inferred 我推断的 / mood 聊天氛围 */
    val origin: String = if (source == "owner") "user" else "inferred",
    /** 置信度 0~1 */
    val confidence: Float = if (source == "owner") 0.8f else 0.55f,
    /** 最后确认（被想起或再次被说起）的时间 */
    val lastConfirm: Long = ts,
    /** 稳定度，单位天，越大越难忘 */
    val stability: Float = if (source == "owner") 5f else 3f,
    /** 复习次数 */
    val reviewCount: Int = 0,
    /** 是否已归档（模糊层） */
    val archived: Boolean = false,
    /** 向量来源：hash 特征哈希 / bge-large-zh 本地模型 */
    val engine: String = "hash"
)

data class MemEdge(val from: String, val to: String, val w: Float, val kind: String)

class MemoryStore(ctx: Context) {

    companion object {
        const val DIM = 512
        const val REGION_HIPPO = "hippocampus"
        const val REGION_NEO = "neocortex"
        const val REGION_FLASH = "flash"

        /** 多久跑一次睡眠巩固 */
        const val CONSOLIDATE_INTERVAL = 20 * 3600_000L

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

        /** 遗忘曲线：越久没被确认，保留率越低 */
        fun retention(n: MemNode, now: Long): Float {
            val days = ((now - n.lastConfirm).coerceAtLeast(0L)) / 86_400_000.0
            val s = n.stability.coerceAtLeast(0.5f).toDouble()
            return exp(-days / s).toFloat().coerceIn(0f, 1f)
        }

        fun confidenceLabel(c: Float): String = when {
            c >= 0.75f -> "确定"
            c >= 0.45f -> "较确定"
            else -> "不太确定"
        }

        fun originLabel(o: String): String = when (o) {
            "user" -> "主人说的"
            "consolidated" -> "整理得出的"
            "mood" -> "聊天氛围"
            else -> "我猜的"
        }
    }

    val nodes = LinkedHashMap<String, MemNode>()
    val edges = ArrayList<MemEdge>()
    private val file = File(ctx.filesDir, "memory_graph.json")
    private var lastId = 0L
    private var lastConsolidate = 0L

    /** 由 App 注入：接入本地语义模型后返回 1024 维向量 */
    var localEmbed: ((String) -> FloatArray?)? = null

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

        val now = System.currentTimeMillis()
        val id = "m" + (++lastId) + "_" + now.toString(36)
        val local = vecOverride ?: localEmbed?.invoke(clean)
        val vec = local ?: embed(clean)
        val engine = if (vec.size == LocalEmbedder.DIM) LocalEmbedder.ENGINE else "hash"

        // 与已有记忆高度相似时，视为同一件事被再次确认：加固而不是新建
        val twin = nodes.values.filter { cosine(vec, it.vec) >= 0.94f }.maxByOrNull { it.weight }
        if (twin != null) {
            val upd = twin.copy(
                text = clean,
                weight = (twin.weight + 6f).coerceAtMost(100f),
                confidence = (twin.confidence + 0.08f).coerceAtMost(1f),
                lastConfirm = now,
                stability = (twin.stability * 1.6f).coerceAtMost(120f),
                reviewCount = twin.reviewCount + 1,
                archived = false
            )
            nodes[twin.id] = upd
            save()
            return upd
        }

        val node = MemNode(
            id, clean, region, w0, surprise, v, a, now, source, vec,
            origin = if (source == "owner") "user" else "inferred",
            confidence = if (source == "owner") 0.8f else 0.55f,
            lastConfirm = now,
            stability = if (source == "owner") 5f else 3f,
            engine = engine
        )
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
        val qv = queryVec ?: localEmbed?.invoke(query) ?: embed(query)
        val intent = intentOf(query)
        val bud = budget(intent)
        val now = System.currentTimeMillis()

        val cos = HashMap<String, Float>()
        val qTokens = tokens(query)
        for (n in nodes.values) {
            cos[n.id] = if (n.vec.size == qv.size) {
                cosine(qv, n.vec)
            } else {
                // 新旧向量空间不同，改用词面重叠度
                jaccard(qTokens, tokens(n.text)) * 0.9f
            }
        }

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
            // 遗忘曲线：越久没被确认，越难被想起
            val keep = retention(n, now)
            val effW = n.weight * (0.5f + 0.5f * keep) * (if (n.archived) 0.3f else 1f)
            val base = 0.5f * c + 0.3f * act + 0.2f * (effW / maxW)
            val bonus = bud[n.region] ?: 0.2f
            val confBonus = 0.85f + 0.3f * n.confidence
            n to (base * (0.75f + 0.5f * bonus) * confBonus)
        }.filter { it.second > 0.12f }
            .sortedByDescending { it.second }
            .take(topK)

        // 再巩固：被想起的记忆变牢，置信度略升
        if (scored.isNotEmpty()) {
            for ((n, _) in scored.take(3)) {
                val upd = n.copy(
                    lastConfirm = now,
                    stability = (n.stability * 1.35f).coerceAtMost(180f),
                    reviewCount = n.reviewCount + 1
                )
                nodes[n.id] = upd
            }
            save()
        }
        return scored
    }

    fun formatForPrompt(hits: List<Pair<MemNode, Float>>): String {
        if (hits.isEmpty()) return ""
        val lines = hits.joinToString("\n") { (n, s) ->
            val tag = confidenceLabel(n.confidence)
            val origin = originLabel(n.origin)
            "- [${regionLabel(n.region)}|$origin|$tag] ${n.text.take(80)}（强度${n.weight.toInt()}）"
        }
        return "【长期记忆·按脑区标注，供参考，不要逐字复述】\n$lines\n" +
            "只有标「确定」的可以当事实说；标「不太确定」的要用试探口气，例如「我有点记不清，你是不是说过…」。"
    }

    fun regionLabel(r: String): String = when (r) {
        REGION_HIPPO -> "海马·情景"
        REGION_NEO -> "新皮层·语义"
        REGION_FLASH -> "杏仁核·闪光"
        else -> r
    }

    // ---------------- 睡眠巩固 ----------------

    /**
     * 整理一次记忆：
     * 1) 相似情景合并成一条语义记忆
     * 2) 长期没被确认的降权、归档为模糊层
     * 返回 (合并条数, 归档条数)
     */
    fun consolidate(now: Long = System.currentTimeMillis()): Pair<Int, Int> {
        if (nodes.isEmpty()) return 0 to 0

        var merged = 0
        val used = HashSet<String>()
        val list = nodes.values.toList()
        for (i in list.indices) {
            val a = list[i]
            if (a.id in used) continue
            val group = ArrayList<MemNode>()
            group.add(a)
            for (j in i + 1 until list.size) {
                val b = list[j]
                if (b.id in used) continue
                if (cosine(a.vec, b.vec) >= 0.72f) {
                    group.add(b)
                }
            }
            if (group.size < 3) continue

            group.forEach { used.add(it.id) }
            val newest = group.maxByOrNull { it.ts } ?: a
            val summary = group.sortedByDescending { it.ts }
                .map { it.text.trim().take(40) }
                .distinct()
                .take(3)
                .joinToString("；")
            val avgW = group.sumOf { it.weight.toDouble() }.toFloat() / group.size
            val avgConf = group.sumOf { it.confidence.toDouble() }.toFloat() / group.size

            val id = "m" + (++lastId) + "_c" + now.toString(36)
            val vec = embed(summary)
            val node = MemNode(
                id = id,
                text = summary,
                region = REGION_NEO,
                weight = (avgW * 1.2f).coerceAtMost(100f),
                surprise = group.minOf { it.surprise },
                valence = group.sumOf { it.valence.toDouble() }.toFloat() / group.size,
                arousal = group.sumOf { it.arousal.toDouble() }.toFloat() / group.size,
                ts = newest.ts,
                source = "other",
                vec = vec,
                origin = "consolidated",
                confidence = (avgConf * 0.95f).coerceIn(0f, 1f),
                lastConfirm = now,
                stability = 14f
            )
            nodes[id] = node

            // 原条目归档，变模糊
            for (n in group) {
                nodes[n.id] = n.copy(archived = true, weight = (n.weight * 0.45f).coerceAtLeast(1f))
            }
            edges.add(MemEdge(id, newest.id, 0.6f, "abstraction"))
            merged++
        }

        // 长期不用的记忆归档
        var archived = 0
        for ((id, n) in nodes.entries.toList()) {
            if (n.archived) continue
            val keep = retention(n, now)
            val daysIdle = (now - n.lastConfirm) / 86_400_000.0
            if (keep < 0.18f && daysIdle > 14) {
                nodes[id] = n.copy(archived = true, weight = (n.weight * 0.6f).coerceAtLeast(1f))
                archived++
            }
        }

        lastConsolidate = now
        save()
        return merged to archived
    }

    /** 把向量空间不一致的旧记忆重新编码，返回处理条数 */
    fun reembedAll(onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val fn = localEmbed ?: return 0
        val targets = nodes.values.filter { it.vec.size != LocalEmbedder.DIM }
        if (targets.isEmpty()) return 0
        var done = 0
        for (n in targets) {
            val v = runCatching { fn(n.text) }.getOrNull() ?: continue
            nodes[n.id] = n.copy(vec = v, engine = LocalEmbedder.ENGINE)
            done++
            onProgress(done, targets.size)
            if (done % 20 == 0) save()
        }
        if (done > 0) {
            // 重建语义边
            edges.removeAll { it.kind == "semantic" }
            val list = nodes.values.toList()
            for (i in list.indices) {
                val a = list[i]
                val near = list.drop(i + 1)
                    .map { it to cosine(a.vec, it.vec) }
                    .sortedByDescending { it.second }
                    .take(2)
                for ((b, sim) in near) {
                    if (sim > 0.35f) {
                        edges.add(MemEdge(a.id, b.id, sim, "semantic"))
                        edges.add(MemEdge(b.id, a.id, sim * 0.8f, "semantic"))
                    }
                }
            }
        }
        save()
        return done
    }

    fun consolidateIfDue(now: Long = System.currentTimeMillis()): Pair<Int, Int> {
        if (now - lastConsolidate < CONSOLIDATE_INTERVAL) return 0 to 0
        return consolidate(now)
    }

    // ---------------- 持久化 ----------------

    fun exportJson(): JSONObject {
        val arr = JSONArray()
        for (n in nodes.values) arr.put(nodeToJson(n))
        val earr = JSONArray()
        for (e in edges) {
            earr.put(
                JSONObject().put("from", e.from).put("to", e.to)
                    .put("w", e.w).put("kind", e.kind)
            )
        }
        return JSONObject().put("nodes", arr).put("edges", earr)
            .put("lastId", lastId).put("lastConsolidate", lastConsolidate)
    }

    fun importJson(root: JSONObject) {
        nodes.clear()
        edges.clear()
        lastId = root.optLong("lastId", 0)
        lastConsolidate = root.optLong("lastConsolidate", 0)
        val arr = root.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val n = nodeFromJson(arr.getJSONObject(i)) ?: continue
            nodes[n.id] = n
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

    fun save() {
        runCatching {
            file.writeText(exportJson().toString())
        }
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            lastId = root.optLong("lastId", 0)
            lastConsolidate = root.optLong("lastConsolidate", 0)
            val arr = root.optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val n = nodeFromJson(arr.getJSONObject(i)) ?: continue
                nodes[n.id] = n
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

    private fun nodeToJson(n: MemNode): JSONObject = JSONObject()
        .put("id", n.id).put("text", n.text).put("region", n.region)
        .put("weight", n.weight).put("surprise", n.surprise)
        .put("valence", n.valence).put("arousal", n.arousal)
        .put("ts", n.ts).put("source", n.source)
        .put("origin", n.origin).put("confidence", n.confidence)
        .put("lastConfirm", n.lastConfirm).put("stability", n.stability)
        .put("reviewCount", n.reviewCount).put("archived", n.archived)
        .put("engine", n.engine)
        .put("vec", vecToString(n.vec))

    private fun nodeFromJson(o: JSONObject): MemNode? = runCatching {
        val ts = o.optLong("ts", 0L)
        MemNode(
            id = o.getString("id"),
            text = o.getString("text"),
            region = o.getString("region"),
            weight = o.optDouble("weight", 0.0).toFloat(),
            surprise = o.optDouble("surprise", 0.0).toFloat(),
            valence = o.optDouble("valence", 0.0).toFloat(),
            arousal = o.optDouble("arousal", 0.0).toFloat(),
            ts = ts,
            source = o.optString("source", "other"),
            vec = stringToVec(o.optString("vec", "")),
            origin = o.optString("origin", if (o.optString("source") == "owner") "user" else "inferred"),
            confidence = o.optDouble("confidence", 0.6).toFloat(),
            lastConfirm = o.optLong("lastConfirm", ts),
            stability = o.optDouble("stability", 3.0).toFloat(),
            reviewCount = o.optInt("reviewCount", 0),
            archived = o.optBoolean("archived", false),
            engine = o.optString("engine", if (o.optString("vec", "").count { it == ',' } > 700) LocalEmbedder.ENGINE else "hash")
        )
    }.getOrNull()

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
