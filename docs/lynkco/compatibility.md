# 领克适配兼容性与验收边界

本文件是合并实现的当前状态；reference-* 文件为参考副本原文归档，里面的独立包名、版本和历史测试结果不代表本仓库当前发布结果。

- 目标：中国版 `com.lynkco.customer`，`com.lynkco.vehicle.widget.ListWidgetProvider`，`layout_widget`，155 dp 宿主；小卡只读状态，不绑定为控车卡。
- 来源：用户 APK 4.2.9 / 402090100，SHA-256 `62f87947a43090620fb96d058a5092a037093f1a57cfc37966a2bea4f3ab53c3`；4.3.0 小组件面一致性为提供的交叉核对结论；4.3.1 未核对。
- 4.2.9 官方中型桌面组件的设置页提供三个可配置位置。扫描保留布局中的四个节点及原有中性 wire action：lynk_unlock / lynk_window / lynk_kjck / lynk_ccqk。可见且具有点击监听及可渲染图标的槽位才开放；加载/隐藏/确认态阻止执行。
- 穿戴手动操作继续使用 prepare/token/13 秒确认，服务端 token 上限保留 15 秒。已核对的锁窗动作可在本次派发后的 5 秒内提交同一卡片上标题匹配的新官方确认。其他卡片弹窗沿用手机人工处理。
- 缺少 brand 的老协议按银河兼容；有品牌的回包按品牌过滤，缓存带品牌；切换关闭车控及语音并清空待确认 token、回执和 dispatch。旧品牌卡片独立保留，切回后需重新授权。
- 语音：GALAXY 对应 GARAGE_V1；LYNKCO 对应 LYNKCO_V1，开放 TEST 与锁窗命令，控制受签名/分类/方向与穿戴确认约束。不能从静态字符串或槽位名证明上锁/解锁/开窗方向。

## P3 真机检查

逐槽记录：设备/车型、官方版本、可见性、图标、驻车条件、prepare 文案、点击前后状态、是否触发 verify、手机确认后的回执。kjck/ccqk 实际功能、全部槽位方向、副作用、图标指纹、condition_txt 内容、time 格式、确认浮层触发条件均未验证。当前使用既有保守状态解析器，解析失败显示未知，不把占位符当车况。

本次未安装或运行用户 APK，未连接手机/车辆，未做生产发布。不删除独立仓库。当前实现使用共享 GalaxyWidgets 宿主和 LynkWidgets 固定槽适配器，避免双份宿主逻辑漂移。

## 可配置槽位与运行时证据

4.2.9 APK 的 `activity_widget` 有且仅有「车控功能 1、2、3」，中型组件预览图 `pic_02` 也展示三个按钮。`layout_widget` 有第四个初始隐藏的 `fr_ccqk` 节点，保留扫描兼容，暂无证据表明它是第四个可配置位置。App 首页的「自定义快捷功能」有自己的布局，不用于判断桌面组件容量。

位置模型：fr_unlock/unlock/pb_unlock；fr_window/window/pb_window；fr_kjck/kjck/pb_kjck；fr_ccqk/ccqk_on/pb_ccqk_on。位置用于扫描。伴侣可按控件的明确描述或 4.2.9 APK 内已确认资源的轮廓标注车锁、车窗及后备箱；寻车可通过明确描述或车主标记识别。锁扣轮廓只能显示「开锁图标」或「闭锁图标」。车窗图标的两种颜色共用轮廓，只能标注「车窗」，有明确文字时才细分微开和全开。穿戴端使用识别结果对应的内置图标和短名称。自动识别只用于显示；语音仍需要车主标记及方向核对。

车主可点预览槽位明确标记分类，并对照卡片当前锁/窗状态及真机点按记录目标方向，或选择未知。车窗微开与全开分别标记，不能相互代用。独立领克标记表保存在 lynkcoWidgetCards 的 bindings，包含 slot、fingerprint、description、category、direction；无预置真机指纹，不加载银河图标表。签名包含 provider 版本/更新时间/夜间模式、槽位、点击资源、描述及 48x48 图像 SHA-256。每次扫描和执行前核对；观察到签名变化即删除旧标记，官方更新还会清空待确认 token。隐藏或暂不可读会保守撤销标记，需重新核对。

采集方法（本次未执行）：在真实设备添加官方中卡并刷新，记录车型、官方版本、显示模式和官方 App 显示的当前状态；点伴侣预览查看自动标签、槽位签名及 contentDescription，保存截图及上述字段。切换功能、状态、日夜模式和升级后重复采集，确认未知布局回退到手动标记，旧标记与 token 按原规则失效。

语音场景与步骤见 [voice.md](voice.md)。只有 TEST、LOCK、UNLOCK、WINDOW_VENT、WINDOW_OPEN、WINDOW_CLOSE 开放。ENGINE/HEAT/COOL/SUNROOF/TRUNK/FIND 等为关闭占位，不能将 kjck/ccqk 的含义当作已证实功能。
