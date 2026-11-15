package com.feiyu.app

import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal object SectionState {
    var open: String? by androidx.compose.runtime.mutableStateOf(null)
}

object Ui {
    val Accent = Color(0xFF4C9DF8)
    val AccentDeep = Color(0xFF2F7FE0)
    val Ink = Color(0xFF16202C)
    val Sub = Color(0xFF5B6B85)
    val Muted = Color(0xFF8A96A8)
    val Danger = Color(0xFFD14343)
    val FieldBg = Color.White.copy(alpha = 0.55f)
    val CardRadius = 20.dp
    val ChipRadius = 12.dp
}
@Composable
fun ChatAvatar(isBot: Boolean, settings: Settings) {
    val path = if (isBot) settings.botAvatar else settings.userAvatar
    val bitmap = remember(path) {
        if (path.isNotEmpty()) decodeSampledBitmap(realPath(path), 256) else null
    }
    val brush = if (isBot) {
        Brush.linearGradient(listOf(Color(0xFF8FC2FF), Color(0xFF4C9DF8)))
    } else {
        Brush.linearGradient(listOf(Color(0xFFCFD6E2), Color(0xFF98A4B8)))
    }
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(brush),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (isBot) {
            Txt("🐋", size = 17.sp)
        } else {
            val initial = settings.userName.trim().take(1).ifBlank { "我" }
            Txt(initial, size = 14.sp, weight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color(0xFF16202C),
    shadow: Boolean = false
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontSize = size,
            fontWeight = weight,
            color = color,
            shadow = if (shadow) androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.30f),
                offset = Offset(0f, 1f),
                blurRadius = 4f
            ) else null
        )
    )
}

@Composable
fun Modifier.animateIn(key: Any): Modifier {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        kotlinx.coroutines.delay(30)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing),
        label = "inAlpha"
    )
    val sc by animateFloatAsState(
        targetValue = if (shown) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f),
        label = "inScale"
    )
    val dy by animateFloatAsState(
        targetValue = if (shown) 0f else 16f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 200f),
        label = "inDy"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        scaleX = sc
        scaleY = sc
        translationY = dy
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
}

@Composable
fun Modifier.springPress(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.34f, stiffness = 720f),
        label = "springPress"
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val haptic = LocalHapticFeedback.current
    return this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

@Composable
fun GlassBox(
    backdrop: LayerBackdrop,
    shape: Shape,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    intensity: Float = 1f,
    useLens: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (glass) Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(11.dp.toPx() * intensity)
                        // ① 折射 + ② 色散（RGB 分通道），只在大块玻璃上启用
                        if (useLens && intensity >= 0.99f && Build.VERSION.SDK_INT >= 33) {
                            lens(
                                20.dp.toPx(),
                                30.dp.toPx(),
                                false,   // depthEffect
                                true     // chromaticAberration 色散
                            )
                        }
                    },
                    highlight = {
                        if (glass) {
                            // ③ 细白边 + 轻微模糊，增强通透感
                            Highlight(
                                1.dp,
                                1.2.dp,
                                0.9f,
                                HighlightStyle.Default(0.7f, 45f, 0.85f)
                            )
                        } else {
                            Highlight.Plain
                        }
                    }
                ) else Modifier
            )
            .background(
                Color.White.copy(
                    alpha = if (glass) (0.07f + (1f - intensity) * 0.16f).coerceAtMost(0.4f) else 0.5f
                )
            )
    ) { content() }
}
@Composable
fun Section(
    backdrop: LayerBackdrop,
    settings: Settings,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val expanded = SectionState.open == title
    @Suppress("UNUSED_EXPRESSION")
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "chevron"
    )

    GlassBox(backdrop, RoundedCornerShape(Ui.CardRadius), Modifier.fillMaxWidth(), settings.glass) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().springPress {
                    SectionState.open = if (SectionState.open == title) null else title
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Txt(title, size = 13.sp, weight = FontWeight.SemiBold, color = Color(0xFF25314A))
                Spacer(Modifier.weight(1f))
                Canvas(
                    Modifier.size(16.dp).graphicsLayer { rotationZ = chevron }
                ) {
                    val stroke = 1.6.dp.toPx()
                    val path = Path().apply {
                        moveTo(size.width * 0.22f, size.height * 0.38f)
                        lineTo(size.width * 0.5f, size.height * 0.64f)
                        lineTo(size.width * 0.78f, size.height * 0.38f)
                    }
                    drawPath(
                        path,
                        Color(0xFF5B6B85),
                        style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = SectionState.open == title,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(180)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(120))
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
fun Field(
    label: String,
    value: String,
    lines: Int = 1,
    password: Boolean = false,
    onChange: (String) -> Unit
) {
    Txt(label, size = 12.sp, color = Color(0xFF5B6B85))
    Spacer(Modifier.height(6.dp))
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bringRequester = remember { BringIntoViewRequester() }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val navPx = with(LocalDensity.current) { 112.dp.toPx() }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.66f))
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth()
                .bringIntoViewRequester(bringRequester)
                .onSizeChanged { fieldSize = it }
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        scope.launch {
                            delay(280)
                            val w = fieldSize.width.toFloat()
                            val h = fieldSize.height.toFloat()
                            if (w > 0f && h > 0f) {
                                bringRequester.bringIntoView(
                                    androidx.compose.ui.geometry.Rect(0f, 0f, w, h + navPx)
                                )
                            } else {
                                bringRequester.bringIntoView()
                            }
                        }
                    }
                }
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = if (password) 48.dp else 10.dp),
            textStyle = TextStyle(fontSize = 15.sp, color = Color(0xFF16202C)),
            maxLines = lines,
            visualTransformation = if (password && !shown) PasswordVisualTransformation('•') else VisualTransformation.None
        )
        if (password) {
            Txt(
                if (shown) "隐藏" else "显示",
                size = 11.sp, color = Ui.AccentDeep,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .tap { shown = !shown }
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Txt(label, size = 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(13.dp))
                .background(if (checked) Color(0xFF4C9DF8) else Color(0xFFD6DAE2))
                .tap { onChange(!checked) }
        ) {
            Box(
                Modifier.padding(top = 3.dp, start = if (checked) 21.dp else 3.dp)
                    .size(20.dp).clip(CircleShape).background(Color.White)
            )
        }
    }
}

