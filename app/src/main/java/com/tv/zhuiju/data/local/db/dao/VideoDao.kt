package com.tv.zhuiju.data.local.db.dao

import android.database.sqlite.SQLiteDatabase
import com.tv.zhuiju.data.local.db.DbHelper
import com.tv.zhuiju.data.local.db.toContentValues
import com.tv.zhuiju.data.local.db.toVideoItem
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.VideoItem

class VideoDao(private val db: SQLiteDatabase) {

    fun insertOrIgnore(item: VideoItem, category: VideoCategory) {
        db.insertWithOnConflict(
            DbHelper.TABLE_VIDEOS,
            null,
            item.toContentValues(category),
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun existsById(id: Long): Boolean {
        db.rawQuery(
            "SELECT 1 FROM ${DbHelper.TABLE_VIDEOS} WHERE id = ? LIMIT 1",
            arrayOf(id.toString())
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun updateCategory(id: Long, category: VideoCategory) {
        db.execSQL(
            "UPDATE ${DbHelper.TABLE_VIDEOS} SET category = ?, synced_at = ? WHERE id = ?",
            arrayOf(category.name, System.currentTimeMillis(), id)
        )
    }

    fun getByCategory(category: VideoCategory, limit: Int = 100): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        db.rawQuery(
            "SELECT * FROM ${DbHelper.TABLE_VIDEOS} WHERE category = ? ORDER BY synced_at DESC LIMIT ?",
            arrayOf(category.name, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toVideoItem())
            }
        }
        return list
    }

    fun searchByName(keyword: String): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        db.rawQuery(
            "SELECT * FROM ${DbHelper.TABLE_VIDEOS} WHERE name LIKE ? ORDER BY synced_at DESC LIMIT 50",
            arrayOf("%$keyword%")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.toVideoItem())
            }
        }
        return list
    }

    /**
     * 清空本地视频库中的所有视频数据。
     */
    fun deleteAll() {
        db.execSQL("DELETE FROM ${DbHelper.TABLE_VIDEOS}")
    }
}
