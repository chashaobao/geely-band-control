# 领克适配兼容性与验收边界

本文件是合并实现的当前状态；reference-* 文件为参考副本原文归档，里面的独立包名、版本和历史测试结果不代表本仓库当前发布结果。

- 目标：中国版 `com.lynkco.customer`，`com.lynkco.vehicle.widget.ListWidgetProvider`，`layout_widget`，155 dp 宿主；小卡只读状态，不绑定为控车卡。
- 来源：用户 APK 4.2.9 / 402090100，SHA-256 `62f87947a43090620fb96d058a5092a037093f1a57cfc37966a2bea4f3ab53c3`；4.3.0 小组件面一致性为提供的交叉核对结论；4.3.1 未核对。
- 采用固定槽位资源名，四槽用独立中性 wire action：lynk_unlock / lynk_window / lynk_kjck / lynk_ccqk。图标和方向未验证，不复用银河语义和指纹。可见且具有点击监听及可渲染图标的槽位才开放；加载/隐藏/确认态阻止执行。
- 四槽均走 prepare/token/13 秒穿戴确认，再对当前槽位 performClick。token 保留服务端原 15 秒上限。verify 出现时提示去手机官方卡片处理；伴侣不代点确认。
- 缺少 brand 的老协议按银河兼容；有品牌的回包按品牌过滤，缓存带品牌；切换关闭车控及语音并清空待确认 token、回执和 dispatch。旧品牌卡片独立保留，切回后需重新授权。
- 语音：GALAXY 对应 GARAGE_V1；LYNKCO 对应 LYNKCO_V1，只允许 TEST。不能从静态字符串或槽位名证明上锁/解锁/开窗方向。

## P3 真机检查

逐槽记录：设备/车型、官方版本、可见性、图标、驻车条件、prepare 文案、点击前后状态、是否触发 verify、手机确认后的回执。kjck/ccqk 实际功能、全部槽位方向、副作用、图标指纹、condition_txt 内容、time 格式、确认浮层触发条件均未验证。当前使用既有保守状态解析器，解析失败显示未知，不把占位符当车况。

本次未安装或运行用户 APK，未连接手机/车辆，未做生产发布。不删除独立仓库。当前实现使用共享 GalaxyWidgets 宿主和 LynkWidgets 固定槽适配器，避免双份宿主逻辑漂移。
