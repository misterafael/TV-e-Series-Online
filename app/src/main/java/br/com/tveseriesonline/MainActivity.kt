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
                background = Color(0xFF090B12), surface = Color(0xFF171B27),
                primary = Color(0xFF5B8CFF)
            )) { HomeScreen() }
        }
    }
}

private enum class CatalogTab { LIVE, MOVIES, SERIES }

@Composable
private fun HomeScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var sourceUrl by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var sources by remember { mutableStateOf(emptyList<String>()) }
    var accounts by remember { mutableStateOf(emptyList<XtreamAccount>()) }
    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var movies by remember { mutableStateOf(emptyList<VodMovie>()) }
    var series by remember { mutableStateOf(emptyList<VodSeries>()) }
    var episodes by remember { mutableStateOf(emptyList<VodEpisode>()) }
    var selectedSeries by remember { mutableStateOf<VodSeries?>(null) }
    var selectedUrl by remember { mutableStateOf<Pair<String,String>?>(null) }
    var tab by rememberSaveable { mutableStateOf(CatalogTab.LIVE.name) }
    var status by remember { mutableStateOf("Adicione fontes M3U/M3U8 ou contas Xtream autorizadas.") }
    var busy by remember { mutableStateOf(false) }

    fun reloadCatalog() {
        scope.launch {
            withContext(Dispatchers.IO) {
                sources = MultiSourceRepository.getSources(context)
                accounts = VodRepository.accounts(context)
                channels = PlaylistRepository.loadChannels(context) + VodRepository.liveChannels(context)
                movies = VodRepository.movies(context)
                series = VodRepository.series(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val oldSource = PlaylistRepository.getSource(context)
            if (MultiSourceRepository.getSources(context).isEmpty() && oldSource.isNotBlank()) {
                MultiSourceRepository.addSource(context, oldSource)
            }
        }
        reloadCatalog()
    }

    fun refresh() {
        scope.launch {
            busy = true
            status = "Atualizando fontes M3U/M3U8 e Xtream..."
            val result = runCatching { withContext(Dispatchers.IO) { ContentRefreshRepository.refreshAll(context) } }
            result.onSuccess { summary ->
                reloadCatalog()
                status = "Atualização concluída: ${summary.m3uChannels + summary.xtreamChannels} canais, ${summary.vodMovies} filmes e ${summary.vodSeries} séries."
                if (summary.errors.isNotEmpty()) status += " Fontes com erro foram excluídas desta atualização."
            }.onFailure { status = it.message ?: "Falha na atualização. O catálogo anterior foi preservado quando nenhuma fonte respondeu." }
            busy = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("TV e Séries Online", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("TV ao vivo • Filmes • Séries", color = Color(0xFFADB6C8), fontSize = 14.sp)

                    Text("Fontes M3U/M3U8", color = Color.White, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = sourceUrl, onValueChange = { sourceUrl = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("URL da playlist") },
                        placeholder = { Text("https://servidor/autorizado/lista.m3u") }, singleLine = true)
                    Button(enabled = sourceUrl.isNotBlank(), onClick = {
                        runCatching { MultiSourceRepository.addSource(context, sourceUrl) }
                            .onSuccess { sourceUrl = ""; reloadCatalog(); status = "Playlist cadastrada. Toque em Atualizar agora." }
                            .onFailure { status = it.message ?: "Não foi possível cadastrar a playlist." }
                    }) { Text("Adicionar playlist") }

                    Text("Conta Xtream Codes", color = Color.White, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = server, onValueChange = { server = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Servidor (http:// ou https://)") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Usuário") }, singleLine = true)
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha") }, singleLine = true)
                    Button(enabled = server.isNotBlank() && username.isNotBlank() && password.isNotBlank(), onClick = {
                        runCatching { VodRepository.addAccount(context,server,username,password) }
                            .onSuccess { server=""; username=""; password=""; reloadCatalog(); status="Conta Xtream cadastrada." }
                            .onFailure { status=it.message ?: "Não foi possível cadastrar a conta." }
                    }) { Text("Adicionar conta") }

                    if (sources.isNotEmpty() || accounts.isNotEmpty()) {
                        Text("Fontes configuradas: ${sources.size} playlists • ${accounts.size} contas", color = Color(0xFFADB6C8), fontSize = 12.sp)
                        sources.forEach { source ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(source, color = Color(0xFFADB6C8), fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text("Remover", color = Color(0xFFFF8A80), modifier = Modifier.clickable {
                                    MultiSourceRepository.removeSource(context,source); reloadCatalog()
                                }.padding(8.dp))
                            }
                        }
                        accounts.forEach { account ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${account.server} • ${account.username}", color = Color(0xFFADB6C8),
                                    fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text("Remover", color = Color(0xFFFF8A80), modifier = Modifier.clickable {
                                    VodRepository.removeAccount(context,account); reloadCatalog()
                                }.padding(8.dp))
                            }
                        }
                    }
                    Button(enabled = !busy && (sources.isNotEmpty() || accounts.isNotEmpty()), onClick = { refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315A45))) {
                        Text(if (busy) "Atualizando..." else "Atualizar todas as fontes")
                    }
                    Text(status, color = Color(0xFFADB6C8), fontSize = 12.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatalogTab.values().forEach { item ->
                            Button(onClick = { tab = item.name; selectedSeries = null; episodes = emptyList() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tab == item.name) Color(0xFF315A45) else Color(0xFF263044)
                                )) {
                                Text(when(item) { CatalogTab.LIVE -> "TV ao vivo"; CatalogTab.MOVIES -> "Filmes"; CatalogTab.SERIES -> "Séries" })
                            }
                        }
                    }
                    selectedUrl?.let { selected ->
                        VodPlayer(selected.first,selected.second)
                        Button(onClick = { selectedUrl = null }) { Text("Fechar reprodução") }
                    }
                }
            }

            when (CatalogTab.valueOf(tab)) {
                CatalogTab.LIVE -> {
                    if (channels.isEmpty()) item { Text("Nenhum canal importado.", color = Color(0xFF8792A6)) }
                    items(channels.distinctBy { it.streamUrl }, key = { it.streamUrl }) { channel ->
                        ChannelRow(channel, onPlay = { selectedUrl = channel.name to channel.streamUrl }, onFavorite = {
                            scope.launch {
                                withContext(Dispatchers.IO) { PlaylistRepository.setFavorite(context,channel.streamUrl,!channel.isFavorite) }
                                reloadCatalog()
                            }
                        })
                    }
                }
                CatalogTab.MOVIES -> {
                    if (movies.isEmpty()) item { Text("Nenhum filme encontrado. Cadastre uma fonte Xtream e atualize.", color = Color(0xFF8792A6)) }
                    items(movies, key = { it.id }) { movie ->
                        CatalogRow(movie.name,movie.category,movie.plot) {
                            selectedUrl = movie.name to movie.streamUrl
                        }
                    }
                }
                CatalogTab.SERIES -> {
                    if (selectedSeries == null) {
                        if (series.isEmpty()) item { Text("Nenhuma série encontrada. Cadastre uma fonte Xtream e atualize.", color = Color(0xFF8792A6)) }
                        items(series, key = { it.id }) { item ->
                            CatalogRow(item.name,item.category,item.plot) {
                                selectedSeries = item
                                episodes = emptyList()
                                scope.launch {
                                    val account = withContext(Dispatchers.IO) {
                                        VodRepository.accounts(context).firstOrNull { item.id.startsWith(it.id + "|series|") }
                                    }
                                    if (account == null) status = "Não foi possível localizar a conta desta série."
                                    else {
                                        status = "Carregando temporadas e episódios..."
                                        val loaded = runCatching { withContext(Dispatchers.IO) { VodRepository.episodes(context,account,item.id) } }
                                        loaded.onSuccess { episodes = it; status = "Temporadas e episódios carregados." }
                                            .onFailure { status = it.message ?: "Falha ao carregar episódios." }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedSeries!!.name,color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                                Text("Voltar",color=Color(0xFF8DB0FF),modifier=Modifier.clickable {
                                    selectedSeries=null; episodes=emptyList()
                                }.padding(8.dp))
                            }
                            Text("Temporadas e episódios",color=Color(0xFFADB6C8))
                        }
                        if (episodes.isEmpty()) item { Text("Carregando episódios ou nenhum episódio disponível.",color=Color(0xFF8792A6)) }
                        items(episodes,key={it.id}) { episode ->
                            CatalogRow("T${episode.season} • E${episode.episode} — ${episode.title}","Temporada ${episode.season}",null) {
                                selectedUrl = episode.title to episode.streamUrl
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Use somente fontes e transmissões que você tenha autorização para acessar.",color=Color(0xFF78849A),fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun CatalogRow(title:String,category:String,description:String?,onClick:()->Unit) {
    Surface(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),color=Color(0xFF171B27),shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,color=Color.White,fontWeight=FontWeight.Medium)
            Text(category,color=Color(0xFF8DB0FF),fontSize=11.sp)
            if(!description.isNullOrBlank()) Text(description,color=Color(0xFFADB6C8),fontSize=11.sp,maxLines=3)
            Text("Abrir",color=Color(0xFF6E9BFF),fontSize=10.sp)
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel,onPlay:()->Unit,onFavorite:()->Unit) {
    Surface(modifier=Modifier.fillMaxWidth().clickable(onClick=onPlay),color=Color(0xFF171B27),shape=RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(channel.name,color=Color.White,fontWeight=FontWeight.Medium)
                Text(channel.groupName,color=Color(0xFF98A4B8),fontSize=11.sp)
                Text("Toque para assistir",color=Color(0xFF6E9BFF),fontSize=10.sp)
            }
            Text(if(channel.isFavorite) "★" else "☆",modifier=Modifier.clickable(onClick=onFavorite).padding(8.dp),
                color=Color(0xFFFFD166),fontSize=22.sp)
        }
    }
}
