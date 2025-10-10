package li.songe.gkd.adb

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

    // 开发者选项入口选择器（已启用状态）- 修复语法
    val developerOptionsSelectors = listOf(
        Selector.parse("text=`开发者选项` || text=`Developer options`"),
        Selector.parse("text=`开发人员选项` || text=`开发者模式`"),
        Selector.parse("text=`开发者设置` || text=`Developer settings`"),
        Selector.parse("desc*=`开发者` || desc*=`Developer`"),
        Selector.parse("text*=`Developer` || text*=`开发`")
    )

    // 系统与更新选择器（ColorOS 15特有）- 修复语法
    val systemUpdateSelectors = listOf(
        Selector.parse("text=`系统与更新` || text=`System & updates`"),
        Selector.parse("text=`系统和更新` || text=`System and updates`"),
        Selector.parse("desc*=`系统与更新` || desc*=`System update`"),
        Selector.parse("text*=`系统` text*=`更新` || text*=`System` text*=`update`")
    )

    // 关于手机/系统信息选择器（用于激活开发者选项）- 修复语法
    val aboutPhoneSelectors = listOf(
        Selector.parse("text=`关于手机` || text=`About phone`"),
        Selector.parse("text=`关于设备` || text=`About device`"),
        Selector.parse("text=`系统信息` || text=`System info`"),
        Selector.parse("text=`关于本机` || text=`About`"),
        Selector.parse("text=`手机信息` || text=`Phone info`"),
        Selector.parse("desc*=`关于` || desc*=`About`")
    )

    // 版本号选择器（点击7次激活开发者模式）- 修复语法
    val versionSelectors = listOf(
        Selector.parse("text*=`版本号` || text*=`Build number`"),
        Selector.parse("text*=`内部版本` || text*=`Internal version`"),
        Selector.parse("text*=`软件版本` || text*=`Software version`"),
        Selector.parse("text*=`系统版本` || text*=`System version`"),
        Selector.parse("text*=`MIUI版本` || text*=`MIUI version`"),
        Selector.parse("desc*=`版本` || desc*=`Version` || desc*=`Build`")
    )

    // 开发者模式激活确认信息 - 修复语法
    val developerModeConfirmSelectors = listOf(
        Selector.parse("text*=`开发者模式已启用` || text*=`Developer mode enabled`"),
        Selector.parse("text*=`您现在是开发者` || text*=`You are now a developer`"),
        Selector.parse("text*=`开发者选项已启用` || text*=`Developer options enabled`")
    )

    // 无线调试/ADB选择器 - 修复语法
    val wirelessDebuggingSelectors = listOf(
        Selector.parse("text=`无线调试` || text=`Wireless debugging`"),
        Selector.parse("text=`WiFi调试` || text=`WiFi debugging`"),
        Selector.parse("text=`无线ADB` || text=`Wireless ADB`"),
        Selector.parse("text=`网络ADB调试` || text=`Network ADB debugging`"),
        Selector.parse("desc*=`无线调试` || desc*=`Wireless debug`"),
        Selector.parse("desc*=`ADB` || desc*=`adb`")
    )

    // USB调试选择器 - 修复语法
    val usbDebuggingSelectors = listOf(
        Selector.parse("text=`USB调试` || text=`USB debugging`"),
        Selector.parse("text=`ADB调试` || text=`ADB debugging`"),
        Selector.parse("desc*=`USB调试` || desc*=`USB debug`"),
        Selector.parse("desc*=`ADB` + desc*=`调试`")
    )

    // IP地址选择器 - 修复语法
    val ipAddressSelectors = listOf(
        Selector.parse("text~=\\d+\\.\\d+\\.\\d+\\.\\d+"),
        Selector.parse("desc~=\\d+\\.\\d+\\.\\d+\\.\\d+"),
        Selector.parse("text*=`IP` text~=\\d+\\.\\d+\\.\\d+\\.\\d+")
    )

    // 端口选择器 - 修复语法
    val portSelectors = listOf(
        Selector.parse("text*=`端口` text~=\\d{4,5}"),
        Selector.parse("text*=`Port` text~=\\d{4,5}"),
        Selector.parse("desc*=`端口` text~=\\d{4,5}"),
        Selector.parse("text~=\\d{4,5}")
    )

    // 开关/切换按钮选择器 - 修复语法
    val switchSelectors = listOf(
        // 未开启状态的开关
        Selector.parse("checked=false clickable=true className=`android.widget.Switch`"),
        Selector.parse("checked=false clickable=true className=`androidx.appcompat.widget.SwitchCompat`"),
        Selector.parse("checked=false clickable=true > Switch"),
        Selector.parse("className=`android.widget.Switch` checked=false"),
        // 描述文本包含关闭状态
        Selector.parse("desc*=`关闭` || desc*=`OFF` || desc*=`Disabled`"),
        Selector.parse("text*=`关闭` || text*=`OFF` || text*=`已关闭`"),
        // 通用开关选择器
        Selector.parse("clickable=true checkable=true checked=false")
    )

    // 已开启状态的开关（用于确认） - 修复语法
    val enabledSwitchSelectors = listOf(
        Selector.parse("checked=true className=`android.widget.Switch`"),
        Selector.parse("checked=true className=`androidx.appcompat.widget.SwitchCompat`"),
        Selector.parse("desc*=`打开` || desc*=`ON` || desc*=`Enabled`"),
        Selector.parse("text*=`打开` || text*=`ON` || text*=`已打开`")
    )

    // IP和端口信息选择器 - 修复语法
    val ipPortSelectors = listOf(
        // 直接匹配 IP:端口 格式
        Selector.parse("text~=\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+"),
        Selector.parse("desc~=\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+"),

        // IP地址相关文本
        Selector.parse("text*=`IP` + * text~=\\d+\\.\\d+\\.\\d+\\.\\d+"),
        Selector.parse("text*=`地址` + * text~=\\d+\\.\\d+\\.\\d+\\.\\d+"),
        Selector.parse("text*=`Address` + * text~=\\d+\\.\\d+\\.\\d+\\.\\d+"),

        // 端口相关文本
        Selector.parse("text*=`端口` + * text~=\\d+"),
        Selector.parse("text*=`Port` + * text~=\\d+"),

        // 包含IP和端口的容器
        Selector.parse("text*=`IP` text*=`端口`"),
        Selector.parse("text*=`IP` text*=`Port`"),
        Selector.parse("desc*=`IP` desc*=`端口`"),

        // 调试信息相关
        Selector.parse("text*=`调试地址` || text*=`Debug address`"),
        Selector.parse("text*=`连接地址` || text*=`Connection address`")
    )

    // 确认/允许按钮选择器 - 修复语法
    val confirmSelectors = listOf(
        Selector.parse("text=`确定` || text=`OK` || text=`允许` || text=`Allow`"),
        Selector.parse("text=`确认` || text=`Confirm` || text=`是` || text=`Yes`"),
        Selector.parse("text=`启用` || text=`Enable` || text=`开启` || text=`Turn on`"),
        Selector.parse("desc=`确定` || desc=`OK` || desc=`确认` || desc=`Allow`")
    )

    // 取消/拒绝按钮选择器 - 修复语法
    val cancelSelectors = listOf(
        Selector.parse("text=`取消` || text=`Cancel` || text=`拒绝` || text=`Deny`"),
        Selector.parse("text=`不允许` || text*=`Don't allow` || text=`否` || text=`No`"),
        Selector.parse("desc=`取消` || desc=`Cancel` || desc=`拒绝`")
    )

    // 返回按钮选择器 - 修复语法
    val backSelectors = listOf(
        Selector.parse("desc=`返回` || desc=`Back` || desc=`Navigate up`"),
        Selector.parse("className=`android.widget.ImageButton` desc*=`返回`"),
        Selector.parse("className=`android.widget.ImageView` desc*=`返回`")
    )

    // 搜索框选择器 - 修复语法
    val searchSelectors = listOf(
        Selector.parse("hint*=`搜索` || hint*=`Search`"),
        Selector.parse("text*=`搜索` || text*=`Search`"),
        Selector.parse("className=`android.widget.EditText` hint*=`搜索`")
    )

    // 列表项选择器（用于在设置列表中查找） - 修复语法
    val listItemSelectors = listOf(
        Selector.parse("className=`android.widget.LinearLayout` clickable=true"),
        Selector.parse("className=`androidx.recyclerview.widget.RecyclerView` > *"),
        Selector.parse("className=`android.widget.ListView` > *")
    )

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
        val permissionRequestSelectors = listOf(
            Selector.parse("text*=`权限` || text*=`Permission`"),
            Selector.parse("text*=`授权` || text*=`Grant`"),
            Selector.parse("text*=`允许` || text*=`Allow`")
        )

        // 网络连接错误 - 修复语法
        val networkErrorSelectors = listOf(
            Selector.parse("text*=`网络错误` || text*=`Network error`"),
            Selector.parse("text*=`连接失败` || text*=`Connection failed`"),
            Selector.parse("text*=`无法连接` || text*=`Cannot connect`")
        )

        // 功能不可用 - 修复语法
        val unavailableSelectors = listOf(
            Selector.parse("text*=`功能不可用` || text*=`Feature unavailable`"),
            Selector.parse("text*=`暂不支持` || text*=`Not supported`"),
            Selector.parse("text*=`设备不支持` || text*=`Device not supported`")
        )
    }
}