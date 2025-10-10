package li.songe.gkd.adb

import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import li.songe.gkd.a11y.a11yContext
import li.songe.gkd.app
// import li.songe.gkd.notif.createNotif
// import li.songe.gkd.notif.defaultChannel
import li.songe.gkd.service.A11yService
import android.accessibilityservice.AccessibilityService
import li.songe.selector.MatchOption

/**
 * ADB自动化状态
 */
enum class AdbAutoState {
    IDLE,                           // 空闲状态
    WIFI_CONNECTED,                 // WiFi已连接，准备开始
    CHECKING_ACCESSIBILITY,         // 检查无障碍服务
    NAVIGATING_TO_SETTINGS,         // 导航到设置
    SEARCHING_DEVELOPER_OPTIONS,    // 查找开发者选项
    ACTIVATING_DEVELOPER_MODE,      // 激活开发者模式
    ENABLING_USB_DEBUGGING,         // 启用USB调试
    ENABLING_WIRELESS_DEBUGGING,    // 启用无线调试
    EXTRACTING_ADB_INFO,           // 提取ADB信息
    SENDING_NOTIFICATION,          // 发送通知
    SENDING_WEBHOOK,               // 发送Webhook
    COMPLETED,                     // 完成
    ERROR,                         // 错误状态
    RETRYING                       // 重试中
}

/**
 * ADB自动化配置
 */
data class AdbAutoConfig(
    val enabled: Boolean = false,
    val targetWifiSSID: String = "",
    val webhookUrl: String = "",
    val enableNotification: Boolean = true,
    val enableWebhook: Boolean = false,
    val autoRetryCount: Int = 3,
    val retryDelaySeconds: Int = 5,
    val stepTimeoutSeconds: Int = 30,
    val enableUSBDebugging: Boolean = true,
    val autoReturnHome: Boolean = true
)

/**
 * ADB自动化执行结果
 */
data class AdbAutoResult(
    val success: Boolean,
    val adbInfo: AdbInfo? = null,
    val errorMessage: String? = null,
    val duration: Long = 0
)

/**
 * ADB自动化管理器
 * 这是整个自动化功能的核心控制器
 */
class AdbAutoSetup {
    
    private val wifiMonitor = WifiMonitor()
    private val infoExtractor = AdbInfoExtractor()
    private val webhook = AdbWebhook()
    
    private val _currentState = MutableStateFlow(AdbAutoState.IDLE)
    val currentState: StateFlow<AdbAutoState> = _currentState.asStateFlow()
    
    private val _lastAdbInfo = MutableStateFlow<AdbInfo?>(null)
    val lastAdbInfo: StateFlow<AdbInfo?> = _lastAdbInfo.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private var setupJob: Job? = null
    private var config: AdbAutoConfig = AdbAutoConfig()
    private var retryCount = 0
    private var startTime = 0L
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    /**
     * 启动ADB自动化
     * @param config 自动化配置
     */
    fun start(config: AdbAutoConfig) {
        if (_isRunning.value) {
            LogUtils.w("AdbAutoSetup", "自动化已在运行中")
            return
        }
        
        if (!config.enabled) {
            LogUtils.i("AdbAutoSetup", "ADB自动化功能已禁用")
            return
        }
        
        if (config.targetWifiSSID.isEmpty()) {
            LogUtils.w("AdbAutoSetup", "未设置目标WiFi SSID")
            return
        }
        
        this.config = config
        this.retryCount = 0
        _isRunning.value = true
        _currentState.value = AdbAutoState.IDLE
        
        LogUtils.i("AdbAutoSetup", "开始ADB自动化，目标WiFi: ${config.targetWifiSSID}")
        
        // 开始监听WiFi连接
        wifiMonitor.startMonitoring(config.targetWifiSSID)
        
        // 监听WiFi连接状态变化
        setupJob = scope.launch {
            wifiMonitor.isTargetWifiConnected.collect { connected ->
                if (connected && _currentState.value == AdbAutoState.IDLE) {
                    LogUtils.i("AdbAutoSetup", "检测到目标WiFi连接，开始自动化")
                    startAutomationProcess()
                }
            }
        }
    }
    
    /**
     * 停止ADB自动化
     */
    fun stop() {
        LogUtils.i("AdbAutoSetup", "停止ADB自动化")
        
        setupJob?.cancel()
        wifiMonitor.stopMonitoring()
        _isRunning.value = false
        _currentState.value = AdbAutoState.IDLE
    }
    
    /**
     * 手动触发一次自动化流程
     * 不依赖WiFi连接状态
     */
    fun triggerManually() {
        if (_isRunning.value && _currentState.value != AdbAutoState.IDLE) {
            LogUtils.w("AdbAutoSetup", "自动化正在进行中，无法手动触发")
            return
        }
        
        LogUtils.i("AdbAutoSetup", "手动触发ADB自动化")
        scope.launch {
            startAutomationProcess()
        }
    }
    
