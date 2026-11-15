package com.feiyu.app

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** 类似「我的世界」的成就提示：右侧滑入、停留、滑出，并播放合成音效 */
@Composable
fun AchievementToast(achievement: Achievement, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val slide = remember { Animatable(1f) }
    var visible by remember(achievement.id) { mutableStateOf(true) }

    LaunchedEffect(achievement.id) {
        SoundEffect.playAchievement(ctx)
        slide.snapTo(1f)
        slide.animateTo(0f, tween(320))
        delay(2600)
        slide.animateTo(1f, tween(380))
        visible = false
        onDone()
    }

    if (!visible) return

    Row(
        Modifier
            .statusBarsPadding()
            .padding(start = 12.dp, end = 16.dp, top = 70.dp, bottom = 8.dp)
            .graphicsLayer {
                translationX = -slide.value * 340f
                alpha = 1f - slide.value * 0.9f
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1B1B1B).copy(alpha = 0.95f))
            .border(1.dp, Color(0xFF6E6E6E), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF3B3B3B)),
            contentAlignment = Alignment.Center
        ) {
            Txt(achievement.icon, size = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Txt("成就达成！", size = 11.sp, weight = FontWeight.Bold, color = Color(0xFFFFE45C))
            Spacer(Modifier.height(2.dp))
            Txt(achievement.title, size = 13.sp, color = Color.White)
        }
    }
}

/** 合成一段琶音写成 wav，用 MediaPlayer 播放（比 AudioTrack 稳） */
object SoundEffect {

    private var player: android.media.MediaPlayer? = null

    fun playAchievement(ctx: Context) {
        runCatching {
            val file = java.io.File(ctx.cacheDir, "ach_chime.wav")
            if (!file.exists()) writeWav(file)
            player?.release()
            player = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { it.release() }
            }
        }
    }

    private fun writeWav(file: java.io.File) {
        val sampleRate = 44100
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
        val noteLen = 0.11
        val total = (notes.size * noteLen * sampleRate).toInt() + sampleRate / 12
        val pcm = ShortArray(total)

        var offset = 0
        for (freq in notes) {
            val len = (noteLen * sampleRate).toInt()
            for (i in 0 until len) {
                val t = i.toDouble() / sampleRate
                val env = kotlin.math.exp(-6.0 * i / len)
                val v = (sin(2 * PI * freq * t) * 0.6 + sin(2 * PI * freq * 2 * t) * 0.25) * env
                pcm[offset + i] = (v * 12000).toInt().toShort()
            }
            offset += len
        }

        val dataSize = pcm.size * 2
        val out = java.io.ByteArrayOutputStream()
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray()); le32(36 + dataSize); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1)
        le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(dataSize)
        for (v in pcm) {
            out.write(v.toInt() and 0xFF)
            out.write((v.toInt() shr 8) and 0xFF)
        }
        file.writeBytes(out.toByteArray())
    }
}
