package com.feiyu.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 真正的语义向量：调用 OpenAI 兼容的 /embeddings 接口，
 * 再把任意维度随机投影到 512 维（与历史记忆保持同一空间、可直接余弦比较）。
 */
object Embedding {

    private const val DIM = 512
    private val projCache = HashMap<Int, Array<FloatArray>>()

    fun configured(baseUrl: String, key: String): Boolean =
        baseUrl.isNotBlank() && key.isNotBlank()

    /** 批量向量化；失败返回 null */
    suspend fun embed(baseUrl: String, key: String, model: String, texts: List<String>): List<FloatArray>? =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            runCatching {
                val base = baseUrl.trim().trimEnd('/')
                val url = if (base.endsWith("/embeddings")) base else "$base/embeddings"
                val body = JSONObject()
                    .put("model", model.ifBlank { "BAAI/bge-m3" })
                    .put("input", JSONArray(texts))

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${key.trim().replace(" ", "")}")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                val resp = BufferedReader(
                    (if (code in 200..299) conn.inputStream else conn.errorStream).reader()
                ).readText()
                conn.disconnect()
                if (code !in 200..299) return@withContext null

                val arr = JSONObject(resp).optJSONArray("data") ?: return@withContext null
                val out = ArrayList<FloatArray>(arr.length())
                for (i in 0 until arr.length()) {
                    val emb = arr.getJSONObject(i).optJSONArray("embedding") ?: continue
                    val raw = FloatArray(emb.length()) { emb.optDouble(it, 0.0).toFloat() }
                    out.add(project(raw))
                }
                if (out.size != texts.size) null else out
            }.getOrNull()
        }

    /** 随机高斯投影到 512 维 + L2 归一（近似保持余弦相似度） */
    private fun project(v: FloatArray): FloatArray {
        if (v.size == DIM) return normalize(v)
        val matrix = projCache.getOrPut(v.size) {
            val rnd = Random(v.size * 131 + 7)
            Array(DIM) { FloatArray(v.size) { gauss(rnd) } }
        }
        val out = FloatArray(DIM)
        for (i in 0 until DIM) {
            var s = 0f
            val row = matrix[i]
            for (j in v.indices) s += row[j] * v[j]
            out[i] = s
        }
        return normalize(out)
    }

    private fun normalize(v: FloatArray): FloatArray {
        var n = 0f
        for (x in v) n += x * x
        n = sqrt(n)
        if (n > 1e-6f) for (i in v.indices) v[i] /= n
        return v
    }

    private fun gauss(rnd: Random): Float {
        // Box-Muller
        val u1 = (rnd.nextFloat() * 0.9999f + 0.00005f).toDouble()
        val u2 = rnd.nextFloat().toDouble()
        return (sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)).toFloat()
    }
}
