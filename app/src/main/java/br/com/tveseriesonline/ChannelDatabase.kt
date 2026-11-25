package br.com.tveseriesonline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChannelDatabase(context: Context) :
    SQLiteOpenHelper(context, "tv_series_catalog.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE channels (
                stream_url TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                group_name TEXT NOT NULL,
                logo_url TEXT,
                tvg_id TEXT,
                is_favorite INTEGER NOT NULL DEFAULT 0
            )""".trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /**
     * Replaces the catalog with the latest validated snapshot.
     * Favorite flags survive for channels that are still present; stale or
     * invalid entries (including missing favorites) are removed.
     */
    fun replaceFromPlaylist(imported: List<Channel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val favoriteUrls = mutableSetOf<String>()
            db.rawQuery(
                "SELECT stream_url FROM channels WHERE is_favorite = 1",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) favoriteUrls += cursor.getString(0)
            }

            // Delete every old row; only validated entries are inserted again.
            db.delete("channels", null, null)

            imported.distinctBy { it.streamUrl }.forEach { channel ->
                val values = ContentValues().apply {
                    put("stream_url", channel.streamUrl)
                    put("name", channel.name)
                    put("group_name", channel.groupName)
                    put("logo_url", channel.logoUrl)
                    put("tvg_id", channel.tvgId)
                    put("is_favorite", if (channel.streamUrl in favoriteUrls) 1 else 0)
                }
                db.insertOrThrow("channels", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getChannels(): List<Channel> {
        val result = mutableListOf<Channel>()
        readableDatabase.rawQuery(
            """SELECT stream_url, name, group_name, logo_url, tvg_id, is_favorite
               FROM channels ORDER BY is_favorite DESC, group_name COLLATE NOCASE, name COLLATE NOCASE""",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Channel(
                    streamUrl = cursor.getString(0),
                    name = cursor.getString(1),
                    groupName = cursor.getString(2),
                    logoUrl = cursor.getString(3),
                    tvgId = cursor.getString(4),
                    isFavorite = cursor.getInt(5) == 1
                )
            }
        }
        return result
    }

    fun setFavorite(streamUrl: String, favorite: Boolean) {
        val values = ContentValues().apply { put("is_favorite", if (favorite) 1 else 0) }
        writableDatabase.update("channels", values, "stream_url = ?", arrayOf(streamUrl))
    }
}
