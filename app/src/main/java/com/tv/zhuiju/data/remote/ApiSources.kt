package com.tv.zhuiju.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

data class ApiSource(
    val name: String,
    val api: ApiService,
    val homeAc: String?  // 首页列表用的ac参数: 闪电=detail, 极速=videolist, 量子=detail
)

object ApiSources {

    // 闪电
    private const val BASE_URL_SHANDIAN = "https://xsd.sdzyapi.com/"
    // 极速
    private const val BASE_URL_JISU = "https://jszyapi.com/"
    // 量子
    private const val BASE_URL_LIANGZI = "https://cj.lziapi.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun createRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val shandian: ApiService = createRetrofit(BASE_URL_SHANDIAN).create(ApiService::class.java)
    val jisu: ApiService = createRetrofit(BASE_URL_JISU).create(ApiService::class.java)
    val liangzi: ApiService = createRetrofit(BASE_URL_LIANGZI).create(ApiService::class.java)

    val allApis: List<ApiService> = listOf(shandian, jisu, liangzi)

    // 带ac参数的API源配置：闪电用detail，极速用videolist，量子用detail才有封面
    val allSources: List<ApiSource> = listOf(
        ApiSource("闪电", shandian, "detail"),
        ApiSource("极速", jisu, "videolist"),
        ApiSource("量子", liangzi, "detail")
    )
}