    /**
     * 开始自动化流程
     */
    private suspend fun startAutomationProcess() {
        startTime = System.currentTimeMillis()
        
        LogUtils.i("AdbAutoSetup", "===== 开始ADB自动化流程 =====")
        
        try {
            _currentState.value = AdbAutoState.WIFI_CONNECTED
            LogUtils.d("AdbAutoSetup", "状态更新: WiFi已连接")
            
            // 步骤1：检查无障碍服务
            LogUtils.d("AdbAutoSetup", "步骤1: 检查无障碍服务")
            checkAccessibilityService()
            
            // 步骤2：导航到设置
            LogUtils.d("AdbAutoSetup", "步骤2: 导航到设置")
            navigateToSettings()
            
            // 步骤3：查找或激活开发者选项
            LogUtils.d("AdbAutoSetup", "步骤3: 确保开发者选项已启用")
            ensureDeveloperOptionsEnabled()
            
            // 步骤4：启用USB调试（如果需要）
            if (config.enableUSBDebugging) {
                LogUtils.d("AdbAutoSetup", "步骤4: 启用USB调试")
                enableUSBDebugging()
            } else {
                LogUtils.d("AdbAutoSetup", "跳过USB调试步骤（配置已禁用）")
            }
            
            // 步骤5：启用无线调试
            LogUtils.d("AdbAutoSetup", "步骤5: 启用无线调试")
            enableWirelessDebugging()
            
            // 步骤6：提取ADB信息
            LogUtils.d("AdbAutoSetup", "步骤6: 提取ADB信息")
            val adbInfo = extractAdbInformation()
            
            // 步骤7：发送通知和Webhook
            if (adbInfo != null) {
                LogUtils.d("AdbAutoSetup", "步骤7: 发送通知和Webhook")
                _lastAdbInfo.value = adbInfo
                sendNotifications(adbInfo)
            }
            
            _currentState.value = AdbAutoState.COMPLETED
            
            val duration = System.currentTimeMillis() - startTime
            LogUtils.i("AdbAutoSetup", "===== 自动化完成，耗时: ${duration}ms =====")
            
            // 如果配置了自动返回主页
            if (config.autoReturnHome) {
                LogUtils.d("AdbAutoSetup", "自动返回主页")
                returnToHome()
            }
            
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "===== 自动化流程失败: ${e.message} =====", e)
            handleError(e)
        }
    }
    
    /**
     * 检查无障碍服务
     */
    private suspend fun checkAccessibilityService() {
        _currentState.value = AdbAutoState.CHECKING_ACCESSIBILITY
        
        if (!waitForA11yService()) {
            throw Exception("无障碍服务不可用，请先启用GKD的无障碍服务")
        }
        
        LogUtils.d("AdbAutoSetup", "无障碍服务检查通过")
    }
    
    /**
     * 等待无障碍服务可用
     */
    private suspend fun waitForA11yService(maxWaitSeconds: Int = 10): Boolean {
        repeat(maxWaitSeconds) {
            if (A11yService.instance != null) {
                return true
            }
            delay(1000)
        }
        return false
    }
    
    /**
     * 导航到设置应用
     */
    private suspend fun navigateToSettings() {
        _currentState.value = AdbAutoState.NAVIGATING_TO_SETTINGS
        
        LogUtils.d("AdbAutoSetup", "开始导航到设置应用")
        
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
        
        LogUtils.d("AdbAutoSetup", "已发送打开设置应用的Intent，等待应用加载...")
        delay(3000) // 等待设置应用完全加载
        
        // 验证是否成功打开设置应用
        val packageName = infoExtractor.getCurrentPackageName()
        LogUtils.d("AdbAutoSetup", "当前应用包名: $packageName")
        
        if (packageName != null && DeveloperRules.settingsPackageNames.any { 
            packageName.contains(it, ignoreCase = true) 
        }) {
            LogUtils.i("AdbAutoSetup", "成功打开设置应用: $packageName")
        } else {
            LogUtils.w("AdbAutoSetup", "可能未成功打开设置应用，当前包名: $packageName")
        }
        
        LogUtils.d("AdbAutoSetup", "设置导航完成，准备查找开发者选项")
    }
    
    /**
     * 确保开发者选项已启用
     */
    private suspend fun ensureDeveloperOptionsEnabled() {
        _currentState.value = AdbAutoState.SEARCHING_DEVELOPER_OPTIONS
        
        LogUtils.d("AdbAutoSetup", "开始确保开发者选项已启用")
        
        // 首先尝试直接找到开发者选项
        LogUtils.d("AdbAutoSetup", "尝试直接查找开发者选项...")
        if (findAndClickDeveloperOptions()) {
            LogUtils.i("AdbAutoSetup", "成功找到开发者选项，直接进入")
            return
        }
        
        // 如果找不到，尝试激活开发者模式
        LogUtils.w("AdbAutoSetup", "未找到开发者选项，尝试激活开发者模式")
        activateDeveloperMode()
        
        // 激活后再次尝试查找
        LogUtils.d("AdbAutoSetup", "开发者模式激活完成，重新查找开发者选项")
        delay(2000)
        if (!findAndClickDeveloperOptions()) {
            throw Exception("无法找到或激活开发者选项")
        }
        
        LogUtils.i("AdbAutoSetup", "开发者选项处理完成")
    }
    
    /**
     * 查找并点击开发者选项
     */
    private suspend fun findAndClickDeveloperOptions(): Boolean {
        val a11yService = A11yService.instance
        if (a11yService == null) {
            LogUtils.w("AdbAutoSetup", "无障碍服务不可用，无法查找开发者选项")
            return false
        }
        
        LogUtils.d("AdbAutoSetup", "开始查找开发者选项，最多尝试5次")
        
        // 首先尝试使用直接跳转到开发者选项
        if (tryDirectJumpToDeveloperOptions()) {
            LogUtils.i("AdbAutoSetup", "直接跳转到开发者选项成功")
            return true
        }
        
        // 如果直接跳转失败，尝试通过导航查找
        LogUtils.d("AdbAutoSetup", "直接跳转失败，尝试导航查找")
        
        repeat(5) { attempt ->
            LogUtils.d("AdbAutoSetup", "第${attempt + 1}次尝试查找开发者选项")
            
            val root = a11yService.safeActiveWindow
            if (root == null) {
                LogUtils.w("AdbAutoSetup", "无法获取当前窗口")
                return false
            }
            
            // 记录当前页面信息
            val packageName = infoExtractor.getCurrentPackageName()
            LogUtils.d("AdbAutoSetup", "当前窗口包名: $packageName")
            
            // 特别处理ColorOS 15 - 先查找"系统与更新"
            if (packageName == "com.android.settings" && attempt == 0) {
                LogUtils.d("AdbAutoSetup", "检测到设置应用，尝试ColorOS 15路径：系统与更新 → 开发者选项")
                if (navigateToSystemUpdateForColorOS()) {
                    LogUtils.i("AdbAutoSetup", "成功通过ColorOS 15路径找到开发者选项")
                    return true
                }
            }
            
            // 标准开发者选项查找
            for ((index, selector) in DeveloperRules.developerOptionsSelectors.withIndex()) {
                try {
                    LogUtils.d("AdbAutoSetup", "尝试选择器 ${index + 1}: $selector")
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null) {
                        LogUtils.d("AdbAutoSetup", "找到匹配节点: ${node.text} | ${node.contentDescription}")
                        if (node.isClickable) {
                            LogUtils.d("AdbAutoSetup", "节点可点击，尝试点击开发者选项")
                            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            LogUtils.d("AdbAutoSetup", "点击结果: $clicked")
                            if (clicked) {
                                LogUtils.i("AdbAutoSetup", "成功点击开发者选项，等待页面加载")
                                delay(2000) // 等待页面加载
                                return true
                            }
                        } else {
                            LogUtils.w("AdbAutoSetup", "找到开发者选项节点但不可点击")
                        }
                    } else {
                        LogUtils.d("AdbAutoSetup", "选择器未匹配到节点")
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "开发者选项选择器失败: $selector", e)
                }
            }
            
            // 如果未找到，尝试滚动页面
            if (attempt < 4) {
                LogUtils.d("AdbAutoSetup", "未找到开发者选项，尝试滚动页面到底部")
                scrollToBottomOfSettingsPage(root)
                delay(1500)
            }
        }
        
        LogUtils.w("AdbAutoSetup", "所有尝试都失败，未找到开发者选项")
        return false
    }
    
    /**
     * 激活开发者模式
     */
    private suspend fun activateDeveloperMode() {
        _currentState.value = AdbAutoState.ACTIVATING_DEVELOPER_MODE
        
        LogUtils.d("AdbAutoSetup", "开始激活开发者模式")
        
        // 导航到"关于手机"
        if (!navigateToAboutPhone()) {
            throw Exception("无法找到关于手机页面")
        }
        
        // 连续点击版本号7次
        if (!clickVersionNumber()) {
            throw Exception("无法激活开发者模式")
        }
        
        // 返回设置主页
        returnToSettingsHome()
    }
    
    /**
     * 导航到关于手机页面
     */
    private suspend fun navigateToAboutPhone(): Boolean {
        val a11yService = A11yService.instance ?: return false
        
        repeat(3) {
            val root = a11yService.safeActiveWindow ?: return false
            
            for (selector in DeveloperRules.aboutPhoneSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null && node.isClickable) {
                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clicked) {
                            delay(2000)
                            return true
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "关于手机选择器失败", e)
                }
            }
            
            scrollSettingsPage(root)
            delay(1000)
        }
        
        return false
    }
    
    /**
     * 连续点击版本号激活开发者模式
     */
    private suspend fun clickVersionNumber(): Boolean {
        val a11yService = A11yService.instance ?: return false
        
        repeat(10) { // 最多点击10次
            val root = a11yService.safeActiveWindow ?: return false
            
            for (selector in DeveloperRules.versionSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null && node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(300) // 快速点击
                        
                        // 检查是否有开发者模式激活的提示
                        if (checkDeveloperModeActivated(root)) {
                            LogUtils.i("AdbAutoSetup", "开发者模式已激活")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "版本号选择器失败", e)
                }
            }
        }
        
        return false
    }
    
    /**
     * 检查开发者模式是否已激活
     */
    private fun checkDeveloperModeActivated(root: AccessibilityNodeInfo): Boolean {
        for (selector in DeveloperRules.developerModeConfirmSelectors) {
            try {
                val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (node != null) {
                    return true
                }
            } catch (e: Exception) {
                // 忽略错误，继续检查
            }
        }
        return false
    }
    
    /**
     * 启用USB调试
     */
    private suspend fun enableUSBDebugging() {
        _currentState.value = AdbAutoState.ENABLING_USB_DEBUGGING
        
        LogUtils.d("AdbAutoSetup", "启用USB调试")
        
        if (!findAndEnableOption(DeveloperRules.usbDebuggingSelectors, "USB调试")) {
            LogUtils.w("AdbAutoSetup", "无法找到或启用USB调试选项")
        }
        
        delay(1000)
    }
    
    /**
     * 启用无线调试
     */
    private suspend fun enableWirelessDebugging() {
        LogUtils.d("AdbAutoSetup", "启用无线调试 - 使用ColorOS 15增强版")
        
        try {
            // 使用增强版方法，适配ColorOS 15
            enableWirelessDebuggingEnhanced()
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "增强版无线调试启用失败，尝试传统方法", e)
            
            // 如果增强版失败，尝试传统方法
            _currentState.value = AdbAutoState.ENABLING_WIRELESS_DEBUGGING
            
            if (!findAndEnableOption(DeveloperRules.wirelessDebuggingSelectors, "无线调试")) {
                throw Exception("无法找到或启用无线调试选项")
            }
        }
        
        // 等待无线调试信息出现
        delay(3000)
    }
    
    /**
     * 查找并启用指定选项
     */
    private suspend fun findAndEnableOption(
        selectors: List<li.songe.selector.Selector>,
        optionName: String
    ): Boolean {
        val a11yService = A11yService.instance ?: return false
        
        repeat(3) { attempt ->
            val root = a11yService.safeActiveWindow ?: return false
            
            // 首先尝试找到选项
            for (selector in selectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null) {
                        LogUtils.d("AdbAutoSetup", "找到$optionName 选项")
                        
                        // 点击进入选项（如果可点击）
                        if (node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            delay(1000)
                        }
                        
                        // 查找并点击开关
                        return findAndClickSwitch(optionName)
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "$optionName 选择器失败", e)
                }
            }
            
            if (attempt < 2) {
                scrollSettingsPage(root)
                delay(1000)
            }
        }
        
        return false
    }
    
    /**
     * 查找并点击开关
     */
    private suspend fun findAndClickSwitch(optionName: String): Boolean {
        val a11yService = A11yService.instance ?: return false
        val root = a11yService.safeActiveWindow ?: return false
        
        // 首先检查是否已经启用
        for (selector in DeveloperRules.enabledSwitchSelectors) {
            try {
                val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (node != null) {
                    LogUtils.i("AdbAutoSetup", "$optionName 已经启用")
                    return true
                }
            } catch (e: Exception) {
                // 继续检查
            }
        }
        
        // 查找未启用的开关并点击
        for (selector in DeveloperRules.switchSelectors) {
            try {
                val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (node != null && node.isClickable) {
                    LogUtils.d("AdbAutoSetup", "点击$optionName 开关")
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        delay(1000)
                        
                        // 处理可能的确认对话框
                        handleConfirmDialog()
                        
                        return true
                    }
                }
            } catch (e: Exception) {
                LogUtils.w("AdbAutoSetup", "开关选择器失败", e)
            }
        }
        
        return false
    }
    
    /**
     * 处理确认对话框
     */
    private suspend fun handleConfirmDialog() {
        delay(500) // 等待对话框出现
        
        val a11yService = A11yService.instance ?: return
        val root = a11yService.safeActiveWindow ?: return
        
        for (selector in DeveloperRules.confirmSelectors) {
            try {
                val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (node != null && node.isClickable) {
                    LogUtils.d("AdbAutoSetup", "点击确认按钮")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    delay(1000)
                    return
                }
            } catch (e: Exception) {
                // 继续尝试其他选择器
            }
        }
    }
    
    /**
     * 提取ADB信息
     */
    private suspend fun extractAdbInformation(): AdbInfo? {
        _currentState.value = AdbAutoState.EXTRACTING_ADB_INFO
        
        LogUtils.d("AdbAutoSetup", "开始提取ADB信息")
        
        // 多次尝试提取信息
        repeat(5) { attempt ->
            val adbInfo = infoExtractor.extractAdbInfo()
            if (adbInfo != null && adbInfo.isValid()) {
                LogUtils.i("AdbAutoSetup", "成功提取ADB信息: $adbInfo")
                return adbInfo
            }
            
            if (attempt < 4) {
                LogUtils.d("AdbAutoSetup", "第${attempt + 1}次提取失败，等待后重试")
                delay(2000)
            }
        }
        
        throw Exception("无法提取ADB连接信息")
    }
    
    /**
     * 发送通知和Webhook
     */
    private suspend fun sendNotifications(adbInfo: AdbInfo) {
        try {
            // 发送系统通知
            if (config.enableNotification) {
                _currentState.value = AdbAutoState.SENDING_NOTIFICATION
                sendSystemNotification(adbInfo)
            }
            
            // 发送Webhook
            if (config.enableWebhook && config.webhookUrl.isNotEmpty()) {
                _currentState.value = AdbAutoState.SENDING_WEBHOOK
                val wifiSSID = wifiMonitor.getCurrentSSID()
                webhook.sendAdbInfo(config.webhookUrl, adbInfo, wifiSSID)
            }
            
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "发送通知失败", e)
            // 不抛出异常，因为主要功能已完成
        }
    }
    
    /**
     * 发送系统通知
     */
    private fun sendSystemNotification(adbInfo: AdbInfo) {
        try {
            // TODO: 集成GKD通知系统
            LogUtils.i("AdbAutoSetup", "ADB信息: ${adbInfo.ip}:${adbInfo.port}")
            // val title = "ADB无线调试已启用"
            // val content = "连接命令: adb connect ${adbInfo.ip}:${adbInfo.port}"
            // createNotif(app, defaultChannel.id, title, content)
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "发送系统通知失败", e)
        }
    }
    
    /**
     * 滚动设置页面
     */
    private fun scrollSettingsPage(root: AccessibilityNodeInfo) {
        try {
            root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "滚动页面失败", e)
        }
    }
    
    /**
     * 返回设置主页
     */
    private suspend fun returnToSettingsHome() {
        repeat(3) {
            val a11yService = A11yService.instance ?: return
            val root = a11yService.safeActiveWindow ?: return
            
            for (selector in DeveloperRules.backSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null && node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(1000)
                        return
                    }
                } catch (e: Exception) {
                    // 继续尝试
                }
            }
            
            // 如果找不到返回按钮，使用系统返回键
            try {
                A11yService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                delay(1000)
            } catch (e: Exception) {
                LogUtils.w("AdbAutoSetup", "返回操作失败", e)
            }
        }
    }
    
    /**
     * 返回主页
     */
    private suspend fun returnToHome() {
        try {
            A11yService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            delay(1000)
            LogUtils.d("AdbAutoSetup", "已返回主页")
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "返回主页失败", e)
        }
    }
    
    /**
     * 处理错误
     */
    private suspend fun handleError(error: Exception) {
        _currentState.value = AdbAutoState.ERROR
        
        if (retryCount < config.autoRetryCount) {
            retryCount++
            _currentState.value = AdbAutoState.RETRYING
            
            LogUtils.w("AdbAutoSetup", "第${retryCount}次重试，错误: ${error.message}")
            
            delay(config.retryDelaySeconds * 1000L)
            
            // 重试前先返回主页
            returnToHome()
            
            startAutomationProcess()
        } else {
            LogUtils.e("AdbAutoSetup", "自动化失败，已达到最大重试次数: ${error.message}")
            
            // 发送错误通知
            if (config.enableNotification) {
                sendErrorNotification(error.message ?: "未知错误")
            }
        }
    }
    
    /**
     * 发送错误通知
     */
    private fun sendErrorNotification(errorMessage: String) {
        try {
            // TODO: 集成GKD通知系统
            LogUtils.e("AdbAutoSetup", "自动化错误: $errorMessage")
            // val title = "ADB自动化失败"
            // val content = "错误: $errorMessage"
            // createNotif(app, defaultChannel.id, title, content)
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "发送错误通知失败", e)
        }
    }
    
    /**
     * 获取当前状态描述
     */
    fun getStateDescription(): String {
        return when (_currentState.value) {
            AdbAutoState.IDLE -> "空闲"
            AdbAutoState.WIFI_CONNECTED -> "WiFi已连接"
            AdbAutoState.CHECKING_ACCESSIBILITY -> "检查无障碍服务"
            AdbAutoState.NAVIGATING_TO_SETTINGS -> "导航到设置"
            AdbAutoState.SEARCHING_DEVELOPER_OPTIONS -> "查找开发者选项"
            AdbAutoState.ACTIVATING_DEVELOPER_MODE -> "激活开发者模式"
            AdbAutoState.ENABLING_USB_DEBUGGING -> "启用USB调试"
            AdbAutoState.ENABLING_WIRELESS_DEBUGGING -> "启用无线调试"
            AdbAutoState.EXTRACTING_ADB_INFO -> "提取ADB信息"
            AdbAutoState.SENDING_NOTIFICATION -> "发送通知"
            AdbAutoState.SENDING_WEBHOOK -> "发送Webhook"
            AdbAutoState.COMPLETED -> "完成"
            AdbAutoState.ERROR -> "错误"
            AdbAutoState.RETRYING -> "重试中"
        }
    }
    
    /**
     * 尝试直接跳转到开发者选项
     */
    private suspend fun tryDirectJumpToDeveloperOptions(): Boolean {
        return try {
            LogUtils.i("AdbAutoSetup", "开始直接跳转到开发者选项")

            // 尝试多种跳转方式
            val intents = listOf(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
                Intent("com.android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
            )

            for ((index, intent) in intents.withIndex()) {
                try {
                    LogUtils.d("AdbAutoSetup", "尝试第${index + 1}种跳转方式")
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    app.startActivity(intent)
                    delay(3000) // 等待页面加载

                    // 检查是否成功打开开发者选项页面
                    val a11yService = A11yService.instance
                    val root = a11yService?.safeActiveWindow
                    if (root != null) {
                        val packageName = infoExtractor.getCurrentPackageName()
                        LogUtils.d("AdbAutoSetup", "跳转后的页面包名: $packageName")

                        // 方法1：检查页面标题是否包含开发者选项
                        val allText = getAllTextFromPage(root)
                        if (allText.contains("开发者选项") || allText.contains("Developer options") ||
                            allText.contains("开发人员选项") || allText.contains("Developer mode")) {
                            LogUtils.i("AdbAutoSetup", "✅ 直接跳转成功，已进入开发者选项页面")
                            return true
                        }

                        // 方法2：检查是否有开发者选项特有的元素
                        for (selector in DeveloperRules.wirelessDebuggingSelectors) {
                            val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                            if (node != null) {
                                LogUtils.i("AdbAutoSetup", "✅ 直接跳转成功，发现无线调试选项")
                                return true
                            }
                        }

                        // 方法3：检查USB调试选项
                        for (selector in DeveloperRules.usbDebuggingSelectors) {
                            val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                            if (node != null) {
                                LogUtils.i("AdbAutoSetup", "✅ 直接跳转成功，发现USB调试选项")
                                return true
                            }
                        }

                        LogUtils.d("AdbAutoSetup", "第${index + 1}种跳转方式未能确认进入开发者选项")
                    } else {
                        LogUtils.w("AdbAutoSetup", "无法获取当前窗口")
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "第${index + 1}种跳转方式失败", e)
                }
            }

            LogUtils.e("AdbAutoSetup", "❌ 所有直接跳转方式都失败")
            false
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "直接跳转到开发者选项时发生异常", e)
            false
        }
    }
    
    /**
     * ColorOS 15专用：导航到系统与更新页面查找开发者选项
     */
    private suspend fun navigateToSystemUpdateForColorOS(): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "开始ColorOS 15导航：查找系统与更新")
            
            val a11yService = A11yService.instance ?: return false
            
            // 先滚动到底部查找"系统与更新"
            repeat(3) { attempt ->
                val root = a11yService.safeActiveWindow ?: return false
                
                LogUtils.d("AdbAutoSetup", "第${attempt + 1}次查找系统与更新")
                
                // 查找"系统与更新"选项
                for (selector in DeveloperRules.systemUpdateSelectors) {
                    try {
                        val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                        if (node != null && node.isClickable) {
                            LogUtils.d("AdbAutoSetup", "找到系统与更新选项: ${node.text}")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            delay(2000) // 等待页面加载
                            
                            // 在系统与更新页面查找开发者选项
                            return findDeveloperOptionsInSystemUpdate()
                        }
                    } catch (e: Exception) {
                        LogUtils.w("AdbAutoSetup", "查找系统与更新失败: $selector", e)
                    }
                }
                
                // 如果没找到，滚动页面继续查找
                if (attempt < 2) {
                    LogUtils.d("AdbAutoSetup", "未找到系统与更新，滚动页面")
                    scrollToBottomOfSettingsPage(root)
                    delay(1000)
                }
            }
            
            false
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "ColorOS 15导航失败", e)
            false
        }
    }
    
    /**
     * 在系统与更新页面查找开发者选项
     */
    private suspend fun findDeveloperOptionsInSystemUpdate(): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "在系统与更新页面查找开发者选项")
            
            val a11yService = A11yService.instance ?: return false
            
            repeat(3) { attempt ->
                val root = a11yService.safeActiveWindow ?: return false
                
                LogUtils.d("AdbAutoSetup", "系统与更新页面第${attempt + 1}次查找开发者选项")
                
                for (selector in DeveloperRules.developerOptionsSelectors) {
                    try {
                        val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                        if (node != null && node.isClickable) {
                            LogUtils.i("AdbAutoSetup", "在系统与更新页面找到开发者选项: ${node.text}")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            delay(2000) // 等待页面加载
                            return true
                        }
                    } catch (e: Exception) {
                        LogUtils.w("AdbAutoSetup", "系统与更新页面查找开发者选项失败: $selector", e)
                    }
                }
                
                // 如果没找到，向下滚动
                if (attempt < 2) {
                    LogUtils.d("AdbAutoSetup", "系统与更新页面未找到开发者选项，向下滚动")
                    root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    delay(1000)
                }
            }
            
            false
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "在系统与更新页面查找开发者选项失败", e)
            false
        }
    }
    
    /**
     * 滚动到设置页面底部
     */
    private fun scrollToBottomOfSettingsPage(root: AccessibilityNodeInfo) {
        try {
            LogUtils.d("AdbAutoSetup", "开始滚动设置页面到底部")
            
            // 尝试多次向下滚动到达底部
            repeat(5) { scrollIndex ->
                LogUtils.d("AdbAutoSetup", "第${scrollIndex + 1}次向下滚动")
                
                val scrolled = root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                LogUtils.d("AdbAutoSetup", "滚动结果: $scrolled")
                
                if (!scrolled) {
                    LogUtils.d("AdbAutoSetup", "无法继续滚动，可能已到达底部")
                    return@repeat
                }
                
                Thread.sleep(500) // 等待滚动动画完成
            }
            
            LogUtils.d("AdbAutoSetup", "滚动到底部完成")
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "滚动页面到底部失败", e)
        }
    }
    
    /**
     * 增强版无线调试启用（适配ColorOS 15）
     */
    private suspend fun enableWirelessDebuggingEnhanced() {
        _currentState.value = AdbAutoState.ENABLING_WIRELESS_DEBUGGING
        
        LogUtils.d("AdbAutoSetup", "开始启用无线调试（增强版，适配ColorOS 15）")
        
        val a11yService = A11yService.instance ?: throw Exception("无障碍服务不可用")
        
        repeat(3) { attempt ->
            LogUtils.d("AdbAutoSetup", "第${attempt + 1}次尝试启用无线调试")
            
            val root = a11yService.safeActiveWindow ?: throw Exception("无法获取当前窗口")
            
            // 需要先向下滚动找到无线调试选项（ColorOS 15需要滚动约两页）
            if (attempt == 0) {
                LogUtils.d("AdbAutoSetup", "ColorOS 15需要向下滚动约两页找到无线调试")
                repeat(6) { scrollIndex ->
                    LogUtils.d("AdbAutoSetup", "向下滚动查找无线调试: ${scrollIndex + 1}/6")
                    root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    delay(500)
                }
            }
            
            // 查找无线调试选项
            for (selector in DeveloperRules.wirelessDebuggingSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null && node.isClickable) {
                        LogUtils.d("AdbAutoSetup", "找到无线调试选项: ${node.text}")
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(2000) // 等待进入无线调试页面
                        
                        // 查找并启用开关
                        if (enableWirelessDebuggingSwitch()) {
                            LogUtils.i("AdbAutoSetup", "无线调试已成功启用")
                            return
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "查找无线调试选项失败: $selector", e)
                }
            }
            
            // 如果没找到，继续滚动
            if (attempt < 2) {
                LogUtils.d("AdbAutoSetup", "未找到无线调试，继续滚动")
                root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                delay(1000)
            }
        }
        
        throw Exception("无法找到或启用无线调试选项")
    }
    
    /**
     * 启用无线调试开关
     */
    private suspend fun enableWirelessDebuggingSwitch(): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "查找无线调试开关")
            
            val a11yService = A11yService.instance ?: return false
            val root = a11yService.safeActiveWindow ?: return false
            
            // 查找并点击开关
            for (selector in DeveloperRules.switchSelectors) {
                try {
                    val switchNode = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (switchNode != null && switchNode.isClickable && !switchNode.isChecked) {
                        LogUtils.d("AdbAutoSetup", "找到未开启的无线调试开关，尝试开启")
                        val clicked = switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        LogUtils.d("AdbAutoSetup", "开关点击结果: $clicked")
                        
                        if (clicked) {
                            delay(2000) // 等待开关生效
                            
                            // 可能会弹出确认对话框
                            handleConfirmationDialog()
                            
                            LogUtils.i("AdbAutoSetup", "无线调试开关已启用")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "操作无线调试开关失败: $selector", e)
                }
            }
            
            false
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "启用无线调试开关失败", e)
            false
        }
    }
    
    /**
     * 处理确认对话框
     */
    private suspend fun handleConfirmationDialog() {
        try {
            LogUtils.d("AdbAutoSetup", "检查是否有确认对话框")
            delay(1000) // 等待对话框出现

            val a11yService = A11yService.instance ?: return
            val root = a11yService.safeActiveWindow ?: return

            for (selector in DeveloperRules.confirmSelectors) {
                try {
                    val confirmNode = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (confirmNode != null && confirmNode.isClickable) {
                        LogUtils.d("AdbAutoSetup", "找到确认按钮: ${confirmNode.text}")
                        confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(1000)
                        LogUtils.d("AdbAutoSetup", "已点击确认按钮")
                        return
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbAutoSetup", "处理确认对话框失败: $selector", e)
                }
            }
        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "处理确认对话框失败", e)
        }
    }

    /**
     * ColorOS 15专用的调试版本完整自动化流程
     * 用于按钮5的完整自动化测试，包含详细的调试信息
     */
    suspend fun triggerColorOS15FullAutomation(): Boolean {
        return try {
            LogUtils.i("AdbAutoSetup", "===== 开始ColorOS 15完整自动化调试 =====")

            // 重置状态
            _currentState.value = AdbAutoState.IDLE

            // 步骤1：检查无障碍服务
            if (A11yService.instance == null) {
                LogUtils.e("AdbAutoSetup", "无障碍服务不可用")
                return false
            }

            // 步骤2：直接跳转到开发者选项界面
            LogUtils.i("AdbAutoSetup", "步骤1: 直接跳转到开发者选项界面")
            if (!tryDirectJumpToDeveloperOptions()) {
                LogUtils.e("AdbAutoSetup", "无法直接跳转到开发者选项")
                return false
            }

            // 步骤4：在开发者选项页面找到并启用无线调试
            LogUtils.i("AdbAutoSetup", "步骤3: 启用无线调试")
            if (!enableWirelessDebuggingWithGkdRules()) {
                LogUtils.e("AdbAutoSetup", "启用无线调试失败")
                return false
            }

            // 步骤5：提取ADB信息
            LogUtils.i("AdbAutoSetup", "步骤4: 提取ADB信息")
            delay(3000) // 等待ADB信息出现
            val adbInfo = infoExtractor.extractAdbInfo()
            if (adbInfo != null) {
                LogUtils.i("AdbAutoSetup", "✅ 成功提取ADB信息: $adbInfo")
                _lastAdbInfo.value = adbInfo
                return true
            } else {
                LogUtils.e("AdbAutoSetup", "❌ 未能提取到ADB信息")
                return false
            }

        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "❌ ColorOS 15完整自动化失败", e)
            return false
        } finally {
            LogUtils.i("AdbAutoSetup", "===== ColorOS 15完整自动化调试结束 =====")
        }
    }

    /**
     * 使用GKD规则框架启用无线调试
     */
    private suspend fun enableWirelessDebuggingWithGkdRules(): Boolean {
        return try {
            LogUtils.i("AdbAutoSetup", "开始使用GKD规则框架启用无线调试")

            val a11yService = A11yService.instance ?: throw Exception("无障碍服务不可用")
            val root = a11yService.safeActiveWindow ?: throw Exception("无法获取当前窗口")

            // 步骤1：先滚动页面确保无线调试选项可见
            LogUtils.d("AdbAutoSetup", "步骤1: 滚动页面查找无线调试选项")
            scrollToFindWirelessDebugging(root)

            // 步骤2：查找并点击无线调试选项
            LogUtils.d("AdbAutoSetup", "步骤2: 查找并点击无线调试选项")
            if (!findAndClickWirelessDebuggingOption(root)) {
                LogUtils.w("AdbAutoSetup", "未能找到无线调试选项")
            }

            // 步骤3：查找并启用无线调试开关
            LogUtils.d("AdbAutoSetup", "步骤3: 查找并启用无线调试开关")
            if (!enableWirelessDebuggingSwitch(root)) {
                LogUtils.w("AdbAutoSetup", "未能启用无线调试开关，可能已经启用")
            }

            // 步骤4：处理确认对话框
            LogUtils.d("AdbAutoSetup", "步骤4: 处理可能的确认对话框")
            handleConfirmationDialog()

            LogUtils.i("AdbAutoSetup", "✅ 无线调试启用流程完成")
            true

        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "使用GKD规则启用无线调试失败", e)
            false
        }
    }

    /**
     * 滚动页面查找无线调试选项
     */
    private suspend fun scrollToFindWirelessDebugging(root: AccessibilityNodeInfo) {
        LogUtils.d("AdbAutoSetup", "开始滚动查找无线调试选项")

        // 先检查当前页面是否已有无线调试选项
        if (checkWirelessDebuggingExists(root)) {
            LogUtils.d("AdbAutoSetup", "当前页面已发现无线调试选项")
            return
        }

        // 向下滚动查找
        repeat(5) { scrollIndex ->
            LogUtils.d("AdbAutoSetup", "向下滚动查找无线调试: ${scrollIndex + 1}/5")

            val scrolled = root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (!scrolled) {
                LogUtils.d("AdbAutoSetup", "无法继续滚动，可能已到达页面底部")
                return
            }

            delay(800) // 等待滚动完成

            // 检查滚动后是否找到无线调试选项
            if (checkWirelessDebuggingExists(root)) {
                LogUtils.i("AdbAutoSetup", "滚动后找到无线调试选项")
                return
            }
        }

        LogUtils.w("AdbAutoSetup", "滚动完成后仍未找到无线调试选项")
    }

    /**
     * 检查当前页面是否有无线调试选项（彻底修复版 - 不使用GKD选择器）
     */
    private fun checkWirelessDebuggingExists(root: AccessibilityNodeInfo): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "开始检查无线调试选项（简单文本搜索）")

            // 获取页面所有文本
            val allText = getAllTextFromPage(root)
            LogUtils.d("AdbAutoSetup", "页面文本预览: ${allText.take(200)}...")

            // 直接通过文本搜索判断，不使用GKD选择器
            val hasWirelessDebugging = allText.contains("无线调试") ||
                                         allText.contains("Wireless debugging") ||
                                         allText.contains("WiFi调试") ||
                                         allText.contains("WiFi debugging") ||
                                         allText.contains("无线ADB") ||
                                         allText.contains("Wireless ADB")

            LogUtils.d("AdbAutoSetup", "无线调试选项检查结果: $hasWirelessDebugging")
            hasWirelessDebugging

        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "检查无线调试选项时发生异常", e)
            false
        }
    }

    /**
     * 查找并点击无线调试选项（彻底修复版 - 不使用GKD选择器）
     */
    private suspend fun findAndClickWirelessDebuggingOption(root: AccessibilityNodeInfo): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "开始查找并点击无线调试选项（文本搜索版）")

            // 获取页面所有文本
            val allText = getAllTextFromPage(root)
            LogUtils.d("AdbAutoSetup", "页面文本预览: ${allText.take(300)}...")

            // 递归查找包含无线调试文本的可点击节点
            val wirelessNodes = findClickableNodesWithText(root, listOf("无线调试", "Wireless debugging", "WiFi调试", "WiFi debugging"))

            if (wirelessNodes.isNotEmpty()) {
                LogUtils.i("AdbAutoSetup", "找到 ${wirelessNodes.size} 个无线调试相关节点")

                for ((index, node) in wirelessNodes.withIndex()) {
                    try {
                        LogUtils.d("AdbAutoSetup", "尝试点击无线调试节点 ${index + 1}: '${node.text}'")
                        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        LogUtils.d("AdbAutoSetup", "点击结果: $clicked")

                        if (clicked) {
                            LogUtils.i("AdbAutoSetup", "🎯 成功点击无线调试选项，等待页面加载...")
                            delay(2500)
                            return true
                        }
                    } catch (e: Exception) {
                        LogUtils.w("AdbAutoSetup", "点击无线调试节点失败", e)
                    }
                }
            } else {
                LogUtils.w("AdbAutoSetup", "未找到无线调试相关节点")
            }

            false
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "查找并点击无线调试选项时发生异常", e)
            false
        }
    }

    /**
     * 递归查找包含指定文本的可点击节点
     */
    private fun findClickableNodesWithText(root: AccessibilityNodeInfo, keywords: List<String>): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()

        fun searchNode(node: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > 10) return // 防止过深递归

            try {
                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""

                // 检查节点文本或描述是否包含关键词
                val containsKeyword = keywords.any { keyword ->
                    nodeText.contains(keyword, ignoreCase = true) ||
                    nodeDesc.contains(keyword, ignoreCase = true)
                }

                if (containsKeyword && node.isClickable) {
                    results.add(node)
                    LogUtils.d("AdbAutoSetup", "找到匹配节点: '$nodeText' (可点击=${node.isClickable})")
                }

                // 递归搜索子节点
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        searchNode(child, depth + 1)
                    }
                }
            } catch (e: Exception) {
                // 忽略单个节点的错误
            }
        }

        searchNode(root)
        return results
    }

    /**
     * 验证是否进入了无线调试详细页面
     */
    private suspend fun verifyEnteredWirelessDebuggingPage(): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "验证是否进入无线调试详细页面")

            val a11yService = A11yService.instance ?: return false
            val root = a11yService.safeActiveWindow ?: return false

            // 方法1：检查页面文本是否包含无线调试相关内容
            val pageText = getAllTextFromPage(root)
            val hasWirelessContent = pageText.contains("无线调试") ||
                                       pageText.contains("Wireless debugging") ||
                                       pageText.contains("IP地址") ||
                                       pageText.contains("IP address")

            LogUtils.d("AdbAutoSetup", "页面文本检查: ${if (hasWirelessContent) "包含无线调试内容" else "未包含无线调试内容"}")

            // 方法2：检查是否有开关控件
            val hasSwitchControls = DeveloperRules.switchSelectors.any { selector ->
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    node != null
                } catch (e: Exception) {
                    false
                }
            }

            LogUtils.d("AdbAutoSetup", "开关控件检查: ${if (hasSwitchControls) "发现开关控件" else "未发现开关控件"}")

            val isWirelessPage = hasWirelessContent || hasSwitchControls
            LogUtils.i("AdbAutoSetup", "无线调试页面验证结果: ${if (isWirelessPage) "✅ 是" else "❌ 否"}")

            isWirelessPage

        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "验证无线调试页面失败", e)
            false
        }
    }

    /**
     * 使用GKD规则启用无线调试开关（增强版）
     */
    private suspend fun enableWirelessDebuggingSwitch(root: AccessibilityNodeInfo): Boolean {
        LogUtils.d("AdbAutoSetup", "开始查找并启用无线调试开关（增强版）")

        // 获取当前页面文本，用于调试
        val pageText = getAllTextFromPage(root)
        LogUtils.d("AdbAutoSetup", "当前页面文本: ${pageText.take(300)}...")

        // 增强版开关查找策略：使用多种方法
        LogUtils.d("AdbAutoSetup", "=== 开始增强版开关查找 ===")

        // 方法1：标准选择器查找
        var foundSwitches = findSwitchesWithSelectors(root)
        if (foundSwitches.isNotEmpty()) {
            LogUtils.i("AdbAutoSetup", "方法1成功：找到 ${foundSwitches.size} 个开关")
            return tryEnableSwitches(foundSwitches)
        }

        // 方法2：基于文本内容查找开关
        LogUtils.d("AdbAutoSetup", "方法1失败，尝试基于文本内容查找开关")
        foundSwitches = findSwitchesByText(root)
        if (foundSwitches.isNotEmpty()) {
            LogUtils.i("AdbAutoSetup", "方法2成功：找到 ${foundSwitches.size} 个开关")
            return tryEnableSwitches(foundSwitches)
        }

        // 方法3：递归搜索所有可能的开关
        LogUtils.d("AdbAutoSetup", "方法2失败，尝试递归搜索所有开关")
        foundSwitches = findAllSwitchNodes(root)
        if (foundSwitches.isNotEmpty()) {
            LogUtils.i("AdbAutoSetup", "方法3成功：找到 ${foundSwitches.size} 个开关")
            return tryEnableSwitches(foundSwitches)
        }

        // 方法4：智能区域搜索
        LogUtils.d("AdbAutoSetup", "方法3失败，尝试智能区域搜索")
        foundSwitches = findSwitchesInWirelessSection(root)
        if (foundSwitches.isNotEmpty()) {
            LogUtils.i("AdbAutoSetup", "方法4成功：找到 ${foundSwitches.size} 个开关")
            return tryEnableSwitches(foundSwitches)
        }

        LogUtils.w("AdbAutoSetup", "❌ 所有开关查找方法都失败")

        // 最后检查：是否已经在无线调试页面但开关被隐藏
        if (pageText.contains("无线调试") && (pageText.contains("IP地址") || pageText.contains(":"))) {
            LogUtils.i("AdbAutoSetup", "✅ 检测到已启用无线调试（页面包含ADB信息）")
            return true
        }

        return false
    }

    /**
     * 使用标准选择器查找开关
     */
    private fun findSwitchesWithSelectors(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val switches = mutableListOf<AccessibilityNodeInfo>()

        for ((index, selector) in DeveloperRules.switchSelectors.withIndex()) {
            try {
                LogUtils.d("AdbAutoSetup", "尝试标准开关选择器 ${index + 1}: $selector")
                val switchNode = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (switchNode != null) {
                    val nodeText = switchNode.text?.toString() ?: switchNode.contentDescription?.toString() ?: "未知"
                    LogUtils.i("AdbAutoSetup", "✅ 标准选择器找到开关 ${index + 1}: '$nodeText', 可点击=${switchNode.isClickable}")
                    switches.add(switchNode)
                }
            } catch (e: Exception) {
                LogUtils.w("AdbAutoSetup", "标准开关选择器 ${index + 1} 失败", e)
            }
        }

        return switches
    }

    /**
     * 基于文本内容查找开关
     */
    private fun findSwitchesByText(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val switches = mutableListOf<AccessibilityNodeInfo>()

        fun searchByText(node: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > 10) return // 防止过深递归

            try {
                val nodeText = node.text?.toString()?.lowercase() ?: ""
                val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

                // 查找与无线调试相关的文本
                val wirelessKeywords = listOf("无线", "wireless", "调试", "debug", "adb")
                val isRelatedToWireless = wirelessKeywords.any { keyword ->
                    nodeText.contains(keyword) || nodeDesc.contains(keyword)
                }

                // 如果节点与无线调试相关，查找其子节点中的开关
                if (isRelatedToWireless) {
                    LogUtils.d("AdbAutoSetup", "发现无线调试相关节点: '$nodeText'")

                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { child ->
                            if (isSwitchNode(child)) {
                                LogUtils.i("AdbAutoSetup", "✅ 文本搜索找到开关: '${child.text}'")
                                switches.add(child)
                            }
                        }
                    }
                }

                // 递归搜索子节点
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        searchByText(child, depth + 1)
                    }
                }
            } catch (e: Exception) {
                // 忽略单个节点的错误
            }
        }

        searchByText(root)
        return switches
    }

    /**
     * 递归搜索所有可能的开关节点
     */
    private fun findAllSwitchNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val switches = mutableListOf<AccessibilityNodeInfo>()

        fun collectAllSwitches(node: AccessibilityNodeInfo, depth: Int = 0) {
            if (depth > 15) return // 防止过深递归

            try {
                // 检查当前节点是否是开关
                if (isSwitchNode(node)) {
                    val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: "未知"
                    LogUtils.i("AdbAutoSetup", "✅ 递归搜索找到开关: '$nodeText', 类名=${node.className}")
                    switches.add(node)
                }

                // 递归处理子节点
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        collectAllSwitches(child, depth + 1)
                    }
                }
            } catch (e: Exception) {
                // 忽略单个节点的错误
            }
        }

        collectAllSwitches(root)
        return switches
    }

    /**
     * 在无线调试相关区域查找开关
     */
    private fun findSwitchesInWirelessSection(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val switches = mutableListOf<AccessibilityNodeInfo>()

        // 首先找到包含无线调试文本的节点
        fun findWirelessSections(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val sections = mutableListOf<AccessibilityNodeInfo>()

            fun search(node: AccessibilityNodeInfo) {
                try {
                    val nodeText = node.text?.toString()?.lowercase() ?: ""
                    val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

                    if (nodeText.contains("无线调试") || nodeDesc.contains("wireless debugging") ||
                        nodeText.contains("adb") || nodeDesc.contains("adb")) {
                        sections.add(node)
                        LogUtils.d("AdbAutoSetup", "找到无线调试区域: '$nodeText'")
                    }

                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { child ->
                            search(child)
                        }
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }

            search(node)
            return sections
        }

        val wirelessSections = findWirelessSections(root)
        for (section in wirelessSections) {
            // 在这些区域中查找开关
            fun findSwitchesInSection(node: AccessibilityNodeInfo) {
                if (isSwitchNode(node)) {
                    switches.add(node)
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        findSwitchesInSection(child)
                    }
                }
            }

            findSwitchesInSection(section)
        }

        return switches
    }

    /**
     * 判断节点是否是开关
     */
    private fun isSwitchNode(node: AccessibilityNodeInfo): Boolean {
        return try {
            val className = node.className?.toString()?.lowercase() ?: ""
            val isSwitchClass = className.contains("switch") ||
                               className.contains("toggle") ||
                               className.contains("checkbox")

            val isCheckable = node.isCheckable
            val isClickable = node.isClickable

            val isSwitch = isSwitchClass || isCheckable

            if (isSwitch) {
                val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
                LogUtils.d("AdbAutoSetup", "检测到开关节点: '$nodeText', 类名=$className, 可检查=$isCheckable, 可点击=$isClickable")
            }

            isSwitch
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 尝试启用找到的开关列表
     */
    private suspend fun tryEnableSwitches(switches: List<AccessibilityNodeInfo>): Boolean {
        LogUtils.i("AdbAutoSetup", "开始尝试启用 ${switches.size} 个开关")

        for ((index, switchNode) in switches.withIndex()) {
            try {
                val nodeText = switchNode.text?.toString() ?: switchNode.contentDescription?.toString() ?: "未知"
                val isChecked = switchNode.isChecked
                val isClickable = switchNode.isClickable

                LogUtils.i("AdbAutoSetup", "处理开关 ${index + 1}: '$nodeText', 已启用=$isChecked, 可点击=$isClickable")

                if (isChecked) {
                    LogUtils.i("AdbAutoSetup", "✅ 开关 ${index + 1} 已启用")
                    return true
                }

                if (!isClickable) {
                    LogUtils.w("AdbAutoSetup", "⚠️ 开关 ${index + 1} 不可点击，跳过")
                    continue
                }

                // 尝试启用开关
                LogUtils.d("AdbAutoSetup", "尝试启用开关 ${index + 1}...")
                val clicked = switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                LogUtils.d("AdbAutoSetup", "开关 ${index + 1} 点击结果: $clicked")

                if (clicked) {
                    LogUtils.i("AdbAutoSetup", "🎯 成功点击开关 ${index + 1}，等待生效...")
                    delay(3000)

                    // 验证开关是否真的启用了
                    if (verifySwitchEnabled(switchNode)) {
                        LogUtils.i("AdbAutoSetup", "✅ 确认开关 ${index + 1} 已启用")
                        return true
                    } else {
                        LogUtils.w("AdbAutoSetup", "⚠️ 开关 ${index + 1} 点击后状态未改变")
                    }
                } else {
                    LogUtils.w("AdbAutoSetup", "❌ 点击开关 ${index + 1} 失败")
                }

            } catch (e: Exception) {
                LogUtils.w("AdbAutoSetup", "处理开关 ${index + 1} 时发生异常", e)
            }
        }

        LogUtils.w("AdbAutoSetup", "所有开关都无法启用")
        return false
    }

    /**
     * 验证开关是否真的启用了
     */
    private suspend fun verifySwitchEnabled(switchNode: AccessibilityNodeInfo): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "验证开关状态...")
            delay(1000) // 等待状态更新

            val currentChecked = switchNode.isChecked
            LogUtils.d("AdbAutoSetup", "开关当前状态: $currentChecked")

            // 多次检查确保状态稳定
            repeat(3) { checkIndex ->
                delay(500)
                val newChecked = switchNode.isChecked
                LogUtils.d("AdbAutoSetup", "检查 ${checkIndex + 1}: $newChecked")
                if (newChecked != currentChecked) {
                    LogUtils.i("AdbAutoSetup", "开关状态已改变: $currentChecked → $newChecked")
                    return newChecked
                }
            }

            currentChecked

        } catch (e: Exception) {
            LogUtils.w("AdbAutoSetup", "验证开关状态失败", e)
            false
        }
    }

    /**
     * 获取页面所有文本（从AdbInfoExtractor复制）
     */
    private fun getAllTextFromPage(root: AccessibilityNodeInfo): String {
        val textBuilder = StringBuilder()

        fun collectText(node: AccessibilityNodeInfo) {
            try {
                // 添加当前节点的文本
                node.text?.let { text ->
                    if (text.isNotEmpty()) {
                        textBuilder.append(text).append(" ")
                    }
                }

                // 添加内容描述
                node.contentDescription?.let { desc ->
                    if (desc.isNotEmpty()) {
                        textBuilder.append(desc).append(" ")
                    }
                }

                // 递归处理子节点
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        collectText(child)
                    }
                }
            } catch (e: Exception) {
                // 忽略单个节点的错误
            }
        }

        collectText(root)
        return textBuilder.toString()
    }
}