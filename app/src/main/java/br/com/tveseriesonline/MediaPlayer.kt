package br.com.tveseriesonline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun ChannelPlayer(channel: Channel) {
    val context = LocalContext.current
    var playbackMessage by remember(channel.streamUrl) { mutableStateOf("Conectando à transmissão...") }
    val player = remember(channel.streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.streamUrl))
            playWhenReady = true
            prepare()
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    playbackMessage = when (playbackState) {
                        Player.STATE_BUFFERING -> "Carregando transmissão..."
                        Player.STATE_READY -> "Reproduzindo"
                        Player.STATE_ENDED -> "Transmissão encerrada"
                        else -> playbackMessage
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    playbackMessage = "Não foi possível reproduzir: ${error.errorCodeName}"
                }
            })
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = true
                }
            },
            update = { it.player = player }
        )
        Text(playbackMessage, color = Color(0xFFADB6C8))
    }
}
