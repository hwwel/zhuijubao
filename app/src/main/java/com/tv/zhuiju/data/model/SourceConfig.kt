package com.tv.zhuiju.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 用户自定义的 API 采集源配置
 */
data class SourceConfig(
    val id: String = System.currentTimeMillis().toString(), // 唯一标识
    val name: String,                                        // 源名称
    val baseUrl: String,                                     // API 基础 URL
    val homeAc: String = "videolist",                        // 首页 ac 参数
    val enabled: Boolean = true,                             // 是否启用
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 分类绑定：源分类 → 本地分类的映射
 */
data class CategoryBinding(
    val sourceTypeName: String,  // 源返回的分类名（type_name）
    val sourceTypeId: Int,       // 源返回的分类 ID（type_id）
    val localCategory: String    // 本地 VideoCategory 枚举名（如 "TV_SERIES", "ETHICS"）
)

/**
 * 完整的采集源配置（包含分类绑定列表）
 */
data class SourceConfigFull(
    val config: SourceConfig,
    val bindings: List<CategoryBinding> = emptyList()
) {
    companion object {
        private val gson = Gson()

        fun toJson(config: SourceConfigFull): String = gson.toJson(config)
        fun fromJson(json: String): SourceConfigFull? = try {
            gson.fromJson(json, SourceConfigFull::class.java)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 源返回的分类信息（用于展示和绑定）
 */
data class SourceCategoryItem(
    val typeName: String,
    val typeId: Int,
    val typePid: Int? = null,
    val boundLocalCategory: VideoCategory? = null  // 已绑定的本地分类，null 表示未绑定
)