@Composable
fun Chip(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Ui.ChipRadius))
            .background(
                if (danger) Color(0xFFE3EFFF).copy(alpha = 0.92f)
                else Color.White.copy(alpha = 0.62f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .springPress(onClick)
    ) {
        Txt(
            label, size = 12.sp,
            weight = FontWeight.Medium,
            color = if (danger) Ui.AccentDeep else Color(0xFF2F415E)
        )
    }
}

@Composable
fun ColumnScope.FloatingTabBar(
    backdrop: LayerBackdrop,
    glass: Boolean,
    current: Int,
    onSelect: (Int) -> Unit
) {
    val labels = listOf("聊天", "动态", "日记", "设置")
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        GlassBox(
            backdrop,
            RoundedCornerShape(30.dp),
            Modifier.fillMaxWidth().height(64.dp),
            glass,
            0.55f
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val slot = maxWidth / labels.size
                val density = LocalDensity.current
                val offset = remember { Animatable(0f) }
                var inited by remember { mutableStateOf(false) }

                LaunchedEffect(current, slot) {
                    val target = with(density) { (slot * current).toPx() }
                    if (!inited) {
                        offset.snapTo(target)
                        inited = true
                    } else {
                        // 果冻：低阻尼弹簧，会轻微过冲再回弹
                        offset.animateTo(
                            targetValue = target,
                            animationSpec = spring(dampingRatio = 0.38f, stiffness = 220f)
                        )
                    }
                }

                GlassBox(
                    backdrop,
                    RoundedCornerShape(24.dp),
                    Modifier
                        .width(slot)
                        .fillMaxHeight()
                        .offset { IntOffset(offset.value.roundToInt(), 0) },
                    glass,
                    0.45f,
                    useLens = false
                ) {}

                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    labels.forEachIndexed { i, label ->
                        val active = i == current
                        val bounce by animateFloatAsState(
                            targetValue = if (active) 1.12f else 1f,
                            animationSpec = spring(dampingRatio = 0.32f, stiffness = 300f),
                            label = "tabBounce"
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .tap { onSelect(i) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = bounce
                                    scaleY = bounce
                                },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                TabIcon(i, active)
                                Spacer(Modifier.height(3.dp))
                                Txt(
                                    label, size = 10.sp,
                                    weight = if (active) FontWeight.Medium else FontWeight.Normal,
                                    color = if (active) Ui.AccentDeep else Color(0xFF3A3A3A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabIcon(index: Int, active: Boolean) {
    val morph = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active) {
        morph.animateTo(
            if (active) 1f else 0f,
            tween(240, easing = LinearOutSlowInEasing)
        )
    }
    val color = if (active) Ui.AccentDeep else Color(0xFF3A3A3A)
    val white = Color.White

    Canvas(Modifier.size(22.dp)) {
        val p = morph.value
        val o = 1f - p
        val stroke = Stroke(
            width = size.minDimension * 0.09f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        val round = androidx.compose.ui.graphics.StrokeCap.Round

        when (index) {
            // 聊天：空心气泡 → 实心气泡 + 三个白点
            0 -> {
                val w = size.width * 0.78f
                val h = size.height * 0.62f
                val l = (size.width - w) / 2f
                val t = size.height * 0.14f
                val cr = androidx.compose.ui.geometry.CornerRadius(h * 0.32f)
                if (o > 0.01f) {
                    drawRoundRect(color.copy(alpha = o), androidx.compose.ui.geometry.Offset(l, t), Size(w, h), cr, style = stroke)
                    val tail = Path().apply {
                        moveTo(size.width * 0.32f, t + h)
                        lineTo(size.width * 0.30f, t + h + size.height * 0.18f)
                        lineTo(size.width * 0.48f, t + h)
                    }
                    drawPath(tail, color.copy(alpha = o), style = stroke)
                }
                if (p > 0.01f) {
                    drawRoundRect(color.copy(alpha = p), androidx.compose.ui.geometry.Offset(l, t), Size(w, h), cr)
                    val tailF = Path().apply {
                        moveTo(size.width * 0.30f, t + h)
                        lineTo(size.width * 0.28f, t + h + size.height * 0.20f)
                        lineTo(size.width * 0.50f, t + h)
                        close()
                    }
                    drawPath(tailF, color.copy(alpha = p))
                    listOf(0.36f, 0.50f, 0.64f).forEach { fx ->
                        drawCircle(white.copy(alpha = p), size.width * 0.045f, androidx.compose.ui.geometry.Offset(size.width * fx, t + h / 2f))
                    }
                }
            }

            // 动态：空心卡片 → 实心叠卡
            1 -> {
                val back = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.14f)
                val backSize = Size(size.width * 0.52f, size.height * 0.22f)
                val front = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.36f)
                val frontSize = Size(size.width * 0.68f, size.height * 0.48f)
                val cr = androidx.compose.ui.geometry.CornerRadius(size.width * 0.11f)
                if (o > 0.01f) {
                    drawRoundRect(color.copy(alpha = o), back, backSize, cr, style = stroke)
                    drawRoundRect(color.copy(alpha = o), front, frontSize, cr, style = stroke)
                }
                if (p > 0.01f) {
                    drawRoundRect(color.copy(alpha = p * 0.45f), back, backSize, cr)
                    drawRoundRect(color.copy(alpha = p), front, frontSize, cr)
                    drawCircle(
                        white.copy(alpha = p),
                        size.width * 0.05f,
                        androidx.compose.ui.geometry.Offset(front.x + frontSize.width * 0.72f, front.y + frontSize.height * 0.62f)
                    )
                }
            }

            // 日记：空心本子 → 实心本子 + 白线
            2 -> {
                val r = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.16f)
                val sz = Size(size.width * 0.64f, size.height * 0.68f)
                val cr = androidx.compose.ui.geometry.CornerRadius(size.width * 0.10f)
                if (o > 0.01f) {
                    drawRoundRect(color.copy(alpha = o), r, sz, cr, style = stroke)
                    drawLine(color.copy(alpha = o), androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.42f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.42f), strokeWidth = stroke.width, cap = round)
                    drawLine(color.copy(alpha = o), androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.60f), androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.60f), strokeWidth = stroke.width, cap = round)
                }
                if (p > 0.01f) {
                    drawRoundRect(color.copy(alpha = p), r, sz, cr)
                    drawLine(white.copy(alpha = p), androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.40f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.40f), strokeWidth = stroke.width, cap = round)
                    drawLine(white.copy(alpha = p), androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.58f), androidx.compose.ui.geometry.Offset(size.width * 0.56f, size.height * 0.58f), strokeWidth = stroke.width, cap = round)
                }
            }

            // 设置：空心滑杆 → 实心滑块
            else -> {
                val y1 = size.height * 0.34f
                val y2 = size.height * 0.66f
                if (o > 0.01f) {
                    drawLine(color.copy(alpha = o), androidx.compose.ui.geometry.Offset(size.width * 0.14f, y1), androidx.compose.ui.geometry.Offset(size.width * 0.86f, y1), strokeWidth = stroke.width, cap = round)
                    drawLine(color.copy(alpha = o), androidx.compose.ui.geometry.Offset(size.width * 0.14f, y2), androidx.compose.ui.geometry.Offset(size.width * 0.86f, y2), strokeWidth = stroke.width, cap = round)
                    drawCircle(color.copy(alpha = o), size.width * 0.10f, androidx.compose.ui.geometry.Offset(size.width * 0.34f, y1), style = Stroke(width = stroke.width))
                    drawCircle(color.copy(alpha = o), size.width * 0.10f, androidx.compose.ui.geometry.Offset(size.width * 0.64f, y2), style = Stroke(width = stroke.width))
                }
                if (p > 0.01f) {
                    drawLine(color.copy(alpha = p), androidx.compose.ui.geometry.Offset(size.width * 0.14f, y1), androidx.compose.ui.geometry.Offset(size.width * 0.86f, y1), strokeWidth = stroke.width * 1.45f, cap = round)
                    drawLine(color.copy(alpha = p), androidx.compose.ui.geometry.Offset(size.width * 0.14f, y2), androidx.compose.ui.geometry.Offset(size.width * 0.86f, y2), strokeWidth = stroke.width * 1.45f, cap = round)
                    drawCircle(color.copy(alpha = p), size.width * 0.12f, androidx.compose.ui.geometry.Offset(size.width * 0.34f, y1))
                    drawCircle(color.copy(alpha = p), size.width * 0.12f, androidx.compose.ui.geometry.Offset(size.width * 0.64f, y2))
                }
            }
        }
    }
}
/** 顶栏「正在说话」的三点跳动 */
@Composable
fun TypingDots(color: Color = Color(0xFF5B6B85), dot: androidx.compose.ui.unit.Dp = 4.dp) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(3) { i ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(700, delayMillis = i * 140, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot$i"
            )
            val up = kotlin.math.sin(phase * Math.PI).toFloat()
            Box(
                Modifier
                    .offset(y = (-3f * up).dp)
                    .size(dot)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color.copy(alpha = 0.35f + 0.65f * up))
            )
        }
    }
}

