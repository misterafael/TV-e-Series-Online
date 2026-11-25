package br.com.tveseriesonline

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshScheduler.scheduleAutomatic(applicationContext)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color(0xFF090B12),
                surface = Color(0xFF171B27),
                primary = Color(0xFF5B8CFF)
            )) {
                HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var sourceUrl by rememberSaveable { mutableStateOf(PlaylistRepository.getSource(context)) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var status by remember { mutableStateOf("Adicione uma URL M3U/M3U8 de uma fonte autorizada.") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        channels = withContext(Dispatchers.IO) { PlaylistRepository.loadChannels(context) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("TV e Séries Online", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("TV ao vivo • Filmes • Séries", color = Color(0xFFADB6C8), fontSize = 14.sp)

            OutlinedTextField(
                value = sourceUrl,
                onValueChange = { sourceUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endereço da playlist M3U/M3U8") },
                placeholder = { Text("https://seu-servidor/lista.m3u") },
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    PlaylistRepository.saveSource(context, sourceUrl)
                                }
                            }.onSuccess {
                                status = "Fonte salva. Toque em Atualizar agora para importar os canais."
                            }.onFailure {
                                status = it.message ?: "Não foi possível salvar a fonte."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B68))
                ) { Text("Salvar fonte") }

                Button(
                    enabled = !busy && sourceUrl.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            status = "Buscando e validando a playlist..."
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    PlaylistRepository.saveSource(context, sourceUrl)
                                    PlaylistRepository.refreshConfiguredSource(context)
                                }
                            }
                            result.onSuccess { count ->
                                channels = withContext(Dispatchers.IO) {
                                    PlaylistRepository.loadChannels(context)
                                }
                                status = "$count canais importados. Seus favoritos foram preservados."
                            }.onFailure {
                                status = it.message ?: "Falha ao atualizar. A lista anterior foi mantida."
                            }
                            busy = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315A45))
                ) { Text(if (busy) "Atualizando..." else "Atualizar agora") }
            }

            Text(status, color = Color(0xFFADB6C8), fontSize = 12.sp)
            Text(
                "Canais salvos (${channels.size})",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )

            if (channels.isEmpty()) {
                Text(
                    "Sua lista aparecerá aqui depois da primeira importação bem-sucedida.",
                    color = Color(0xFF8792A6),
                    fontSize = 13.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(channels, key = { it.streamUrl }) { channel ->
                        ChannelRow(channel = channel, onFavorite = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    PlaylistRepository.setFavorite(context, channel.streamUrl, !channel.isFavorite)
                                }
                                channels = withContext(Dispatchers.IO) {
                                    PlaylistRepository.loadChannels(context)
                                }
                            }
                        })
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Use somente listas e transmissões que você tem autorização para acessar.",
                color = Color(0xFF78849A),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onFavorite: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF171B27),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, color = Color.White, fontWeight = FontWeight.Medium)
                Text(channel.groupName, color = Color(0xFF98A4B8), fontSize = 11.sp)
            }
            Text(
                if (channel.isFavorite) "★" else "☆",
                modifier = Modifier.clickable(onClick = onFavorite).padding(8.dp),
                color = Color(0xFFFFD166),
                fontSize = 22.sp
            )
        }
    }
}
