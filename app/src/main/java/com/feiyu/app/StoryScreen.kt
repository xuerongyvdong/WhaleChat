package com.feiyu.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** 好感度剧情：等级提升时出现的短篇场景 */
@Composable
fun StoryScreen(
    chapter: String,
    levelName: String,
    text: String,
    loading: Boolean,
    onClose: () -> Unit
) {
    var shown by remember(text) { mutableIntStateOf(0) }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        shown = 0
        if (text.isEmpty()) return@LaunchedEffect
        while (shown < text.length && !skipped) {
            shown += 2
            delay(32)
        }
        shown = text.length
    }

    Column(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1220), Color(0xFF16243C), Color(0xFF22314C))
                )
            )
            .statusBarsPadding()
            .tap {
                if (shown < text.length) {
                    skipped = true
                    shown = text.length
                }
            }
            .padding(horizontal = 26.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Txt(chapter, size = 12.sp, color = Color(0xFF7FB2FF).copy(alpha = 0.85f))
        Spacer(Modifier.height(6.dp))
        Txt(levelName, size = 30.sp, weight = FontWeight.Light, color = Color.White)
        Spacer(Modifier.height(26.dp))

        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when {
                    loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TypingWaves()
                            Spacer(Modifier.width(8.dp))
                            Txt("正在写这段故事…", size = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    text.isEmpty() -> {
                        Txt("这段故事没能写出来。", size = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                    else -> {
                        Txt(
                            text.take(shown),
                            size = 16.sp,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (!loading && (shown >= text.length || skipped)) {
            Box(
                Modifier.fillMaxWidth().height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .springPress(onClose),
                contentAlignment = Alignment.Center
            ) {
                Txt("收下这段记忆", size = 15.sp, color = Color.White)
            }
            Spacer(Modifier.height(30.dp))
        } else {
            Spacer(Modifier.height(30.dp))
        }
    }
}
