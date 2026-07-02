package com.tv.zhuiju.data.repository

import com.google.gson.Gson
import com.tv.zhuiju.data.local.db.DatabaseProvider
import com.tv.zhuiju.data.local.YouthModeManager
import com.tv.zhuiju.data.model.ApiResponse
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.remote.ApiService
import com.tv.zhuiju.data.remote.ApiSource
import com.tv.zhuiju.data.remote.ApiSources
import com.tv.zhuiju.ui.screen.home.WatchHistoryItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

data class AggregatedData(
    val allItems: List<VideoItem> = emptyList(),
    val tvSeries: List<VideoItem> = emptyList(),
    val movies: List<VideoItem> = emptyList(),
    val variety: List<VideoItem> = emptyList(),
    val anime: List<VideoItem> = emptyList(),
    val documentary: List<VideoItem> = emptyList(),
    val sports: List<VideoItem> = emptyList(),
    val drama: List<VideoItem> = emptyList(),
    val ethics: List<VideoItem> = emptyList(),
    val others: List<VideoItem> = emptyList()
)

data class FetchResult(
    val items: List<VideoItem>,
    /** 当前页码 */
    val page: Int,
    /** 服务端总页数，null 表示未知 */
    val pageCount: Int?
)

/** 分类映射：typeName -> typeId，聚合所有 API 源的分类列表 */
data class CategoryMapping(
    val nameToId: Map<String, Int> = emptyMap()
)

class VideoRepository {

    private val videoDao = DatabaseProvider.videoDao()
    private val watchHistoryDao = DatabaseProvider.watchHistoryDao()
    private val searchHistoryDao = DatabaseProvider.searchHistoryDao()
    private val gson = Gson()

    /**
     * 自定义分类绑定：sourceTypeName -> VideoCategory。
     * 由 SourceManager 提供，用于将自定义 API 源的分类映射到本地分类。
     */
    var customBindings: Map<String, VideoCategory> = emptyMap()

    /**
     * 自定义 API 服务列表（用户添加的采集源）。
     * 由 ViewModel 层通过 SourceManager 设置。
     */
    var customApiServices: List<ApiService> = emptyList()

    /** 所有 API 服务（内置 + 自定义） */
    private val allApiServices: List<ApiService>
        get() = ApiSources.allApis + customApiServices

    /** 获取所有 API 服务（供外部使用） */
    fun queryAllApiServices(): List<ApiService> = allApiServices

    /** 所有 API 源配置（内置 + 自定义） */
    private val allApiSources: List<ApiSource>
        get() = ApiSources.allSources + customApiSources

    /** 自定义 API 源配置列表 */
    var customApiSources: List<ApiSource> = emptyList()

    // ==================== 分类映射缓存（静态，跨实例共享） ====================

    /** 分类映射缓存，带过期时间（5分钟） */
    companion object {
        private var cachedCategoryMapping: CategoryMapping? = null
        private var categoryMappingCacheTime: Long = 0L
        private const val categoryMappingCacheDuration = 5 * 60 * 1000L // 5分钟

        /** 清除分类映射缓存（采集源变更时调用） */
        fun clearCategoryMappingCache() {
            cachedCategoryMapping = null
            categoryMappingCacheTime = 0L
        }
    }

    // ==================== 首页数据：先读本地，再同步远程 ====================

    fun getHomeDataFromDb(): AggregatedData {
        val tvSeries = videoDao.getByCategory(VideoCategory.TV_SERIES, 50)
        val movies = videoDao.getByCategory(VideoCategory.MOVIE, 50)
        val variety = videoDao.getByCategory(VideoCategory.VARIETY, 50)
        val anime = videoDao.getByCategory(VideoCategory.ANIME, 50)
        val documentary = videoDao.getByCategory(VideoCategory.DOCUMENTARY, 50)
        val sports = videoDao.getByCategory(VideoCategory.SPORTS, 50)
        val drama = videoDao.getByCategory(VideoCategory.DRAMA, 50)
        val ethics = videoDao.getByCategory(VideoCategory.ETHICS, 50)
        val others = videoDao.getByCategory(VideoCategory.OTHER, 50)
        val allItems = (tvSeries + movies + variety + anime + documentary + sports + drama + ethics + others)
            .distinctBy { it.id }
        return AggregatedData(
            allItems = allItems,
            tvSeries = tvSeries,
            movies = movies,
            variety = variety,
            anime = anime,
            documentary = documentary,
            sports = sports,
            drama = drama,
            ethics = ethics,
            others = others
        )
    }

