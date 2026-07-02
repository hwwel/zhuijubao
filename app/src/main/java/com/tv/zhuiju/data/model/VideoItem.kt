package com.tv.zhuiju.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("msg") val msg: String?,
    @SerializedName("page") val page: Int?,
    @SerializedName("pagecount") val pageCount: Int?,
    @SerializedName("limit") val limit: Any? = null,
    @SerializedName("total") val total: Int?,
    @SerializedName("list") val list: List<VideoItem>?,
    @SerializedName("class") val classList: List<CategoryItem>?
) {
    val limitInt: Int
        get() = when (limit) {
            is Int -> limit
            is String -> limit.toIntOrNull() ?: 20
            else -> 20
        }
}

data class VideoItem(
    @SerializedName("vod_id") val id: Long,
    @SerializedName("vod_name") val name: String,
    @SerializedName("vod_pic") val pic: String? = null,
    @SerializedName("vod_remarks") val remarks: String?,
    @SerializedName("type_name") val typeName: String?,
    @SerializedName("type_id") val typeId: Int? = null,
    @SerializedName("vod_year") val year: String?,
    @SerializedName("vod_area") val area: String?,
    @SerializedName("vod_actor") val actor: String?,
    @SerializedName("vod_director") val director: String?,
    @SerializedName("vod_content") val content: String?,
    @SerializedName("vod_play_url") val playUrl: String?,
    @SerializedName("vod_play_from") val playFrom: String?,
    @SerializedName("vod_score") val score: String?,
    @SerializedName("vod_score_num") val scoreNum: Int?,
    @SerializedName("vod_hits") val hits: Int?,
    @SerializedName("vod_hits_day") val hitsDay: Int?,
    @SerializedName("vod_hits_week") val hitsWeek: Int?,
    @SerializedName("vod_hits_month") val hitsMonth: Int?,
    @SerializedName("vod_duration") val duration: String?,
    @SerializedName("vod_time") val time: String?,
    @SerializedName("vod_en") val en: String?,
    @SerializedName("vod_down_url") val downUrl: String?,
    @SerializedName("vod_down_from") val downFrom: String?,
    @SerializedName("vod_down_note") val downNote: String?,
    @SerializedName("vod_down_server") val downServer: String?,
    /** 分类标签（逗号分隔），API 返回 vod_class 字段，如 "喜剧,动作,韩剧" */
    @SerializedName("vod_class") val vodClass: String? = null
)

data class CategoryItem(
    @SerializedName("type_id") val typeId: Int,
    @SerializedName("type_name") val typeName: String,
    @SerializedName("type_pid") val typePid: Int? = null
)

/** 从 playUrl 解析剧集数量 */
fun VideoItem.countEpisodes(): Int = VideoCategory.countEpisodes(this.playUrl)