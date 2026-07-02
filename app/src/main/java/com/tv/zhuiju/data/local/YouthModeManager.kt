package com.tv.zhuiju.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 青少年模式管理
 */
object YouthModeManager {

    private const val PREF_NAME = "youth_mode"
    private const val KEY_YOUTH_MODE_ENABLED = "youth_mode_enabled"

    private lateinit var prefs: SharedPreferences

    /** 青少年模式状态流，UI层可观察 */
    private val _isEnabled = MutableStateFlow(false)
    val isEnabledFlow: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean(KEY_YOUTH_MODE_ENABLED, false)
    }

    /** 是否启用青少年模式 */
    var isEnabled: Boolean
        get() = _isEnabled.value
        set(value) {
            _isEnabled.value = value
            prefs.edit().putBoolean(KEY_YOUTH_MODE_ENABLED, value).apply()
        }

    /** 青少年模式下需要屏蔽的分类名列表 */
    val blockedCategories: Set<String>
        get() = if (isEnabled) {
            setOf("伦理", "伦理片", "午夜", "午夜福利", "情色", "成人", "写真", "福利")
        } else {
            emptySet()
        }
}