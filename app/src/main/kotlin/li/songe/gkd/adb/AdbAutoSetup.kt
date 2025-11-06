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
import li.songe.gkd.util.toast
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
        
        // 临时绕过DeveloperRules依赖 - 直接使用已知包名列表
        val knownSettingsPackages = listOf(
            "com.android.settings",        // 原生Android
            "com.miui.securitycenter",     // MIUI
            "com.huawei.systemmanager",    // EMUI/HarmonyOS
            "com.coloros.safecenter",      // ColorOS
            "com.vivo.permissionmanager",  // FunTouch OS
            "com.samsung.android.settings" // Samsung One UI
        )
        if (packageName != null && knownSettingsPackages.any {
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
            
            // 使用新的父节点查找逻辑
            LogUtils.d("AdbAutoSetup", "使用父节点查找逻辑查找开发者选项")
            val textNode = findNodeWithText(root, listOf("开发者选项", "Developer options", "开发人员选项"))
            if (textNode != null) {
                var clickableParent: AccessibilityNodeInfo? = textNode
                while (clickableParent != null && !clickableParent.isClickable) {
                    clickableParent = clickableParent.parent
                }

                clickableParent?.let {
                    LogUtils.i("AdbAutoSetup", "找到可点击的父节点，尝试点击开发者选项")
                    if (it.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        LogUtils.i("AdbAutoSetup", "成功点击开发者选项，等待页面加载")
                        delay(2000)
                        return true
                    }
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
            
            // 临时跳过传统方法，直接使用增强版（避免GKD选择器）
            LogUtils.d("AdbAutoSetup", "跳过传统方法，直接使用增强版避免GKD选择器")
            // if (!findAndEnableOption(DeveloperRules.wirelessDebuggingSelectors, "无线调试")) {
            //     throw Exception("无法找到或启用无线调试选项")
            // }
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
                LogUtils.e("AdbAutoSetup", "❌ 无障碍服务不可用，请确保GKD无障碍服务已启用")
                return false
            }
            
            // 检查无障碍服务是否真的在运行
            val a11yService = A11yService.instance
            if (a11yService == null) {
                LogUtils.e("AdbAutoSetup", "❌ 无法获取无障碍服务实例")
                return false
            }
            
            LogUtils.i("AdbAutoSetup", "✅ 无障碍服务已启用")
            
            // 检查是否可以获取当前窗口
            val testWindow = a11yService.safeActiveWindow
            if (testWindow == null) {
                LogUtils.e("AdbAutoSetup", "❌ 无法获取当前窗口，可能是权限问题")
                LogUtils.e("AdbAutoSetup", "请检查：1. GKD无障碍服务是否已启用 2. 是否授予了所有必要权限")
                return false
            }
            
            LogUtils.i("AdbAutoSetup", "✅ 可以获取当前窗口，权限正常")

            // 步骤2：直接跳转到开发者选项界面
            LogUtils.i("AdbAutoSetup", "步骤1: 直接跳转到开发者选项界面")
            if (!tryDirectJumpToDeveloperOptions()) {
                LogUtils.e("AdbAutoSetup", "无法直接跳转到开发者选项")
                return false
            }

            // 额外等待，确保开发者选项页面完全加载
            LogUtils.i("AdbAutoSetup", "等待开发者选项页面完全加载...")
            delay(2000)
            
            // 验证是否真的在开发者选项页面
            val verifyRoot = a11yService.safeActiveWindow
            if (verifyRoot != null) {
                val pageText = getAllTextFromPage(verifyRoot)
                LogUtils.i("AdbAutoSetup", "当前页面文本预览: ${pageText.take(300)}...")
                if (!pageText.contains("开发者选项") && !pageText.contains("Developer options")) {
                    LogUtils.w("AdbAutoSetup", "⚠️ 可能未在开发者选项页面，但继续尝试")
                }
            }

            // 验证无障碍服务权限和能力（不测试滚动，避免消耗滚动次数）
            LogUtils.i("AdbAutoSetup", "步骤2: 验证无障碍服务权限和能力")
            val testRoot = a11yService.safeActiveWindow
            if (testRoot == null) {
                LogUtils.e("AdbAutoSetup", "❌ 无法获取当前窗口，可能是权限问题")
                return false
            }
            
            // 检查是否可以获取节点信息（不测试滚动，避免消耗）
            val nodeCount = testRoot.childCount
            LogUtils.i("AdbAutoSetup", "当前窗口子节点数量: $nodeCount")
            if (nodeCount == 0) {
                LogUtils.w("AdbAutoSetup", "⚠️ 当前窗口没有子节点，可能是权限问题或页面未完全加载")
            } else {
                LogUtils.i("AdbAutoSetup", "✅ 可以获取节点信息，权限正常")
            }

            // 步骤4：在开发者选项页面找到并启用无线调试
            LogUtils.i("AdbAutoSetup", "步骤3: 启用无线调试")
            if (!enableWirelessDebuggingWithGkdRules()) {
                LogUtils.e("AdbAutoSetup", "启用无线调试失败")
                return false
            }

            // 步骤5：提取ADB信息（使用与按钮7相同的简单文本提取方法）
            LogUtils.i("AdbAutoSetup", "步骤4: 提取ADB信息（简单文本提取方法）")
            delay(3000) // 等待ADB信息出现
            
            // 重新获取根节点，确保是最新的页面
            val extractRoot = a11yService.safeActiveWindow
            if (extractRoot == null) {
                LogUtils.e("AdbAutoSetup", "❌ 无法获取当前窗口")
                toast("❌ 无法获取当前窗口")
                return false
            }
            
            // 使用与按钮7相同的简单文本提取方法
            val adbInfo = extractAdbInfoFromText(extractRoot)
            if (adbInfo != null) {
                LogUtils.i("AdbAutoSetup", "✅ 成功提取ADB信息: $adbInfo")
                _lastAdbInfo.value = adbInfo
                
                // 显示成功提示
                toast("✅ ADB信息提取成功\nIP: ${adbInfo.ip}\n端口: ${adbInfo.port}\n连接: ${adbInfo.ip}:${adbInfo.port}")
                
                return true
            } else {
                LogUtils.e("AdbAutoSetup", "❌ 未能提取到ADB信息")
                toast("❌ 未能提取到ADB信息，请确保无线调试已启用")
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
            var root = a11yService.safeActiveWindow ?: throw Exception("无法获取当前窗口")

            // 步骤1：先滚动页面确保无线调试选项可见
            LogUtils.i("AdbAutoSetup", "步骤1: 滚动页面查找无线调试选项")
            scrollToFindWirelessDebugging(root)
            
            // 重新获取根节点（滚动后可能变化）
            root = a11yService.safeActiveWindow ?: throw Exception("无法获取当前窗口")

            // 步骤2：查找并点击无线调试选项
            LogUtils.i("AdbAutoSetup", "步骤2: 查找并点击无线调试选项")
            if (!findAndClickWirelessDebuggingOption(root)) {
                LogUtils.e("AdbAutoSetup", "❌ 未能找到或点击无线调试选项，流程终止")
                return false
            }

            // 等待页面跳转
            LogUtils.i("AdbAutoSetup", "等待页面跳转到无线调试页面...")
            delay(2000)
            
            // 验证是否成功进入无线调试页面
            val newRoot = a11yService.safeActiveWindow ?: throw Exception("无法获取无线调试页面的窗口")
            if (!verifyEnteredWirelessDebuggingPage()) {
                LogUtils.e("AdbAutoSetup", "❌ 未能成功进入无线调试页面，流程终止")
                return false
            }
            
            LogUtils.i("AdbAutoSetup", "✅ 已成功进入无线调试页面")

            // 步骤3：查找并启用无线调试开关
            LogUtils.i("AdbAutoSetup", "步骤3: 查找并启用无线调试开关")
            if (!enableWirelessDebuggingSwitch(newRoot)) {
                LogUtils.w("AdbAutoSetup", "⚠️ 未能启用无线调试开关，可能已经启用或需要手动操作")
                // 即使开关启用失败，也继续尝试提取信息（可能已经启用）
            } else {
                LogUtils.i("AdbAutoSetup", "✅ 无线调试开关已启用")
            }

            // 步骤4：处理确认对话框
            LogUtils.i("AdbAutoSetup", "步骤4: 处理可能的确认对话框")
            handleConfirmationDialog()
            
            // 再次等待，确保ADB信息已显示
            delay(2000)

            LogUtils.i("AdbAutoSetup", "✅ 无线调试启用流程完成")
            true

        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "❌ 使用GKD规则启用无线调试失败", e)
            false
        }
    }

    /**
     * 滚动页面查找无线调试选项
     */
    private suspend fun scrollToFindWirelessDebugging(root: AccessibilityNodeInfo) {
        LogUtils.i("AdbAutoSetup", "开始滚动查找无线调试选项")

        val a11yService = A11yService.instance ?: return
        var currentRoot = root

        // 先输出当前页面信息，便于调试
        val initialText = getAllTextFromPage(currentRoot)
        LogUtils.i("AdbAutoSetup", "初始页面文本长度: ${initialText.length}, 预览: ${initialText.take(200)}...")

        // 先检查当前页面是否已有无线调试选项
        if (checkWirelessDebuggingExists(currentRoot)) {
            LogUtils.i("AdbAutoSetup", "✅ 当前页面已发现无线调试选项，无需滚动")
            return
        }

        LogUtils.i("AdbAutoSetup", "当前页面未发现无线调试选项，开始向下滚动查找...")

        // 向下滚动查找（增加滚动次数，ColorOS 15可能需要更多滚动）
        var scrollFailureCount = 0
        repeat(10) { scrollIndex ->
            LogUtils.i("AdbAutoSetup", "向下滚动查找无线调试: ${scrollIndex + 1}/10")

            // 检查节点是否仍然有效
            if (currentRoot == null) {
                LogUtils.w("AdbAutoSetup", "节点已失效，重新获取...")
                currentRoot = a11yService.safeActiveWindow ?: return
            }
            
            // 尝试滚动
            val scrolled = try {
                currentRoot.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            } catch (e: SecurityException) {
                LogUtils.e("AdbAutoSetup", "❌ 滚动操作权限错误: ${e.message}")
                scrollFailureCount++
                if (scrollFailureCount >= 3) {
                    LogUtils.e("AdbAutoSetup", "❌ 连续3次滚动失败，可能是权限问题，停止滚动")
                    return
                }
                false
            } catch (e: Exception) {
                LogUtils.e("AdbAutoSetup", "滚动操作异常: ${e.message}")
                false
            }
            
            if (!scrolled) {
                scrollFailureCount++
                LogUtils.w("AdbAutoSetup", "滚动返回false (失败次数: $scrollFailureCount/3)")
                
                if (scrollFailureCount >= 2) {
                    // 尝试使用手势滑动作为备用方案
                    LogUtils.i("AdbAutoSetup", "滚动操作失败，尝试使用手势滑动...")
                    swipeDownPage()
                    
                    // 重新获取根节点
                    currentRoot = a11yService.safeActiveWindow ?: return
                    
                    // 检查是否找到无线调试选项
                    if (checkWirelessDebuggingExists(currentRoot)) {
                        LogUtils.i("AdbAutoSetup", "✅ 使用手势滑动后找到无线调试选项")
                        return
                    }
                }
                
                if (scrollFailureCount >= 3) {
                    LogUtils.e("AdbAutoSetup", "❌ 连续3次滚动失败，可能是权限问题或已到达底部")
                    // 即使无法滚动，也检查一次当前页面
                    delay(800)
                    currentRoot = a11yService.safeActiveWindow ?: return
                    if (checkWirelessDebuggingExists(currentRoot)) {
                        LogUtils.i("AdbAutoSetup", "✅ 在页面底部找到无线调试选项")
                        return
                    }
                    // 如果到达底部还没找到，输出当前页面内容帮助调试
                    val bottomText = getAllTextFromPage(currentRoot)
                    LogUtils.w("AdbAutoSetup", "已滚动到底部，当前页面文本预览: ${bottomText.take(300)}...")
                    return
                }
                
                // 如果只是单次失败，继续下一次循环
                delay(500)
            } else {
                // 滚动成功，重置失败计数
                scrollFailureCount = 0

                delay(1200) // 等待滚动完成（增加延迟确保页面稳定）

                // 重新获取根节点（滚动后页面结构可能变化）
                currentRoot = a11yService.safeActiveWindow ?: return

                // 检查滚动后是否找到无线调试选项
                if (checkWirelessDebuggingExists(currentRoot)) {
                    LogUtils.i("AdbAutoSetup", "✅ 滚动后找到无线调试选项（第${scrollIndex + 1}次滚动）")
                    return
                }
                
                // 每3次滚动输出一次当前页面信息
                if ((scrollIndex + 1) % 3 == 0) {
                    val currentText = getAllTextFromPage(currentRoot)
                    LogUtils.d("AdbAutoSetup", "第${scrollIndex + 1}次滚动后，页面文本预览: ${currentText.take(200)}...")
                }
            }
        }

        // 滚动完成后，再次检查并输出最终页面内容
        currentRoot = a11yService.safeActiveWindow ?: return
        val finalText = getAllTextFromPage(currentRoot)
        LogUtils.w("AdbAutoSetup", "⚠️ 滚动完成后仍未找到无线调试选项")
        LogUtils.w("AdbAutoSetup", "最终页面文本长度: ${finalText.length}, 预览: ${finalText.take(400)}...")
        
        // 即使滚动失败，也尝试在当前页面查找（可能无线调试选项已经在页面中）
        if (checkWirelessDebuggingExists(currentRoot)) {
            LogUtils.i("AdbAutoSetup", "✅ 在最终页面中找到无线调试选项")
        } else {
            LogUtils.w("AdbAutoSetup", "继续尝试查找（可能无线调试选项在页面中但文本匹配失败）")
        }
    }
    
    /**
     * 使用手势滑动页面（备用方案，当滚动操作不可用时使用）
     */
    private suspend fun swipeDownPage() {
        try {
            val a11yService = A11yService.instance ?: return
            val service = a11yService as? android.accessibilityservice.AccessibilityService ?: return
            
            // 获取屏幕尺寸
            val screenWidth = li.songe.gkd.util.ScreenUtils.getScreenWidth()
            val screenHeight = li.songe.gkd.util.ScreenUtils.getScreenHeight()
            
            // 从屏幕中间向上滑动（模拟向下滚动）
            val startX = screenWidth / 2f
            val startY = screenHeight * 0.7f
            val endX = screenWidth / 2f
            val endY = screenHeight * 0.3f
            
            LogUtils.i("AdbAutoSetup", "使用手势滑动: ($startX, $startY) -> ($endX, $endY)")
            
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, 300
                    )
                )
                .build()
            
            val result = service.dispatchGesture(gesture, null, null)
            LogUtils.i("AdbAutoSetup", "手势滑动结果: $result")
            
            delay(800) // 等待滑动完成
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "手势滑动失败", e)
        }
    }

    /**
     * 检查当前页面是否有无线调试选项（彻底修复版 - 不使用GKD选择器）
     */
    private fun checkWirelessDebuggingExists(root: AccessibilityNodeInfo): Boolean {
        return try {
            LogUtils.d("AdbAutoSetup", "开始检查无线调试选项（简单文本搜索）")

            // 获取页面所有文本
            val allText = getAllTextFromPage(root)
            
            // 扩展关键词列表，包括更多可能的变体
            val keywords = listOf(
                "无线调试", "Wireless debugging", "WiFi调试", "WiFi debugging",
                "无线ADB", "Wireless ADB", "无线调试功能", "Wireless debugging feature",
                "无线调试开关", "无线调试设置", "无线调试选项"
            )

            // 直接通过文本搜索判断，不使用GKD选择器
            val hasWirelessDebugging = keywords.any { keyword ->
                allText.contains(keyword, ignoreCase = true)
            }

            if (hasWirelessDebugging) {
                // 找到匹配的关键词，输出详细信息
                val matchedKeyword = keywords.firstOrNull { allText.contains(it, ignoreCase = true) }
                LogUtils.i("AdbAutoSetup", "✅ 找到无线调试选项！匹配关键词: $matchedKeyword")
                LogUtils.d("AdbAutoSetup", "页面文本预览: ${allText.take(300)}...")
            } else {
                LogUtils.d("AdbAutoSetup", "未找到无线调试选项，页面文本预览: ${allText.take(200)}...")
            }

            hasWirelessDebugging

        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "检查无线调试选项时发生异常", e)
            false
        }
    }

    /**
     * 查找并点击无线调试选项（增强版：支持多种点击方式）
     */
    private suspend fun findAndClickWirelessDebuggingOption(root: AccessibilityNodeInfo): Boolean {
        try {
            LogUtils.i("AdbAutoSetup", "开始查找并点击无线调试选项（增强版）")
            
            // 扩展关键词列表，包括更多可能的变体
            val keywords = listOf(
                "无线调试", "Wireless debugging", "WiFi调试", "WiFi debugging", 
                "无线ADB", "Wireless ADB", "无线调试功能", "Wireless debugging feature",
                "无线调试开关", "无线调试设置", "无线调试选项"
            )
            
            // 先输出当前页面文本，便于调试
            val pageText = getAllTextFromPage(root)
            LogUtils.i("AdbAutoSetup", "当前页面文本长度: ${pageText.length}")
            LogUtils.d("AdbAutoSetup", "页面文本预览: ${pageText.take(400)}...")

            // 方法1：查找包含关键词的文本节点，然后点击其可点击的父节点
            val textNode = findNodeWithText(root, keywords)
            if (textNode != null) {
                val nodeText = textNode.text?.toString() ?: textNode.contentDescription?.toString() ?: "null"
                LogUtils.i("AdbAutoSetup", "✅ 找到了包含文本'$nodeText'的节点")

                // 尝试向上查找可点击的父节点
                var clickableParent: AccessibilityNodeInfo? = textNode
                var depth = 0
                while (clickableParent != null && !clickableParent.isClickable && depth < 5) {
                    clickableParent = clickableParent.parent
                    depth++
                }

                clickableParent?.let {
                    LogUtils.i("AdbAutoSetup", "找到了可点击的父节点（深度=$depth），尝试点击")
                    val clicked = it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        LogUtils.i("AdbAutoSetup", "🎯 成功点击无线调试选项（方法1），等待页面加载...")
                        delay(2500)
                        return true
                    } else {
                        LogUtils.w("AdbAutoSetup", "方法1：点击父节点失败，尝试其他方法")
                    }
                }

                // 如果父节点不可点击，尝试直接点击文本节点（某些UI可能文本节点本身可点击）
                if (textNode.isClickable) {
                    LogUtils.i("AdbAutoSetup", "文本节点本身可点击，尝试直接点击")
                    val clicked = textNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        LogUtils.i("AdbAutoSetup", "🎯 成功点击无线调试选项（方法1-直接），等待页面加载...")
                        delay(2500)
                        return true
                    }
                }
            } else {
                LogUtils.w("AdbAutoSetup", "在页面上未找到包含'无线调试'的文本节点")
            }

            // 方法2：尝试通过坐标点击（如果知道大概位置）
            LogUtils.d("AdbAutoSetup", "方法1失败，尝试通过坐标点击")
            val allText = getAllTextFromPage(root)
            if (allText.contains("无线调试") || allText.contains("Wireless debugging")) {
                LogUtils.w("AdbAutoSetup", "页面包含无线调试文本，但无法找到可点击节点，可能需要手动操作")
                // 可以尝试通过坐标点击，但这里先返回false，让用户知道需要手动操作
            }

            LogUtils.e("AdbAutoSetup", "❌ 所有方法都失败，无法点击无线调试选项")
            return false
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "查找并点击无线调试选项时发生异常", e)
            return false
        }
    }

    /**
     * 递归查找包含指定文本的节点（不要求可点击）
     */
    private fun findNodeWithText(root: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            try {
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
                if (keywords.any { keyword ->
                    nodeText.contains(keyword, ignoreCase = true) ||
                    nodeDesc.contains(keyword, ignoreCase = true)
                }) {
                    return node
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            } catch (e: Exception) {
                // 忽略单个节点的错误
            }
        }
        return null
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
     * 启用无线调试开关（重构版）
     * 递归查找页面上第一个可用的、未开启的开关并点击
     */
    private suspend fun enableWirelessDebuggingSwitch(root: AccessibilityNodeInfo): Boolean {
        LogUtils.i("AdbAutoSetup", "开始查找并启用无线调试开关（重构版）")

        // 先查找所有开关节点
        val allSwitches = findAllSwitchNodes(root)
        if (allSwitches.isEmpty()) {
            LogUtils.w("AdbAutoSetup", "❌ 在无线调试页面未找到任何开关节点")
            // 即使没有找到开关，也检查是否已启用（通过IP地址判断）
            val pageText = getAllTextFromPage(root)
            if (pageText.contains("IP地址") || pageText.contains("IP address") || 
                Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d{4,5}""").find(pageText) != null) {
                LogUtils.i("AdbAutoSetup", "✅ 检测到无线调试已启用（页面含IP信息）")
                return true
            }
            return false
        }

        LogUtils.i("AdbAutoSetup", "找到 ${allSwitches.size} 个开关节点，检查状态...")
        
        // 检查是否有已启用的开关
        val enabledSwitches = allSwitches.filter { it.isChecked }
        if (enabledSwitches.isNotEmpty()) {
            LogUtils.i("AdbAutoSetup", "✅ 检测到 ${enabledSwitches.size} 个已启用的开关，无线调试已开启")
            return true
        }

        // 如果没有已启用的开关，尝试启用第一个可点击的开关
        LogUtils.i("AdbAutoSetup", "未找到已启用的开关，尝试启用...")
        val result = tryEnableSwitches(allSwitches)
        
        if (result) {
            LogUtils.i("AdbAutoSetup", "✅ 成功启用无线调试开关")
            toast("✅ 无线调试开关已开启")
        } else {
            LogUtils.w("AdbAutoSetup", "⚠️ 未能启用无线调试开关")
        }
        
        return result
    }
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
     * 从页面文本中提取ADB信息（使用与按钮7相同的改进方法）
     */
    private fun extractAdbInfoFromText(root: AccessibilityNodeInfo): AdbInfo? {
        return try {
            val allText = getAllTextFromPage(root)
            LogUtils.i("AdbAutoSetup", "页面全部文本长度: ${allText.length}")
            LogUtils.d("AdbAutoSetup", "页面文本预览: ${allText.take(500)}...")

            // 方法1：标准的IP:端口格式匹配（支持多种分隔符）
            val ipPortPatterns = listOf(
                Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{4,5})"""),  // 标准格式
                Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s*[：:]\s*(\d{4,5})"""),  // 支持中文冒号
                Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s+(\d{4,5})"""),  // 空格分隔
            )
            
            for (pattern in ipPortPatterns) {
                val match = pattern.find(allText)
                if (match != null) {
                    val ip = match.groupValues[1]
                    val port = match.groupValues[2].toInt()
                    
                    // 验证IP和端口有效性
                    if (isValidIp(ip) && port in 1024..65535) {
                        LogUtils.i("AdbAutoSetup", "✅ 成功提取ADB信息: $ip:$port")
                        return AdbInfo(ip, port)
                    } else {
                        LogUtils.w("AdbAutoSetup", "提取的IP或端口无效: $ip:$port")
                    }
                }
            }
            
            // 方法2：分离IP和端口匹配
            LogUtils.w("AdbAutoSetup", "未找到标准IP:端口格式，尝试分离匹配...")
            val ipPattern = Regex("""\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\b""")
            val portPattern = Regex("""\b(\d{4,5})\b""")

            val foundIPs = ipPattern.findAll(allText).map { it.groupValues[1] }.filter { isValidIp(it) }.toList()
            val foundPorts = portPattern.findAll(allText).map { it.groupValues[1].toInt() }.filter { it in 1024..65535 }.toList()

            LogUtils.d("AdbAutoSetup", "找到IP列表: $foundIPs")
            LogUtils.d("AdbAutoSetup", "找到端口列表: $foundPorts")

            if (foundIPs.isNotEmpty() && foundPorts.isNotEmpty()) {
                val ip = foundIPs.first()
                val port = foundPorts.first()
                LogUtils.i("AdbAutoSetup", "✅ 通过分离匹配提取ADB信息: $ip:$port")
                return AdbInfo(ip, port)
            } else {
                LogUtils.w("AdbAutoSetup", "找到IP: ${foundIPs.joinToString()}, 端口: ${foundPorts.joinToString()}")
            }
            
            null
        } catch (e: Exception) {
            LogUtils.e("AdbAutoSetup", "提取ADB信息时发生异常", e)
            null
        }
    }
    
    /**
     * 验证IP地址是否有效
     */
    private fun isValidIp(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            parts.all { part ->
                val num = part.toInt()
                num in 0..255
            }
        } catch (e: Exception) {
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
