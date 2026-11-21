package com.feiyu.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.delay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 弹性开关：滑块起步横向拉长，落位后压回原宽。
 */
@Composable
fun SpringSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val trackW = 46.dp
    val trackH = 26.dp
    val thumb = 20.dp
    val pad = 3.dp

    val travel = with(density) { (trackW - thumb - pad * 2).toPx() }
    val offset = remember { Animatable(if (checked) travel else 0f) }
    val stretch = remember { Animatable(1f) }

    LaunchedEffect(checked) {
        // 起步拉长
        stretch.animateTo(1.45f, tween(90, easing = LinearOutSlowInEasing))
        offset.animateTo(
            targetValue = if (checked) travel else 0f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f)
        )
        // 落位压回
        stretch.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 500f))
    }

    val bg by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "swBg")

    Box(
        Modifier.width(trackW).height(trackH)
            .clip(RoundedCornerShape(trackH / 2))
            .background(
                Color(0xFF4C9DF8).copy(alpha = 1f) ) // 占位，下面覆盖真实颜色
            .tap {
                if (enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChange(!checked)
                }
            }
    ) {
        Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(trackH / 2)).background(
                lerpColor(Color(0xFFD6DAE2), Color(0xFF4C9DF8), bg)
            )
        )
        Box(
            Modifier
                .padding(start = pad, top = pad)
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.roundToInt(), 0) }
                .width((thumb.value + (stretch.value - 1f).coerceAtLeast(0f) * 10f).dp)
                .height(thumb)
                .clip(CircleShape)
                .background(Color.White)
                .graphicsLayer {
                    // 拉伸时压扁一点，像橡皮
                    scaleY = 1f - (stretch.value - 1f) * 0.25f
                }
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

/**
 * 逐笔绘制的勾：用于「已完成 / 成功」状态。
 */
@Composable
fun DrawCheck(
    visible: Boolean,
    color: Color = Ui.AccentDeep,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 14.dp
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        else progress.snapTo(0f)
    }

    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.78f)
            lineTo(w * 0.86f, h * 0.22f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        drawPath(
            drawn,
            color = color,
            style = Stroke(width = w * 0.16f, cap = StrokeCap.Round)
        )
    }
}

/**
 * 翻转卡片：正面 / 背面各自只在朝向自己时可见，避免穿帮。
 */
@Composable
fun FlipCard(
    flipped: Boolean,
    modifier: Modifier = Modifier,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit
) {
    val rotation = remember { Animatable(if (flipped) 180f else 0f) }
    LaunchedEffect(flipped) {
        rotation.animateTo(
            if (flipped) 180f else 0f,
            spring(dampingRatio = 0.72f, stiffness = 260f)
        )
    }
    val deg = rotation.value
    val showFront = deg <= 90f

    Box(
        modifier.graphicsLayer {
            rotationY = deg
            cameraDistance = 12f * density
        }
    ) {
        if (showFront) {
            Box(Modifier.graphicsLayer { rotationY = 0f; alpha = 1f }) { front() }
        } else {
            Box(Modifier.graphicsLayer { rotationY = 180f; alpha = 1f }) { back() }
        }
    }
}

/**
 * 数字滚动：带减速的缓停，不会中途骤停。
 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.TextUnit = 13.sp,
    color: Color = Color(0xFF16202C),
    weight: FontWeight = FontWeight.Medium
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) {
        anim.animateTo(
            value.toFloat(),
            tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }
    Txt(
        "${anim.value.roundToInt()}",
        modifier = modifier,
        size = size,
        color = color,
        weight = weight
    )
}

/**
 * 液态滑块：轨道填充与滑扭共用一条弹簧，磁吸到最近档位。
 */
