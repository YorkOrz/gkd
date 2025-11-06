package li.songe.gkd.adb

import kotlin.collections.buildList
import li.songe.selector.Selector

/**
 * 开发者选项导航规则（修复GKD选择器语法版本）
 * 包含各种厂商UI的适配规则和选择器
 */
object DeveloperRules {

    // 主要设置应用包名
    val settingsPackageNames = listOf(
        "com.android.settings",        // 原生Android
        "com.miui.securitycenter",     // MIUI
        "com.huawei.systemmanager",    // EMUI/HarmonyOS
        "com.coloros.safecenter",      // ColorOS
        "com.vivo.permissionmanager",  // FunTouch OS
        "com.samsung.android.settings" // Samsung One UI
    )

    // 设置应用主界面识别
    val settingsActivityNames = listOf(
        "com.android.settings.Settings",
        "com.android.settings.SubSettings"
    )

    // 开发者选项入口选择器（已启用状态）- 修复语法（延迟初始化，避免ExceptionInInitializerError）
    val developerOptionsSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`开发者选项` || text=`Developer options`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`开发人员选项` || text=`开发者模式`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`开发者设置` || text=`Developer settings`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`开发者` || desc*=`Developer`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`Developer` || text*=`开发`")) } catch (e: Exception) {}
        }
    }

    // 系统与更新选择器（ColorOS 15特有）- 修复语法（延迟初始化）
    val systemUpdateSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`系统与更新` || text=`System & updates`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`系统和更新` || text=`System and updates`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`系统与更新` || desc*=`System update`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`系统` text*=`更新` || text*=`System` text*=`update`")) } catch (e: Exception) {}
        }
    }

    // 关于手机/系统信息选择器（用于激活开发者选项）- 修复语法（延迟初始化）
    val aboutPhoneSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`关于手机` || text=`About phone`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`关于设备` || text=`About device`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`系统信息` || text=`System info`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`关于本机` || text=`About`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`手机信息` || text=`Phone info`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`关于` || desc*=`About`")) } catch (e: Exception) {}
        }
    }

    // 版本号选择器（点击7次激活开发者模式）- 修复语法（延迟初始化）
    val versionSelectors by lazy {
        buildList {
            try { add(Selector.parse("text*=`版本号` || text*=`Build number`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`内部版本` || text*=`Internal version`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`软件版本` || text*=`Software version`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`系统版本` || text*=`System version`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`MIUI版本` || text*=`MIUI version`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`版本` || desc*=`Version` || desc*=`Build`")) } catch (e: Exception) {}
        }
    }

    // 开发者模式激活确认信息 - 修复语法（延迟初始化）
    val developerModeConfirmSelectors by lazy {
        buildList {
            try { add(Selector.parse("text*=`开发者模式已启用` || text*=`Developer mode enabled`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`您现在是开发者` || text*=`You are now a developer`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`开发者选项已启用` || text*=`Developer options enabled`")) } catch (e: Exception) {}
        }
    }

    // 无线调试/ADB选择器 - 修复语法（延迟初始化）
    val wirelessDebuggingSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`无线调试` || text=`Wireless debugging`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`WiFi调试` || text=`WiFi debugging`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`无线ADB` || text=`Wireless ADB`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`网络ADB调试` || text=`Network ADB debugging`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`无线调试` || desc*=`Wireless debug`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`ADB` || desc*=`adb`")) } catch (e: Exception) {}
        }
    }

    // USB调试选择器 - 修复语法（延迟初始化）
    val usbDebuggingSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`USB调试` || text=`USB debugging`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`ADB调试` || text=`ADB debugging`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`USB调试` || desc*=`USB debug`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`ADB` + desc*=`调试`")) } catch (e: Exception) {}
        }
    }

    // IP地址选择器 - 修复语法（延迟初始化，逐条容错）
    val ipAddressSelectors by lazy {
        buildList {
            try { add(Selector.parse("text~=\\d+\\.\\d+\\.\\d+\\.\\d+")) } catch (e: Exception) {}
            try { add(Selector.parse("desc~=\\d+\\.\\d+\\.\\d+\\.\\d+")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`IP` text~=\\d+\\.\\d+\\.\\d+\\.\\d+")) } catch (e: Exception) {}
        }
    }

    // 端口选择器 - 修复语法（延迟初始化，逐条容错）
    val portSelectors by lazy {
        buildList {
            try { add(Selector.parse("text*=`端口` text~=\\d{4,5}")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`Port` text~=\\d{4,5}")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`端口` text~=\\d{4,5}")) } catch (e: Exception) {}
            try { add(Selector.parse("text~=\\d{4,5}")) } catch (e: Exception) {}
        }
    }

    // 开关/切换按钮选择器 - 修复语法（延迟初始化，避免ExceptionInInitializerError）
    val switchSelectors by lazy {
        buildList {
            try { add(Selector.parse("checked=false clickable=true className=`android.widget.Switch`")) } catch (e: Exception) {}
            try { add(Selector.parse("checked=false clickable=true className=`androidx.appcompat.widget.SwitchCompat`")) } catch (e: Exception) {}
            try { add(Selector.parse("checked=false clickable=true > Switch")) } catch (e: Exception) {}
            try { add(Selector.parse("className=`android.widget.Switch` checked=false")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`关闭` || desc*=`OFF` || desc*=`Disabled`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`关闭` || text*=`OFF` || text*=`已关闭`")) } catch (e: Exception) {}
            try { add(Selector.parse("clickable=true checkable=true checked=false")) } catch (e: Exception) {}
        }
    }

    // 已开启状态的开关（用于确认） - 修复语法（延迟初始化）
    val enabledSwitchSelectors by lazy {
        buildList {
            try { add(Selector.parse("checked=true className=`android.widget.Switch`")) } catch (e: Exception) {}
            try { add(Selector.parse("checked=true className=`androidx.appcompat.widget.SwitchCompat`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`打开` || desc*=`ON` || desc*=`Enabled`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`打开` || text*=`ON` || text*=`已打开`")) } catch (e: Exception) {}
        }
    }

    // IP和端口信息选择器 - 修复语法（延迟初始化，逐条容错）
    val ipPortSelectors by lazy {
        buildList {
            // 直接匹配 IP:端口 格式
            try { add(Selector.parse("text~=\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+")) } catch (e: Exception) {}
            try { add(Selector.parse("desc~=\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+")) } catch (e: Exception) {}

            // IP地址相关文本
            try { add(Selector.parse("text*=`IP` + * text~=\\d+\\.\\d+\\.\\d+\\.\\d+")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`地址` + * text~=\\d+\\.\\d+\\.\\d+\\.\\d+")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`Address` + * text~=\\d+\\.\\d+\\.\\d+\\.\\d+")) } catch (e: Exception) {}

            // 端口相关文本
            try { add(Selector.parse("text*=`端口` + * text~=\\d+")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`Port` + * text~=\\d+")) } catch (e: Exception) {}

            // 包含IP和端口的容器
            try { add(Selector.parse("text*=`IP` text*=`端口`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`IP` text*=`Port`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc*=`IP` desc*=`端口`")) } catch (e: Exception) {}

            // 调试信息相关
            try { add(Selector.parse("text*=`调试地址` || text*=`Debug address`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`连接地址` || text*=`Connection address`")) } catch (e: Exception) {}
        }
    }

    // 确认/允许按钮选择器 - 修复语法（延迟初始化）
    val confirmSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`确定` || text=`OK` || text=`允许` || text=`Allow`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`确认` || text=`Confirm` || text=`是` || text=`Yes`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`启用` || text=`Enable` || text=`开启` || text=`Turn on`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc=`确定` || desc=`OK` || desc=`确认` || desc=`Allow`")) } catch (e: Exception) {}
        }
    }

    // 取消/拒绝按钮选择器 - 修复语法（延迟初始化，逐条容错）
    val cancelSelectors by lazy {
        buildList {
            try { add(Selector.parse("text=`取消` || text=`Cancel` || text=`拒绝` || text=`Deny`")) } catch (e: Exception) {}
            try { add(Selector.parse("text=`不允许` || text*=`Don't allow` || text=`否` || text=`No`")) } catch (e: Exception) {}
            try { add(Selector.parse("desc=`取消` || desc=`Cancel` || desc=`拒绝`")) } catch (e: Exception) {}
        }
    }

    // 返回按钮选择器 - 修复语法（延迟初始化，逐条容错）
    val backSelectors by lazy {
        buildList {
            try { add(Selector.parse("desc=`返回` || desc=`Back` || desc=`Navigate up`")) } catch (e: Exception) {}
            try { add(Selector.parse("className=`android.widget.ImageButton` desc*=`返回`")) } catch (e: Exception) {}
            try { add(Selector.parse("className=`android.widget.ImageView` desc*=`返回`")) } catch (e: Exception) {}
        }
    }

    // 搜索框选择器 - 修复语法（延迟初始化，逐条容错）
    val searchSelectors by lazy {
        buildList {
            try { add(Selector.parse("hint*=`搜索` || hint*=`Search`")) } catch (e: Exception) {}
            try { add(Selector.parse("text*=`搜索` || text*=`Search`")) } catch (e: Exception) {}
            try { add(Selector.parse("className=`android.widget.EditText` hint*=`搜索`")) } catch (e: Exception) {}
        }
    }

    // 列表项选择器（用于在设置列表中查找） - 修复语法（延迟初始化，逐条容错）
    val listItemSelectors by lazy {
        buildList {
            try { add(Selector.parse("className=`android.widget.LinearLayout` clickable=true")) } catch (e: Exception) {}
            try { add(Selector.parse("className=`androidx.recyclerview.widget.RecyclerView` > *")) } catch (e: Exception) {}
            try { add(Selector.parse("className=`android.widget.ListView` > *")) } catch (e: Exception) {}
        }
    }

    /**
     * 厂商特定的开发者选项路径映射
     */
    object VendorPaths {
        // MIUI路径
        val miuiPaths = listOf(
            listOf("更多设置", "开发者选项"),
            listOf("其他设置", "开发者选项"),
            listOf("Additional settings", "Developer options")
        )

        // EMUI/HarmonyOS路径
        val emuiPaths = listOf(
            listOf("系统和更新", "开发人员选项"),
            listOf("System & updates", "Developer options")
        )

        // ColorOS路径 (包含ColorOS 15的特殊路径)
        val colorOSPaths = listOf(
            // ColorOS 15 特殊路径：设置 → 系统与更新 → 开发者选项
            listOf("系统与更新", "开发者选项"),
            listOf("System & updates", "Developer options"),
            // 旧版ColorOS路径
            listOf("其他设置", "开发者选项"),
            listOf("Additional settings", "Developer options")
        )

        // FunTouch OS路径
        val funTouchPaths = listOf(
            listOf("更多设置", "开发者选项"),
            listOf("More settings", "Developer options")
        )
    }

    /**
     * 常见的错误和重试场景
     */
    object ErrorScenarios {
        // 权限请求相关 - 修复语法
        val permissionRequestSelectors by lazy {
            buildList {
                try { add(Selector.parse("text*=`权限` || text*=`Permission`")) } catch (e: Exception) {}
                try { add(Selector.parse("text*=`授权` || text*=`Grant`")) } catch (e: Exception) {}
                try { add(Selector.parse("text*=`允许` || text*=`Allow`")) } catch (e: Exception) {}
            }
        }

        // 网络连接错误 - 修复语法
        val networkErrorSelectors by lazy {
            buildList {
                try { add(Selector.parse("text*=`网络错误` || text*=`Network error`")) } catch (e: Exception) {}
                try { add(Selector.parse("text*=`连接失败` || text*=`Connection failed`")) } catch (e: Exception) {}
                try { add(Selector.parse("text*=`无法连接` || text*=`Cannot connect`")) } catch (e: Exception) {}
            }
        }

        // 功能不可用 - 修复语法
        val unavailableSelectors by lazy {
            buildList {
                try { add(Selector.parse("text*=`功能不可用` || text*=`Feature unavailable`")) } catch (e: Exception) {}
                try { add(Selector.parse("text*=`暂不支持` || text*=`Not supported`")) } catch (e: Exception) {}
                try { add(Selector.parse("text*=`设备不支持` || text*=`Device not supported`")) } catch (e: Exception) {}
            }
        }
    }
}