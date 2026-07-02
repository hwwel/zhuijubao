package com.tv.zhuiju.data.local.db.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.tv.zhuiju.data.local.db.DbHelper

class SearchHistoryDao(private val db: SQLiteDatabase) {

    fun insertOrUpdate(keyword: String) {
        val values = ContentValues().apply {
            put("keyword", keyword)
            put("searched_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            DbHelper.TABLE_SEARCH_HISTORY,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAll(): List<String> {
        val list = mutableListOf<String>()
        db.rawQuery(
            "SELECT keyword FROM ${DbHelper.TABLE_SEARCH_HISTORY} ORDER BY searched_at DESC LIMIT 20",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursor.getString(0))
            }
        }
        return list
    }

    fun delete(keyword: String) {
        db.delete(DbHelper.TABLE_SEARCH_HISTORY, "keyword = ?", arrayOf(keyword))
    }

    fun deleteAll() {
        db.delete(DbHelper.TABLE_SEARCH_HISTORY, null, null)
    }
}
