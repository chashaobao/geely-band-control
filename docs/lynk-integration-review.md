# 领克整合方案代码评审

结论：**带修改通过**。单仓库、单伴侣、单穿戴包及 P0→P1→P2 顺序可行；以下修改属于实现细化，不推翻目标。

基线：origin/main 85b0e00，版本 0.1.29；开始时工作区干净，原分支 feat/oppo-breeno-voice 比 origin/main 超前 2 提交。已 fetch；功能分支从 origin/main 创建，不包含该分支额外语音助手功能。

| 项目 | 结论与实施要求 |
| --- | --- |
| BrandConfig | 参考文件是领克静态常量，必须改为 GALAXY/LYNKCO 不可变配置实例，不能用可变全局静态品牌。银河沿用既有 host ID、widgetCards 偏好键和所有资源名。领克单独 host ID/偏好键。 |
| Widget adapter | 原代码 Card/Target/Binding 深度耦合 GalaxyWidgets；采用共享宿主实现与配置驱动 WidgetTree，LynkWidgets 作为可配置槽位适配器，不复制两份约 600 行宿主，也不引入无法隔离这些类型的空接口。明确保留 bind/scan/read/confirmation 边界。未知 provider/layout 不作宽松自动猜测。 |
| 首次选择与升级 | 全新安装提示选择；有旧 widgetCards 的用户自动迁移银河，避免打断已有卡片。设置可切换；切换须取消 pending bind、token、回执及 dispatch，关闭授权和语音，停止旧 host，再恢复对应品牌卡片或请求重新绑定。 |
| 槽位与状态 | fr_unlock/fr_window 只有槽位身份证据，不能据此推断开关方向；领克四槽用中性 slot action，保留真实资源名，kjck/ccqk 标为待验证，可见且可点击时走原 prepare/token/13 秒确认。隐藏槽位不制造可执行按钮。禁用银河图标识别和 ID 猜配；允许车主明确标记当前槽位签名和描述；condition_txt/time 不补造解析规则。 |
| 确认 | 原实现有手机端明确确认/取消按钮，不是自动确认；保留银河流程。领克 verify 出现后仅提示去手机官方卡片确认，不在伴侣模拟接受/取消。 |
| 手环与 Watch | 同一包，hello/status 带 brand，按品牌过滤功能；缺省字段保持银河兼容。缓存带品牌；切换时清除旧显示及图片。参考 29 项测试移植到共同页面，另加品牌切换、隔离及待验证槽位测试。 |
| 语音 | GARAGE_V1 保持原状；LYNKCO_V1 开放 TEST、LOCK、UNLOCK、WINDOW_VENT、WINDOW_OPEN、WINDOW_CLOSE；控制需当前车主标记与方向核对，并经穿戴确认。静态存在车锁/车窗不等于执行方向已验证。命名空间必须严格隔离。 |
| 版本与发布 | 两个 Android module 与 band manifest 统一 0.1.30，versionCode 递增；applicationId 仍 com.garage.bandcontrol。不删除独立仓库，不发布或安装 APK。 |

证据：本地参考副本 BrandConfig/LynkWidgets/WidgetTree、原 GalaxyWidgets/GarageApp/MainActivity/BandMessage、band 页面与测试；用户 APK 解析 summary.json 确认 com.lynkco.customer 4.2.9 / 402090100、ListWidgetProvider 和 layout_widget。4.3.0 一致性为提供的静态交叉核对结论；4.3.1 未核对。

P3 未包含：kjck/ccqk 功能、全部槽位方向及副作用、图标指纹、condition_txt/time 运行时格式、verify 触发条件、设备配对和真车回执。测试或编译不能替代真机验收。

## 实施与验证记录（2026-11-26）

已完成 P0–P2：BrandConfig 不可变实例配置，银河 host/偏好键兼容；LynkWidgets 可配置槽位；全新选择与旧用户迁移；品牌切换授权/token/回执/宿主隔离；band/Watch 品牌过滤与缓存归属；命名空间隔离；0.1.30 / versionCode 31；包名不变。

与原草案的具体差异：共享宿主保留 GalaxyWidgets 类型名，资源映射抽到 BrandConfig，LynkWidgets 提供位置身份；没有复制领克副本的全局静态配置和宽松 provider/layout 猜测。四个领克槽位均为中性 action，锁窗方向也未宣称验证。参考 29 项 band 测试仅调整共同路由、旧协议默认银河文案、原仓库最低兼容版本和默认图片断言；另增 3 项品牌测试。

实际本机存在 JDK 21 与 D:\env\Android\Sdk API 36，无需安装：app 47 项、Watch 6 项单元测试通过，两端 lintDebug 通过（有 warning，未声称零告警）；band 重新构建成功，node --test 为 61/61。git diff --check 通过。仅做编译和测试，未运行或安装用户 APK，未进行设备/真车验证。

## Follow-up 实施细化（2026-11-26）

维持带修改通过结论和已完成 P0–P2；本轮只修正槽位模型并增加领克通知语音。四个可配置槽位（7 选 4）分别配置容器、图标、progress，位置和车主确认的功能分离。候选：锁、车窗、发动机、快速降温、快速升温、天窗、后备箱；资源存在不能独立证明所有车型支持任意七选四，fr_ccqk 初始隐藏不能独立证明出厂三个功能。

运行时使用领克独立车主标记表，绑定签名与描述；无真机指纹数据，不用银河图标名或拼音推断。签名变化删除标记并使 token 失效。锁窗语音在分类及当前方向可核对时进入 prepare/token/13 秒穿戴确认；未知即拒绝，verify 仅提示手机官方卡片处理。其他功能语音仍关闭。实现及采集方法见 [compatibility.md](lynkco/compatibility.md)，场景见 [voice.md](lynkco/voice.md)。本轮仍无真机验收，版本保持 0.1.30。
