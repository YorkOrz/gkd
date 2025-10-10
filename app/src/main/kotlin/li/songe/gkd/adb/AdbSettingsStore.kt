package li.songe.gkd.adb

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import li.songe.gkd.store.createAnyFlow

/**
 * ADB自动化设置存储
 */
@Serializable
data class AdbSettingsStore(
    // 基础设置
    val enabled: Boolean = false,
    val targetWifiSSID: String = "",
    
    // 通知设置
    val enableNotification: Boolean = true,
    val notificationTitle: String = "ADB无线调试",
    val notificationContent: String = "连接命令: adb connect \${ip}:\${port}",
    
    // Webhook设置
    val enableWebhook: Boolean = false,
    val webhookUrl: String = "",
    val webhookFormat: String = "FULL", // FULL 或 SIMPLE
    val webhookTimeout: Int = 30,
    val webhookIncludeDeviceInfo: Boolean = true,
    
    // 自动化设置
    val autoRetryCount: Int = 3,
    val retryDelaySeconds: Int = 5,
    val stepTimeoutSeconds: Int = 30,
    val enableUSBDebugging: Boolean = true,
    val autoReturnHome: Boolean = true,
    
    // 高级设置
    val enableAutoActivateDeveloper: Boolean = true,
    val skipIfAlreadyEnabled: Boolean = true,
    val waitForA11yTimeout: Int = 10,
    val extractInfoRetryCount: Int = 5,
    val extractInfoRetryDelay: Int = 2,
    
    // 调试设置
    val enableDebugLog: Boolean = false,
    val enableDetailedLog: Boolean = false,
    val saveAutomationLog: Boolean = false,
    
    // 状态信息
    val lastSuccessTime: Long = 0,
    val lastAttemptTime: Long = 0,
    val totalSuccessCount: Long = 0,
    val totalFailureCount: Long = 0,
    val lastErrorMessage: String = "",
    
    // 设备特定设置
    val deviceSpecificSettings: Map<String, DeviceAdbSettings> = emptyMap()
)

/**
 * 设备特定的ADB设置
 */
@Serializable
data class DeviceAdbSettings(
    val deviceModel: String = "",
    val preferredSelectors: List<String> = emptyList(),
    val customNavigationSteps: List<String> = emptyList(),
    val skipSteps: List<String> = emptyList(),
    val additionalDelay: Int = 0
)

/**
 * ADB设置流管理
 */
val adbSettingsFlow: MutableStateFlow<AdbSettingsStore> by lazy {
    createAnyFlow(
        key = "adb_settings",
        default = { AdbSettingsStore() }
    )
}

/**
 * ADB自动化历史记录
 */
@Serializable
data class AdbAutomationRecord(
    val timestamp: Long,
    val success: Boolean,
    val wifiSSID: String,
    val adbInfo: String? = null,
    val errorMessage: String? = null,
    val duration: Long = 0,
    val steps: List<AdbAutomationStep> = emptyList()
)

/**
 * 自动化步骤记录
 */
@Serializable
data class AdbAutomationStep(
    val stepName: String,
    val timestamp: Long,
    val success: Boolean,
    val duration: Long = 0,
    val errorMessage: String? = null,
    val details: String? = null
)

/**
 * ADB自动化历史记录流
 */
val adbAutomationHistoryFlow: MutableStateFlow<List<AdbAutomationRecord>> by lazy {
    createAnyFlow(
        key = "adb_automation_history",
        default = { emptyList<AdbAutomationRecord>() }
    )
}

/**
 * ADB设置扩展函数
 */
object AdbSettingsExt {
    
    /**
     * 更新设置
     */
    fun updateSettings(update: (AdbSettingsStore) -> AdbSettingsStore) {
        adbSettingsFlow.value = update(adbSettingsFlow.value)
    }
    
    /**
     * 启用/禁用ADB自动化
     */
    fun setEnabled(enabled: Boolean) {
        updateSettings { it.copy(enabled = enabled) }
    }
    
    /**
     * 设置目标WiFi SSID
     */
    fun setTargetWifiSSID(ssid: String) {
        updateSettings { it.copy(targetWifiSSID = ssid.trim()) }
    }
    
    /**
     * 设置Webhook URL
     */
    fun setWebhookUrl(url: String) {
        updateSettings { it.copy(webhookUrl = url.trim()) }
    }
    
    /**
     * 启用/禁用Webhook
     */
    fun setWebhookEnabled(enabled: Boolean) {
        updateSettings { it.copy(enableWebhook = enabled) }
    }
    
    /**
     * 启用/禁用通知
     */
    fun setNotificationEnabled(enabled: Boolean) {
        updateSettings { it.copy(enableNotification = enabled) }
    }
    
    /**
     * 设置重试次数
     */
    fun setRetryCount(count: Int) {
        updateSettings { it.copy(autoRetryCount = count.coerceIn(0, 10)) }
    }
    
    /**
     * 设置重试延迟
     */
    fun setRetryDelay(seconds: Int) {
        updateSettings { it.copy(retryDelaySeconds = seconds.coerceIn(1, 60)) }
    }
    
    /**
     * 记录成功的自动化
     */
    fun recordSuccess(adbInfo: AdbInfo, wifiSSID: String, duration: Long, steps: List<AdbAutomationStep>) {
        val currentTime = System.currentTimeMillis()
        
        // 更新统计信息
        updateSettings { 
            it.copy(
                lastSuccessTime = currentTime,
                lastAttemptTime = currentTime,
                totalSuccessCount = it.totalSuccessCount + 1,
                lastErrorMessage = ""
            )
        }
        
        // 添加历史记录
        addAutomationRecord(
            AdbAutomationRecord(
                timestamp = currentTime,
                success = true,
                wifiSSID = wifiSSID,
                adbInfo = adbInfo.toString(),
                duration = duration,
                steps = steps
            )
        )
    }
    
