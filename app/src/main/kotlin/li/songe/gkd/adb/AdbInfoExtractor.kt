package li.songe.gkd.adb

import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.a11y.a11yContext
import li.songe.gkd.service.A11yService
import li.songe.selector.MatchOption

/**
 * ADB信息数据类
 */
data class AdbInfo(
    val ip: String,
    val port: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "$ip:$port"
    }
    
    fun isValid(): Boolean {
        return ip.isNotEmpty() && port in 1024..65535
    }
}

/**
 * ADB信息提取器
 * 从无线调试界面提取IP地址和端口号
 */
class AdbInfoExtractor {
    
    /**
     * 提取ADB连接信息 - ColorOS 15兼容版
     * @return AdbInfo对象，如果提取失败返回null
     */
    fun extractAdbInfo(): AdbInfo? {
        return try {
            val a11yService = A11yService.instance
            if (a11yService == null) {
                LogUtils.w("AdbInfoExtractor", "无障碍服务不可用")
                return null
            }

            val root = a11yService.safeActiveWindow
            if (root == null) {
                LogUtils.w("AdbInfoExtractor", "无法获取当前窗口")
                return null
            }

            // 记录当前应用信息
            val packageName = getCurrentPackageName()
            LogUtils.d("AdbInfoExtractor", "当前应用: $packageName")
            LogUtils.d("AdbInfoExtractor", "开始提取ADB信息 (ColorOS 15兼容版)")

            // 首先检查是否在正确的页面
            if (!isInDeveloperOptions(root)) {
                LogUtils.w("AdbInfoExtractor", "当前可能不在开发者选项页面")
            }

            // 优先使用简单文本提取方法（已在ColorOS 15验证有效）
            extractFromSimpleText(root)?.let {
                LogUtils.i("AdbInfoExtractor", "简单文本提取成功: $it")
                return it
            }

            // 后备方法：全文本搜索
            extractFromAllText(root)?.let {
                LogUtils.i("AdbInfoExtractor", "全文本匹配成功: $it")
                return it
            }

            LogUtils.w("AdbInfoExtractor", "未能提取到ADB信息")
            // 记录页面信息以供调试
            dumpPageInfo(root)
            null

        } catch (e: Exception) {
            LogUtils.e("AdbInfoExtractor", "提取ADB信息时发生异常", e)
            null
        }
    }

