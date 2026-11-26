# 吉利汽车 App 3.58.0 接入调研

2026 年 11 月 26 日

## 结论

「吉利汽车」可以作为智在银河的第三个手机车况来源继续适配。这个版本有 Android 官方中号小组件，布局声明了续航、油电百分比、状态提示、更新时间、四个车控槽和官方确认层。现有伴侣的组件宿主、车况传输和穿戴端可复用，手机端需要新增吉利汽车适配。

当前 0.1.30 的品牌入口只有「银河」和「领克」。这次核对使用 APK 静态资源和本地源码，尚未在手机绑定该组件、登录账号或验证车辆响应。本次调研只新增本文档。

## APK 身份

| 样本 | 应用 | 包名 | 版本 |
| --- | --- | --- | --- |
| `base(1).apk` | 吉利汽车 | `com.geely.consumer` | 3.58.0（358000021） |
| `base.apk` | 吉利银河 | `com.geelygalaxy.customer` | 1.59.0（159000047） |

新样本 SHA-256 为 `C79861788034CE774806CC30982D65ACE4F49FA552F2DBC10F6A602B8D58EB03`。使用 Android SDK `aapt2` 核对包名、Manifest、资源表与编译后的布局，`apksigner verify` 返回成功。两个样本的签名证书摘要不同。吉利汽车包名与小米应用商店条目一致。 [吉利汽车应用说明](https://app.mi.com/details?id=com.geely.consumer)

## 中号卡接入

中号和小号 provider 分别为 `com.geely.consumer.carcontrol.widget.appwidget.Wg_Middle` 和 `com.geely.consumer.carcontrol.widget.appwidget.Wg_Small`。两者均由 Android 小组件框架管理，声明的更新周期为 30 分钟。中号最小尺寸为 390 × 110 dp，初始布局为 `wg_middle_unbound`。另有 `wg_middle_unable` 显示当前车辆不支持远程控制。小号为 140 × 140 dp，正常布局没有四个车控槽。 [Android 小组件宿主](https://developer.android.com/develop/ui/views/appwidgets/host)

| 能力 | APK 证据 | 适配和验证 |
| --- | --- | --- |
| 总续航 | `wg_middle_layout_mileage`、`wg_middle_text_mileage`、`wg_middle_text_mileage_km` | 为吉利汽车映射这三个 ID，过滤模板中的 `--`。 |
| 油量、电量 | 油电容器中分别有百分比文本和进度条。二者复用子 ID，布局默认值为 37% 和 36%。 | 按容器取值，燃油、纯电、混动车型只显示卡片实际提供的字段。分项续航公里数等待实机确认。 |
| 更新时间及状态 | `wg_tv_update_time`、加载节点和 `wg_alarm_container`。时间模板为「更新于03/15 12:00」。 | 现有日期解析支持斜杠格式，需针对该包识别模板并排除未收到有效回传的初始读数。车辆状态文案按实机卡片核对。 |
| 设置、刷新 | 设置 ID 同为 `wg_btn_setting`。刷新区域包含 `wg_view_ll_update_time`、`wg_iv_update_time` 和更新时间文本。 | 银河版的 `wg_btn_update_time` 未见于新包资源。绑定后观察官方实际设置的点击节点，之后用卡片回调验证刷新。 |
| 控制槽及确认 | 四个 `wgFun*Container` 均包含图标和加载态。确认层包括标题、取消、确认。 | 复用槽位扫描和官方确认处理。模板重复使用锁图标，取得有效卡片回传、确认槽位语义和动作方向后再启用操作。 |
| 功能图标 | 有黑白两套 `wg_*_lock/unlock`、`air`、`findcar`、`wind`、`ventilate` 资源。 | 车锁、空调、寻车可优先验证。通风相关的两组图标需以官方配置文案确定语义。银河版的图标命名规则和 11 项识别清单不能直接沿用。 |

星越 L、星瑞可作为首轮车型候选。吉利车型官方配置说明列有手机 App 远程控制，具体适用年款、配置和组件可选动作仍需用绑定车辆核对。 [星越 L 配置](https://xingyue.geely.com/) · [星瑞配置](https://preface.geely.com/)

## 其他入口

| 入口 | 调研结果 |
| --- | --- |
| 手机数字钥匙 | APK 含手机厂商数字钥匙适配痕迹。荣耀提供吉利汽车蓝牙钥匙的公开开通流程和适用车型，适合符合条件的车主在近车时使用。小米手环 10 Pro 与目标车型的原生钥匙组合缺少官方支持证据。 [荣耀钱包吉利汽车说明](https://www.honor.com/cn/support/content/zh-cn15880484/) · [小米手环 10 Pro 规格](https://www.mi.com/prod/xiaomi-smart-band-10-pro/specs) |
| App Link 或桌面快捷方式 | APK 有普通启动与 Android App Link 声明。静态检查未找到公开的第三方车况查询、车控快捷方式或车控 Intent 契约。普通启动可用于引导车主进入官方 App。 |
| 云端直连 | 应用商店确认官方 App 提供车况及远控。本轮查阅的公开资料未提供适用于该 App 的第三方车况、远控授权文档。 [吉利汽车应用说明](https://app.mi.com/details?id=com.geely.consumer) |
| 穿戴端、米家语音 | 两者可复用现有手机端转发方式。待该品牌的卡片、车型能力和动作映射验证后，再将同一来源接入穿戴或语音。 |

## 实施与验收顺序

1. 在手机伴侣新增「吉利汽车」品牌配置、包可见性和独立的组件绑定与授权记录。沿用现有 `GalaxyWidgets` 的通用宿主能力，按该包配置布局节点、初始态和不可用态。
2. 先接只读车况。在绑定一辆通过吉利汽车 App 管理的车后，对照手机桌面卡和伴侣卡验证续航、油电、状态、时间和刷新回传，再同步到手环或手表。
3. 核对官方卡片实际提供的功能、图标方向和确认行为后，再开目标动作。记录官方确认、发出指令和车辆结果，保留各自的提示。

现有实现入口为 `BrandConfig.java`、`GalaxyWidgets.java`、`WidgetTree.java` 和 `WidgetIcons.java`。与该包的差异主要集中在手机端，现有车况协议已能表达可选的总续航、油电里程和百分比字段。
