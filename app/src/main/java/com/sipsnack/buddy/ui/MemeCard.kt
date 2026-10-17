package com.sipsnack.buddy.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sipsnack.buddy.memes.Memes
import com.sipsnack.buddy.rules.Moment
import com.sipsnack.buddy.ui.theme.Sip
import com.sipsnack.buddy.ui.theme.SipType

/** The pop-up for a big moment: the meme video with its sound, then a line of warm copy. */
@Composable
fun MemeCard(moment: Moment, onAction: () -> Unit, onDismiss: () -> Unit) {
    val meme = Memes.forMoment(moment)
    Box(
        Modifier
            .fillMaxSize()
            .background(Sip.Ink.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .card()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            meme.video?.let { video ->
                MemeVideo(video, Modifier.fillMaxWidth().aspectRatio(if (moment == Moment.STILL_DELAYING) 16f / 9f else 1f))
            }
            Text(meme.title, style = SipType.Title)
            Text(meme.subtitle, style = SipType.Body.copy(color = Sip.Ice))
            PrimaryButton(meme.button, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MemeVideo(res: Int, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$res")))
            volume = if (Memes.soundAllowed(context)) 1f else 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.parseColor("#182033"))
            }
        },
        modifier = modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Sip.Line, RoundedCornerShape(20.dp)),
    )
}
