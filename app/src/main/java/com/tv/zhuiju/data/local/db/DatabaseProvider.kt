package com.tv.zhuiju.data.local.db

import android.content.Context
import com.tv.zhuiju.data.local.db.dao.SearchHistoryDao
import com.tv.zhuiju.data.local.db.dao.VideoDao
import com.tv.zhuiju.data.local.db.dao.WatchHistoryDao

object DatabaseProvider {
    private var dbHelper: DbHelper? = null

    fun init(context: Context) {
        if (dbHelper == null) {
            dbHelper = DbHelper(context.applicationContext)
        }
    }

    fun readableDb() = dbHelper?.readableDatabase
        ?: throw IllegalStateException("Database not initialized. Call init() first.")

    private fun getDb() = dbHelper?.writableDatabase
        ?: throw IllegalStateException("Database not initialized. Call init() first.")

    fun videoDao(): VideoDao = VideoDao(getDb())
    fun watchHistoryDao(): WatchHistoryDao = WatchHistoryDao(getDb())
    fun searchHistoryDao(): SearchHistoryDao = SearchHistoryDao(getDb())
}