    /**
     * 从远程获取数据并同步到本地数据库。
     * 拉取前3页（每源），确保首页有足够数据展示。
     */
    suspend fun syncRemoteToDb(): Result<AggregatedData> = supervisorScope {
        try {
            // 每个源拉取前3页，并行请求
            val pages = (1..3).toList()
            val allRequests = allApiServices.flatMap { api ->
                pages.map { page ->
                    async {
                        runCatching {
                            api.getVideoList(ac = "videolist", pg = page, h = 0)
                        }
                    }
                }
            }

            val allItems = allRequests.awaitAll()
                .filter { it.isSuccess }
                .flatMap {
                    it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list
                        ?: emptyList()
                }
                .distinctBy { it.id }

            val categorized = categorize(allItems)

            // 同步到数据库：按分类保存，只插入数据库中没有的数据
            syncCategoryToDb(categorized.tvSeries, VideoCategory.TV_SERIES)
            syncCategoryToDb(categorized.movies, VideoCategory.MOVIE)
            syncCategoryToDb(categorized.variety, VideoCategory.VARIETY)
            syncCategoryToDb(categorized.anime, VideoCategory.ANIME)
            syncCategoryToDb(categorized.documentary, VideoCategory.DOCUMENTARY)
            syncCategoryToDb(categorized.sports, VideoCategory.SPORTS)
            syncCategoryToDb(categorized.drama, VideoCategory.DRAMA)
            syncCategoryToDb(categorized.ethics, VideoCategory.ETHICS)
            syncCategoryToDb(categorized.others, VideoCategory.OTHER)

            Result.success(categorized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun syncCategoryToDb(items: List<VideoItem>, category: VideoCategory) {
        items.forEach { item ->
            if (videoDao.existsById(item.id)) {
                // 已存在则更新分类（防止旧数据分类错误）
                videoDao.updateCategory(item.id, category)
            } else {
                videoDao.insertOrIgnore(item, category)
            }
        }
    }

    /**
     * 同时请求三个API源，聚合数据后按分类归类（仅远程，不操作数据库）
     */
    suspend fun fetchAggregatedHomeData(): Result<AggregatedData> = supervisorScope {
        try {
            val results = allApiSources.map { source ->
                async {
                    runCatching { source.api.getHomeList(ac = source.homeAc, h = 0) }
                }
            }.awaitAll()

            val allItems = results
                .filter { it.isSuccess }
                .flatMap {
                    it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list
                        ?: emptyList()
                }
                .distinctBy { it.id }

            val categorized = categorize(allItems)
            Result.success(categorized)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从所有 API 源获取分类列表，构建 typeName -> typeId 映射。
     * 使用静态缓存，5分钟内重复调用直接返回缓存结果。
     */
    suspend fun fetchCategoryMapping(): CategoryMapping = supervisorScope {
        // 检查缓存是否有效
        val now = System.currentTimeMillis()
        val cached = cachedCategoryMapping
        if (cached != null && (now - categoryMappingCacheTime) < categoryMappingCacheDuration) {
            return@supervisorScope cached
        }

        val results = allApiServices.map { api ->
            async {
                runCatching { api.getCategoryList() }
            }
        }.awaitAll()

        val nameToId = mutableMapOf<String, Int>()
        results.forEach { result ->
            result.getOrNull()?.classList?.forEach { cat ->
                // 只保留第一个匹配的，避免覆盖
                if (cat.typeName !in nameToId) {
                    nameToId[cat.typeName] = cat.typeId
                }
            }
        }
        val mapping = CategoryMapping(nameToId = nameToId)
        // 更新缓存
        cachedCategoryMapping = mapping
        categoryMappingCacheTime = now
        mapping
    }

    /**
     * 分页加载分类数据（聚合所有源，支持按 type_id 精确请求）。
     * 通过分类映射表，将用户选中的分类标签映射到各 API 源的 type_id，提高请求精度。
     * @param category 本地分类（用于客户端过滤兜底）
     * @param allTypeIds 所有 API 源中该分类对应的 type_id 列表（来源：fetchCategoryMapping 构建的映射）
     * @param customTypeIds 自定义源绑定的 type_id 列表
     */
    suspend fun fetchCategoryPage(
        page: Int = 1,
        category: VideoCategory? = null,
        typeIds: List<Int> = emptyList(),
        maxPages: Int = 1,
        customTypeIds: List<Int> = emptyList(),
        allTypeIds: List<Int> = emptyList()
    ): Result<FetchResult> = supervisorScope {
        try {
            // 合并所有 type_id：内置 + 自定义
            val mergedTypeIds = (allTypeIds + customTypeIds).distinct()

            // 收集所有 API 响应，用于提取 pageCount
            val allResponses = mutableListOf<ApiResponse>()

            val allItems = if (mergedTypeIds.isNotEmpty()) {
                // 有 type_id 时，用 type_id 精确请求（每个 type_id 请求 1 页）
                val builtInResults = ApiSources.allApis.flatMap { api ->
                    mergedTypeIds.map { typeId ->
                        async {
                            runCatching { api.getVideoList(ac = "videolist", pg = page, t = typeId.toString(), h = 0) }
                        }
                    }
                }
                // 自定义源也使用合并后的 typeId
                val customResults = customApiServices.flatMap { api ->
                    mergedTypeIds.map { typeId ->
                        async {
                            runCatching { api.getVideoList(ac = "videolist", pg = page, t = typeId.toString(), h = 0) }
                        }
                    }
                }
                (builtInResults + customResults).awaitAll()
                    .filter { it.isSuccess }
                    .onEach { allResponses.add(it.getOrThrow()) }
                    .flatMap {
                        it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list ?: emptyList()
                    }
            } else {
                // 无 type_id 时（"全部"），拉取全量数据
                val builtInResults = ApiSources.allApis.map { api ->
                    async {
                        runCatching { api.getVideoList(ac = "videolist", pg = page, h = 0) }
                    }
                }
                val customResults = customApiServices.map { api ->
                    async {
                        runCatching { api.getVideoList(ac = "videolist", pg = page, h = 0) }
                    }
                }
                (builtInResults + customResults).awaitAll()
                    .filter { it.isSuccess }
                    .onEach { allResponses.add(it.getOrThrow()) }
                    .flatMap {
                        it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list ?: emptyList()
                    }
            }.distinctBy { it.id }

            // 客户端过滤（兜底）
            val filtered = if (category != null) {
                allItems.filter { VideoCategory.classify(it, customBindings) == category }
            } else {
                allItems
            }

            // 青少年模式：过滤被屏蔽的分类数据
            val youthFiltered = VideoCategory.filterYouthMode(filtered)

            // 从所有 API 响应中提取最大 pageCount，用于判断是否还有下一页
            val maxPageCount = allResponses
                .mapNotNull { it.pageCount }
                .maxOrNull()

            Result.success(FetchResult(items = youthFiltered, page = page, pageCount = maxPageCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 对无 typeId 的分类，一次性拉取多页数据后进行客户端过滤。
     * @deprecated 请使用 fetchCategoryPage，所有分类统一使用客户端过滤。
     */
    suspend fun fetchCategoryMultiPage(
        category: VideoCategory,
        maxPages: Int = 3
    ): Result<FetchResult> = fetchCategoryPage(category = category, maxPages = maxPages)

    /**
     * 聚合搜索三个API
     */
    suspend fun searchAggregated(keyword: String): Result<List<VideoItem>> = supervisorScope {
        try {
            val results = allApiServices.map { api ->
                async {
                    runCatching { api.searchVideo(wd = keyword) }
                }
            }.awaitAll()

            val allItems = results
                .filter { it.isSuccess }
                .flatMap {
                    it.getOrDefault(ApiResponse(0, null, null, null, null, null, null, null)).list
                        ?: emptyList()
                }
                .distinctBy { it.id }

            // 青少年模式：过滤被屏蔽的分类数据
            val youthFiltered = VideoCategory.filterYouthMode(allItems)

            Result.success(youthFiltered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 本地数据库搜索
     */
    fun searchLocal(keyword: String): List<VideoItem> {
        return videoDao.searchByName(keyword)
    }

    // ==================== 观看历史 ====================

    fun getWatchHistory(): List<WatchHistoryItem> {
        return watchHistoryDao.getAll()
    }

    /** 获取非短剧的历史记录 */
    fun getVideoHistory(): List<WatchHistoryItem> {
        return watchHistoryDao.getAllByType("video")
    }

    /** 获取短剧的历史记录 */
    fun getDramaHistory(): List<WatchHistoryItem> {
        return watchHistoryDao.getAllByType("drama")
    }

    fun addWatchHistory(
        videoItem: VideoItem,
        lastPosition: Long = 0,
        episodeTitle: String? = null,
        sourceIndex: Int = 0,
        episodeIndex: Int = 0,
        dramaType: String = "video"
    ) {
        watchHistoryDao.insertOrUpdate(
            videoId = videoItem.id,
            videoJson = gson.toJson(videoItem),
            name = videoItem.name,
            pic = videoItem.pic,
            episodeTitle = episodeTitle,
            sourceIndex = sourceIndex,
            episodeIndex = episodeIndex,
            dramaType = dramaType
        )
    }

    fun getWatchHistoryByVideoId(videoId: Long): WatchHistoryItem? {
        return watchHistoryDao.getByVideoId(videoId)
    }

    fun deleteWatchHistory(videoId: Long) {
        watchHistoryDao.deleteById(videoId)
    }

    fun clearWatchHistory() {
        watchHistoryDao.deleteAll()
    }

    fun clearDramaHistory() {
        watchHistoryDao.deleteAllByType("drama")
    }

    fun getLastPosition(videoId: Long): Long {
        return watchHistoryDao.getLastPosition(videoId)
    }

    fun updatePlaybackState(
        videoId: Long,
        position: Long,
        episodeTitle: String? = null,
        sourceIndex: Int? = null,
        episodeIndex: Int? = null
    ) {
        watchHistoryDao.updatePlaybackState(videoId, position, episodeTitle, sourceIndex, episodeIndex)
    }

    // ==================== 搜索历史 ====================

    fun getSearchHistory(): List<String> {
        return searchHistoryDao.getAll()
    }

    fun addSearchHistory(keyword: String) {
        if (keyword.isNotBlank()) {
            searchHistoryDao.insertOrUpdate(keyword.trim())
        }
    }

    fun deleteSearchHistory(keyword: String) {
        searchHistoryDao.delete(keyword)
    }

    fun clearSearchHistory() {
        searchHistoryDao.deleteAll()
    }

    // ==================== 按分类绑定规则归类 ====================

    fun categorize(items: List<VideoItem>): AggregatedData {
        // 青少年模式：过滤被屏蔽的分类数据
        val filteredItems = VideoCategory.filterYouthMode(items)
        val tvSeries = mutableListOf<VideoItem>()
        val movies = mutableListOf<VideoItem>()
        val variety = mutableListOf<VideoItem>()
        val anime = mutableListOf<VideoItem>()
        val documentary = mutableListOf<VideoItem>()
        val sports = mutableListOf<VideoItem>()
        val drama = mutableListOf<VideoItem>()
        val ethics = mutableListOf<VideoItem>()
        val others = mutableListOf<VideoItem>()

        filteredItems.forEach { item ->
            val bindings = customBindings
            when (VideoCategory.classify(item, bindings)) {
                VideoCategory.TV_SERIES -> tvSeries.add(item)
                VideoCategory.MOVIE -> movies.add(item)
                VideoCategory.VARIETY -> variety.add(item)
                VideoCategory.ANIME -> anime.add(item)
                VideoCategory.DOCUMENTARY -> documentary.add(item)
                VideoCategory.SPORTS -> sports.add(item)
                VideoCategory.DRAMA -> drama.add(item)
                VideoCategory.ETHICS -> ethics.add(item)
                VideoCategory.OTHER -> others.add(item)
            }
        }

        return AggregatedData(
            allItems = filteredItems,
            tvSeries = tvSeries,
            movies = movies,
            variety = variety,
            anime = anime,
            documentary = documentary,
            sports = sports,
            drama = drama,
            ethics = ethics,
            others = others
        )
    }
}
