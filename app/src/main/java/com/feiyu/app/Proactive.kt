package com.feiyu.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 后台主动性：WorkManager 每 15 分钟检查一次，
 * 判断「安静时长」是否达到设定值，够了就调模型生成一句话并推送通知。
 */
object Proactive {

    const val CHANNEL_ID = "whalechat_proactive_v2"
    const val WORK_NAME = "whalechat_proactive_work"
    const val NOTIFY_ID = 1001

    fun prefs(ctx: Context) = ctx.getSharedPreferences("feiyu", Context.MODE_PRIVATE)

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "主动消息", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "她在安静一段时间后主动找你"
        ch.enableVibration(true)
        runCatching {
            ch.setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        mgr.createNotificationChannel(ch)
    }

    const val JOB_ID = 1001

    fun schedule(ctx: Context) {
        runCatching {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, ProactiveJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            js.schedule(info)
        }
    }

    fun cancel(ctx: Context) {
        runCatching {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            js.cancel(JOB_ID)
        }
    }

    fun notify(ctx: Context, text: String) {
        ensureChannel(ctx)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val name = runCatching {
            JSONObject(prefs(ctx).getString("settings", "{}") ?: "{}").optString("botName", "")
        }.getOrDefault("").ifBlank { "小鱼" }

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setContentTitle(name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFY_ID, n)
        }
    }

    /** 真正发一条，返回发出的内容；不需要发则返回 null */
    fun maybeSend(ctx: Context): String? {
        val p = prefs(ctx)
        val settings = runCatching { JSONObject(p.getString("settings", "{}") ?: "{}") }.getOrNull() ?: return null
        if (!settings.optBoolean("proactive", true)) return null

        val raw = p.getString("messages", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        if (arr.length() == 0) return null

        val intervalMs = settings.optInt("proactiveMin", 45).coerceAtLeast(5) * 60_000L
        val lastActive = p.getLong("lastActive", 0L)
        val now = System.currentTimeMillis()
        if (lastActive <= 0L) {
            p.edit().putLong("lastActive", now).apply()
            return null
        }
        if (now - lastActive < intervalMs) return null
        // 晚上 1~7 点不打扰
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (hour in 1..7) return null

        val base = settings.optString("baseUrl", "").trim().trimEnd('/')
        val key = SecureKey.decrypt(ctx, settings.optString("apiKey", "")).trim()
        if (base.isEmpty() || key.isEmpty()) return null

        val history = StringBuilder()
        val start = maxOf(0, arr.length() - 6)
        for (i in start until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val who = if (o.optBoolean("m", false)) "主人" else "你"
            history.append(who).append("：").append(o.optString("t", "")).append("\n")
        }

        val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(now))
        val prompt = "现在是 $hm。你没有在聊天，主动给主人发一条消息。\n" +
            "要求：一句话，口语，可以带情绪或说你在干嘛；不要每次都问「在干嘛」；不要重复最近说过的话。\n" +
            "最近的对话：\n$history"

        val text = chat(ctx, settings, prompt) ?: return null
        if (text.isBlank()) return null
        val clean = text.trim().trim('"', '“', '”')

        // 写入消息列表
        runCatching {
            val list = JSONArray(raw)
            list.put(
                JSONObject()
                    .put("t", clean)
                    .put("m", false)
                    .put("ts", now)
            )
            val trimmed = JSONArray()
            val from = maxOf(0, list.length() - 200)
            for (i in from until list.length()) trimmed.put(list.get(i))
            p.edit().putString("messages", trimmed.toString()).putLong("lastActive", now).apply()
        }
        notify(ctx, clean)
        return clean
    }

    private fun chat(ctx: Context, settings: JSONObject, prompt: String): String? = runCatching {
        val base = settings.optString("baseUrl", "").trim().trimEnd('/')
        val key = SecureKey.decrypt(ctx, settings.optString("apiKey", "")).trim().replace(" ", "")
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val persona = settings.optString("persona", "").trim()
        val rules = settings.optString("rules", "").trim()
        val sys = buildString {
            if (persona.isNotEmpty()) append(persona)
            if (rules.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("规则：").append(rules)
            }
        }

        val messages = JSONArray()
        if (sys.isNotEmpty()) messages.put(JSONObject().put("role", "system").put("content", sys))
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        val body = JSONObject()
            .put("model", settings.optString("model", "deepseek-chat"))
            .put("messages", messages)
            .put("max_tokens", 120)

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = BufferedReader(
            (if (code in 200..299) conn.inputStream else conn.errorStream).reader()
        ).readText()
        conn.disconnect()
        if (code !in 200..299) return@runCatching null

        val obj = JSONObject(text)
        obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
    }.getOrNull()
}

class ProactiveJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            runCatching { Proactive.maybeSend(applicationContext) }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}
