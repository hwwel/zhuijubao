package com.tv.zhuiju.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tv.zhuiju.data.model.VideoItem

/**
 * 追剧收藏管理器，使用 SharedPreferences 存储
 */
class FavoritesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun isFavorite(videoId: Long): Boolean {
        return getFavorites().any { it.id == videoId }
    }

    fun addFavorite(item: VideoItem) {
        val list = getFavorites().toMutableList()
        if (list.none { it.id == item.id }) {
            list.add(item)
            saveFavorites(list)
        }
    }

    fun removeFavorite(videoId: Long) {
        val list = getFavorites().filter { it.id != videoId }
        saveFavorites(list)
    }

    fun toggleFavorite(item: VideoItem): Boolean {
        return if (isFavorite(item.id)) {
            removeFavorite(item.id)
            false
        } else {
            addFavorite(item)
            true
        }
    }

    fun getFavorites(): List<VideoItem> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<VideoItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFavorites(list: List<VideoItem>) {
        prefs.edit().putString(KEY_FAVORITES, gson.toJson(list)).apply()
    }

    companion object {
        private const val PREFS_NAME = "favorites_prefs"
        private const val KEY_FAVORITES = "favorites"

        @Volatile
        private var instance: FavoritesManager? = null

        fun getInstance(context: Context): FavoritesManager {
            return instance ?: synchronized(this) {
                instance ?: FavoritesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
