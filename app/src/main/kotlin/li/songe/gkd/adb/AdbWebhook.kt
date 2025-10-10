package li.songe.gkd.adb

import com.blankj.utilcode.util.LogUtils
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import li.songe.gkd.util.client
import li.songe.gkd.util.json

/**
 * Webhook数据格式
 */
@Serializable
data class AdbWebhookData(
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val adbInfo: AdbConnectionInfo,
    val wifiInfo: WifiConnectionInfo
)

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val appVersion: String
)

@Serializable
data class AdbConnectionInfo(
    val ip: String,
    val port: Int,
    val connectionString: String
)

@Serializable
data class WifiConnectionInfo(
    val ssid: String,
    val bssid: String? = null,
    val signalStrength: Int? = null
)

/**
 * ADB Webhook发送器
 * 负责将ADB连接信息发送到指定的Webhook URL
 */
class AdbWebhook {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 发送ADB信息到Webhook
     * @param webhookUrl Webhook URL
     * @param adbInfo ADB连接信息
     * @param wifiSSID 当前WiFi SSID
     */
    fun sendAdbInfo(
        webhookUrl: String, 
        adbInfo: AdbInfo, 
        wifiSSID: String? = null
    ) {
        if (webhookUrl.isEmpty()) {
            LogUtils.w("AdbWebhook", "Webhook URL为空，跳过发送")
            return
        }
        
        scope.launch {
            try {
                val webhookData = createWebhookData(adbInfo, wifiSSID)
                sendWebhookRequest(webhookUrl, webhookData)
            } catch (e: Exception) {
                LogUtils.e("AdbWebhook", "发送Webhook失败", e)
            }
        }
    }
    
    /**
     * 创建Webhook数据
     */
    private fun createWebhookData(adbInfo: AdbInfo, wifiSSID: String?): AdbWebhookData {
        return AdbWebhookData(
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo(),
            adbInfo = AdbConnectionInfo(
                ip = adbInfo.ip,
                port = adbInfo.port,
                connectionString = "adb connect ${adbInfo.ip}:${adbInfo.port}"
            ),
            wifiInfo = WifiConnectionInfo(
                ssid = wifiSSID ?: "Unknown",
                bssid = null, // 可以后续扩展
                signalStrength = null // 可以后续扩展
            )
        )
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            deviceId = getDeviceId(),
            model = android.os.Build.MODEL,
            manufacturer = android.os.Build.MANUFACTURER,
            androidVersion = android.os.Build.VERSION.RELEASE,
            appVersion = getAppVersion()
        )
    }
    
    /**
     * 获取设备ID
     */
    private fun getDeviceId(): String {
        return try {
            android.provider.Settings.Secure.getString(
                li.songe.gkd.app.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
        } catch (e: Exception) {
            LogUtils.w("AdbWebhook", "获取设备ID失败", e)
            "unknown"
        }
    }
    
    /**
     * 获取应用版本
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = li.songe.gkd.app.packageManager.getPackageInfo(
                li.songe.gkd.app.packageName, 0
            )
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            LogUtils.w("AdbWebhook", "获取应用版本失败", e)
            "unknown"
        }
    }
    
    /**
     * 发送Webhook请求
     */
    private suspend fun sendWebhookRequest(url: String, data: AdbWebhookData) {
        try {
            LogUtils.i("AdbWebhook", "开始发送Webhook到: $url")
            
            val jsonData = json.encodeToString(data)
            LogUtils.d("AdbWebhook", "Webhook数据: $jsonData")
            
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(jsonData)
            }
            
            if (response.status.isSuccess()) {
                LogUtils.i("AdbWebhook", "Webhook发送成功: ${response.status}")
            } else {
                LogUtils.w("AdbWebhook", "Webhook发送失败: ${response.status}")
            }
            
        } catch (e: Exception) {
            LogUtils.e("AdbWebhook", "Webhook请求异常", e)
            throw e
        }
    }
    
    /**
     * 测试Webhook连接
     * @param webhookUrl Webhook URL
     * @return 测试结果
     */
    suspend fun testWebhook(webhookUrl: String): WebhookTestResult {
        return try {
            val testData = createTestWebhookData()
            val response: HttpResponse = client.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(testData))
            }
            
            WebhookTestResult(
                success = response.status.isSuccess(),
                statusCode = response.status.value,
                message = if (response.status.isSuccess()) "连接成功" else "HTTP ${response.status.value}"
            )
            
        } catch (e: Exception) {
            LogUtils.e("AdbWebhook", "Webhook测试失败", e)
            WebhookTestResult(
                success = false,
                statusCode = 0,
                message = e.message ?: "未知错误"
            )
        }
    }
    
    /**
     * 创建测试用的Webhook数据
     */
    private fun createTestWebhookData(): AdbWebhookData {
        return AdbWebhookData(
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo(),
            adbInfo = AdbConnectionInfo(
                ip = "192.168.1.100",
                port = 5555,
                connectionString = "adb connect 192.168.1.100:5555"
            ),
            wifiInfo = WifiConnectionInfo(
                ssid = "Test-WiFi",
                bssid = "00:00:00:00:00:00",
                signalStrength = -50
            )
        )
    }
    
    /**
     * 发送简单的通知Webhook（仅包含基本信息）
     */
    fun sendSimpleNotification(
        webhookUrl: String,
        title: String,
        message: String,
        adbInfo: AdbInfo? = null
    ) {
        if (webhookUrl.isEmpty()) return
        
        scope.launch {
            try {
                val simpleData = mapOf(
                    "timestamp" to System.currentTimeMillis(),
                    "title" to title,
                    "message" to message,
                    "device" to android.os.Build.MODEL,
                    "adb_info" to (adbInfo?.toString() ?: ""),
                    "adb_command" to (adbInfo?.let { "adb connect ${it.ip}:${it.port}" } ?: "")
                )
                
                val response: HttpResponse = client.post(webhookUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(simpleData))
                }
                
                if (response.status.isSuccess()) {
                    LogUtils.i("AdbWebhook", "简单通知发送成功")
                } else {
                    LogUtils.w("AdbWebhook", "简单通知发送失败: ${response.status}")
                }
                
            } catch (e: Exception) {
                LogUtils.e("AdbWebhook", "发送简单通知失败", e)
            }
        }
    }
}

/**
 * Webhook测试结果
 */
data class WebhookTestResult(
    val success: Boolean,
    val statusCode: Int,
    val message: String
)

/**
 * Webhook配置
 */
@Serializable
data class WebhookConfig(
    val url: String = "",
    val enabled: Boolean = false,
    val retryCount: Int = 3,
    val timeoutSeconds: Int = 30,
    val includeDeviceInfo: Boolean = true,
    val format: WebhookFormat = WebhookFormat.FULL
)

/**
 * Webhook数据格式
 */
@Serializable
enum class WebhookFormat {
    FULL,    // 完整数据格式
    SIMPLE   // 简单数据格式
}