package com.tv.zhuiju.ui.screen.source

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tv.zhuiju.data.local.SourceManager
import com.tv.zhuiju.data.model.CategoryBinding
import com.tv.zhuiju.data.model.CloudSource
import com.tv.zhuiju.data.model.SourceCategoryItem
import com.tv.zhuiju.data.model.SourceConfig
import com.tv.zhuiju.data.model.SourceConfigFull
import com.tv.zhuiju.data.model.VideoCategory
import com.tv.zhuiju.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SourceManagementUiState(
    /** 当前选中的 Tab：0=我的采集源, 1=云端采集站 */
    val selectedTab: Int = 0,
    /** 所有已保存的采集源 */
    val sources: List<SourceConfigFull> = emptyList(),
    /** 是否正在加载 */
    val isLoading: Boolean = false,
    /** 当前正在测试的源 ID，null 表示未在测试 */
    val testingSourceId: String? = null,
    /** 测试获取到的分类列表（仅在测试模式下显示） */
    val testCategories: List<SourceCategoryItem> = emptyList(),
    /** 当前正在编辑分类绑定的源 ID */
    val editingBindingsSourceId: String? = null,
    /** 错误信息 */
    val error: String? = null,
    /** 添加/编辑对话框 */
    val showAddDialog: Boolean = false,
    /** 云端采集站列表 */
    val cloudSources: List<CloudSource> = emptyList(),
    /** 云端采集站测试状态：key=源名称, value=测试状态 */
    val cloudTestStates: Map<String, CloudTestState> = emptyMap(),
    val editingSource: SourceConfigFull? = null,
    // 对话框字段
    val dialogName: String = "",
    val dialogUrl: String = "",
    val dialogHomeAc: String = "detail"
)

/** 云端源测试状态 */
enum class CloudTestState {
    /** 未测试 */
    UNTESTED,
    /** 测试中 */
    TESTING,
    /** 可用 */
    AVAILABLE,
    /** 不可用 */
    UNAVAILABLE
}

class SourceManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val sourceManager = SourceManager(application)

    private val _uiState = MutableStateFlow(SourceManagementUiState())
    val uiState: StateFlow<SourceManagementUiState> = _uiState.asStateFlow()

    val localCategories: List<VideoCategory> = VideoCategory.entries.toList()

    init {
        loadSources()
        loadCloudSources()
    }

    fun loadSources() {
        _uiState.value = _uiState.value.copy(
            sources = sourceManager.getAllSources(),
            isLoading = false
        )
    }

    /** 选择Tab */
    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
    }

    // ==================== 对话框 ====================

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingSource = null,
            dialogName = "",
            dialogUrl = "",
            dialogHomeAc = "videolist"
        )
    }

    fun showEditDialog(source: SourceConfigFull) {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingSource = source,
            dialogName = source.config.name,
            dialogUrl = source.config.baseUrl,
            dialogHomeAc = source.config.homeAc
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            editingSource = null
        )
    }

    // ==================== 云端采集站 ====================

    /** 加载云端采集站列表，并关联已添加的自定义源状态 */
    fun loadCloudSources() {
        val sources = sourceManager.loadCloudSources()
        // 关联已添加的源：检查哪些云端源已被添加到自定义源中
        val customSourceUrls = sourceManager.getAllSources()
            .map { it.config.baseUrl.trimEnd('/') }
            .toSet()
        _uiState.value = _uiState.value.copy(
            cloudSources = sources
        )
    }

    /** 云端采集站：一键添加并启用 */
    fun addCloudSource(cloudSource: CloudSource) {
        val normalizedUrl = cloudSource.url.trimEnd('/')
        // 检查是否已存在
        val existing = sourceManager.getAllSources().find {
            it.config.baseUrl.trimEnd('/') == normalizedUrl
        }
        if (existing != null) {
            // 已存在，切换启用状态
            val updated = existing.copy(
                config = existing.config.copy(enabled = !existing.config.enabled)
            )
            sourceManager.saveSource(updated)
        } else {
            // 新增
            val config = SourceConfig(
                name = cloudSource.name,
                baseUrl = cloudSource.url,
                homeAc = "detail"
            )
            sourceManager.saveSource(SourceConfigFull(config = config))
        }
        loadSources()
    }

    /** 云端采集站：测试连接 */
    fun testCloudSource(cloudSource: CloudSource) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                cloudTestStates = _uiState.value.cloudTestStates + (cloudSource.name to CloudTestState.TESTING)
            )
            sourceManager.testSource(cloudSource.url)
                .onSuccess { categories ->
                    _uiState.value = _uiState.value.copy(
                        cloudTestStates = _uiState.value.cloudTestStates + (cloudSource.name to CloudTestState.AVAILABLE)
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        cloudTestStates = _uiState.value.cloudTestStates + (cloudSource.name to CloudTestState.UNAVAILABLE)
                    )
                }
        }
    }

    /** 云端采集站：测试并绑定分类（先添加源，再测试分类，再绑定） */
    fun testAndBindCloudSource(cloudSource: CloudSource) {
        // 先确保源已添加
        val normalizedUrl = cloudSource.url.trimEnd('/')
        var sourceId: String? = sourceManager.getAllSources().find {
            it.config.baseUrl.trimEnd('/') == normalizedUrl
        }?.config?.id

        if (sourceId == null) {
            val config = SourceConfig(
                name = cloudSource.name,
                baseUrl = cloudSource.url,
                homeAc = "detail"
            )
            val full = SourceConfigFull(config = config)
            sourceManager.saveSource(full)
            sourceId = full.config.id
            loadSources()
        }

        // 测试并获取分类
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                testingSourceId = sourceId,
                testCategories = emptyList(),
                error = null
            )
            sourceManager.testSourceWithBindings(sourceId!!, cloudSource.url)
                .onSuccess { categories ->
                    _uiState.value = _uiState.value.copy(
                        testingSourceId = null,
                        testCategories = categories,
                        editingBindingsSourceId = sourceId
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        testingSourceId = null,
                        error = "测试失败: ${e.message}"
                    )
                }
        }
    }

    fun updateDialogName(name: String) {
        _uiState.value = _uiState.value.copy(dialogName = name)
    }

    fun updateDialogUrl(url: String) {
        _uiState.value = _uiState.value.copy(dialogUrl = url)
    }

    fun updateDialogHomeAc(ac: String) {
        _uiState.value = _uiState.value.copy(dialogHomeAc = ac)
    }

    // ==================== 保存源 ====================

    fun saveSource() {
        val state = _uiState.value
        val name = state.dialogName.trim()
        val url = state.dialogUrl.trim()
        if (name.isEmpty() || url.isEmpty()) {
            _uiState.value = state.copy(error = "名称和地址不能为空")
            return
        }

        val config = if (state.editingSource != null) {
            state.editingSource.config.copy(
                name = name,
                baseUrl = url,
                homeAc = state.dialogHomeAc
            )
        } else {
            SourceConfig(name = name, baseUrl = url, homeAc = state.dialogHomeAc)
        }

        val bindings = state.editingSource?.bindings ?: emptyList()
        sourceManager.saveSource(SourceConfigFull(config = config, bindings = bindings))

        // 清除分类映射缓存，确保下次使用最新数据
        VideoRepository.clearCategoryMappingCache()

        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            editingSource = null,
            error = null
        )
        loadSources()
    }

    // ==================== 删除源 ====================

    fun deleteSource(id: String) {
        sourceManager.deleteSource(id)
        VideoRepository.clearCategoryMappingCache()
        loadSources()
        // 刷新云端源关联状态
        loadCloudSources()
    }

    // ==================== 切换启用状态 ====================

    fun toggleSourceEnabled(id: String) {
        val source = sourceManager.getSource(id) ?: return
        val updated = source.copy(
            config = source.config.copy(enabled = !source.config.enabled)
        )
        sourceManager.saveSource(updated)
        VideoRepository.clearCategoryMappingCache()
        loadSources()
    }

    // ==================== 测试连接 ====================

    fun testSource(sourceId: String) {
        val source = sourceManager.getSource(sourceId) ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                testingSourceId = sourceId,
                testCategories = emptyList(),
                error = null
            )
            sourceManager.testSourceWithBindings(sourceId, source.config.baseUrl)
                .onSuccess { categories ->
                    _uiState.value = _uiState.value.copy(
                        testingSourceId = null,
                        testCategories = categories,
                        editingBindingsSourceId = sourceId
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        testingSourceId = null,
                        error = "测试失败: ${e.message}"
                    )
                }
        }
    }

    // ==================== 分类绑定 ====================

    fun bindCategory(sourceTypeName: String, sourceTypeId: Int, localCategory: VideoCategory) {
        val sourceId = _uiState.value.editingBindingsSourceId ?: return
        val source = sourceManager.getSource(sourceId) ?: return

        val bindings = source.bindings.toMutableList()
        // 移除已有的同名绑定
        bindings.removeAll { it.sourceTypeName == sourceTypeName }
        // 添加新绑定
        bindings.add(
            CategoryBinding(
                sourceTypeName = sourceTypeName,
                sourceTypeId = sourceTypeId,
                localCategory = localCategory.name
            )
        )
        sourceManager.updateBindings(sourceId, bindings)

        // 清除分类映射缓存，确保下次使用最新绑定
        VideoRepository.clearCategoryMappingCache()

        // 更新测试分类列表中的绑定状态
        val updatedCategories = _uiState.value.testCategories.map { item ->
            if (item.typeName == sourceTypeName) {
                item.copy(boundLocalCategory = localCategory)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(testCategories = updatedCategories)
        loadSources()
    }

    fun unbindCategory(sourceTypeName: String) {
        val sourceId = _uiState.value.editingBindingsSourceId ?: return
        val source = sourceManager.getSource(sourceId) ?: return

        val bindings = source.bindings.filter { it.sourceTypeName != sourceTypeName }
        sourceManager.updateBindings(sourceId, bindings)

        // 清除分类映射缓存
        VideoRepository.clearCategoryMappingCache()

        val updatedCategories = _uiState.value.testCategories.map { item ->
            if (item.typeName == sourceTypeName) {
                item.copy(boundLocalCategory = null)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(testCategories = updatedCategories)
        loadSources()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismissTestResults() {
        _uiState.value = _uiState.value.copy(
            testCategories = emptyList(),
            editingBindingsSourceId = null
        )
    }
}