    /**
     * 记录失败的自动化
     */
    fun recordFailure(errorMessage: String, wifiSSID: String, duration: Long, steps: List<AdbAutomationStep>) {
        val currentTime = System.currentTimeMillis()
        
        // 更新统计信息
        updateSettings { 
            it.copy(
                lastAttemptTime = currentTime,
                totalFailureCount = it.totalFailureCount + 1,
                lastErrorMessage = errorMessage
            )
        }
        
        // 添加历史记录
        addAutomationRecord(
            AdbAutomationRecord(
                timestamp = currentTime,
                success = false,
                wifiSSID = wifiSSID,
                errorMessage = errorMessage,
                duration = duration,
                steps = steps
            )
        )
    }
    
    /**
     * 添加自动化记录
     */
    private fun addAutomationRecord(record: AdbAutomationRecord) {
        val currentHistory = adbAutomationHistoryFlow.value.toMutableList()
        currentHistory.add(0, record) // 添加到列表开头
        
        // 保持最多100条记录
        if (currentHistory.size > 100) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        adbAutomationHistoryFlow.value = currentHistory
    }
    
    /**
     * 清除历史记录
     */
    fun clearHistory() {
        adbAutomationHistoryFlow.value = emptyList()
    }
    
    /**
     * 获取成功率
     */
    fun getSuccessRate(): Double {
        val settings = adbSettingsFlow.value
        val total = settings.totalSuccessCount + settings.totalFailureCount
        return if (total > 0) {
            settings.totalSuccessCount.toDouble() / total * 100
        } else {
            0.0
        }
    }
    
    /**
     * 重置统计信息
     */
    fun resetStatistics() {
        updateSettings { 
            it.copy(
                lastSuccessTime = 0,
                lastAttemptTime = 0,
                totalSuccessCount = 0,
                totalFailureCount = 0,
                lastErrorMessage = ""
            )
        }
    }
    
    /**
     * 获取当前设备的特定设置
     */
    fun getDeviceSettings(): DeviceAdbSettings? {
        val deviceModel = android.os.Build.MODEL
        return adbSettingsFlow.value.deviceSpecificSettings[deviceModel]
    }
    
    /**
     * 更新设备特定设置
     */
    fun updateDeviceSettings(deviceSettings: DeviceAdbSettings) {
        val deviceModel = android.os.Build.MODEL
        updateSettings { 
            it.copy(
                deviceSpecificSettings = it.deviceSpecificSettings + (deviceModel to deviceSettings)
            )
        }
    }
    
    /**
     * 验证设置
     */
    fun validateSettings(): List<String> {
        val errors = mutableListOf<String>()
        val settings = adbSettingsFlow.value
        
        if (settings.enabled) {
            if (settings.targetWifiSSID.isEmpty()) {
                errors.add("目标WiFi SSID不能为空")
            }
            
            if (settings.enableWebhook && settings.webhookUrl.isEmpty()) {
                errors.add("启用Webhook时URL不能为空")
            }
            
            if (settings.webhookUrl.isNotEmpty() && !isValidUrl(settings.webhookUrl)) {
                errors.add("Webhook URL格式不正确")
            }
            
            if (settings.autoRetryCount < 0 || settings.autoRetryCount > 10) {
                errors.add("重试次数应在0-10之间")
            }
            
            if (settings.retryDelaySeconds < 1 || settings.retryDelaySeconds > 60) {
                errors.add("重试延迟应在1-60秒之间")
            }
        }
        
        return errors
    }
    
    /**
     * 验证URL格式
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            java.net.URL(url)
            url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 导出设置
     */
    fun exportSettings(): String {
        return li.songe.gkd.util.json.encodeToString(
            AdbSettingsStore.serializer(),
            adbSettingsFlow.value
        )
    }
    
    /**
     * 导入设置
     */
    fun importSettings(json: String): Boolean {
        return try {
            val settings = li.songe.gkd.util.json.decodeFromString(
                AdbSettingsStore.serializer(),
                json
            )
            adbSettingsFlow.value = settings
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取配置摘要
     */
    fun getConfigSummary(): String {
        val settings = adbSettingsFlow.value
        return buildString {
            append("状态: ${if (settings.enabled) "启用" else "禁用"}")
            if (settings.enabled) {
                append("\n目标WiFi: ${settings.targetWifiSSID}")
                append("\n通知: ${if (settings.enableNotification) "启用" else "禁用"}")
                append("\nWebhook: ${if (settings.enableWebhook) "启用" else "禁用"}")
                append("\n重试次数: ${settings.autoRetryCount}")
                val successRate = getSuccessRate()
                if (successRate > 0) {
                    append("\n成功率: ${"%.1f".format(successRate)}%")
                }
            }
        }
    }
    
    /**
     * 创建自动化配置对象
     */
    fun createAdbAutoConfig(): AdbAutoConfig {
        val settings = adbSettingsFlow.value
        return AdbAutoConfig(
            enabled = settings.enabled,
            targetWifiSSID = settings.targetWifiSSID,
            webhookUrl = settings.webhookUrl,
            enableNotification = settings.enableNotification,
            enableWebhook = settings.enableWebhook,
            autoRetryCount = settings.autoRetryCount,
            retryDelaySeconds = settings.retryDelaySeconds,
            stepTimeoutSeconds = settings.stepTimeoutSeconds,
            enableUSBDebugging = settings.enableUSBDebugging,
            autoReturnHome = settings.autoReturnHome
        )
    }
}