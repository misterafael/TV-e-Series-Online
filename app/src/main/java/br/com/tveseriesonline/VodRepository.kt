package br.com.tveseriesonline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Integração padrão da API Xtream Codes. Requer credenciais e acesso autorizados pelo provedor. */
object VodRepository {
    private const val PREFS = "xtream_accounts"
    private const val KEY = "accounts"
    private const val TIMEOUT = 20000

    fun accounts(context: Context): List<XtreamAccount> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val a = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            runCatching { XtreamAccount(o.getString("server"), o.getString("username"), o.getString("password")) }.getOrNull()
        }
    }

    fun addAccount(context: Context, serverValue: String, username: String, password: String) {
        val server = serverValue.trim().trimEnd('/')
        require(server.startsWith("https://") || server.startsWith("http://")) { "Informe o servidor com http:// ou https://." }
        require(username.isNotBlank() && password.isNotBlank()) { "Preencha usuário e senha." }
        require(URL(server).host.isNotBlank()) { "Endereço do servidor inválido." }
        val item = XtreamAccount(server, username.trim(), password)
        val all = accounts(context).filterNot { it.id == item.id } + item
        val a = JSONArray()
        all.forEach { a.put(JSONObject().put("server",it.server).put("username",it.username).put("password",it.password)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply()
    }

    fun removeAccount(context: Context, account: XtreamAccount) {
        val a = JSONArray()
        accounts(context).filterNot { it.id == account.id }.forEach {
            a.put(JSONObject().put("server",it.server).put("username",it.username).put("password",it.password))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply()
    }

    suspend fun refreshAll(context: Context): VodRefreshSummary {
        val accounts = accounts(context)
        require(accounts.isNotEmpty()) { "Cadastre uma conta Xtream Codes para importar o catálogo." }
        val movies = mutableListOf<VodMovie>()
        val series = mutableListOf<VodSeries>()
        val live = mutableListOf<Channel>()
        val errors = mutableListOf<String>()
        accounts.forEachIndexed { index, account ->
            try {
                val liveCats = categories(account, "get_live_categories")
                api(account,"get_live_streams").objects().forEach { o ->
                    val id=o.optString("stream_id")
                    if(id.isNotBlank()) live += Channel(streamUrl(account,"live",id,null),o.optString("name"),
                        liveCats[o.optString("category_id")] ?: "Ao vivo",o.optString("stream_icon").ifBlank { null },
                        o.optString("epg_channel_id").ifBlank { null })
                }
                val movieCats = categories(account,"get_vod_categories")
                api(account,"get_vod_streams").objects().forEach { o ->
                    val id=o.optString("stream_id")
                    if(id.isNotBlank()) movies += VodMovie(account.id+"|movie|"+id,o.optString("name"),
                        o.optString("stream_icon").ifBlank { null },o.optString("plot").ifBlank { null },
                        movieCats[o.optString("category_id")] ?: "Sem categoria",
                        streamUrl(account,"movie",id,o.optString("container_extension").ifBlank { null }),
                        o.optString("container_extension").ifBlank { null })
                }
                val seriesCats = categories(account,"get_series_categories")
                api(account,"get_series").objects().forEach { o ->
                    val id=o.optString("series_id")
                    if(id.isNotBlank()) series += VodSeries(account.id+"|series|"+id,o.optString("name"),
                        o.optString("cover").ifBlank { null },o.optString("plot").ifBlank { null },
                        seriesCats[o.optString("category_id")] ?: "Sem categoria",o.optString("year").ifBlank { null })
                }
            } catch (e: Exception) { errors += "Conta ${index+1}: ${e.message ?: "falha na importação"}" }
        }
        require(movies.isNotEmpty() || series.isNotEmpty() || live.isNotEmpty()) {
            errors.firstOrNull() ?: "Nenhuma conta retornou conteúdo válido."
        }
        VodStore(context).use { db ->
            if (movies.isNotEmpty()) db.replaceMovies(movies)
            if (series.isNotEmpty()) db.replaceSeries(series)
            if (live.isNotEmpty()) db.replaceLive(live)
        }
        return VodRefreshSummary(movies.size,series.size,live.size,errors)
    }

    suspend fun episodes(context: Context, account: XtreamAccount, seriesIdWithPrefix: String): List<VodEpisode> {
        val id = seriesIdWithPrefix.substringAfterLast("|")
        val response = api(account,"get_series_info","series_id" to id)
        val seasons = response.optJSONObject("episodes") ?: JSONObject()
        val result = mutableListOf<VodEpisode>()
        val keys = seasons.keys()
        while(keys.hasNext()) {
            val seasonKey=keys.next()
            val season=seasonKey.toIntOrNull() ?: 0
            val arr=seasons.optJSONArray(seasonKey) ?: JSONArray()
            for(i in 0 until arr.length()) {
                val e=arr.optJSONObject(i) ?: continue
                val episodeId=e.optString("id")
                if(episodeId.isNotBlank()) result += VodEpisode(episodeId,seriesIdWithPrefix,season,
                    e.optInt("episode_num",i+1),e.optString("title").ifBlank { "Episódio ${i+1}" },
                    streamUrl(account,"series",episodeId,e.optString("container_extension").ifBlank { null }),
                    e.optString("container_extension").ifBlank { null })
            }
        }
        return result.sortedWith(compareBy<VodEpisode>{it.season}.thenBy{it.episode})
    }

    fun movies(context: Context) = VodStore(context).use { it.movies() }
    fun series(context: Context) = VodStore(context).use { it.series() }
    fun liveChannels(context: Context) = VodStore(context).use { it.live() }

    private fun categories(account: XtreamAccount, action: String): Map<String,String> =
        api(account,action).objects().associate { it.optString("category_id") to it.optString("category_name") }

    private fun api(account: XtreamAccount, action: String, vararg params: Pair<String,String>): JSONObject {
        val values = listOf("username" to account.username,"password" to account.password,"action" to action)+params
        val url=account.server+"/player_api.php?"+values.joinToString("&"){enc(it.first)+"="+enc(it.second)}
        val conn=(URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout=TIMEOUT; readTimeout=TIMEOUT; requestMethod="GET"
            setRequestProperty("Accept","application/json")
            setRequestProperty("User-Agent","TVeSeriesOnline/1.0")
        }
        try {
            require(conn.responseCode in 200..299) { "Servidor respondeu HTTP ${conn.responseCode}" }
            return JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { conn.disconnect() }
    }

    private fun JSONObject.objects(): List<JSONObject> {
        val out=mutableListOf<JSONObject>()
        // Xtream list actions normally return arrays, not objects.
        return out
    }
    private fun categoriesArray(account: XtreamAccount, action: String): JSONArray = JSONArray()
    private fun streamUrl(a:XtreamAccount,type:String,id:String,extension:String?):String {
        val ext=extension?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) } ?: if(type=="live") "" else "mp4"
        val base=a.server+"/$type/${enc(a.username)}/${enc(a.password)}/${enc(id)}"
        return if(ext.isBlank()) base else "$base.$ext"
    }
    private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
}

private class VodStore(context: Context): SQLiteOpenHelper(context,"tv_series_vod.db",null,1) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE movies(id TEXT PRIMARY KEY,name TEXT,poster TEXT,plot TEXT,category TEXT,stream_url TEXT,extension TEXT)")
        db.execSQL("CREATE TABLE series(id TEXT PRIMARY KEY,name TEXT,poster TEXT,plot TEXT,category TEXT,year TEXT)")
        db.execSQL("CREATE TABLE xtream_live(url TEXT PRIMARY KEY,name TEXT,category TEXT,logo TEXT,epg TEXT)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    fun replaceMovies(items:List<VodMovie>) {
        val db=writableDatabase; db.beginTransaction()
        try { db.delete("movies",null,null); items.distinctBy{it.id}.forEach { db.execSQL("INSERT OR REPLACE INTO movies VALUES(?,?,?,?,?,?,?)",arrayOf(it.id,it.name,it.poster,it.plot,it.category,it.streamUrl,it.extension)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun replaceSeries(items:List<VodSeries>) {
        val db=writableDatabase; db.beginTransaction()
        try { db.delete("series",null,null); items.distinctBy{it.id}.forEach { db.execSQL("INSERT OR REPLACE INTO series VALUES(?,?,?,?,?,?)",arrayOf(it.id,it.name,it.poster,it.plot,it.category,it.year)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun replaceLive(items:List<Channel>) {
        val db=writableDatabase; db.beginTransaction()
        try { db.delete("xtream_live",null,null); items.distinctBy{it.streamUrl}.forEach { db.execSQL("INSERT OR REPLACE INTO xtream_live VALUES(?,?,?,?,?)",arrayOf(it.streamUrl,it.name,it.groupName,it.logoUrl,it.tvgId)) }; db.setTransactionSuccessful() }
        finally { db.endTransaction() }
    }
    fun movies():List<VodMovie> {
        val out=mutableListOf<VodMovie>(); readableDatabase.rawQuery("SELECT * FROM movies ORDER BY name COLLATE NOCASE",null).use { c ->
            while(c.moveToNext()) out += VodMovie(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6))
        }; return out
    }
    fun series():List<VodSeries> {
        val out=mutableListOf<VodSeries>(); readableDatabase.rawQuery("SELECT * FROM series ORDER BY name COLLATE NOCASE",null).use { c ->
            while(c.moveToNext()) out += VodSeries(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))
        }; return out
    }
    fun live():List<Channel> {
        val out=mutableListOf<Channel>(); readableDatabase.rawQuery("SELECT * FROM xtream_live ORDER BY name COLLATE NOCASE",null).use { c ->
            while(c.moveToNext()) out += Channel(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4))
        }; return out
    }
}
