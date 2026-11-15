package com.feiyu.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import java.io.File

val EMOJIS = listOf(
    "😀", "😄", "😁", "😊", "🥰", "😍", "😘", "😋",
    "😜", "🤪", "😎", "🤩", "🥳", "😏", "🙃", "😴",
    "🥱", "😪", "😔", "😢", "🥺", "😭", "😤", "😡",
    "🤯", "😱", "😰", "😳", "🫠", "🤔", "🙄", "😮",
    "🐋", "🐟", "🐳", "🐬", "🦋", "🌊", "🌙", "⭐",
    "✨", "🌸", "🍀", "🔥", "💧", "❄️", "🌈", "☁️",
    "💗", "💙", "💛", "💚", "🎀", "🎵", "🍬", "🧋",
    "👍", "👏", "🙏", "💪", "🤝", "✌️", "👋", "🫶"
)

@Composable
fun ColumnScope.ChatScreen(
    backdrop: LayerBackdrop,
    settings: Settings,
    messages: List<Msg>,
    listState: LazyListState,
    busy: Boolean,
    error: String,
    onSend: (String) -> Unit,
    onSendImage: (String) -> Unit
) {
    var showEmoji by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            runCatching {
                val f = File(ctx.filesDir, "chat_" + System.currentTimeMillis() + ".jpg")
                if (saveDownscaledImage(ctx, uri, f, 1600, 86)) {
                    onSendImage(f.absolutePath)
                }
            }
        }
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp, top = 112.dp, bottom = 210.dp
            )
        ) {
            items(messages.size, key = { messages[it].ts }) { i ->
                MessageRow(backdrop, settings, messages[i])
            }

            if (messages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Txt("🐋", size = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Txt("WhaleChat", size = 18.sp, weight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Txt(
                            if (settings.apiKey.isBlank() || settings.baseUrl.isBlank())
                                "还没有配置接口，先去「设置」填写接口地址和 API Key"
                            else "说点什么吧",
                            size = 13.sp, color = Color(0xFF5B6B85)
                        )
                    }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 96.dp)
        ) {
            if (error.isNotEmpty()) {
                Txt(
                    error,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    size = 12.sp, color = Color(0xFFD14343)
                )
            }

            if (showEmoji) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    GlassBox(backdrop, RoundedCornerShape(20.dp), Modifier.fillMaxWidth(), settings.glass) {
                        Column(
                            Modifier.fillMaxWidth().height(92.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            EMOJIS.chunked(8).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { e ->
                                        Box(
                                            Modifier.weight(1f).padding(2.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .tap { input = input + e },
                                            contentAlignment = Alignment.Center
                                        ) { Txt(e, size = 21.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Box(
                Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            ) {
                GlassBox(backdrop, RoundedCornerShape(26.dp), Modifier.fillMaxWidth(), settings.glass) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF16202C)),
                            maxLines = 4
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.55f))
                                .springPress {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) { Txt("🖼️", size = 16.sp) }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.55f))
                                .springPress { showEmoji = !showEmoji },
                            contentAlignment = Alignment.Center
                        ) { Txt("😊", size = 18.sp) }
                        Spacer(Modifier.width(6.dp))

                        val sendInteraction = remember { MutableInteractionSource() }
                        val pressed by sendInteraction.collectIsPressedAsState()
                        val sendScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (pressed) 0.90f else 1f,
                            animationSpec = androidx.compose.animation.core.tween(90),
                            label = "sendScale"
                        )
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .graphicsLayer { scaleX = sendScale; scaleY = sendScale }
                                .background(Color(0xFF4C9DF8).copy(alpha = 0.92f))
                                .clickable(
                                    interactionSource = sendInteraction,
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                    )
                                    if (input.isNotBlank()) {
                                        onSend(input)
                                        input = ""
                                        showEmoji = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Txt("↑", size = 19.sp, weight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageRow(backdrop: LayerBackdrop, settings: Settings, m: Msg) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).animateIn(m.ts),
        verticalAlignment = Alignment.Top
    ) {
        if (!m.mine) {
            ChatAvatar(isBot = true, settings = settings)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start
        ) {
            val displayName = if (m.mine) settings.userName else settings.botName
            if (displayName.isNotBlank()) {
                Txt(
                    displayName, size = 11.sp,
                    color = if (m.mine) Color(0xFF6B7C93) else Color(0xFF3C6FA8),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
            }

            GlassBox(
                backdrop,
                RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (m.mine) 18.dp else 6.dp,
                    bottomEnd = if (m.mine) 6.dp else 18.dp
                ),
                Modifier.widthIn(max = 260.dp),
                settings.glass && settings.bubbleGlass,
                0.55f,
                useLens = false
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (m.image.isNotBlank()) {
                        val bmp = remember(m.image) { decodeSampled(m.image, 900) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(width = 200.dp, height = 150.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            if (m.caption.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Txt(m.caption, size = 11.sp, color = Color(0xFF8A96A8))
                            }
                        } else {
                            Txt("🖼️ 图片已清理", size = 12.sp, color = Color(0xFF8A96A8))
                            if (m.caption.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Txt(m.caption, size = 13.sp, color = Color(0xFF5B6B85))
                            }
                        }
                    }
                    if (m.text.isNotBlank()) Txt(m.text, size = 15.sp)
                    if (settings.showTime) {
                        Spacer(Modifier.height(4.dp))
                        Txt(
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(m.ts)),
                            size = 10.sp, color = Color(0xFF5B6B85)
                        )
                    }
                }
            }
        }
        if (m.mine) {
            Spacer(Modifier.width(8.dp))
            ChatAvatar(isBot = false, settings = settings)
        }
    }
}

private fun decodeSampled(path: String, maxSide: Int): android.graphics.Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)
}.getOrNull()