@Composable
fun LiquidSlider(
    value: Float,
    steps: List<Float>,
    onChange: (Float) -> Unit,
    label: (Float) -> String = { it.toString() }
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val index = remember(value) { steps.indexOfFirst { it == value }.coerceAtLeast(0) }
    val ratio = remember(index, steps) {
        if (steps.size <= 1) 0f else index.toFloat() / (steps.size - 1)
    }

    val pos = remember { Animatable(ratio) }
    LaunchedEffect(ratio) {
        pos.animateTo(ratio, spring(dampingRatio = 0.58f, stiffness = 320f))
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt("每条间隔", size = 12.sp, color = Color(0xFF5B6B85))
            Spacer(Modifier.weight(1f))
            Txt(label(steps[index]), size = 13.sp, weight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(34.dp)
                .pointerInput(steps) {
                    detectHorizontalDragGestures(
                        onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        onDragEnd = { },
                        onHorizontalDrag = { change, _ ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val t = (change.position.x / w).coerceIn(0f, 1f)
                            val nearest = steps.minByOrNull {
                                kotlin.math.abs(
                                    steps.indexOf(it).toFloat() / (steps.size - 1).coerceAtLeast(1) - t
                                )
                            } ?: steps.first()
                            if (nearest != value) {
                                onChange(nearest)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = size.height / 2f
                val stroke = 8.dp.toPx()
                val radius = (size.height - stroke) / 2f
                drawRoundRect(
                    color = Color(0xFFE9EDF2),
                    topLeft = Offset(stroke / 2, cy - stroke / 2),
                    size = Size(size.width - stroke, stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke / 2)
                )
                // 轨道填充与滑扭同一条弹簧
                val fillW = (size.width - stroke) * pos.value
                drawRoundRect(
                    color = Ui.Accent,
                    topLeft = Offset(stroke / 2, cy - stroke / 2),
                    size = Size(fillW, stroke),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke / 2)
                )
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = Offset(stroke / 2 + fillW, cy)
                )
                drawCircle(
                    color = Ui.Accent.copy(alpha = 0.35f),
                    radius = radius + 3.dp.toPx(),
                    center = Offset(stroke / 2 + fillW, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}


/**
 * 底部阻尼抽屉：拖到锚点外有橡皮筋阻力，松手按位置+速度吸附最近锚点。
 */
@Composable
fun DampedBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullPx = with(density) { maxHeight.toPx() }
        val anchors = listOf(fullPx * 0.55f, fullPx * 0.92f)
        var cur by remember { mutableStateOf(anchors.first()) }
        val height = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            height.animateTo(
                anchors.first(),
                spring(dampingRatio = 0.82f, stiffness = 220f)
            )
        }

        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .tap { onDismiss() }
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { },
                        onVerticalDrag = { change, drag ->
                            change.consume()
                            var next = cur + drag
                            // 锚点区间内跟手，超出锚点做橡皮筋
                            next = when {
                                next < anchors.first() -> anchors.first() +
                                    (next - anchors.first()) * 0.35f
                                next > anchors.last() -> anchors.last() +
                                    (next - anchors.last()) * 0.35f
                                else -> next
                            }
                            cur = next
                        },
                        onDragCancel = { },
                        onDragEnd = {
                            // 按位置与方向吸附
                            val target = anchors.minByOrNull { kotlin.math.abs(it - cur) } ?: anchors.first()
                            cur = target
                        }
                    )
                }
        ) {
            // 高度变化用弹簧追，避免松手瞬移
            LaunchedEffect(cur) {
                height.animateTo(cur, spring(dampingRatio = 0.72f, stiffness = 260f))
            }
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.width(38.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD8DEE8))
                    )
                }
                content()
            }
        }
    }
}

/** 与真实卡片高度对齐的骨架 */
@Composable
fun FeedSkeleton(backdrop: com.kyant.backdrop.backdrops.LayerBackdrop, glass: Boolean) {
    val shimmer = remember { Animatable(0.35f) }
    LaunchedEffect(Unit) {
        while (true) {
            shimmer.animateTo(0.75f, tween(700))
            shimmer.animateTo(0.35f, tween(700))
        }
    }
    Column {
        repeat(2) {
            GlassBox(backdrop, RoundedCornerShape(18.dp), Modifier.fillMaxWidth(), glass) {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        Modifier.width(44.dp).height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DatePill(shimmer.value))
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth(0.92f).height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(DatePill(shimmer.value))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier.fillMaxWidth(0.6f).height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(DatePill(shimmer.value))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun DatePill(alpha: Float) = Color(0xFFDCE3EE).copy(alpha = alpha)


/**
 * 二级页面统一出场：背景压暗，卡片从下方浮起并放大到全屏，
 * 弹簧落位；返回时反向收回。
 */
@Composable
fun SecondaryPage(
    visible: Boolean,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(
            androidx.compose.animation.core.tween(180)
        ) + androidx.compose.animation.scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f)
        ) + androidx.compose.animation.slideInVertically(
            initialOffsetY = { it / 18 },
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 220f)
        ),
        exit = androidx.compose.animation.fadeOut(
            androidx.compose.animation.core.tween(160)
        ) + androidx.compose.animation.scaleOut(
            targetScale = 0.96f,
            animationSpec = androidx.compose.animation.core.tween(180)
        ) + androidx.compose.animation.slideOutVertically(
            targetOffsetY = { it / 22 },
            animationSpec = androidx.compose.animation.core.tween(200)
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            // 压暗层：点一下返回
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.16f))
                    .tap { onClose() }
            )
            // 页面铺满屏幕，不留边
            Box(Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}


