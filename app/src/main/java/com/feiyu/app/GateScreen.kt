package com.feiyu.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun GateScreen(onPassed: () -> Unit) {
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun showKeyboard() {
        focusManager.clearFocus()
        focus.requestFocus()
        keyboard?.show()
    }

    // 呼吸光效
    val breathe = rememberInfiniteTransition(label = "breathe")
    val glow by breathe.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LaunchedEffect(Unit) {
        delay(280)
        showKeyboard()
    }

    fun check() {
        if (answer == "世界和平") {
            keyboard?.hide()
            success = true
        } else {
            wrong = true
            answer = ""
        }
    }

    LaunchedEffect(success) {
        if (success) {
            delay(260)
            onPassed()
        }
    }

    LaunchedEffect(wrong) {
        if (wrong) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    -12f at 60
                    12f at 130
                    -8f at 210
                    8f at 300
                    -3f at 360
                    0f at 420
                }
            )
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070B14), Color(0xFF101A2E), Color(0xFF1A2740))
                )
            )
            .tap { showKeyboard() }
    ) {
        // 顶部柔光
        Box(
            Modifier.fillMaxWidth().height(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4C9DF8).copy(alpha = 0.28f * glow),
                            Color.Transparent
                        ),
                        center = Offset(540f, 0f),
                        radius = 720f
                    )
                )
        )

        BasicTextField(
            value = answer,
            onValueChange = { v ->
                val clean = v.filter { !it.isWhitespace() }
                if (clean.length <= 4) {
                    answer = clean
                    wrong = false
                    if (clean.length == 4) check()
                }
            },
            modifier = Modifier.size(1.dp).alpha(0.01f).focusRequester(focus),
            textStyle = TextStyle(fontSize = 1.sp, color = Color.Transparent)
        )

        Column(
            Modifier.align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 32.dp, end = 32.dp, top = 110.dp)
        ) {
            Txt("🐋", size = 30.sp)

            Spacer(Modifier.height(18.dp))

            Txt(
                "我们的愿望是",
                size = 31.sp,
                weight = FontWeight.Light,
                color = Color.White.copy(alpha = 0.95f),
                shadow = true
            )

            Spacer(Modifier.height(10.dp))

            Txt(
                "写下我们共同的愿望",
                size = 12.sp,
                color = Color.White.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(54.dp))

            Row(
                Modifier
                    .graphicsLayer { translationX = shake.value }
                    .tap { showKeyboard() },
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (i in 0 until 4) {
                    val filled = i < answer.length
                    val isCursor = i == answer.length

                    val charScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (filled) 1f else 0.8f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.42f, stiffness = 420f
                        ),
                        label = "charScale"
                    )

                    Box(
                        Modifier.width(44.dp).height(52.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (filled) {
                            Txt(
                                answer[i].toString(),
                                size = 27.sp,
                                weight = FontWeight.Normal,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .graphicsLayer { scaleX = charScale; scaleY = charScale },
                                shadow = true
                            )
                        }

                        // 底线：当前格呼吸发亮
                        Box(
                            Modifier.fillMaxWidth().height(1.5.dp)
                                .background(
                                    if (isCursor) {
                                        Color(0xFF7FB2FF).copy(alpha = glow)
                                    } else {
                                        Color.White.copy(alpha = 0.22f)
                                    }
                                )
                        )
                    }
                }
            }

            if (wrong) {
                Spacer(Modifier.height(20.dp))
                Txt(
                    "不对，再想想。",
                    size = 13.sp,
                    color = Color(0xFFFF9A9A)
                )
            }
        }

        // 成功时的白光
        if (success) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
                    .alpha(1f)
            )
        }
    }
}
