package com.tv.zhuiju.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.VideoItem

class DbHelper(context: Context) : SQLiteOpenHelper(
    context,
    "zhuijubao.db",
    null,
    3
) {
    companion object {
        const val TABLE_VIDEOS = "videos"
        const val TABLE_WATCH_HISTORY = "watch_history"
        const val TABLE_SEARCH_HISTORY = "search_history"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_VIDEOS (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                pic TEXT,
                remarks TEXT,
                type_name TEXT,
                type_id INTEGER,
                year TEXT,
                area TEXT,
                actor TEXT,
                director TEXT,
                content TEXT,
                play_url TEXT,
                play_from TEXT,
                score TEXT,
                score_num INTEGER,
                hits INTEGER,
                hits_day INTEGER,
                hits_week INTEGER,
                hits_month INTEGER,
                duration TEXT,
                time TEXT,
                en TEXT,
                down_url TEXT,
                down_from TEXT,
                down_note TEXT,
                down_server TEXT,
                category TEXT NOT NULL,
                synced_at INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_WATCH_HISTORY (
                video_id INTEGER PRIMARY KEY,
                video_json TEXT NOT NULL,
                name TEXT NOT NULL,
                pic TEXT,
                last_position INTEGER DEFAULT 0,
                episode_title TEXT,
                source_index INTEGER DEFAULT 0,
                episode_index INTEGER DEFAULT 0,
                watched_at INTEGER DEFAULT 0,
                drama_type TEXT DEFAULT 'video'
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_SEARCH_HISTORY (
                keyword TEXT PRIMARY KEY,
                searched_at INTEGER DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // 升级到版本3：添加 drama_type 列区分短剧和剧集历史
            try {
                db.execSQL("ALTER TABLE $TABLE_WATCH_HISTORY ADD COLUMN drama_type TEXT DEFAULT 'video'")
            } catch (_: Exception) {
                // 列已存在则忽略
            }
        }
    }
}

fun VideoItem.toContentValues(category: VideoCategory): ContentValues {
    return ContentValues().apply {
        put("id", id)
        put("name", name)
        put("pic", pic)
        put("remarks", remarks)
        put("type_name", typeName)
        put("type_id", typeId)
        put("year", year)
        put("area", area)
        put("actor", actor)
        put("director", director)
        put("content", content)
        put("play_url", playUrl)
        put("play_from", playFrom)
        put("score", score)
        put("score_num", scoreNum)
        put("hits", hits)
        put("hits_day", hitsDay)
        put("hits_week", hitsWeek)
        put("hits_month", hitsMonth)
        put("duration", duration)
        put("time", time)
        put("en", en)
        put("down_url", downUrl)
        put("down_from", downFrom)
        put("down_note", downNote)
        put("down_server", downServer)
        put("category", category.name)
        put("synced_at", System.currentTimeMillis())
    }
}

fun android.database.Cursor.toVideoItem(): VideoItem {
    return VideoItem(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        pic = getStringOrNull("pic"),
        remarks = getStringOrNull("remarks"),
        typeName = getStringOrNull("type_name"),
        typeId = getIntOrNull("type_id"),
        year = getStringOrNull("year"),
        area = getStringOrNull("area"),
        actor = getStringOrNull("actor"),
        director = getStringOrNull("director"),
        content = getStringOrNull("content"),
        playUrl = getStringOrNull("play_url"),
        playFrom = getStringOrNull("play_from"),
        score = getStringOrNull("score"),
        scoreNum = getIntOrNull("score_num"),
        hits = getIntOrNull("hits"),
        hitsDay = getIntOrNull("hits_day"),
        hitsWeek = getIntOrNull("hits_week"),
        hitsMonth = getIntOrNull("hits_month"),
        duration = getStringOrNull("duration"),
        time = getStringOrNull("time"),
        en = getStringOrNull("en"),
        downUrl = getStringOrNull("down_url"),
        downFrom = getStringOrNull("down_from"),
        downNote = getStringOrNull("down_note"),
        downServer = getStringOrNull("down_server")
    )
}

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val idx = getColumnIndex(column)
    return if (idx >= 0 && !isNull(idx)) getString(idx) else null
}

private fun android.database.Cursor.getIntOrNull(column: String): Int? {
    val idx = getColumnIndex(column)
    return if (idx >= 0 && !isNull(idx)) getInt(idx) else null
}
