package com.tv.zhuiju.data.local.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.tv.zhuiju.data.local.db.DbHelper
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.ui.screen.home.WatchHistoryItem

class WatchHistoryDao(private val db: SQLiteDatabase) {

    private val gson = Gson()

    fun insertOrUpdate(
        videoId: Long,
        videoJson: String,
        name: String,
        pic: String?,
        episodeTitle: String? = null,
        sourceIndex: Int = 0,
        episodeIndex: Int = 0,
        dramaType: String = "video"
    ) {
        val values = ContentValues().apply {
            put("video_id", videoId)
            put("video_json", videoJson)
            put("name", name)
            put("pic", pic)
            put("episode_title", episodeTitle)
            put("source_index", sourceIndex)
            put("episode_index", episodeIndex)
            put("watched_at", System.currentTimeMillis())
            put("drama_type", dramaType)
        }
        db.insertWithOnConflict(
            DbHelper.TABLE_WATCH_HISTORY,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAll(): List<WatchHistoryItem> {
        val list = mutableListOf<WatchHistoryItem>()
        db.rawQuery(
            "SELECT * FROM ${DbHelper.TABLE_WATCH_HISTORY} ORDER BY watched_at DESC LIMIT 100",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToItem(cursor))
            }
        }
        return list
    }

    fun getAllByType(dramaType: String): List<WatchHistoryItem> {
        val list = mutableListOf<WatchHistoryItem>()
        db.rawQuery(
            "SELECT * FROM ${DbHelper.TABLE_WATCH_HISTORY} WHERE drama_type = ? ORDER BY watched_at DESC LIMIT 100",
            arrayOf(dramaType)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToItem(cursor))
            }
        }
        return list
    }

    fun getByVideoId(videoId: Long): WatchHistoryItem? {
        db.rawQuery(
            "SELECT * FROM ${DbHelper.TABLE_WATCH_HISTORY} WHERE video_id = ? LIMIT 1",
            arrayOf(videoId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursorToItem(cursor) else null
        }
    }

    private fun cursorToItem(cursor: android.database.Cursor): WatchHistoryItem {
        return WatchHistoryItem(
            videoId = cursor.getLong(cursor.getColumnIndexOrThrow("video_id")),
            videoJson = cursor.getString(cursor.getColumnIndexOrThrow("video_json")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            pic = cursor.getStringOrNull("pic"),
            lastPosition = cursor.getLong(cursor.getColumnIndexOrThrow("last_position")),
            episodeTitle = cursor.getStringOrNull("episode_title"),
            sourceIndex = cursor.getInt(cursor.getColumnIndexOrThrow("source_index")),
            episodeIndex = cursor.getInt(cursor.getColumnIndexOrThrow("episode_index")),
            watchedAt = cursor.getLong(cursor.getColumnIndexOrThrow("watched_at")),
            dramaType = cursor.getStringOrNull("drama_type") ?: "video"
        )
    }

    fun deleteById(videoId: Long) {
        db.delete(DbHelper.TABLE_WATCH_HISTORY, "video_id = ?", arrayOf(videoId.toString()))
    }

    fun deleteAll() {
        db.delete(DbHelper.TABLE_WATCH_HISTORY, null, null)
    }

    fun deleteAllByType(dramaType: String) {
        db.delete(DbHelper.TABLE_WATCH_HISTORY, "drama_type = ?", arrayOf(dramaType))
    }

    fun getLastPosition(videoId: Long): Long {
        db.rawQuery(
            "SELECT last_position FROM ${DbHelper.TABLE_WATCH_HISTORY} WHERE video_id = ? LIMIT 1",
            arrayOf(videoId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun updatePlaybackState(
        videoId: Long,
        position: Long,
        episodeTitle: String? = null,
        sourceIndex: Int? = null,
        episodeIndex: Int? = null
    ) {
        val values = ContentValues().apply {
            put("last_position", position)
            if (episodeTitle != null) put("episode_title", episodeTitle)
            if (sourceIndex != null) put("source_index", sourceIndex)
            if (episodeIndex != null) put("episode_index", episodeIndex)
            put("watched_at", System.currentTimeMillis())
        }
        db.update(DbHelper.TABLE_WATCH_HISTORY, values, "video_id = ?", arrayOf(videoId.toString()))
    }

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val idx = getColumnIndex(column)
        return if (idx >= 0 && !isNull(idx)) getString(idx) else null
    }
}