/**
 * 保持键：点击后收起成圆并转圈 → 成功变绿勾 → 停留片刻再展开回文字。
 * 失败时变红叉并轻微抖动。
 */
@Composable
fun HoldButton(
    label: String,
    modifier: Modifier = Modifier,
    expandedWidth: androidx.compose.ui.unit.Dp = 108.dp,
    height: androidx.compose.ui.unit.Dp = 42.dp,
    onClick: suspend () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var state by remember { mutableStateOf(0) } // 0 idle, 1 loading, 2 done, 3 error
    var shake by remember { mutableStateOf(0f) }

    val collapsed = state != 0
    val width by animateDpAsState(
        targetValue = if (collapsed) height else expandedWidth,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "holdWidth"
    )
    val bg by animateColorAsState(
        targetValue = when (state) {
            2 -> Color(0xFFDCEBFF)
            3 -> Color(0xFFE05A5A)
            else -> Color(0xFF4C9DF8)
        },
        animationSpec = tween(220),
        label = "holdBg"
    )
    val spin = rememberInfiniteTransition(label = "holdSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
        label = "holdAngle"
    )

    Box(
        modifier
            .width(width)
            .height(height)
            .graphicsLayer { translationX = shake }
            .clip(RoundedCornerShape(height / 2))
            .background(bg)
            .tap {
                if (state == 1) return@tap
                // 任何确认点击都先收起键盘、清掉焦点
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                state = 1
                scope.launch {
                    val ok = runCatching { onClick() }.getOrDefault(false)
                    state = if (ok) 2 else 3
                    if (!ok) {
                        // 小抖动
                        repeat(3) { i ->
                            shake = if (i % 2 == 0) 8f else -8f
                            delay(45)
                        }
                        shake = 0f
                    }
                    delay(if (ok) 1500 else 1200)
                    state = 0
                }
            },
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            0 -> Txt(label, size = 13.sp, color = Color.White, weight = FontWeight.Medium)
            1 -> Canvas(Modifier.size(18.dp)) {
                drawArc(
                    color = Color.White.copy(alpha = 0.35f),
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color.White,
                    startAngle = angle, sweepAngle = 110f, useCenter = false,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            2 -> DrawCheck(visible = true, color = Ui.AccentDeep, size = 20.dp)
            else -> Txt("×", size = 18.sp, color = Color.White, weight = FontWeight.Bold)
        }
    }
}


/**
 * 长按确认：按住时外圈沿胶囊描边走到满才执行；
 * 中途松手，进度回退到 0，不执行。
 */
@Composable
fun HoldToConfirm(
    label: String,
    modifier: Modifier = Modifier,
    holdMillis: Int = 1100,
    onConfirm: () -> Unit
) {
    var pressing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Box(
        modifier
            .height(50.dp)
            .width(210.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (done) return@awaitEachGesture
                    pressing = true
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    scope.launch {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(holdMillis, easing = LinearEasing)
                        )
                        // 走满：执行
                        done = true
                        pressing = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                        delay(700)
                        done = false
                        progress.snapTo(0f)
                    }
                    val up = waitForUpOrCancellation()
                    // 松手时若还没走满 → 回退
                    if (progress.value < 1f && !done) {
                        pressing = false
                        scope.launch {
                            progress.animateTo(0f, tween(220, easing = LinearOutSlowInEasing))
                        }
                    }
                }
            }
    ) {
        // 外圈：沿胶囊描边推进
        Canvas(Modifier.matchParentSize()) {
            val stroke = 2.5.dp.toPx()
            val inset = stroke / 2f
            val r = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)
            val path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        r,
                        androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                )
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val len = measure.length

            // 底圈
            drawPath(
                path,
                color = Color(0xFFDCE3EE),
                style = Stroke(width = stroke)
            )

            // 进度圈
            if (progress.value > 0.001f) {
                val seg = Path()
                measure.getSegment(0f, len * progress.value, seg, true)
                drawPath(
                    seg,
                    color = if (done) Color(0xFF41B883) else Color(0xFFE05A5A),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        // 内容
        Box(
            Modifier.fillMaxSize()
                .padding(3.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    when {
                        done -> Color(0xFFE9F7F0)
                        pressing -> Color(0xFFFDEAEA)
                        else -> Color(0xFFF4F6FA)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Txt(
                when {
                    done -> "已执行"
                    pressing -> "继续按住…"
                    else -> label
                },
                size = 13.sp,
                weight = FontWeight.Medium,
                color = when {
                    done -> Color(0xFF2E9E6B)
                    pressing -> Color(0xFFD14343)
                    else -> Color(0xFF5B6B85)
                }
            )
        }
    }
}


/**
 * 圆形长按确认浮层：磨砂玻璃圆 + 外圈进度，按住 1.5 秒走满才执行。
 * 中途松手进度回退；成功后图标变为蓝色对勾并自动关闭。
 */
@Composable
fun HoldConfirmOverlay(
    title: String,
    hint: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    var pressing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    val requestDismiss: () -> Unit = {
        if (!done && !cancelled) {
            cancelled = true
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                delay(620)
                onDismiss()
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = requestDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true
        )
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
                .tap { if (!pressing && !done) requestDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(120.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (done) return@awaitEachGesture
                                pressing = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    progress.animateTo(
                                        1f,
                                        tween(1500, easing = LinearEasing)
                                    )
                                    if (progress.value >= 0.999f) {
                                        done = true
                                        pressing = false
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onConfirm()
                                        delay(700)
                                        onDismiss()
                                    }
                                }
                                waitForUpOrCancellation()
                                if (!done && progress.value < 0.999f) {
                                    pressing = false
                                    scope.launch {
                                        progress.animateTo(0f, tween(220, easing = LinearOutSlowInEasing))
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 外圈进度
                    Canvas(Modifier.matchParentSize()) {
                        val stroke = 3.dp.toPx()
                        val r = size.minDimension / 2f - stroke
                        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = Color(0xFF3A3A42),
                            radius = r,
                            center = c,
                            style = Stroke(width = stroke)
                        )
                        if (cancelled) {
                            drawCircle(
                                color = Color(0xFF9AA6B6),
                                radius = r,
                                center = c,
                                style = Stroke(width = stroke)
                            )
                        } else if (progress.value > 0.001f) {
                            drawArc(
                                color = if (done) Color(0xFF5AA9FF) else Color(0xFFE05A5A),
                                startAngle = -90f,
                                sweepAngle = 360f * progress.value,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(c.x - r, c.y - r),
                                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // 磨砂玻璃圆（小面积也开折射）
                    Box(
                        Modifier.size(88.dp)
                            .graphicsLayer { scaleX = if (pressing) 0.96f else 1f; scaleY = scaleX }
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> Color(0xFFDCEBFF).copy(alpha = 0.85f)
                                    cancelled -> Color(0xFFE9EDF2).copy(alpha = 0.85f)
                                    else -> Color.White.copy(alpha = if (pressing) 0.16f else 0.12f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.radialGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.16f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        if (done) {
                            DrawCheck(visible = true, color = Ui.AccentDeep, size = 34.dp)
                        } else if (cancelled) {
                            Txt("×", size = 30.sp, color = Color(0xFF9AA6B6), weight = FontWeight.Light)
                        } else {
                            Box(
                                Modifier.graphicsLayer {
                                    scaleX = if (pressing) 1.06f else 1f
                                    scaleY = scaleX
                                    alpha = 1f - progress.value * 0.15f
                                }
                            ) {
                                Canvas(Modifier.size(30.dp)) {
                                    // 简约垃圾桶：桶身 + 桶盖 + 提手
                                    val w = size.width
                                    val h = size.height
                                    val red = Color(0xFFE05A5A)
                                    val sw = 2.dp.toPx()
                                    val left = w * 0.24f
                                    val right = w * 0.76f
                                    val top = h * 0.32f
                                    drawRoundRect(
                                        color = red,
                                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                        size = androidx.compose.ui.geometry.Size(right - left, h * 0.52f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                                        style = Stroke(width = sw)
                                    )
                                    drawLine(
                                        color = red,
                                        start = androidx.compose.ui.geometry.Offset(w * 0.16f, top),
                                        end = androidx.compose.ui.geometry.Offset(w * 0.84f, top),
                                        strokeWidth = sw,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = red,
                                        start = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.2f),
                                        end = androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.2f),
                                        strokeWidth = sw,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Txt(title, size = 15.sp, color = Color.White, weight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Txt(
                    when {
                        done -> "已执行"
                        cancelled -> "已取消，没有删除任何内容"
                        pressing -> "继续按住…"
                        else -> hint
                    },
                    size = 11.sp,
                    color = Color.White.copy(alpha = 0.62f)
                )
            }
        }
    }
}
