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
            )) { HomeScreen() }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var sources by remember { mutableStateOf(emptyList<String>()) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var status by remember { mutableStateOf("Adicione uma ou mais fontes M3U/M3U8 autorizadas.") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val oldSource = PlaylistRepository.getSource(context)
            if (MultiSourceRepository.getSources(context).isEmpty() && oldSource.isNotBlank()) {
                MultiSourceRepository.addSource(context, oldSource)
            }
            sources = MultiSourceRepository.getSources(context)
            channels = PlaylistRepository.loadChannels(context)
        }
    }

    fun reloadCatalog() {
        scope.launch {
            channels = withContext(Dispatchers.IO) { PlaylistRepository.loadChannels(context) }
            sources = withContext(Dispatchers.IO) { MultiSourceRepository.getSources(context) }
            selectedChannel = selectedChannel?.let { selected ->
                channels.firstOrNull { it.streamUrl == selected.streamUrl }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("TV e Séries Online", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("TV ao vivo • Filmes • Séries", color = Color(0xFFADB6C8), fontSize = 14.sp)
                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nova fonte M3U/M3U8") },
                        placeholder = { Text("https://servidor autorizado/lista.m3u") },
                        singleLine = true
                    )
                    Button(
                        enabled = sourceUrl.isNotBlank(),
                        onClick = {
                            runCatching { MultiSourceRepository.addSource(context, sourceUrl) }
                                .onSuccess {
                                    sourceUrl = ""
                                    reloadCatalog()
                                    status = "Fonte adicionada. Atualize para importar o conteúdo."
                                }
                                .onFailure { status = it.message ?: "Não foi possível adicionar a fonte." }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263B68))
                    ) { Text("Adicionar fonte") }

                    if (sources.isNotEmpty()) {
                        Text("Fontes cadastradas (${sources.size})", color = Color.White, fontWeight = FontWeight.SemiBold)
                        sources.forEachIndexed { index, source ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${index + 1}. $source",
                                    color = Color(0xFFADB6C8),
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Remover",
                                    color = Color(0xFFFF8A80),
                                    modifier = Modifier.clickable {
                                        MultiSourceRepository.removeSource(context, source)
                                        reloadCatalog()
                                    }.padding(8.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Button(
                        enabled = !busy && sources.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                busy = true
                                status = "Atualizando todas as fontes e preservando os últimos dados válidos..."
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        MultiSourceRepository.refreshAll(context)
                                    }
                                }
                                result.onSuccess { summary ->
                                    reloadCatalog()
                                    status = "${summary.channelCount} canais reunidos; ${summary.updatedSources}/${summary.totalSources} fontes atualizadas." +
                                        if (summary.errors.isNotEmpty()) " Algumas fontes falharam; seus últimos dados válidos foram mantidos." else ""
                                }.onFailure {
                                    status = it.message ?: "Falha na atualização. O catálogo anterior foi mantido."
                                }
                                busy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315A45))
                    ) { Text(if (busy) "Atualizando..." else "Atualizar agora") }

                    Text(status, color = Color(0xFFADB6C8), fontSize = 12.sp)
                    Text("TV ao vivo • Canais (${channels.size})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    selectedChannel?.let { channel ->
                        Text("Assistindo: ${channel.name}", color = Color.White, fontWeight = FontWeight.Medium)
                        ChannelPlayer(channel)
                        Button(onClick = { selectedChannel = null }) { Text("Fechar player") }
                    }
                }
            }

            if (channels.isEmpty()) {
                item {
                    Text(
                        "Seus canais aparecerão aqui depois de importar uma fonte válida.",
                        color = Color(0xFF8792A6), fontSize = 13.sp
                    )
                }
            } else {
                items(channels, key = { it.streamUrl }) { channel ->
                    ChannelRow(
                        channel = channel,
                        onPlay = { selectedChannel = channel },
                        onFavorite = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    PlaylistRepository.setFavorite(context, channel.streamUrl, !channel.isFavorite)
                                }
                                reloadCatalog()
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Use somente listas e transmissões que você tem autorização para acessar.",
                    color = Color(0xFF78849A), fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
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
                Text("Toque para assistir", color = Color(0xFF6E9BFF), fontSize = 10.sp)
            }
            Text(
                if (channel.isFavorite) "★" else "☆",
                modifier = Modifier.clickable(onClick = onFavorite).padding(8.dp),
                color = Color(0xFFFFD166), fontSize = 22.sp
            )
        }
    }
}
