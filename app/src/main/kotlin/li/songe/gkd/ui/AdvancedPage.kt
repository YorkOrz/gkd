package li.songe.gkd.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import li.songe.gkd.MainActivity
import li.songe.gkd.permission.canDrawOverlaysState
import li.songe.gkd.permission.foregroundServiceSpecialUseState
import li.songe.gkd.permission.notificationState
import li.songe.gkd.permission.requiredPermission
import li.songe.gkd.permission.shizukuOkState
import li.songe.gkd.service.ButtonService
import li.songe.gkd.service.HttpService
import li.songe.gkd.service.RecordService
import li.songe.gkd.service.ScreenshotService
import li.songe.gkd.shizuku.shizukuContextFlow
import li.songe.gkd.shizuku.updateBinderMutex
import li.songe.gkd.store.storeFlow
import li.songe.gkd.ui.component.AuthCard
import li.songe.gkd.ui.component.PerfIcon
import li.songe.gkd.ui.component.PerfIconButton
import li.songe.gkd.ui.component.PerfTopAppBar
import li.songe.gkd.ui.component.SettingItem
import li.songe.gkd.ui.component.TextSwitch
import li.songe.gkd.ui.component.autoFocus
import li.songe.gkd.ui.component.updateDialogOptions
import li.songe.gkd.ui.share.LocalMainViewModel
import li.songe.gkd.service.A11yService
import li.songe.gkd.adb.DeveloperRules
import li.songe.gkd.adb.AdbInfoExtractor
import li.songe.gkd.a11y.a11yContext
import com.blankj.utilcode.util.LogUtils
import li.songe.selector.MatchOption
import li.songe.gkd.util.launchTry
import kotlinx.coroutines.delay
import li.songe.gkd.ui.share.asMutableState
import li.songe.gkd.ui.style.EmptyHeight
import li.songe.gkd.ui.style.ProfileTransitions
import li.songe.gkd.ui.style.itemPadding
import li.songe.gkd.ui.style.titleItemPadding
import li.songe.gkd.util.AndroidTarget
import li.songe.gkd.util.ShortUrlSet
import li.songe.gkd.util.launchAsFn
import li.songe.gkd.util.throttle
import li.songe.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AdvancedPage() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AdvancedVm>()
    val store by storeFlow.collectAsState()

    var showEditPortDlg by vm.showEditPortDlgFlow.asMutableState()
    if (showEditPortDlg) {
        val portRange = remember { 1000 to 65535 }
        val placeholderText = remember { "请输入 ${portRange.first}-${portRange.second} 的整数" }
        var value by remember {
            mutableStateOf(store.httpServerPort.toString())
        }
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text(text = "服务端口") },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = placeholderText)
                    },
                    onValueChange = {
                        value = it.filter { c -> c.isDigit() }.take(5)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = "${value.length} / 5",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                )
            },
            onDismissRequest = {
                showEditPortDlg = false
            },
            confirmButton = {
                TextButton(
                    enabled = value.isNotEmpty(),
                    onClick = {
                        val newPort = value.toIntOrNull()
                        if (newPort == null || !(portRange.first <= newPort && newPort <= portRange.second)) {
                            toast(placeholderText)
                            return@TextButton
                        }
                        showEditPortDlg = false
                        if (newPort != store.httpServerPort) {
                            storeFlow.value = store.copy(
                                httpServerPort = newPort
                            )
                            toast("更新成功")
                        }
                    }
                ) {
                    Text(
                        text = "确认", modifier = Modifier
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPortDlg = false }) {
                    Text(
                        text = "取消"
                    )
                }
            }
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = {
                        mainVm.popBackStack()
                    })
                },
                title = { Text(text = "高级设置") },
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .titleItemPadding(showTop = false),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier,
                    text = "Shizuku",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                val lineHeightDp = LocalDensity.current.run {
                    MaterialTheme.typography.titleSmall.lineHeight.toDp()
                }
                PerfIcon(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = throttle {
                            val c = shizukuContextFlow.value
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "授权状态",
                                text = arrayOf(
                                    "IUserService" to c.serviceWrapper,
                                    "IUserManager" to c.userManager,
                                    "IPackageManager" to c.packageManager,
                                    "IActivityManager" to c.activityManager,
                                    "IActivityTaskManager" to c.activityTaskManager,
                                ).joinToString("\n") { (name, state) ->
                                    name + " " + if (state != null) "✅" else "❎"
                                }
                            )
                        })
                        .size(lineHeightDp),
                    imageVector = PerfIcon.Api,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            val shizukuOk by shizukuOkState.stateFlow.collectAsState()
            if (!shizukuOk) {
                AuthCard(
                    title = "未授权",
                    subtitle = "点击授权以优化体验",
                    onAuthClick = {
                        mainVm.requestShizuku()
                    }
                )
            }
            TextSwitch(
                title = "启用优化",
                subtitle = "提升权限优化体验",
                suffix = "了解更多",
                suffixUnderline = true,
                onSuffixClick = { mainVm.navigateWebPage(ShortUrlSet.URL14) },
                checked = store.enableShizuku,
            ) {
                if (updateBinderMutex.mutex.isLocked) {
                    toast("正在连接中，请稍后")
                    return@TextSwitch
                }
                if (it && !shizukuOk) {
                    toast("未授权")
                }
                storeFlow.value = store.copy(enableShizuku = it)
            }

            val server by HttpService.httpServerFlow.collectAsState()
            val httpServerRunning = server != null
            val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsState()

            Text(
                text = "HTTP",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.itemPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HTTP服务",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium
                    ) {
                        Text(text = if (httpServerRunning) "点击链接打开即可自动连接" else "在浏览器下连接调试工具")
                        AnimatedVisibility(httpServerRunning) {
                            Column {
                                Row {
                                    val localUrl = "http://127.0.0.1:${store.httpServerPort}"
                                    Text(
                                        text = localUrl,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                        modifier = Modifier.clickable(onClick = throttle {
                                            mainVm.openUrl(localUrl)
                                        }),
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(text = "仅本设备可访问")
                                }
                                localNetworkIps.forEach { host ->
                                    val lanUrl = "http://${host}:${store.httpServerPort}"
                                    Text(
                                        text = lanUrl,
                                        color = MaterialTheme.colorScheme.primary,
                                        style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                        modifier = Modifier.clickable(onClick = throttle {
                                            mainVm.openUrl(lanUrl)
                                        })
                                    )
                                }
                            }
                        }
                    }
                }
                Switch(
                    checked = httpServerRunning,
                    onCheckedChange = throttle(fn = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, foregroundServiceSpecialUseState)
                            requiredPermission(context, notificationState)
                            HttpService.start()
                        } else {
                            HttpService.stop()
                        }
                    })
                )
            }

            SettingItem(
                title = "服务端口",
                subtitle = store.httpServerPort.toString(),
                imageVector = PerfIcon.Edit,
                onClick = {
                    showEditPortDlg = true
                }
            )

            TextSwitch(
                title = "清除订阅",
                subtitle = "服务关闭时，删除内存订阅",
                checked = store.autoClearMemorySubs
            ) {
                storeFlow.value = store.copy(
                    autoClearMemorySubs = it
                )
            }

            Text(
                text = "快照",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(
                title = "快照记录",
                subtitle = "应用界面节点信息及截图",
                onClick = {
                    mainVm.navigatePage(SnapshotPageDestination)
                }
            )

            if (!AndroidTarget.R) {
                val screenshotRunning by ScreenshotService.isRunning.collectAsState()
                TextSwitch(
                    title = "截屏服务",
                    subtitle = "生成快照需要获取屏幕截图",
                    checked = screenshotRunning,
                    onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, notificationState)
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val activityResult =
                                context.launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                            if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                                ScreenshotService.start(intent = activityResult.data!!)
                            }
                        } else {
                            ScreenshotService.stop()
                        }
                    }
                )
            }

            TextSwitch(
                title = "快照按钮",
                subtitle = "悬浮显示按钮点击保存快照",
                checked = ButtonService.isRunning.collectAsState().value,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, foregroundServiceSpecialUseState)
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        ButtonService.start()
                    } else {
                        ButtonService.stop()
                    }
                }
            )

            TextSwitch(
                title = "音量快照",
                subtitle = "音量变化时保存快照",
                checked = store.captureVolumeChange
            ) {
                storeFlow.value = store.copy(
                    captureVolumeChange = it
                )
            }

            TextSwitch(
                title = "截屏快照",
                subtitle = "截屏时保存快照",
                suffix = "查看限制",
                onSuffixClick = {
                    mainVm.dialogFlow.updateDialogOptions(
                        title = "限制说明",
                        text = "仅支持部分小米设备截屏触发\n\n只保存节点信息不保存图片，用户需要在快照记录里替换截图",
                    )
                },
                checked = store.captureScreenshot
            ) {
                storeFlow.value = store.copy(
                    captureScreenshot = it
                )
            }

            TextSwitch(
                title = "隐藏状态栏",
                subtitle = "隐藏快照截图状态栏",
                checked = store.hideSnapshotStatusBar
            ) {
                storeFlow.value = store.copy(
                    hideSnapshotStatusBar = it
                )
            }

            TextSwitch(
                title = "保存提示",
                subtitle = "提示「正在保存快照」",
                checked = store.showSaveSnapshotToast
            ) {
                storeFlow.value = store.copy(
                    showSaveSnapshotToast = it
                )
            }

            SettingItem(
                title = "Github Cookie",
                subtitle = "生成快照/日志链接",
                suffix = "获取教程",
                suffixUnderline = true,
                onSuffixClick = {
                    mainVm.navigateWebPage(ShortUrlSet.URL1)
                },
                imageVector = PerfIcon.Edit,
                onClick = {
                    mainVm.showEditCookieDlgFlow.value = true
                }
            )

            Text(
                text = "界面",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "界面记录",
                onClick = {
                    mainVm.navigatePage(ActivityLogPageDestination)
                }
            )
            TextSwitch(
                title = "记录界面",
                subtitle = "记录打开的应用及界面",
                checked = store.enableActivityLog
            ) {
                storeFlow.value = store.copy(
                    enableActivityLog = it
                )
            }
            TextSwitch(
                title = "记录服务",
                subtitle = "悬浮显示界面信息",
                checked = RecordService.isRunning.collectAsState().value,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, foregroundServiceSpecialUseState)
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        RecordService.start()
                    } else {
                        RecordService.stop()
                    }
                }
            )
            
            // ADB自动化测试功能 (简化版)
            Text(
                text = "ADB自动化测试",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            // 测试按钮组
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WiFi检测测试
                TextButton(
                    onClick = {
                        try {
                            val wifiMonitor = li.songe.gkd.adb.WifiMonitor()
                            val currentSSID = wifiMonitor.getCurrentSSID()
                            toast("当前WiFi: ${currentSSID ?: "未连接"}")
                        } catch (e: Exception) {
                            toast("WiFi测试失败: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1. 测试WiFi检测")
                }
                
                // 无障碍服务测试
                TextButton(
                    onClick = {
                        try {
                            val a11yService = li.songe.gkd.service.A11yService.instance
                            if (a11yService != null) {
                                val root = a11yService.safeActiveWindow
                                if (root != null) {
                                    toast("无障碍服务正常，可获取窗口信息")
                                } else {
                                    toast("无障碍服务已启用，但无法获取当前窗口")
                                }
                            } else {
                                toast("无障碍服务未启用或不可用")
                            }
                        } catch (e: Exception) {
                            toast("无障碍测试失败: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("2. 测试无障碍服务")
                }
                
                // 设置应用导航测试
                TextButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            toast("已打开设置应用，请手动返回GKD")
                        } catch (e: Exception) {
                            toast("打开设置失败: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("3. 测试打开设置应用")
                }
                
                // ADB信息提取测试 - 简化版
                TextButton(
                    onClick = {
                        try {
                            toast("开始简化测试...")
                            
                            // 最简单的测试：只检查服务可用性
                            val a11yService = li.songe.gkd.service.A11yService.instance
                            if (a11yService == null) {
                                toast("无障碍服务不可用")
                                return@TextButton
                            }
                            
                            val root = a11yService.safeActiveWindow
                            if (root == null) {
                                toast("无法获取当前窗口")
                                return@TextButton
                            }
                            
                            toast("基础检查通过，窗口获取成功")
                            
                            // 延迟后尝试最简单的文本提取
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                try {
                                    val packageName = root.packageName?.toString() ?: "unknown"
                                    val className = root.className?.toString() ?: "unknown"
                                    toast("当前应用: $packageName, 类名: $className")
                                } catch (e: Exception) {
                                    toast("简化测试也失败: ${e.message}")
                                }
                            }, 1000)
                            
                        } catch (e: Exception) {
                            toast("测试异常: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("4. 简化ADB测试")
                }
                
                // 完整自动化测试（ColorOS 15增强版）
                Button(
                    onClick = {
                        try {
                            toast("⚠️ ColorOS 15完整自动化将在3秒后启动")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                vm.viewModelScope.launchTry {
                                    toast("启动ColorOS 15完整自动化，请观察设备操作...")

                                    val autoSetup = li.songe.gkd.adb.AdbAutoSetup()
                                    val success = autoSetup.triggerColorOS15FullAutomation()

                                    if (success) {
                                        toast("✅ ColorOS 15自动化完成！")
                                        // 提取最后成功的ADB信息
                                        val lastAdbInfo = autoSetup.lastAdbInfo.value
                                        if (lastAdbInfo != null) {
                                            toast("ADB信息: $lastAdbInfo")
                                        }
                                    } else {
                                        toast("❌ ColorOS 15自动化失败，请查看logcat")
                                    }
                                }
                            }, 3000)
                        } catch (e: Exception) {
                            toast("自动化启动失败: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("5. ColorOS 15完整自动化 🚀")
                }
                
                // 调试当前页面按钮
                Button(
                    onClick = {
                        vm.viewModelScope.launchTry {
                            toast("开始调试当前页面...")
                            debugCurrentPageNodes()
                            toast("调试完成，请查看logcat")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("6. 调试当前页面节点 🔍")
                }
                
                // ADB信息提取测试（改进版，包含详细日志）
                Button(
                    onClick = {
                        vm.viewModelScope.launchTry {
                            toast("3秒后开始提取ADB信息，请确保已在无线调试页面...")
                            delay(3000) // 等待3秒
                            toast("开始提取ADB信息...")

                            try {
                                Log.i("AdbExtract", "===== 开始ADB信息提取 =====")
                                LogUtils.i("AdbExtract", "===== 开始ADB信息提取 =====")
                                
                                val a11yService = A11yService.instance
                                if (a11yService == null) {
                                    toast("❌ 无障碍服务不可用")
                                    Log.e("AdbExtract", "无障碍服务不可用")
                                    LogUtils.e("AdbExtract", "无障碍服务不可用")
                                    return@launchTry
                                }
                                Log.i("AdbExtract", "✅ 无障碍服务可用")
                                LogUtils.i("AdbExtract", "✅ 无障碍服务可用")

                                // 尝试多次获取窗口，确保获取到正确的页面
                                var root: AccessibilityNodeInfo? = null
                                var retryCount = 0
                                while (root == null && retryCount < 5) {
                                    root = a11yService.safeActiveWindow
                                    if (root == null) {
                                        retryCount++
                                        Log.w("AdbExtract", "无法获取当前窗口，重试 $retryCount/5")
                                        delay(500)
                                    }
                                }
                                
                                if (root == null) {
                                    toast("❌ 无法获取当前窗口，请确保已在无线调试页面")
                                    Log.e("AdbExtract", "无法获取当前窗口")
                                    LogUtils.e("AdbExtract", "无法获取当前窗口")
                                    return@launchTry
                                }
                                
                                Log.i("AdbExtract", "✅ 成功获取当前窗口，包名: ${root.packageName}")
                                LogUtils.i("AdbExtract", "✅ 成功获取当前窗口，包名: ${root.packageName}")
                                
                                // 验证是否在设置页面
                                if (root.packageName != "com.android.settings") {
                                    toast("⚠️ 当前不在设置页面，包名: ${root.packageName}，请切换到无线调试页面")
                                    Log.w("AdbExtract", "⚠️ 当前不在设置页面，包名: ${root.packageName}")
                                    LogUtils.w("AdbExtract", "⚠️ 当前不在设置页面，包名: ${root.packageName}")
                                }

                                // 使用改进的文本提取方法
                                val allText = getAllTextFromPage(root)
                                
                                // 验证页面是否包含无线调试相关内容
                                if (!allText.contains("无线调试") && !allText.contains("IP") && !allText.contains("端口")) {
                                    toast("⚠️ 页面可能不是无线调试页面，请确保已在无线调试页面")
                                    Log.w("AdbExtract", "⚠️ 页面可能不是无线调试页面，提取的文本: ${allText.take(200)}")
                                    LogUtils.w("AdbExtract", "⚠️ 页面可能不是无线调试页面，提取的文本: ${allText.take(200)}")
                                }
                                Log.i("AdbExtract", "页面文本提取完成，长度: ${allText.length}")
                                LogUtils.i("AdbExtract", "页面文本提取完成，长度: ${allText.length}")

                                // 方法1：标准的IP:端口格式匹配（支持多种分隔符）
                                Log.i("AdbExtract", "开始方法1：标准IP:端口格式匹配...")
                                LogUtils.i("AdbExtract", "开始方法1：标准IP:端口格式匹配...")
                                val ipPortPatterns = listOf(
                                    Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{4,5})"""),  // 标准格式
                                    Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s*[：:]\s*(\d{4,5})"""),  // 支持中文冒号
                                    Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\s+(\d{4,5})"""),  // 空格分隔
                                )
                                
                                var matchFound = false
                                for ((index, pattern) in ipPortPatterns.withIndex()) {
                                    Log.d("AdbExtract", "尝试模式 ${index + 1}/${ipPortPatterns.size}")
                                    LogUtils.d("AdbExtract", "尝试模式 ${index + 1}/${ipPortPatterns.size}")
                                    val match = pattern.find(allText)
                                    if (match != null) {
                                        val ip = match.groupValues[1]
                                        val port = match.groupValues[2].toInt()
                                        Log.d("AdbExtract", "模式 ${index + 1} 匹配到: $ip:$port")
                                        LogUtils.d("AdbExtract", "模式 ${index + 1} 匹配到: $ip:$port")
                                        
                                        // 验证IP和端口有效性
                                        if (isValidIp(ip) && port in 1024..65535) {
                                            toast("✅ 提取成功: $ip:$port")
                                            Log.i("AdbExtract", "✅✅✅ ADB信息提取成功: $ip:$port")
                                            LogUtils.i("AdbExtract", "✅✅✅ ADB信息提取成功: $ip:$port")
                                            matchFound = true
                                            break
                                        } else {
                                            Log.w("AdbExtract", "模式 ${index + 1} 匹配的IP或端口无效: $ip:$port")
                                            LogUtils.w("AdbExtract", "模式 ${index + 1} 匹配的IP或端口无效: $ip:$port")
                                        }
                                    } else {
                                        Log.d("AdbExtract", "模式 ${index + 1} 未匹配")
                                        LogUtils.d("AdbExtract", "模式 ${index + 1} 未匹配")
                                    }
                                }

                                if (!matchFound) {
                                    Log.w("AdbExtract", "方法1失败，开始方法2：分离IP和端口匹配...")
                                    LogUtils.w("AdbExtract", "方法1失败，开始方法2：分离IP和端口匹配...")
                                    
                                    // 方法2：分离IP和端口匹配
                                    val ipPattern = Regex("""\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\b""")
                                    val portPattern = Regex("""\b(\d{4,5})\b""")

                                    val allIPs = ipPattern.findAll(allText).map { it.groupValues[1] }.toList()
                                    val allPorts = portPattern.findAll(allText).map { it.groupValues[1].toInt() }.toList()
                                    
                                    Log.d("AdbExtract", "找到所有IP候选: $allIPs")
                                    Log.d("AdbExtract", "找到所有端口候选: $allPorts")
                                    LogUtils.d("AdbExtract", "找到所有IP候选: $allIPs")
                                    LogUtils.d("AdbExtract", "找到所有端口候选: $allPorts")

                                    val foundIPs = allIPs.filter { isValidIp(it) }
                                    val foundPorts = allPorts.filter { it in 1024..65535 }

                                    Log.i("AdbExtract", "验证后IP列表: $foundIPs")
                                    Log.i("AdbExtract", "验证后端口列表: $foundPorts")
                                    LogUtils.i("AdbExtract", "验证后IP列表: $foundIPs")
                                    LogUtils.i("AdbExtract", "验证后端口列表: $foundPorts")

                                    if (foundIPs.isNotEmpty() && foundPorts.isNotEmpty()) {
                                        val ip = foundIPs.first()
                                        val port = foundPorts.first()
                                        toast("✅ 提取成功: $ip:$port")
                                        Log.i("AdbExtract", "✅✅✅ 通过分离匹配提取成功: $ip:$port")
                                        LogUtils.i("AdbExtract", "✅✅✅ 通过分离匹配提取成功: $ip:$port")
                                    } else if (foundIPs.isNotEmpty() || foundPorts.isNotEmpty()) {
                                        toast("🔍 找到IP: ${foundIPs.joinToString()} 端口: ${foundPorts.joinToString()}")
                                        Log.w("AdbExtract", "找到分离的IP: $foundIPs, 端口: $foundPorts，但无法配对")
                                        LogUtils.w("AdbExtract", "找到分离的IP: $foundIPs, 端口: $foundPorts，但无法配对")
                                    } else {
                                        toast("❌ 未找到IP或端口")
                                        Log.e("AdbExtract", "❌ 未找到有效的IP或端口")
                                        Log.e("AdbExtract", "页面文本前1000字符: ${allText.take(1000)}")
                                        Log.e("AdbExtract", "页面文本后1000字符: ${allText.takeLast(1000)}")
                                        LogUtils.e("AdbExtract", "❌ 未找到有效的IP或端口")
                                        LogUtils.e("AdbExtract", "页面文本前1000字符: ${allText.take(1000)}")
                                        LogUtils.e("AdbExtract", "页面文本后1000字符: ${allText.takeLast(1000)}")
                                    }
                                }
                                
                                Log.i("AdbExtract", "===== ADB信息提取结束 =====")
                                LogUtils.i("AdbExtract", "===== ADB信息提取结束 =====")

                            } catch (e: Exception) {
                                val errorMsg = e.message ?: e.javaClass.simpleName
                                toast("❌ 提取失败: $errorMsg")
                                Log.e("AdbExtract", "ADB信息提取失败: $errorMsg", e)
                                LogUtils.e("AdbExtract", "ADB信息提取失败: $errorMsg", e)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("7. ADB信息提取 ⏰ (详细日志版)")
                }
            }
            
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

// 验证IP地址是否有效
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

// 改进的文本提取函数（更全面地收集文本，包含详细日志）
private fun getAllTextFromPage(root: AccessibilityNodeInfo): String {
    val textBuilder = StringBuilder()
    val collectedTexts = mutableSetOf<String>() // 用于去重
    var nodeCount = 0
    var textNodeCount = 0
    var descNodeCount = 0
    
    fun collectText(node: AccessibilityNodeInfo, depth: Int = 0) {
        if (depth > 30) return // 防止过深递归，增加到30层
        
        nodeCount++
        
        try {
            // 添加当前节点的文本
            node.text?.toString()?.trim()?.let { text ->
                if (text.isNotEmpty()) {
                    textNodeCount++
                    if (collectedTexts.add(text)) {
                        textBuilder.append(text).append(" ")
                        // 如果包含IP或端口格式，记录日志
                        if (text.contains(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")) || 
                            text.contains(Regex("""\d{4,5}"""))) {
                            Log.d("getAllTextFromPage", "发现包含IP/端口的文本节点: '$text'")
                            LogUtils.d("getAllTextFromPage", "发现包含IP/端口的文本节点: '$text'")
                        }
                    }
                }
            }
            
            // 添加内容描述
            node.contentDescription?.toString()?.trim()?.let { desc ->
                if (desc.isNotEmpty()) {
                    descNodeCount++
                    if (collectedTexts.add(desc)) {
                        textBuilder.append(desc).append(" ")
                        // 如果包含IP或端口格式，记录日志
                        if (desc.contains(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")) || 
                            desc.contains(Regex("""\d{4,5}"""))) {
                            Log.d("getAllTextFromPage", "发现包含IP/端口的内容描述: '$desc'")
                            LogUtils.d("getAllTextFromPage", "发现包含IP/端口的内容描述: '$desc'")
                        }
                    }
                }
            }
            
            // 递归处理子节点
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        collectText(child, depth + 1)
                    }
                } catch (e: Exception) {
                    // 忽略单个子节点的错误
                }
            }
        } catch (e: Exception) {
            // 忽略单个节点的错误
        }
    }
    
    Log.i("getAllTextFromPage", "开始提取页面文本...")
    LogUtils.i("getAllTextFromPage", "开始提取页面文本...")
    collectText(root)
    val result = textBuilder.toString()
    
    Log.i("getAllTextFromPage", "文本提取完成:")
    Log.i("getAllTextFromPage", "  - 遍历节点数: $nodeCount")
    Log.i("getAllTextFromPage", "  - 文本节点数: $textNodeCount")
    Log.i("getAllTextFromPage", "  - 描述节点数: $descNodeCount")
    Log.i("getAllTextFromPage", "  - 去重后文本数量: ${collectedTexts.size}")
    Log.i("getAllTextFromPage", "  - 提取的文本总长度: ${result.length}")
    Log.d("getAllTextFromPage", "  - 文本预览(前500字符): ${result.take(500)}")
    Log.d("getAllTextFromPage", "  - 文本预览(后500字符): ${result.takeLast(500)}")
    LogUtils.i("getAllTextFromPage", "文本提取完成:")
    LogUtils.i("getAllTextFromPage", "  - 遍历节点数: $nodeCount")
    LogUtils.i("getAllTextFromPage", "  - 文本节点数: $textNodeCount")
    LogUtils.i("getAllTextFromPage", "  - 描述节点数: $descNodeCount")
    LogUtils.i("getAllTextFromPage", "  - 去重后文本数量: ${collectedTexts.size}")
    LogUtils.i("getAllTextFromPage", "  - 提取的文本总长度: ${result.length}")
    LogUtils.d("getAllTextFromPage", "  - 文本预览(前500字符): ${result.take(500)}")
    LogUtils.d("getAllTextFromPage", "  - 文本预览(后500字符): ${result.takeLast(500)}")
    
    return result
}

// 调试工具函数
private fun debugCurrentPageNodes() {
    try {
        val a11yService = A11yService.instance
        if (a11yService == null) {
            LogUtils.w("PageDebug", "无障碍服务不可用")
            return
        }
        
        val root = a11yService.safeActiveWindow
        if (root == null) {
            LogUtils.w("PageDebug", "无法获取当前窗口")
            return
        }
        
        LogUtils.d("PageDebug", "===== 开始调试当前页面 =====")
        LogUtils.d("PageDebug", "窗口包名: ${root.packageName}")
        
        // 递归遍历所有节点，输出关键信息
        fun traverseNodes(node: AccessibilityNodeInfo, depth: Int = 0) {
            try {
                val indent = "  ".repeat(depth)
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val className = node.className?.toString() ?: ""
                val isClickable = node.isClickable
                val isScrollable = node.isScrollable
                
                // 只输出有意义的节点
                if (isClickable && (text.isNotEmpty() || desc.isNotEmpty())) {
                    LogUtils.d("PageDebug", "$indent[CLICKABLE] '$text' | '$desc' | $className")
                }
                
                // 输出可滚动节点
                if (isScrollable) {
                    LogUtils.d("PageDebug", "$indent[SCROLLABLE] '$text' | '$desc' | $className")
                }
                
                // 输出包含"系统"、"更新"、"开发"、"设置"关键词的节点
                val keyWords = listOf("系统", "更新", "开发", "设置", "System", "Update", "Developer", "Settings")
                if (keyWords.any { keyword -> 
                    text.contains(keyword, ignoreCase = true) || desc.contains(keyword, ignoreCase = true) 
                }) {
                    LogUtils.d("PageDebug", "$indent[KEYWORD] '$text' | '$desc' | $className | clickable=$isClickable")
                }
                
                // 递归遍历子节点（最多3层深度，避免过多输出）
                if (depth < 3) {
                    for (i in 0 until node.childCount) {
                        node.getChild(i)?.let { child ->
                            traverseNodes(child, depth + 1)
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtils.w("PageDebug", "遍历节点失败: ${e.message}")
            }
        }
        
        traverseNodes(root)
        
        // 测试我们的选择器
        LogUtils.d("PageDebug", "===== 测试系统与更新选择器 =====")
        for ((index, selector) in DeveloperRules.systemUpdateSelectors.withIndex()) {
            try {
                val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (node != null) {
                    LogUtils.d("PageDebug", "系统与更新选择器 ${index + 1} ✓ 找到: '${node.text}' | '${node.contentDescription}'")
                } else {
                    LogUtils.d("PageDebug", "系统与更新选择器 ${index + 1} ✗ 未找到")
                }
            } catch (e: Exception) {
                LogUtils.w("PageDebug", "系统与更新选择器 ${index + 1} 异常: ${e.message}")
            }
        }
        
        LogUtils.d("PageDebug", "===== 测试开发者选项选择器 =====")
        for ((index, selector) in DeveloperRules.developerOptionsSelectors.withIndex()) {
            try {
                val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                if (node != null) {
                    LogUtils.d("PageDebug", "开发者选项选择器 ${index + 1} ✓ 找到: '${node.text}' | '${node.contentDescription}'")
                } else {
                    LogUtils.d("PageDebug", "开发者选项选择器 ${index + 1} ✗ 未找到")
                }
            } catch (e: Exception) {
                LogUtils.w("PageDebug", "开发者选项选择器 ${index + 1} 异常: ${e.message}")
            }
        }
        
        LogUtils.d("PageDebug", "===== 调试完成 =====")
        
    } catch (e: Exception) {
        LogUtils.e("PageDebug", "页面调试失败", e)
    }
}
