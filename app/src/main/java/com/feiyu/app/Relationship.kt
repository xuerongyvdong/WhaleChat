package com.feiyu.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** 活跃度 + 亲密度 */
object Relationship {

    const val DAILY_TARGET = 40

    private val levels = listOf(
        0 to "初识",
        100 to "熟悉",
        300 to "亲近",
        700 to "依赖",
        1500 to "羁绊",
        3000 to "命定",
        6000 to "唯一"
    )

    fun prefs(ctx: Context) = ctx.getSharedPreferences("feiyu", Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    /** 记录一次互动：type = message / image / diary */
    fun record(ctx: Context, type: String) {
        val p = prefs(ctx)
        val (daily, intimacy) = when (type) {
            "message" -> 2 to 1
            "image" -> 3 to 3
            "diary" -> 5 to 4
            else -> 1 to 1
        }

        val day = today()
        if (p.getString("rel_day", "") != day) {
            p.edit().putString("rel_day", day).putInt("rel_today", 0).apply()
        }
        val todayXp = p.getInt("rel_today", 0) + daily
        val total = p.getInt("rel_intimacy", 0) + intimacy
        val dayCount = p.getInt("act_$day", 0) + 1

        p.edit()
            .putInt("rel_today", todayXp)
            .putInt("rel_intimacy", total)
            .putInt("act_$day", dayCount)
            .apply()
    }

    fun todayXp(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("rel_day", "") == today()) p.getInt("rel_today", 0) else 0
    }

    fun intimacy(ctx: Context): Int = prefs(ctx).getInt("rel_intimacy", 0)

    fun levelName(value: Int): String {
        var name = levels.first().second
        for ((threshold, n) in levels) if (value >= threshold) name = n
        return name
    }

    fun levelProgress(value: Int): Triple<String, String, Float> {
        val current = levels.lastOrNull { value >= it.first } ?: levels.first()
        val next = levels.firstOrNull { value < it.first }
        if (next == null) return Triple(current.second, "满级", 1f)
        val span = (next.first - current.first).toFloat().coerceAtLeast(1f)
        val done = (value - current.first).toFloat()
        return Triple(current.second, next.second, (done / span).coerceIn(0f, 1f))
    }

    /** 最近 n 天的活跃计数（旧 → 新） */
    fun recentDays(ctx: Context, n: Int = 30): List<Pair<String, Int>> {
        val p = prefs(ctx)
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1))
        val out = ArrayList<Pair<String, Int>>(n)
        repeat(n) {
            val key = fmt.format(cal.time)
            out.add(key to p.getInt("act_$key", 0))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    fun streak(ctx: Context): Int = prefs(ctx).getInt("stat_streak", 1)

    fun levelIndex(value: Int): Int = levels.indexOfLast { value >= it.first }

    /** 若刚到新等级且没看过剧情，返回(章节序号, 等级名)，否则 null */
    fun consumeNewLevel(ctx: Context, value: Int): Pair<Int, String>? {
        val idx = levelIndex(value)
        val p = prefs(ctx)
        val seen = p.getInt("rel_story_seen", 0)
        if (idx <= seen || idx <= 0) return null
        p.edit().putInt("rel_story_seen", idx).apply()
        return idx to levels[idx].second
    }
}

@Composable
fun RelationshipScreen(
    ctx: Context,
    messages: Int,
    diary: Int,
    memories: Int,
    onClose: () -> Unit
) {
    val intimacy = Relationship.intimacy(ctx)
    val todayXp = Relationship.todayXp(ctx)
    val (level, nextLevel, progress) = Relationship.levelProgress(intimacy)
    val days = remember { Relationship.recentDays(ctx, 30) }
    var tick by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
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
            Txt("相处", size = 16.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(28.dp))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            // 亲密度
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color.White).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("亲密度", size = 13.sp, weight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Txt("Lv. $level", size = 12.sp, color = Ui.AccentDeep)
                }
                Spacer(Modifier.height(10.dp))
                Txt("$intimacy", size = 34.sp, weight = FontWeight.Light, color = Color(0xFF16202C))
                Spacer(Modifier.height(2.dp))
                Txt(
                    if (nextLevel == "满级") "已经走到最后一格了"
                    else "距离「$nextLevel」还差 ${((1 - progress) * 100).toInt()}%",
                    size = 11.sp, color = Color(0xFF8A96A8)
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE9EDF2))
                ) {
                    Box(
                        Modifier.fillMaxWidth(progress).height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Ui.Accent)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 今日活跃 + 连续天数
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(Color.White).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(88.dp)) {
                            val stroke = 8.dp.toPx()
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(
                                color = Color(0xFFE9EDF2),
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Ui.Accent,
                                startAngle = -90f,
                                sweepAngle = 360f * min(1f, todayXp.toFloat() / Relationship.DAILY_TARGET),
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt("$todayXp", size = 20.sp, weight = FontWeight.Medium)
                            Txt("/ ${Relationship.DAILY_TARGET}", size = 9.sp, color = Color(0xFF8A96A8))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Txt("今日活跃", size = 12.sp, color = Color(0xFF5B6B85))
                }

                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                        .background(Color.White).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    Txt("🔥", size = 34.sp)
                    Spacer(Modifier.height(6.dp))
                    Txt("连续 ${Relationship.streak(ctx)} 天", size = 14.sp, weight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Txt("每天都在", size = 10.sp, color = Color(0xFF8A96A8))
                }
            }

            Spacer(Modifier.height(12.dp))

            // 最近 30 天热力图
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color.White).padding(16.dp)
            ) {
                Txt("最近 30 天", size = 13.sp, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                val maxCount = days.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.chunked(10).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { (_, count) ->
                                val alpha = if (count == 0) 0.06f else 0.25f + 0.75f * (count.toFloat() / maxCount)
                                Box(
                                    Modifier.weight(1f).height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Ui.Accent.copy(alpha = alpha))
                                )
                            }
                            repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    "消息" to messages,
                    "日记" to diary,
                    "记忆" to memories
                ).forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(Color.White).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Txt("$value", size = 18.sp, weight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Txt(label, size = 10.sp, color = Color(0xFF8A96A8))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
