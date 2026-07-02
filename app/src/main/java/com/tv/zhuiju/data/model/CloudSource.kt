package com.tv.zhuiju.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 云端采集站配置
 */
data class CloudSource(
    val name: String,       // 名称
    val url: String,        // API URL
    val type: String,       // 类型
    val category: String,   // 分类：main/vod/short/anime/midnight
    val recommend: Int,     // 是否推荐
    val verified: Boolean,  // 是否验证可用
    val remark: String      // 备注
) {
    companion object {
        private val gson = Gson()

        /**
         * 从 JSON 字符串解析云端采集站列表
         */
        fun parseList(json: String): List<CloudSource> {
            return try {
                val type = object : TypeToken<List<CloudSource>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                android.util.Log.e("CloudSource", "解析云端采集站JSON失败: ${e.message}", e)
                emptyList()
            }
        }
    }
}