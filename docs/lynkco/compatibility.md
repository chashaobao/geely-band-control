# 领克适配兼容性与验收边界

本文件是合并实现的当前状态；reference-* 文件为参考副本原文归档，里面的独立包名、版本和历史测试结果不代表本仓库当前发布结果。

- 目标：中国版 `com.lynkco.customer`，`com.lynkco.vehicle.widget.ListWidgetProvider`，`layout_widget`，155 dp 宿主；小卡只读状态，不绑定为控车卡。
- 来源：用户 APK 4.2.9 / 402090100，SHA-256 `62f87947a43090620fb96d058a5092a037093f1a57cfc37966a2bea4f3ab53c3`；4.3.0 小组件面一致性为提供的交叉核对结论；4.3.1 未核对。
- 采用四个可配置槽位（7 选 4）的容器/图标/progress 资源模型，四槽用独立中性 wire action：lynk_unlock / lynk_window / lynk_kjck / lynk_ccqk。图标和方向未验证，不复用银河语义和指纹。可见且具有点击监听及可渲染图标的槽位才开放；加载/隐藏/确认态阻止执行。
- 四槽均走 prepare/token/13 秒穿戴确认，再对当前槽位 performClick。token 保留服务端原 15 秒上限。verify 出现时提示去手机官方卡片处理；伴侣不代点确认。
- 缺少 brand 的老协议按银河兼容；有品牌的回包按品牌过滤，缓存带品牌；切换关闭车控及语音并清空待确认 token、回执和 dispatch。旧品牌卡片独立保留，切回后需重新授权。
- 语音：GALAXY 对应 GARAGE_V1；LYNKCO 对应 LYNKCO_V1，开放 TEST 与锁窗命令，控制受签名/分类/方向与穿戴确认约束。不能从静态字符串或槽位名证明上锁/解锁/开窗方向。

## P3 真机检查

逐槽记录：设备/车型、官方版本、可见性、图标、驻车条件、prepare 文案、点击前后状态、是否触发 verify、手机确认后的回执。kjck/ccqk 实际功能、全部槽位方向、副作用、图标指纹、condition_txt 内容、time 格式、确认浮层触发条件均未验证。当前使用既有保守状态解析器，解析失败显示未知，不把占位符当车况。

本次未安装或运行用户 APK，未连接手机/车辆，未做生产发布。不删除独立仓库。当前实现使用共享 GalaxyWidgets 宿主和 LynkWidgets 固定槽适配器，避免双份宿主逻辑漂移。

## 可配置槽位与运行时证据

四个可配置槽位（7 选 4）的候选功能为：锁、车窗、发动机、快速降温、快速升温、天窗、后备箱。七种图标和四个位置属于静态资源证据，不能独立证明所有车型都支持任意七选四；fr_ccqk 默认隐藏也不能独立证明出厂只配置三个功能。这两项仍需按车型、账号、官方版本真机核对。

位置模型：fr_unlock/unlock/pb_unlock；fr_window/window/pb_window；fr_kjck/kjck/pb_kjck；fr_ccqk/ccqk_on/pb_ccqk_on。模型只决定扫描位置，不决定功能。四个中性 action 保留，kjck/ccqk 继续显示槽位名与待验证。

车主可点预览槽位明确标记分类，并对照卡片当前锁/窗状态及真机点按记录目标方向，或选择未知。车窗微开与全开分别标记，不能相互代用。独立领克标记表保存在 lynkcoWidgetCards 的 bindings，包含 slot、fingerprint、description、category、direction；无预置真机指纹，不加载银河图标表。签名包含 provider 版本/更新时间/夜间模式、槽位、点击资源、描述及 48x48 图像 SHA-256。每次扫描和执行前核对；观察到签名变化即删除旧标记，官方更新还会清空待确认 token。隐藏或暂不可读会保守撤销标记，需重新核对。

采集方法（本次未执行）：在真实设备添加官方中卡并刷新，记录车型/官方版本/显示模式/当前状态；点伴侣预览查看槽位签名及 contentDescription，保存截图及上述字段；车主按官方卡片核对并在安全条件下自行验证点按，再明确分类及方向。切换功能、状态、日夜模式和升级后重复采集，确认旧标记与 token 失效。不要从资源拼音或七个图标名填入分类。

语音场景与步骤见 [voice.md](voice.md)。只有 TEST、LOCK、UNLOCK、WINDOW_VENT、WINDOW_OPEN、WINDOW_CLOSE 开放。ENGINE/HEAT/COOL/SUNROOF/TRUNK/FIND 等为关闭占位，不能将 kjck/ccqk 的含义当作已证实功能。
