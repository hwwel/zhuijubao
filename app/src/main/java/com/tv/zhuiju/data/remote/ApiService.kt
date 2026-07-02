package com.tv.zhuiju.data.remote

import com.tv.zhuiju.data.model.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("api.php/provide/vod/")
    suspend fun getHomeList(
        @Query("ac") ac: String? = null,
        @Query("h") h: Int? = null
    ): ApiResponse

    @GET("api.php/provide/vod/")
    suspend fun getVideoList(
        @Query("ac") ac: String = "videolist",
        @Query("t") t: String? = null,
        @Query("pg") pg: Int? = null,
        @Query("h") h: Int? = null,
        @Query("wd") wd: String? = null
    ): ApiResponse

    @GET("api.php/provide/vod/")
    suspend fun getVideoDetail(
        @Query("ac") ac: String = "detail",
        @Query("ids") ids: Long
    ): ApiResponse

    @GET("api.php/provide/vod/")
    suspend fun searchVideo(
        @Query("ac") ac: String = "videolist",
        @Query("wd") wd: String
    ): ApiResponse

    /** 获取分类列表（标准模式，返回 class 字段） */
    @GET("api.php/provide/vod/")
    suspend fun getCategoryList(): ApiResponse
}