    /**
     * 简单文本提取方法（ColorOS 15验证有效）
     */
    private fun extractFromSimpleText(root: AccessibilityNodeInfo): AdbInfo? {
        return try {
            // 简单获取所有文本
            val allText = getAllText(root)
            LogUtils.d("AdbInfoExtractor", "页面全部文本长度: ${allText.length}")

            // 简单的IP端口匹配
            val ipPortPattern = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{4,5})""")
            val match = ipPortPattern.find(allText)

            if (match != null) {
                val ip = match.groupValues[1]
                val port = match.groupValues[2].toInt()
                if (isValidIp(ip) && port in 1024..65535) {
                    return AdbInfo(ip, port)
                }
            }

            // 如果直接匹配失败，尝试分离匹配
            val ipPattern = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")
            val portPattern = Regex("""\b\d{4,5}\b""")

            val foundIPs = ipPattern.findAll(allText).map { it.value }.filter { isValidIp(it) }.toList()
            val foundPorts = portPattern.findAll(allText).map { it.value }.map { it.toInt() }.filter { it in 1024..65535 }.toList()

            if (foundIPs.isNotEmpty() && foundPorts.isNotEmpty()) {
                return AdbInfo(foundIPs.first(), foundPorts.first())
            }

            null
        } catch (e: Exception) {
            LogUtils.e("AdbInfoExtractor", "简单文本提取失败", e)
            null
        }
    }
    
    /**
     * 方法1：直接匹配IP:端口格式
     */
    private fun extractDirectIpPort(root: AccessibilityNodeInfo): AdbInfo? {
        return try {
            val ipPortSelectors = try {
                DeveloperRules.ipPortSelectors
            } catch (e: ExceptionInInitializerError) {
                LogUtils.w("AdbInfoExtractor", "DeveloperRules 初始化失败，跳过 ipPortSelectors", e)
                emptyList()
            } catch (e: NoClassDefFoundError) {
                LogUtils.w("AdbInfoExtractor", "DeveloperRules 类不可用，跳过 ipPortSelectors", e)
                emptyList()
            } catch (e: Exception) {
                LogUtils.w("AdbInfoExtractor", "获取 ipPortSelectors 失败", e)
                emptyList()
            }

            for (selector in ipPortSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
                    if (node != null) {
                        extractFromNode(node)?.let { return it }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbInfoExtractor", "选择器匹配失败: ${selector}", e)
                    continue
                }
            }
            null
        } catch (e: Exception) {
            LogUtils.e("AdbInfoExtractor", "直接匹配时发生异常", e)
            null
        }
    }
    
    /**
     * 方法2：分别提取IP和端口
     */
    private fun extractSeparateIpPort(root: AccessibilityNodeInfo): AdbInfo? {
        return try {
            var ip: String? = null
            var port: Int? = null
            
            // 提取IP地址
            val ipAddressSelectors = try {
                DeveloperRules.ipAddressSelectors
            } catch (e: ExceptionInInitializerError) {
                LogUtils.w("AdbInfoExtractor", "DeveloperRules 初始化失败，跳过 ipAddressSelectors", e)
                emptyList()
            } catch (e: NoClassDefFoundError) {
                LogUtils.w("AdbInfoExtractor", "DeveloperRules 类不可用，跳过 ipAddressSelectors", e)
                emptyList()
            } catch (e: Exception) {
                LogUtils.w("AdbInfoExtractor", "获取 ipAddressSelectors 失败", e)
                emptyList()
            }

            for (ipSelector in ipAddressSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, ipSelector, MatchOption())
                    if (node != null) {
                        val extractedIp = extractIpFromNode(node)
                        if (extractedIp != null) {
                            ip = extractedIp
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbInfoExtractor", "IP选择器匹配失败", e)
                    continue
                }
            }
            
            // 提取端口号
            val portSelectors = try {
                DeveloperRules.portSelectors
            } catch (e: ExceptionInInitializerError) {
                LogUtils.w("AdbInfoExtractor", "DeveloperRules 初始化失败，跳过 portSelectors", e)
                emptyList()
            } catch (e: NoClassDefFoundError) {
                LogUtils.w("AdbInfoExtractor", "DeveloperRules 类不可用，跳过 portSelectors", e)
                emptyList()
            } catch (e: Exception) {
                LogUtils.w("AdbInfoExtractor", "获取 portSelectors 失败", e)
                emptyList()
            }

            for (portSelector in portSelectors) {
                try {
                    val node = a11yContext.querySelfOrSelector(root, portSelector, MatchOption())
                    if (node != null) {
                        val extractedPort = extractPortFromNode(node)
                        if (extractedPort != null) {
                            port = extractedPort
                            break
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.w("AdbInfoExtractor", "端口选择器匹配失败", e)
                    continue
                }
            }
            
            if (ip != null && port != null) {
                AdbInfo(ip, port)
            } else {
                LogUtils.d("AdbInfoExtractor", "分离提取失败: IP=$ip, Port=$port")
                null
            }
        } catch (e: Exception) {
            LogUtils.e("AdbInfoExtractor", "分离提取时发生异常", e)
            null
        }
    }
    
    /**
     * 方法3：全文本搜索
     */
    private fun extractFromAllText(root: AccessibilityNodeInfo): AdbInfo? {
        return try {
            val allText = getAllText(root)
            LogUtils.d("AdbInfoExtractor", "提取到的全部文本长度: ${allText.length}")
            parseAdbInfo(allText)
        } catch (e: Exception) {
            LogUtils.e("AdbInfoExtractor", "全文本搜索时发生异常", e)
            null
        }
    }
    
    /**
     * 从单个节点提取信息
     */
    private fun extractFromNode(node: AccessibilityNodeInfo): AdbInfo? {
        val text = getNodeText(node) ?: return null
        LogUtils.d("AdbInfoExtractor", "节点文本: $text")
        return parseAdbInfo(text)
    }
    
    /**
     * 从节点提取IP地址
     */
    private fun extractIpFromNode(node: AccessibilityNodeInfo): String? {
        val text = getNodeText(node) ?: return null
        val ipPattern = "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b".toRegex()
        return ipPattern.find(text)?.groupValues?.get(1)
    }
    
    /**
     * 从节点提取端口号
     */
    private fun extractPortFromNode(node: AccessibilityNodeInfo): Int? {
        val text = getNodeText(node) ?: return null
        val portPattern = "\\b(\\d{4,5})\\b".toRegex()
        val matches = portPattern.findAll(text)
        
        // 寻找合理的端口号范围
        for (match in matches) {
            val port = match.groupValues[1].toIntOrNull()
            if (port != null && port in 1024..65535) {
                return port
            }
        }
        return null
    }
    
    /**
     * 获取节点的文本内容
     */
    private fun getNodeText(node: AccessibilityNodeInfo): String? {
        return try {
            node.text?.toString() ?: node.contentDescription?.toString()
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "获取节点文本失败", e)
            null
        }
    }
    
    /**
     * 获取所有匹配的节点
     */
    private fun getAllMatchingNodes(root: AccessibilityNodeInfo, selector: li.songe.selector.Selector): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        try {
            // 这里需要根据GKD的API来实现获取所有匹配节点的方法
            // 暂时使用单个查询
            val node = a11yContext.querySelfOrSelector(root, selector, MatchOption())
            if (node != null) {
                nodes.add(node)
            }
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "批量节点匹配失败", e)
        }
        return nodes
    }
    
    /**
     * 递归获取所有文本内容
     */
    private fun getAllText(node: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        
        try {
            // 添加当前节点的文本
            node.text?.let { builder.append(it).append(" ") }
            node.contentDescription?.let { builder.append(it).append(" ") }
            
            // 递归处理子节点
            for (i in 0 until node.childCount) {
                try {
                    node.getChild(i)?.let { child ->
                        builder.append(getAllText(child))
                    }
                } catch (e: Exception) {
                    // 跳过有问题的子节点
                }
            }
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "获取文本时出错", e)
        }
        
        return builder.toString()
    }
    
    /**
     * 解析文本中的ADB信息
     */
    private fun parseAdbInfo(text: String): AdbInfo? {
        if (text.isEmpty()) return null
        
        // 匹配标准格式：IP:端口
        val ipPortPattern = "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d{4,5})\\b".toRegex()
        ipPortPattern.find(text)?.let { match ->
            val ip = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull()
            if (port != null && isValidIp(ip) && port in 1024..65535) {
                return AdbInfo(ip, port)
            }
        }
        
        // 匹配分离格式
        val separateResult = parseSeparateFormat(text)
        if (separateResult != null) return separateResult
        
        // 匹配其他可能的格式
        val alternativeResult = parseAlternativeFormats(text)
        if (alternativeResult != null) return alternativeResult
        
        LogUtils.d("AdbInfoExtractor", "文本解析失败: $text")
        return null
    }
    
    /**
     * 解析分离格式的IP和端口
     */
    private fun parseSeparateFormat(text: String): AdbInfo? {
        // 查找IP地址
        val ipPatterns = listOf(
            "(?:IP|地址|Address)[：:\\s]*([\\d\\.]+)".toRegex(),
            "([\\d\\.]+)\\s*(?:IP|地址|Address)".toRegex(),
            "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b".toRegex()
        )
        
        var ip: String? = null
        for (pattern in ipPatterns) {
            pattern.find(text)?.let { match ->
                val candidateIp = match.groupValues[1]
                if (isValidIp(candidateIp)) {
                    ip = candidateIp
                    break
                }
            }
        }
        
        // 查找端口号
        val portPatterns = listOf(
            "(?:端口|Port)[：:\\s]*(\\d{4,5})".toRegex(),
            "(\\d{4,5})\\s*(?:端口|Port)".toRegex(),
            "\\b(\\d{4,5})\\b".toRegex()
        )
        
        var port: Int? = null
        for (pattern in portPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                val candidatePort = match.groupValues[1].toIntOrNull()
                if (candidatePort != null && candidatePort in 1024..65535) {
                    port = candidatePort
                    break
                }
            }
            if (port != null) break
        }
        
        return if (ip != null && port != null) {
            AdbInfo(ip, port)
        } else null
    }
    
    /**
     * 解析其他可能的格式
     */
    private fun parseAlternativeFormats(text: String): AdbInfo? {
        // 尝试匹配各种可能的格式
        val patterns = listOf(
            // 带空格的格式: IP : 端口
            "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\s*[：:]\\s*(\\d{4,5})\\b".toRegex(),
            // 带文字描述的格式
            "连接地址[：:\\s]*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})[：:\\s]*(\\d{4,5})".toRegex(),
            // 调试地址格式
            "调试[：:\\s]*(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})[：:\\s]*(\\d{4,5})".toRegex()
        )
        
        for (pattern in patterns) {
            pattern.find(text)?.let { match ->
                val ip = match.groupValues[1]
                val port = match.groupValues[2].toIntOrNull()
                if (port != null && isValidIp(ip) && port in 1024..65535) {
                    return AdbInfo(ip, port)
                }
            }
        }
        
        return null
    }
    
    /**
     * 验证IP地址格式
     */
    private fun isValidIp(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        for (part in parts) {
            val num = part.toIntOrNull()
            if (num == null || num < 0 || num > 255) return false
        }
        
        // 检查是否为局域网IP
        val firstOctet = parts[0].toInt()
        val secondOctet = parts[1].toInt()
        
        return when (firstOctet) {
            10 -> true                              // 10.0.0.0/8
            172 -> secondOctet in 16..31           // 172.16.0.0/12
            192 -> secondOctet == 168              // 192.168.0.0/16
            else -> false
        }
    }
    
    /**
     * 获取当前窗口的包名
     * 用于判断是否在正确的界面
     */
    fun getCurrentPackageName(): String? {
        return try {
            A11yService.instance?.safeActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "获取包名失败", e)
            null
        }
    }
    
    /**
     * 检查是否在设置应用中
     */
    fun isInSettingsApp(): Boolean {
        val packageName = getCurrentPackageName()
        if (packageName == null) return false
        val settingsPackages = try {
            DeveloperRules.settingsPackageNames
        } catch (e: ExceptionInInitializerError) {
            LogUtils.w("AdbInfoExtractor", "DeveloperRules 初始化失败，使用默认设置包名列表", e)
            // 退化为常见设置包名，避免崩溃
            listOf(
                "com.android.settings",
                "com.coloros.safecenter",
                "com.oplus.applicationsettings",
                "com.coloros.settings"
            )
        } catch (e: NoClassDefFoundError) {
            LogUtils.w("AdbInfoExtractor", "DeveloperRules 类不可用，使用默认设置包名列表", e)
            listOf(
                "com.android.settings",
                "com.coloros.safecenter",
                "com.oplus.applicationsettings",
                "com.coloros.settings"
            )
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "获取 settingsPackageNames 失败，使用默认", e)
            listOf("com.android.settings")
        }
        return settingsPackages.any { packageName.contains(it, ignoreCase = true) }
    }
    
    /**
     * 检查是否在开发者选项页面
     */
    private fun isInDeveloperOptions(root: AccessibilityNodeInfo): Boolean {
        val allText = getAllText(root).lowercase()
        return allText.contains("开发者选项") || 
               allText.contains("developer options") ||
               allText.contains("开发人员选项") ||
               allText.contains("开发者设置") ||
               allText.contains("无线调试") ||
               allText.contains("wireless debugging")
    }
    
    /**
     * 方法4：查找ADB调试相关区域
     */
    private fun extractFromAdbSection(root: AccessibilityNodeInfo): AdbInfo? {
        return try {
            // 查找包含无线调试或ADB字样的节点
            val adbKeywords = listOf("无线调试", "wireless debugging", "adb", "调试")
            
            for (keyword in adbKeywords) {
                val nodes = findNodesContainingText(root, keyword)
                for (node in nodes) {
                    // 在该节点及其父子节点中搜索IP:端口信息
                    extractFromNodeAndFamily(node)?.let { return it }
                }
            }
            
            null
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "ADB区域搜索失败", e)
            null
        }
    }
    
    /**
     * 在节点及其家族（父、子、兄弟节点）中搜索
     */
    private fun extractFromNodeAndFamily(node: AccessibilityNodeInfo): AdbInfo? {
        try {
            // 检查当前节点
            extractFromNode(node)?.let { return it }
            
            // 检查子节点
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    extractFromNode(child)?.let { return it }
                }
            }
            
            // 检查父节点
            node.parent?.let { parent ->
                extractFromNode(parent)?.let { return it }
                
                // 检查兄弟节点
                for (i in 0 until parent.childCount) {
                    parent.getChild(i)?.let { sibling ->
                        if (sibling != node) {
                            extractFromNode(sibling)?.let { return it }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "节点家族搜索失败", e)
        }
        
        return null
    }
    
    /**
     * 查找包含指定文本的节点
     */
    private fun findNodesContainingText(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        
        fun searchNode(node: AccessibilityNodeInfo) {
            try {
                val nodeText = getNodeText(node)?.lowercase()
                if (nodeText?.contains(text.lowercase()) == true) {
                    nodes.add(node)
                }
                
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        searchNode(child)
                    }
                }
            } catch (e: Exception) {
                // 忽略单个节点的错误
            }
        }
        
        try {
            searchNode(root)
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "文本搜索失败", e)
        }
        
        return nodes
    }
    
    /**
     * 记录页面信息用于调试
     */
    private fun dumpPageInfo(root: AccessibilityNodeInfo) {
        try {
            val packageName = getCurrentPackageName()
            val allText = getAllText(root)
            
            LogUtils.d("AdbInfoExtractor", "=== 页面调试信息 ===")
            LogUtils.d("AdbInfoExtractor", "包名: $packageName")
            LogUtils.d("AdbInfoExtractor", "页面文本长度: ${allText.length}")
            
            // 记录前500个字符的文本内容
            val preview = if (allText.length > 500) {
                allText.substring(0, 500) + "..."
            } else {
                allText
            }
            LogUtils.d("AdbInfoExtractor", "页面文本预览: $preview")
            
            // 查找可能的IP地址
            val ipPattern = "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b".toRegex()
            val ips = ipPattern.findAll(allText).map { it.value }.toList()
            if (ips.isNotEmpty()) {
                LogUtils.d("AdbInfoExtractor", "发现IP地址: $ips")
            }
            
            // 查找可能的端口号
            val portPattern = "\\b\\d{4,5}\\b".toRegex()
            val ports = portPattern.findAll(allText).map { it.value }.distinct().toList()
            if (ports.isNotEmpty()) {
                LogUtils.d("AdbInfoExtractor", "发现端口号: $ports")
            }
            
            LogUtils.d("AdbInfoExtractor", "=== 调试信息结束 ===")
            
        } catch (e: Exception) {
            LogUtils.w("AdbInfoExtractor", "记录调试信息失败", e)
        }
    }
}