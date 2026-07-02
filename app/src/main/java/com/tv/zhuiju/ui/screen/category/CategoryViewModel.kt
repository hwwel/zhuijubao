package com.tv.zhuiju.ui.screen.category

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.SourceManager
import com.tv.zhuiju.data.local.YouthModeManager
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.model.VideoItem
import com.tv.zhuiju.data.repository.CategoryMapping
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CategoryUiState(
    val parentCategories: List<VideoCategory> = VideoCategory.availableCategories(),
    val selectedParentIndex: Int = 0,
    /** 子分类列表（动态：来自 API 源的分类标签，去重合并） */
    val subCategories: List<String> = emptyList(),
    val selectedSubIndex: Int = 0,
    val years: List<String> = emptyList(),
    val selectedYearIndex: Int = 0,
    val allItems: List<VideoItem> = emptyList(),
    val filteredItems: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    /** 仅列表区域加载中（切换分类时标签栏保持可见） */
    val isListLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val error: String? = null,
    /** 总页数（来自 API 响应的 pagecount），null 表示未知 */
    val totalPages: Int? = null
)

class CategoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository()
    private val sourceManager = SourceManager(application)

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    /** 当前选中的分类 */
    private var currentCategory: VideoCategory? = null

    /** 当前分类绑定的自定义源 type_id 列表 */
    private var currentCustomTypeIds: List<Int> = emptyList()

    /** 所有 API 源的分类映射（typeName -> typeId），用于精确请求 */
    private var categoryMapping: CategoryMapping = CategoryMapping()

    /** 当前分类在所有 API 源中的 type_id 列表 */
    private var currentAllTypeIds: List<Int> = emptyList()

    init {
        // 集成自定义采集源
        repository.customBindings = sourceManager.getAllBindings()
        repository.customApiServices = sourceManager.buildApiServices()
        repository.customApiSources = sourceManager.buildApiSources()
        // 预加载分类映射
        loadCategoryMapping()
        loadData()

        // 监听青少年模式切换，自动刷新数据
        viewModelScope.launch {
            YouthModeManager.isEnabledFlow.collectLatest {
                loadData()
            }
        }
    }

    /** 预加载所有 API 源的分类映射，用于构建 type_id 列表 */
    private fun loadCategoryMapping() {
        viewModelScope.launch {
            try {
                categoryMapping = repository.fetchCategoryMapping()
            } catch (_: Exception) {
                // 静默失败，使用默认空映射
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = repository.fetchCategoryPage(
                page = 1,
                category = currentCategory,
                customTypeIds = currentCustomTypeIds,
                allTypeIds = currentAllTypeIds
            )

            result
                .onSuccess { result ->
                    val items = result.items
                    val years = items
                        .mapNotNull { it.year }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sortedDescending()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isListLoading = false,
                        allItems = items,
                        years = years,
                        filteredItems = applyFilters(items),
                        currentPage = 1,
                        totalPages = result.pageCount
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isListLoading = false,
                        error = e.message
                    )
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)

            val result = repository.fetchCategoryPage(
                page = 1,
                category = currentCategory,
                customTypeIds = currentCustomTypeIds,
                allTypeIds = currentAllTypeIds
            )

            result
                .onSuccess { result ->
                    val items = result.items
                    val years = items
                        .mapNotNull { it.year }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sortedDescending()

                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        allItems = items,
                        years = years,
                        filteredItems = applyFilters(items),
                        currentPage = 1,
                        totalPages = result.pageCount
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore) return

        // 如果有 totalPages 且已到达最后一页，不再加载
        val totalPages = state.totalPages
        if (totalPages != null && state.currentPage >= totalPages) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val nextPage = state.currentPage + 1
            repository.fetchCategoryPage(
                page = nextPage,
                category = currentCategory,
                customTypeIds = currentCustomTypeIds,
                allTypeIds = currentAllTypeIds
            )
                .onSuccess { result ->
                    val latestState = _uiState.value
                    val existingIds = latestState.allItems.map { it.id }.toSet()
                    val uniqueNew = result.items.filter { it.id !in existingIds }

                    // 没有新数据，可能已到底
                    if (uniqueNew.isEmpty()) {
                        _uiState.value = latestState.copy(isLoadingMore = false)
                        return@launch
                    }

                    val merged = latestState.allItems + uniqueNew

                    val years = merged
                        .mapNotNull { it.year }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sortedDescending()

                    _uiState.value = latestState.copy(
                        isLoadingMore = false,
                        allItems = merged,
                        years = years,
                        currentPage = nextPage,
                        totalPages = result.pageCount ?: latestState.totalPages,
                        filteredItems = applyFilters(merged)
                    )

                    // 预加载下一页数据
                    preloadNextPage(nextPage + 1)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    /**
     * 预加载下一页数据（后台静默加载，不更新UI状态）。
     * 在用户浏览当前页面时提前加载下一页，提高滚动体验。
     */
    private fun preloadNextPage(page: Int) {
        val state = _uiState.value
        val totalPages = state.totalPages
        if (totalPages != null && page > totalPages) return

        viewModelScope.launch {
            repository.fetchCategoryPage(
                page = page,
                category = currentCategory,
                customTypeIds = currentCustomTypeIds,
                allTypeIds = currentAllTypeIds
            )
                .onSuccess { result ->
                    val latestState = _uiState.value
                    // 如果用户已经翻到了这一页，跳过
                    if (latestState.currentPage >= page) return@launch

                    val existingIds = latestState.allItems.map { it.id }.toSet()
                    val uniqueNew = result.items.filter { it.id !in existingIds }

                    if (uniqueNew.isNotEmpty()) {
                        val merged = latestState.allItems + uniqueNew
                        _uiState.value = latestState.copy(
                            allItems = merged,
                            filteredItems = applyFilters(merged)
                        )
                        // 继续预加载下一页
                        preloadNextPage(page + 1)
                    }
                }
        }
    }

    fun selectParentCategory(index: Int) {
        val state = _uiState.value

        // 切换父分类
        if (index == 0) {
            currentCategory = null
            currentCustomTypeIds = emptyList()
            currentAllTypeIds = emptyList()
        } else {
            val category = state.parentCategories.getOrNull(index - 1) ?: return
            currentCategory = category
            // 获取该分类绑定的自定义源 type_id
            val bindings = sourceManager.getBindingsForCategory(category)
            currentCustomTypeIds = bindings.map { it.sourceTypeId }
            // 构建内置源的 type_id 列表：从分类映射中查找该分类关键词匹配的 type_id
            currentAllTypeIds = buildTypeIdsForCategory(category)
        }

        // 构建子分类：合并 API 分类标签 + 自定义源绑定标签
        val subCategories = buildSubCategoriesForCategory(currentCategory)

        // 不清空 allItems，保持旧数据可见，列表区域显示加载中
        _uiState.value = state.copy(
            selectedParentIndex = index,
            selectedSubIndex = 0,
            selectedYearIndex = 0,
            subCategories = subCategories,
            currentPage = 1,
            isListLoading = true,
            filteredItems = emptyList()
        )

        loadData()
    }

    fun selectSubCategory(index: Int) {
        val state = _uiState.value
        if (index == 0) {
            // 选中"全部"子分类，使用当前分类的所有 type_id
            currentAllTypeIds = currentCategory?.let { buildTypeIdsForCategory(it) } ?: emptyList()
            _uiState.value = state.copy(
                selectedSubIndex = 0,
                isListLoading = true,
                filteredItems = emptyList()
            )
            loadData()
        } else {
            val subName = state.subCategories.getOrNull(index - 1) ?: return
            // 从分类映射中找到该标签的 type_id
            val typeId = categoryMapping.nameToId[subName]
            currentAllTypeIds = typeId?.let { listOf(it) } ?: emptyList()

            // 自定义源：找到对应的 type_id
            currentCategory?.let { cat ->
                val bindings = sourceManager.getBindingsForCategory(cat)
                val binding = bindings.find { it.sourceTypeName == subName }
                currentCustomTypeIds = binding?.let { listOf(it.sourceTypeId) } ?: emptyList()
            }
            _uiState.value = state.copy(
                selectedSubIndex = index,
                isListLoading = true,
                filteredItems = emptyList()
            )
            loadData()
        }
    }

    /** 根据本地分类，从分类映射中构建对应关键词的 type_id 列表 */
    private fun buildTypeIdsForCategory(category: VideoCategory): List<Int> {
        if (categoryMapping.nameToId.isEmpty()) return emptyList()
        // 匹配该分类的所有关键词
        return category.keywords.flatMap { keyword ->
            categoryMapping.nameToId.filter { (name, _) ->
                name.contains(keyword, ignoreCase = true) || keyword.contains(name, ignoreCase = true)
            }.values
        }.distinct()
    }

    /** 构建子分类列表：合并 API 分类标签 + 自定义源绑定标签 */
    private fun buildSubCategoriesForCategory(category: VideoCategory?): List<String> {
        if (category == null) return emptyList()
        val subNames = mutableListOf<String>()

        // 1. API 源的分类标签（从分类映射中匹配）
        if (categoryMapping.nameToId.isNotEmpty()) {
            category.keywords.forEach { keyword ->
                categoryMapping.nameToId.keys.forEach { typeName ->
                    if (typeName.contains(keyword, ignoreCase = true) && typeName !in subNames) {
                        subNames.add(typeName)
                    }
                }
            }
        }

        // 2. 自定义源绑定的 type_name
        val bindings = sourceManager.getBindingsForCategory(category)
        bindings.forEach { binding ->
            if (binding.sourceTypeName !in subNames) {
                subNames.add(binding.sourceTypeName)
            }
        }

        // 3. 如果都没有，使用默认 keywords
        if (subNames.isEmpty()) {
            subNames.addAll(category.keywords)
        }

        return subNames
    }

    fun selectYear(index: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedYearIndex = index,
            filteredItems = applyFilters(state.allItems, yearIndex = index)
        )
    }

    private fun applyFilters(
        items: List<VideoItem>,
        parentIndex: Int = _uiState.value.selectedParentIndex,
        subIndex: Int = _uiState.value.selectedSubIndex,
        yearIndex: Int = _uiState.value.selectedYearIndex
    ): List<VideoItem> {
        var result = items

        // 父分类筛选
        if (parentIndex > 0) {
            val category = _uiState.value.parentCategories.getOrNull(parentIndex - 1) ?: return result
            result = result.filter { VideoCategory.classify(it) == category }
        }

        // 子分类筛选
        val subCategories = _uiState.value.subCategories
        if (subIndex > 0 && subCategories.isNotEmpty()) {
            val subName = subCategories.getOrNull(subIndex - 1) ?: return result
            result = result.filter { it.typeName == subName }
        }

        // 年份筛选
        val years = _uiState.value.years
        if (yearIndex > 0 && years.isNotEmpty()) {
            val year = years.getOrNull(yearIndex - 1) ?: return result
            result = result.filter { it.year == year }
        }

        return result
    }
}