/** 顶栏「正在说话」的多层动态曲线 */
@Composable
fun TypingWaves(
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 58.dp,
    height: androidx.compose.ui.unit.Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "waves")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier.size(width, height)) {
        val h = size.height
        val w = size.width
        val mid = h / 2f
        val layers = listOf(
            Triple(0.46f, 0.95f, 1.6f),   // 振幅比例、透明度、线宽
            Triple(0.30f, 0.65f, 1.3f),
            Triple(0.18f, 0.40f, 1.1f)
        )
        layers.forEachIndexed { i, (ampRatio, alpha, strokeW) ->
            val path = Path()
            val steps = 48
            for (step in 0..steps) {
                val x = w * step / steps
                val norm = step.toFloat() / steps
                // 两条不同频率的正弦叠加，形成不规则起伏
                val y = mid +
                    kotlin.math.sin((norm * 2 * Math.PI * 1.6 + phase * 2 * Math.PI + i * 0.9)).toFloat() *
                    (mid * ampRatio) +
                    kotlin.math.sin((norm * 2 * Math.PI * 0.9 - phase * 2 * Math.PI * 1.3 + i * 0.5)).toFloat() *
                    (mid * ampRatio * 0.5f)
                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = Color(0xFF4C9DF8).copy(alpha = alpha),
                style = Stroke(width = strokeW, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/** 按最长边采样解码 */
fun decodeSampledBitmap(path: String, maxSide: Int): android.graphics.Bitmap? = runCatching {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    android.graphics.BitmapFactory.decodeFile(path, opts)
}.getOrNull()

/** 从相册 Uri 读图，等比缩到最长边 maxSide，压缩后写入文件 */
fun saveDownscaledImage(
    ctx: android.content.Context,
    uri: android.net.Uri,
    target: java.io.File,
    maxSide: Int,
    quality: Int = 85
): Boolean = runCatching {
    val resolver = ctx.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = resolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, opts)
    } ?: return@runCatching false

    val longest = maxOf(bmp.width, bmp.height)
    val finalBmp = if (longest > maxSide) {
        val scale = maxSide.toFloat() / longest
        android.graphics.Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else bmp

    target.outputStream().use { out ->
        finalBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
    }
    true
}.getOrDefault(false)
