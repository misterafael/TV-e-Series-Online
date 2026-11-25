package br.com.tveseriesonline

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppBackground = Color(0xFF080A12)
private val CardBackground = Color(0xFF171B29)
private val MutedText = Color(0xFFADB6C8)
private val AccentPurple = Color(0xFF9B35F5)
private val AccentBlue = Color(0xFF368BFF)

class MainActivity : ComponentActivity() {
    private var updateReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshScheduler.scheduleAutomatic(applicationContext)
        updateReceiver = AppUpdateChecker.DownloadReceiver().also { receiver ->
            val filter = IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                registerReceiver(receiver, filter)
            }
        }
        AppUpdateChecker.checkOnLaunch(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                background = AppBackground,
                surface = CardBackground,
                primary = AccentPurple
            )) { HomeScreen() }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdateChecker.resumeInstallIfReady(this)
    }

    override fun onDestroy() {
        updateReceiver?.let { runCatching { unregisterReceiver(it) } }
        updateReceiver = null
        super.onDestroy()
    }
}

private enum class MainSection { HOME, LIVE, MOVIES, SERIES, ANIME, FAVORITES }

@Composable
private fun HomeScreen() {
    val context = LocalContext.current.applicationContext
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf(emptyList<Channel>()) }
    var movies by remember { mutableStateOf(emptyList<VodMovie>()) }
    var series by remember { mutableStateOf(emptyList<VodSeries>()) }
    var episodes by remember { mutableStateOf(emptyList<VodEpisode>()) }
    var selectedSeries by remember { mutableStateOf<VodSeries?>(null) }
    var selectedMedia by remember { mutableStateOf<Pair<String, String>?>(null) }
    var section by rememberSaveable { mutableStateOf(MainSection.HOME.name) }
    var liveQuery by rememberSaveable { mutableStateOf("") }
    var movieQuery by rememberSaveable { mutableStateOf("") }
    var seriesQuery by rememberSaveable { mutableStateOf("") }
    var animeQuery by rememberSaveable { mutableStateOf("") }
    var favoriteQuery by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("Preparando catálogo e procurando fontes públicas...") }
    var busy by remember { mutableStateOf(false) }
    var favoriteIds by remember {
        mutableStateOf(context.getSharedPreferences("catalog_favorites", Context.MODE_PRIVATE)
            .getStringSet("ids", emptySet())?.toSet() ?: emptySet())
    }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.1.0"
    }

    fun saveFavorite(id: String) {
        val next = favoriteIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        favoriteIds = next
        context.getSharedPreferences("catalog_favorites", Context.MODE_PRIVATE)
            .edit().putStringSet("ids", next).apply()
    }

    suspend fun loadCatalog() {
        val result = withContext(Dispatchers.IO) {
            Triple(
                PlaylistRepository.loadChannels(context) + VodRepository.liveChannels(context),
                VodRepository.movies(context),
                VodRepository.series(context)
            )
        }
        channels = result.first.distinctBy { it.streamUrl }
        movies = result.second
        series = result.third
    }

    fun refreshCatalog() {
        scope.launch {
            busy = true
            status = "Procurando novas fontes públicas e atualizando o catálogo..."
            val discovery = runCatching {
                withContext(Dispatchers.IO) { PublicSourceDiscovery.discoverAndRegister(context) }
            }.getOrNull()
            val refresh = runCatching {
                withContext(Dispatchers.IO) { ContentRefreshRepository.refreshAll(context) }
            }
            loadCatalog()
            if (refresh.isSuccess) {
                val summary = refresh.getOrThrow()
                status = "Busca concluída: ${channels.size} canais, ${summary.vodMovies} filmes e ${summary.vodSeries} séries. ${discovery?.added ?: 0} novas fontes adicionadas."
                if (summary.errors.isNotEmpty() || discovery?.errors?.isNotEmpty() == true) {
                    status += " Algumas fontes não responderam e foram ignoradas."
                }
            } else {
                status = "Fontes verificadas. ${channels.size} canais disponíveis. O catálogo anterior foi mantido quando uma fonte falhou."
                if (movies.isEmpty() && series.isEmpty()) {
                    status += " Filmes e séries dependem de fontes públicas licenciadas compatíveis."
                }
            }
            busy = false
        }
    }

    fun openSeries(item: VodSeries) {
        selectedSeries = item
        episodes = emptyList()
        scope.launch {
            status = "Carregando temporadas e episódios de ${item.name}..."
            val account = withContext(Dispatchers.IO) {
                VodRepository.accounts(context).firstOrNull { item.id.startsWith(it.id + "|series|") }
            }
            if (account == null) {
                status = "Os episódios desta série não estão disponíveis na fonte atual."
            } else {
                val loaded = runCatching {
                    withContext(Dispatchers.IO) { VodRepository.episodes(context, account, item.id) }
                }
                loaded.onSuccess {
                    episodes = it
                    status = "Temporadas e episódios carregados."
                }.onFailure {
                    status = it.message ?: "Não foi possível carregar os episódios."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val oldSource = PlaylistRepository.getSource(context)
            if (MultiSourceRepository.getSources(context).isEmpty() && oldSource.isNotBlank()) {
                MultiSourceRepository.addSource(context, oldSource)
            }
            PublicSourceDiscovery.discoverAndRegister(context)
        }
        loadCatalog()
        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) refreshCatalog()
        else status = "Catálogo carregado. As fontes são verificadas automaticamente a cada seis horas."
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val wideLayout = maxWidth >= 700.dp
            val sidePadding = if (wideLayout) 28.dp else 16.dp
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 1200.dp)
                    .padding(horizontal = sidePadding, vertical = if (wideLayout) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("TV Filme e Cinema", color = Color.White, fontSize = if (wideLayout) 32.sp else 27.sp,
                                    fontWeight = FontWeight.Bold)
                                Text("TV ao vivo • Filmes • Séries • Animes", color = MutedText, fontSize = 14.sp)
                            }
                            Button(
                                onClick = { refreshCatalog() },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                            ) { Text(if (busy) "Procurando..." else "⟳  Procurar fontes") }
                        }
                        if (selectedMedia != null) {
                            Surface(color = CardBackground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Reprodução: ${selectedMedia!!.first}", color = Color.White, fontWeight = FontWeight.Bold)
                                    VodPlayer(selectedMedia!!.first, selectedMedia!!.second)
                                    Button(onClick = { selectedMedia = null }) { Text("Fechar reprodução") }
                                }
                            }
                        }
                        if (selectedSeries != null) {
                            Surface(color = CardBackground, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(selectedSeries!!.name, color = Color.White, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f))
                                        Text("Voltar", color = AccentBlue, modifier = Modifier.clickable {
                                            selectedSeries = null
                                            episodes = emptyList()
                                        }.padding(8.dp))
                                    }
                                    Text("Temporadas e episódios", color = MutedText)
                                    if (episodes.isEmpty()) Text("Aguardando episódios ou fonte compatível...", color = MutedText)
                                    episodes.forEach { episode ->
                                        Surface(color = AppBackground, shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selectedMedia = episode.title to episode.streamUrl
                                            }) {
                                            Text("T${episode.season} • E${episode.episode} — ${episode.title}",
                                                color = Color.White, modifier = Modifier.padding(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Text(status, color = MutedText, fontSize = 12.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MainSection.values().forEach { destination ->
                                val label = when (destination) {
                                    MainSection.HOME -> "Início"
                                    MainSection.LIVE -> "Canais de TV"
                                    MainSection.MOVIES -> "Filmes"
                                    MainSection.SERIES -> "Séries"
                                    MainSection.ANIME -> "Animes"
                                    MainSection.FAVORITES -> "Favoritos"
                                }
                                Button(
                                    onClick = { section = destination.name },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (section == destination.name) AccentPurple else CardBackground
                                    )
                                ) { Text(label) }
                            }
                        }
                    }
                }

                if (section == MainSection.HOME.name || section == MainSection.LIVE.name) {
                    item {
                        ChannelShelf(channels, liveQuery, { liveQuery = it },
                            onPlay = { selectedMedia = it.name to it.streamUrl },
                            onFavorite = { channel ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        PlaylistRepository.setFavorite(context, channel.streamUrl, !channel.isFavorite)
                                    }
                                    loadCatalog()
                                }
                            }, wideLayout = wideLayout)
                    }
                }
                if (section == MainSection.HOME.name || section == MainSection.MOVIES.name) {
                    item {
                        MovieShelf(movies, movieQuery, { movieQuery = it },
                            favoriteIds = favoriteIds,
                            onFavorite = { saveFavorite(it) },
                            onPlay = { selectedMedia = it.name to it.streamUrl },
                            wideLayout = wideLayout)
                    }
                }
                if (section == MainSection.HOME.name || section == MainSection.SERIES.name) {
                    item {
                        SeriesShelf(series.filterNot { isAnime(it) }, seriesQuery, { seriesQuery = it },
                            favoriteIds, ::saveFavorite, ::openSeries, wideLayout)
                    }
                }
                if (section == MainSection.HOME.name || section == MainSection.ANIME.name) {
                    item {
                        SeriesShelf(series.filter { isAnime(it) }, animeQuery, { animeQuery = it },
                            favoriteIds, ::saveFavorite, ::openSeries, wideLayout, title = "Animes")
                    }
                }
                if (section == MainSection.HOME.name || section == MainSection.FAVORITES.name) {
                    item {
                        FavoritesShelf(channels.filter { it.isFavorite }, movies.filter { it.id in favoriteIds },
                            series.filter { it.id in favoriteIds }, favoriteQuery, { favoriteQuery = it },
                            onPlay = { selectedMedia = it.name to it.streamUrl },
                            onOpenSeries = ::openSeries, wideLayout = wideLayout)
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("TV Filme e Cinema  •  Versão $versionName", color = MutedText, fontSize = 12.sp)
                        Text("Atualizações automáticas ativadas • Verificação ao abrir o aplicativo",
                            color = Color(0xFF8290A8), fontSize = 10.sp)
                        Text("Fontes públicas e autorizadas • Catálogo atualizado periodicamente",
                            color = Color(0xFF8290A8), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun isAnime(item: VodSeries): Boolean {
    val text = "${item.name} ${item.category}".lowercase()
    return listOf("anime", "animação japonesa", "animation", "cartoon").any { it in text }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = AccentPurple, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(5.dp).height(28.dp)) {}
        Column {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = MutedText, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(hint, color = MutedText) }, singleLine = true
    )
}

@Composable
private fun ChannelShelf(
    channels: List<Channel>, query: String, onQuery: (String) -> Unit,
    onPlay: (Channel) -> Unit, onFavorite: (Channel) -> Unit, wideLayout: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Canais de TV", "Procure pelo nome do canal")
        SearchBox(query, onQuery, "Exemplo: Globo, SBT, Band...")
        val filtered = channels.filter { it.name.contains(query, true) || it.groupName.contains(query, true) }
        if (filtered.isEmpty()) Text("Nenhum canal encontrado nas fontes disponíveis.", color = MutedText)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.streamUrl }) { channel ->
                Surface(
                    color = CardBackground, shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(if (wideLayout) 170.dp else 142.dp)
                        .clickable { onPlay(channel) }
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!channel.logoUrl.isNullOrBlank()) {
                            AsyncImage(channel.logoUrl, contentDescription = channel.name,
                                modifier = Modifier.fillMaxWidth().height(76.dp), contentScale = ContentScale.Fit)
                        } else {
                            Text("▣", color = AccentPurple, fontSize = 32.sp)
                        }
                        Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Text(channel.groupName, color = MutedText, fontSize = 10.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("● Ao vivo", color = Color(0xFFFF5555), fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(if (channel.isFavorite) "★" else "☆", color = Color(0xFFFFD166),
                                modifier = Modifier.clickable { onFavorite(channel) }.padding(3.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieShelf(
    movies: List<VodMovie>, query: String, onQuery: (String) -> Unit,
    favoriteIds: Set<String>, onFavorite: (String) -> Unit,
    onPlay: (VodMovie) -> Unit, wideLayout: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Filmes", "Procure pelo título do filme")
        SearchBox(query, onQuery, "Exemplo: Vingadores...")
        val filtered = movies.filter { it.name.contains(query, true) || it.category.contains(query, true) }
        if (filtered.isEmpty()) Text("Nenhum filme disponível nas fontes públicas compatíveis no momento.", color = MutedText)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { movie ->
                Surface(color = CardBackground, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(if (wideLayout) 150.dp else 122.dp)) {
                    Column(Modifier.clickable { onPlay(movie) }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (!movie.poster.isNullOrBlank()) AsyncImage(movie.poster, contentDescription = movie.name,
                            modifier = Modifier.fillMaxWidth().height(if (wideLayout) 190.dp else 158.dp),
                            contentScale = ContentScale.Crop)
                        else Surface(color = AppBackground, modifier = Modifier.fillMaxWidth().height(if (wideLayout) 190.dp else 158.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("FILME", color = MutedText) }
                        }
                        Text(movie.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(movie.category, color = MutedText, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(if (movie.id in favoriteIds) "★" else "☆", color = Color(0xFFFFD166),
                                modifier = Modifier.clickable { onFavorite(movie.id) }.padding(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesShelf(
    series: List<VodSeries>, query: String, onQuery: (String) -> Unit,
    favoriteIds: Set<String>, onFavorite: (String) -> Unit,
    onOpen: (VodSeries) -> Unit, wideLayout: Boolean, title: String = "Séries"
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, if (title == "Animes") "Procure pelo nome do anime" else "Procure pelo nome da série")
        SearchBox(query, onQuery, if (title == "Animes") "Exemplo: Naruto..." else "Exemplo: Breaking Bad...")
        val filtered = series.filter { it.name.contains(query, true) || it.category.contains(query, true) }
        if (filtered.isEmpty()) Text("Nenhum conteúdo encontrado nesta categoria nas fontes disponíveis.", color = MutedText)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { item ->
                Surface(color = CardBackground, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(if (wideLayout) 150.dp else 122.dp)) {
                    Column(Modifier.clickable { onOpen(item) }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (!item.poster.isNullOrBlank()) AsyncImage(item.poster, contentDescription = item.name,
                            modifier = Modifier.fillMaxWidth().height(if (wideLayout) 190.dp else 158.dp),
                            contentScale = ContentScale.Crop)
                        else Surface(color = AppBackground, modifier = Modifier.fillMaxWidth().height(if (wideLayout) 190.dp else 158.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(if (title == "Animes") "ANIME" else "SÉRIE", color = MutedText) }
                        }
                        Text(item.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.category, color = MutedText, fontSize = 9.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(if (item.id in favoriteIds) "★" else "☆", color = Color(0xFFFFD166),
                                modifier = Modifier.clickable { onFavorite(item.id) }.padding(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesShelf(
    channels: List<Channel>, movies: List<VodMovie>, series: List<VodSeries>,
    query: String, onQuery: (String) -> Unit,
    onPlay: (VodMovie) -> Unit, onOpenSeries: (VodSeries) -> Unit, wideLayout: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Favoritos", "Seus canais, filmes e séries salvos")
        SearchBox(query, onQuery, "Pesquisar nos favoritos...")
        val favoriteMovies = movies.filter { it.name.contains(query, true) }
        val favoriteSeries = series.filter { it.name.contains(query, true) }
        val favoriteChannels = channels.filter { it.name.contains(query, true) }
        if (favoriteChannels.isEmpty() && favoriteMovies.isEmpty() && favoriteSeries.isEmpty()) {
            Text("Você ainda não tem favoritos salvos.", color = MutedText)
        }
        favoriteChannels.forEach { channel ->
            Text("★ ${channel.name} • ${channel.groupName}", color = Color.White,
                modifier = Modifier.fillMaxWidth().clickable { onPlay(VodMovie(channel.streamUrl, channel.name,
                    channel.logoUrl, null, channel.groupName, channel.streamUrl, null)) }.padding(8.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(favoriteMovies, key = { it.id }) { movie ->
                Surface(color = CardBackground, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.width(if (wideLayout) 150.dp else 122.dp).clickable { onPlay(movie) }) {
                    Column(Modifier.padding(8.dp)) {
                        if (!movie.poster.isNullOrBlank()) AsyncImage(movie.poster, contentDescription = movie.name,
                            modifier = Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Crop)
                        Text(movie.name, color = Color.White, maxLines = 2)
                    }
                }
            }
            items(favoriteSeries, key = { it.id }) { item ->
                Surface(color = CardBackground, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.width(if (wideLayout) 150.dp else 122.dp).clickable { onOpenSeries(item) }) {
                    Column(Modifier.padding(8.dp)) {
                        if (!item.poster.isNullOrBlank()) AsyncImage(item.poster, contentDescription = item.name,
                            modifier = Modifier.fillMaxWidth().height(130.dp), contentScale = ContentScale.Crop)
                        Text(item.name, color = Color.White, maxLines = 2)
                    }
                }
            }
        }
    }
}
