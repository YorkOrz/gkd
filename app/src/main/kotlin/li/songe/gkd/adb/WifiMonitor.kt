package li.songe.gkd.adb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import li.songe.gkd.app
import com.blankj.utilcode.util.LogUtils

/**
 * WiFi连接监听器
 * 监听WiFi连接状态变化，当连接到目标WiFi时触发ADB自动化
 */
class WifiMonitor {
    val connectedSSID = MutableStateFlow<String?>(null)
    val isTargetWifiConnected = MutableStateFlow(false)
    
    private var targetSSID: String = ""
    private var isRegistered = false
    
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    LogUtils.d("WifiMonitor", "WiFi网络状态变化")
                    checkWifiConnection()
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    LogUtils.d("WifiMonitor", "WiFi状态变化")
                    checkWifiConnection()
                }
            }
        }
    }
    
    /**
     * 开始监听WiFi连接
     * @param targetSSID 目标WiFi SSID
     */
    fun startMonitoring(targetSSID: String) {
        if (targetSSID.isEmpty()) {
            LogUtils.w("WifiMonitor", "目标SSID为空，无法开始监听")
            return
        }
        
        this.targetSSID = targetSSID.removeSurrounding("\"") // 移除可能的引号
        
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            
            try {
                ContextCompat.registerReceiver(
                    app,
                    wifiReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isRegistered = true
                LogUtils.i("WifiMonitor", "开始监听WiFi连接，目标SSID: $targetSSID")
            } catch (e: Exception) {
                LogUtils.e("WifiMonitor", "注册WiFi监听器失败", e)
            }
        }
        
        // 立即检查当前连接状态
        checkWifiConnection()
    }
    
    /**
     * 停止监听WiFi连接
     */
    fun stopMonitoring() {
        if (isRegistered) {
            try {
                app.unregisterReceiver(wifiReceiver)
                isRegistered = false
                LogUtils.i("WifiMonitor", "停止监听WiFi连接")
            } catch (e: Exception) {
                LogUtils.w("WifiMonitor", "注销WiFi监听器失败", e)
            }
        }
        
        connectedSSID.value = null
        isTargetWifiConnected.value = false
    }
    
    /**
     * 检查当前WiFi连接状态
     */
    private fun checkWifiConnection() {
        try {
            val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager == null) {
                LogUtils.e("WifiMonitor", "无法获取WiFi管理器")
                return
            }
            
            // 检查位置权限（Android 10+需要）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasLocationPermission = ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || 
                ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!hasLocationPermission) {
                    LogUtils.w("WifiMonitor", "缺少位置权限，无法获取WiFi SSID")
                    connectedSSID.value = "<需要位置权限>"
                    isTargetWifiConnected.value = false
                    return
                }
            }
            
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo == null) {
                LogUtils.d("WifiMonitor", "WiFi信息为空")
                connectedSSID.value = null
                isTargetWifiConnected.value = false
                return
            }
            
            val currentSSID = wifiInfo.ssid?.removeSurrounding("\"")?.takeIf { 
                it != "<unknown ssid>" && it.isNotEmpty() 
            }
            
            LogUtils.d("WifiMonitor", "当前连接的WiFi: $currentSSID, 目标WiFi: $targetSSID")
            
            connectedSSID.value = currentSSID
            val isTarget = currentSSID != null && currentSSID == targetSSID
            
            if (isTarget != isTargetWifiConnected.value) {
                isTargetWifiConnected.value = isTarget
                if (isTarget) {
                    LogUtils.i("WifiMonitor", "已连接到目标WiFi: $targetSSID")
                } else {
                    LogUtils.d("WifiMonitor", "未连接到目标WiFi")
                }
            }
            
        } catch (e: Exception) {
            LogUtils.e("WifiMonitor", "检查WiFi连接状态失败", e)
        }
    }
    
    /**
     * 获取当前连接的WiFi SSID
     */
    fun getCurrentSSID(): String? {
        return try {
            // 检查位置权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasLocationPermission = ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || 
                ContextCompat.checkSelfPermission(
                    app, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                
                if (!hasLocationPermission) {
                    return "<需要位置权限>"
                }
            }
            
            val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ssid = wifiManager?.connectionInfo?.ssid?.removeSurrounding("\"")
            
            // 过滤掉无效的SSID
            if (ssid == "<unknown ssid>" || ssid.isNullOrEmpty()) {
                null
            } else {
                ssid
            }
        } catch (e: Exception) {
            LogUtils.e("WifiMonitor", "获取当前SSID失败", e)
            null
        }
    }
    
    /**
     * 检查WiFi是否已启用
     */
    fun isWifiEnabled(): Boolean {
        return try {
            val wifiManager = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            LogUtils.e("WifiMonitor", "检查WiFi状态失败", e)
            false
        }
    }
}