package com.tv.zhuiju.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tv.zhuiju.data.model.CategoryBinding
import com.tv.zhuiju.data.model.CategoryItem
import com.tv.zhuiju.data.model.SourceCategoryItem
import com.tv.zhuiju.data.model.SourceConfig
import com.tv.zhuiju.data.model.SourceConfigFull
import com.tv.zhuiju.data.model.CloudSource
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.remote.ApiService
import com.tv.zhuiju.data.remote.ApiSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 采集源管理器：负责自定义 API 源的增删改查、分类绑定存储、测试连接
 */
class SourceManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("source_manager", Context.MODE_PRIVATE)

    private val gson = Gson()

    // ==================== 源配置 CRUD ====================

    fun getAllSources(): List<SourceConfigFull> {
        val json = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SourceConfigFull>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSource(id: String): SourceConfigFull? {
        return getAllSources().find { it.config.id == id }
    }

    fun saveSource(config: SourceConfigFull) {
        val sources = getAllSources().toMutableList()
        val existingIndex = sources.indexOfFirst { it.config.id == config.config.id }
        if (existingIndex >= 0) {
            sources[existingIndex] = config
        } else {
            sources.add(config)
        }
        saveAll(sources)
    }

    fun deleteSource(id: String) {
        val sources = getAllSources().filter { it.config.id != id }
        saveAll(sources)
    }

    fun updateBindings(sourceId: String, bindings: List<CategoryBinding>) {
        val sources = getAllSources().toMutableList()
        val index = sources.indexOfFirst { it.config.id == sourceId }
        if (index >= 0) {
            sources[index] = sources[index].copy(bindings = bindings)
            saveAll(sources)
        }
    }

    private fun saveAll(sources: List<SourceConfigFull>) {
        prefs.edit().putString(KEY_SOURCES, gson.toJson(sources)).apply()
    }

    // ==================== 分类绑定查询 ====================

    /**
     * 获取所有已启用的分类绑定（sourceTypeName -> localCategory）
     */
    fun getAllBindings(): Map<String, VideoCategory> {
        val map = mutableMapOf<String, VideoCategory>()
        getAllSources().filter { it.config.enabled }.forEach { source ->
            source.bindings.forEach { binding ->
                try {
                    val category = VideoCategory.valueOf(binding.localCategory)
                    map[binding.sourceTypeName] = category
                } catch (_: Exception) {
                    // 忽略无效的枚举名
                }
            }
        }
        return map
    }

    /**
     * 根据源分类名查找绑定的本地分类
     */
    fun getBoundCategory(sourceTypeName: String): VideoCategory? {
        return getAllBindings()[sourceTypeName]
    }

    /**
     * 获取所有已启用的分类绑定（完整信息，包含 type_id）
     */
    fun getAllBindingsWithIds(): List<CategoryBinding> {
        return getAllSources()
            .filter { it.config.enabled }
            .flatMap { it.bindings }
    }

    /**
     * 获取指定本地分类的所有绑定（包含 type_id）
     */
    fun getBindingsForCategory(category: VideoCategory): List<CategoryBinding> {
        return getAllBindingsWithIds().filter { binding ->
            try {
                VideoCategory.valueOf(binding.localCategory) == category
            } catch (_: Exception) {
                false
            }
        }
    }

    // ==================== 构建 API 服务 ====================

    /**
     * 为所有已启用的自定义源构建 ApiService 实例列表
     */
    fun buildApiServices(): List<ApiService> {
        return getAllSources()
            .filter { it.config.enabled }
            .mapNotNull { buildApiService(it.config.baseUrl) }
    }

    /**
     * 为所有已启用的自定义源构建 ApiSource 配置列表
     */
    fun buildApiSources(): List<ApiSource> {
        return getAllSources()
            .filter { it.config.enabled }
            .mapNotNull { source ->
                val api = buildApiService(source.config.baseUrl) ?: return@mapNotNull null
                ApiSource(name = source.config.name, api = api, homeAc = "videolist")
            }
    }

    private fun buildApiService(baseUrl: String): ApiService? {
        return try {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(createOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit.create(ApiService::class.java)
        } catch (_: Exception) {
            null
        }
    }

    // ==================== 测试连接 ====================

    /**
     * 测试 API 源连接，获取其分类列表。
     * @return 源的分类列表，失败返回空
     */
    suspend fun testSource(baseUrl: String): Result<List<SourceCategoryItem>> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val retrofit = Retrofit.Builder()
                    .baseUrl(normalizedUrl)
                    .client(createOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(ApiService::class.java)
                val response = api.getCategoryList()
                val categories = response.classList?.map { cat ->
                    SourceCategoryItem(
                        typeName = cat.typeName,
                        typeId = cat.typeId,
                        typePid = cat.typePid
                    )
                } ?: emptyList()
                Result.success(categories)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 测试并获取分类列表（带已绑定状态）
     */
    suspend fun testSourceWithBindings(
        sourceId: String,
        baseUrl: String
    ): Result<List<SourceCategoryItem>> {
        return testSource(baseUrl).map { categories ->
            val source = getSource(sourceId)
            val bindingMap = source?.bindings?.associateBy { it.sourceTypeName } ?: emptyMap()
            categories.map { item ->
                val boundCategory = bindingMap[item.typeName]?.localCategory?.let {
                    try { VideoCategory.valueOf(it) } catch (_: Exception) { null }
                }
                item.copy(boundLocalCategory = boundCategory)
            }
        }
    }

    private fun createOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val KEY_SOURCES = "custom_sources"
    }

    // ==================== 云端采集站 ====================

    /**
     * 加载云端采集站列表。
     * 优先从 res/raw 读取，失败则回退到 assets，都失败则使用内置硬编码列表。
     */
    fun loadCloudSources(): List<CloudSource> {
        return try {
            // 方式1：从 res/raw 读取（最可靠）
            val rawJson = try {
                val resId = context.resources.getIdentifier(
                    "cloud_sources", "raw", context.packageName
                )
                if (resId != 0) {
                    context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
                } else null
            } catch (_: Exception) { null }

            // 方式2：从 assets 读取
            val assetsJson = if (rawJson == null) {
                try {
                    context.assets.open("cloud_sources.json").bufferedReader().use { it.readText() }
                } catch (_: Exception) { null }
            } else null

            val json = rawJson ?: assetsJson
            if (json != null) {
                val parsed = CloudSource.parseList(json)
                // 解析成功且非空，使用解析结果；否则回退到内置列表
                if (parsed.isNotEmpty()) {
                    parsed
                } else {
                    android.util.Log.w("SourceManager", "云端采集站JSON解析为空，使用内置列表")
                    getBuiltInCloudSources()
                }
            } else {
                android.util.Log.w("SourceManager", "云端采集站文件读取失败，使用内置列表")
                getBuiltInCloudSources()
            }
        } catch (e: Exception) {
            android.util.Log.e("SourceManager", "加载云端采集站失败: ${e.message}", e)
            getBuiltInCloudSources()
        }
    }

    /** 内置的云端采集站列表（兜底数据，与 cloud_sources.json 内容一致） */
    private fun getBuiltInCloudSources(): List<CloudSource> {
        return listOf(
            // ===== 推荐采集源（main） =====
            CloudSource("量子资源", "https://cj.lziapi.com/api.php/provide/vod/", "2", "main", 1, true, "全网最全资源站，13万+数据，更新快"),
            CloudSource("非凡资源", "https://cj.ffzyapi.com/api.php/provide/vod/", "2", "main", 1, true, "9万+数据，更新速度快，高清资源多"),
            CloudSource("光速资源", "https://api.guangsuapi.com/api.php/provide/vod/", "2", "main", 1, true, "综合资源站，更新及时"),
            CloudSource("红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod/", "2", "main", 1, true, "老牌综合资源站，稳定可靠"),
            CloudSource("暴风资源", "https://bfzyapi.com/api.php/provide/vod/", "2", "main", 1, true, "综合影视资源，更新稳定"),
            CloudSource("新浪资源", "https://api.xinlangapi.com/api.php/provide/vod/", "2", "main", 1, true, "综合影视资源站"),
            CloudSource("闪电资源", "https://www.sdzyapi.com/api.php/provide/vod/", "2", "main", 1, true, "综合影视资源，稳定"),
            CloudSource("卧龙资源", "https://collect.wolongzyw.com/api.php/provide/vod/", "2", "main", 1, true, "综合影视资源站"),
            // ===== 综合采集源（vod） =====
            CloudSource("索尼资源", "https://suonizy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("最大资源", "https://www.zuidazy.co/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("茅台资源", "https://mtzy.tv/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("360资源", "https://www.360zy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("如意资源", "https://ryzy.tv/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("魔都资源", "https://moduzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("豪华资源", "https://huohuzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("麒麟资源", "https://qilinzyz.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("电影天堂", "https://dyttzyw.tv/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("无尽资源", "https://wujinzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("极速资源", "https://www.jisuzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("虎牙资源", "https://huyazy.net/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("豆瓣资源", "https://dbzy.tv/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("影剧资源", "https://yjzy.me/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("天涯影视", "https://tyyszy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("猫眼资源", "https://www.maoyanzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("牛牛资源", "https://niuniuzy.cc/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("OK资源", "https://okzyw.cc/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("鸭鸭资源", "https://yayazy2.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("爱奇艺资源", "https://www.iqiyizy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("天空资源", "https://api.tiankongapi.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("飞速资源", "https://www.feisuzyapi.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("淘片资源", "https://taopianapi.com/home/cjapi/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("百度资源", "https://api.apibdzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("U酷资源", "https://www.ukuapi.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("iKun资源", "https://ikunzyapi.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("快车资源", "https://caiji.kcziyuan.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("金鹰资源", "https://jinyingzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("华为资源", "https://cj.hwzyapi.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("樱花资源", "https://m3u8.apiyhzy.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            CloudSource("优质资源", "https://1080zyk20.com/api.php/provide/vod/", "2", "vod", 0, false, "影视资源"),
            // ===== 短剧采集源（short） =====
            CloudSource("旺旺短剧", "https://wwzy.tv/api.php/provide/vod/", "2", "short", 0, false, "短剧资源"),
            CloudSource("锦鲤短剧", "https://jinlidj.com/api.php/provide/vod/", "2", "short", 0, false, "短剧资源"),
            CloudSource("量子短剧", "https://cj.lziapi.com/api.php/provide/vod/?t=46", "2", "short", 1, true, "量子资源站短剧分类"),
            CloudSource("非凡短剧", "https://cj.ffzyapi.com/api.php/provide/vod/?t=36", "2", "short", 1, true, "非凡资源站短剧分类"),
            // ===== 动漫采集源（anime） =====
            CloudSource("量子动漫", "https://cj.lziapi.com/api.php/provide/vod/?t=4", "2", "anime", 1, true, "量子资源站动漫分类"),
            // ===== 午夜采集源（midnight） =====
            CloudSource("乐播资源", "https://lebozy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("大地资源", "https://dadizy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("JKUN资源", "https://jkunzy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("玉兔资源", "https://yutuzy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("精品X资源", "https://jingpinx.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("辣椒资源", "http://lajiaozy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("155资源", "https://155zy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("滴滴资源", "https://didizy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("鲨鱼资源", "https://shayuzy5.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("奥斯卡资源", "https://aosikazy1.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("色猫资源", "https://semaozy8.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("森林资源", "https://senlinzy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("黑料资源", "https://heiliaozy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("奶香香资源", "https://naixxzy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("幸资源", "https://xzytv.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("杏吧资源", "https://sex8zy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("CK百货资源", "https://ckbh.me/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("百万资源", "https://bwzy.tv/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("桃花资源", "https://thzy1.me/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("豆豆资源", "https://doudouzy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("搜AV资源", "https://souavzyw.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("香蕉资源", "https://www.xiangjiaozyw.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("91精品资源", "https://91jpzyw.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("番茄资源", "https://fqzy.me/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("优优资源", "https://www.yyzywcj.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("小鸡资源", "https://xiaojizy.live/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("红楼资源", "https://hlouzy.cn/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("老色逼资源", "https://laosebizy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("黄色仓库资源", "https://hsckzy001.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("大奶子资源", "https://danaizizy.com/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源"),
            CloudSource("优优TV资源", "https://yytv.cc/api.php/provide/vod/", "2", "midnight", 0, false, "午夜资源")
        )
    }
}