# 吉利银河 App 1.59.0 无小组件集成调研

2026 年 11 月 24 日 · 星耀 8 2025 款尊贵版 · Android 手机伴侣应用

本报告记录公开兼容性研究所使用的样本范围。

## 样本与证据范围

| 项目 | 证据 |
| --- | --- |
| APK | 用户本机安装包样本，包名 `com.geelygalaxy.customer`，版本 `1.59.0`，版本号 `159000047`，`minSdk=26`，`targetSdk=33`。SHA-256 `b74470a9f6e4537f779160eb05b1291b28782543b01bbe9e1414abd2beed02c6`。 |
| REA 包检查 | REA 6.10.1 与 headless JADX 0.7.1。`inspect-android-package` 在 `get_app_info` 阶段返回 `Root AndroidManifest.xml is not supported well-formed XML`。组件清单和行号来自 Android SDK 36.1.0 的 `aapt2 dump xmltree --file AndroidManifest.xml`。 |
| REA 类检查 | `search-android-classes` 的全量视图有 23 个 JADX 类名。按 `com.geely.galaxy` 和 `com.ecarx.blekey` 过滤均无匹配。`inspect-android-class` 按完整类名检查 `GalaxySplashActivity`、`LoginActivity`，均未返回类成员。 |
| 独立交叉核对 | 归档清单用于核对样本内容。APK 中唯一的 `classes.dex` 共有 45 条 `class_defs`、1,036 条可读取的索引字符串。类定义均属加固相关命名空间，38 个导出组件的实现类以及登录、车况页的实现类均未在这些定义中找到。 |
| 其他材料 | 检查 `resources.arsc`、相关布局和网络安全 XML、包内 180 个明文文本资产，并对 91 个原生库做定向字符串检查。加固载荷内的业务实现、运行时接收器、服务端配置和车型能力处于此证据范围之外。 |

本轮只读分析 APK 并写入本报告。应用启动、真实账号登录、银河业务接口请求、车辆指令以及设备或服务写入次数均为零。

## 任务一：导出组件与 Intent 可行性

共扫描 497 个 Manifest 组件声明，显式 `android:exported=true` 合计 **38 个**。未声明 `exported` 的 Activity、Service、Receiver 均无 `intent-filter`，按 Android 默认规则为非导出。Provider 全部显式声明了导出状态。无 Activity Alias，`<application>` 无统一的 `android:permission`。下表的行号指 `aapt2` 解析出的 Manifest XML 行号。权限栏的 `—` 表示组件及应用层均无该入口的 Manifest 权限声明。`未找到` 表示无法从可见类或已检查的资源确认所需额外参数。实际交付仍受系统版本、绑定条件及组件运行时检查影响。[Android 组件与 Intent 匹配规则](https://developer.android.com/guide/components/intents-filters)

| 类型 | 声明数 | `exported=true` | `exported=false` | 未声明 |
| --- | ---: | ---: | ---: | ---: |
| Activity | 365 | 16 | 195 | 154 |
| Activity Alias | 0 | 0 | 0 | 0 |
| Service | 64 | 14 | 29 | 21 |
| BroadcastReceiver | 33 | 7 | 23 | 3 |
| ContentProvider | 35 | 1 | 34 | 0 |
| 合计 | 497 | 38 | 281 | 178 |

### Activity（16）

| 行 | 完整组件名 | Intent filter（action、category、data） | 权限 | Extra 与直接调用判断 |
| ---: | --- | --- | --- | --- |
| 438 | `com.geely.galaxy.main.activity.GalaxySplashActivity` | `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`。另有 `android.intent.action.VIEW` + `android.intent.category.DEFAULT` / `android.intent.category.BROWSABLE`，`scheme=geelygalaxyconsumer`、`host=*`。 | — | 标准启动入口无需业务 extra。未找到自定义 URI 到车控页、车况数据或指令的映射。伴侣 App 可在用户操作时打开银河 App。 |
| 579 | `com.geely.galaxy.main.activity.GalaxyAliYunPushActivity` | `android.intent.action.VIEW` + `android.intent.category.DEFAULT` / `android.intent.category.BROWSABLE`，`scheme=agoo`、`host=com.geelygalaxy.customer`、`path=/thirdpush`。 | — | Extra 未找到。声明允许匹配的推送回调入口，未找到通用导航或控车契约。 |
| 1205 | `com.geelygalaxy.customer.wxapi.WXEntryActivity` | 无 | — | Extra 未找到。导出的微信回调类，直接启动的业务行为未确认。 |
| 1212 | `com.geelygalaxy.customer.wxapi.WXPayEntryActivity` | 无 | — | Extra 未找到。导出的微信支付回调类，未见车控契约。 |
| 1328 | `com.geely.snc.mod.rpa.galaxy.v2.RPAMainActivityV2` | 无 | — | Extra 未找到。名称及 `module_rpav20_*` 资源指向遥控泊车页面，Manifest 层可被显式寻址。车辆会话和页面行为未确认，与需求中的驻车状态查询分开。 |
| 1331 | `com.geely.snc.mod.rpa.galaxy.RPANavigationActivity` | 无 | — | Extra 未找到。泊车模块导航页可被显式寻址，未证明能读车况或执行 11 项操作。 |
| 1419 | `com.sensorsdata.analytics.android.sdk.dialog.SchemeActivity` | `android.intent.action.VIEW` + `android.intent.category.BROWSABLE` / `android.intent.category.DEFAULT`，`scheme=sa10caf24f`。 | — | Extra 未找到。统计 SDK 的 scheme 入口，未见车控契约。 |
| 1715 | `com.haohan.chargehomeclient.activity.HomeChargeActivity` | 无 | — | Extra 未找到。类名指向充电服务，可被显式寻址，业务会话和页面行为未确认。 |
| 1805 | `com.haohan.chargemap.activity.HomeMapActivity` | 无 | — | Extra 未找到。类名指向充电地图，可被显式寻址，未见车控契约。 |
| 1933 | `com.alipay.sdk.app.AlipayResultActivity` | `android.intent.action.VIEW` + `android.intent.category.DEFAULT` / `android.intent.category.BROWSABLE`，`scheme=charge_alipay`。 | — | Extra 未找到。支付结果回调，未见车控契约。 |
| 2197 | `com.geely.nfc.ui.galaxy.ui.AccessibilityGuideActivity` | `android.nfc.action.NDEF_DISCOVERED` + `android.intent.category.DEFAULT`，两组 data 分别为 `mimeType=application/vnd.geely.nfc.onetouch`、`scheme=nfcdemo` / `host=guide` / `path=/accessibility`。另有 `android.nfc.action.TECH_DISCOVERED`、`android.nfc.action.TAG_DISCOVERED`，均带 `android.intent.category.DEFAULT`。 | — | 业务 extra 未找到。声明指向 NFC 引导页，未确认普通伴侣 App 可提供所需的受信 NFC 上下文。 |
| 2285 | `com.unionpay.UPPayWapActivity` | 无 | — | Extra 未找到。支付 SDK 页面，未见车控契约。 |
| 2292 | `com.rrtx.rrcashiersdk.activity.ShowCashierActivity` | 无 | — | Extra 未找到。收银台 SDK 页面，未见车控契约。 |
| 2906 | `com.xiaomi.mipush.sdk.NotificationClickedActivity` | 无 | — | Extra 未找到。小米推送点击回调，未证实能作为车控或车况导航。 |
| 3199 | `com.alipay.sdk.app.PayResultActivity` | 无 | — | Extra 未找到。支付宝结果页，未见车控契约。 |
| 3522 | `com.meizu.mznfcpay.carkey.MeizuPayCarKeyReqActivity` | `com.meizu.mznfcpay.action.CAR_KEY_REQUEST` + `android.intent.category.DEFAULT`。 | — | Extra 未找到。类名和 action 只支持识别为钱包车钥匙请求入口，车主授权、调用方检查和实车动作未确认。 |

### Service（14）

| 行 | 完整组件名 | Intent filter action / category | 权限 | Extra 与直接调用判断 |
| ---: | --- | --- | --- | --- |
| 759 | `com.ecarx.blekey.thirdadapter.service.MiuiService` | `com.geely.geometry.miuiservice.action` | — | Extra、`onBind`、Binder 方法和调用方校验未找到。名称指向 MIUI 的 BLE 钥匙适配，只有服务声明层可达性。 |
| 2259 | `com.geely.gpt.nfc.service.ScreenshotAccessibilityService` | `android.accessibilityservice.AccessibilityService` | `android.permission.BIND_ACCESSIBILITY_SERVICE` | Extra 未找到。无障碍系统绑定入口，普通伴侣 App 无法凭 Intent 获得该绑定权限。 |
| 2650 | `com.vivo.car.networking.sdk.nearby.NearbyService` | `com.vivo.car.action.CONTROL` | `com.vivo.car.networking.CAR_PERMISSION` | Extra、Binder 方法及 OEM 权限授予条件未找到。需由持有声明权限的调用方使用，现有伴侣 App 无授予证据。 |
| 2704 | `com.geely.dk.samsungalivelib.SamsungAliveService` | `com.samsung.android.ble.foreground.service` + `android.intent.category.DEFAULT` | — | Extra 和运行时校验未找到。类名/action 指向三星 BLE 服务接入，未证明是控车接口。 |
| 2718 | `androidx.work.impl.background.systemjob.SystemJobService` | 无 | `android.permission.BIND_JOB_SERVICE` | Extra 未找到。WorkManager 系统任务入口，`enabled` 引用的默认资源为 `true`，绑定受系统权限保护。 |
| 2809 | `com.geely.icc.ryalivelib.RongYaoAliveService` | `com.hihonor.wallet.bleAgent.scanResult` + `android.intent.category.DEFAULT` | — | Extra 和运行时校验未找到。声明与荣耀钱包 BLE 扫描结果有关，未证明能执行车控。 |
| 2879 | `com.heytap.msp.push.service.CompatibleDataMessageCallbackService` | `com.coloros.mcs.action.RECEIVE_MCS_MESSAGE` | `com.coloros.mcs.permission.SEND_MCS_MESSAGE` | Extra 未找到。OEM 推送回调，需要声明权限。 |
| 2888 | `com.heytap.msp.push.service.DataMessageCallbackService` | `com.heytap.mcs.action.RECEIVE_MCS_MESSAGE`、`com.heytap.msp.push.RECEIVE_MCS_MESSAGE` | `com.heytap.mcs.permission.SEND_PUSH_MESSAGE` | Extra 未找到。OEM 推送回调，需要声明权限。 |
| 2929 | `com.xiaomi.mipush.sdk.PushMessageHandler` | 无 | `com.xiaomi.xmsf.permission.MIPUSH_RECEIVE` | Extra 未找到。小米推送服务，需要声明权限。 |
| 2958 | `com.vivo.push.sdk.service.CommandClientService` | 无 | `com.push.permission.UPSTAGESERVICE` | Extra 未找到。vivo 推送客户端服务，需要声明权限。 |
| 3118 | `com.huawei.hms.support.api.push.service.HmsMsgService` | `com.huawei.push.msg.NOTIFY_MSG`、`com.huawei.push.msg.PASSBY_MSG` | — | Extra 与运行时校验未找到。HMS 推送处理服务，未见车控契约。 |
| 3174 | `com.meizu.cloud.pushsdk.NotificationService` | 无 | `com.meizu.cloud.push.permission.MESSAGE` | Extra 未找到。魅族推送服务，需要声明权限。 |
| 3534 | `com.meizu.mznfcpay.carkey.service.CarKeyService` | `com.meizu.mznfcpay.carkey.service.CarKeyService` | — | Extra、Binder 接口和钱包授权条件未找到。声明可被寻址，未证实普通伴侣 App 能发车辆操作。 |
| 3541 | `com.samsung.android.spay.vas.digitalkeycn.sdk.SDigitalKeyWakeupService` | `com.samsung.android.sdk.action.WAKE_UP` + `android.intent.category.DEFAULT` | — | Extra 和调用方校验未找到。声明指向三星数字钥匙唤醒，未找到车辆操作契约。 |

### BroadcastReceiver（7）

| 行 | 完整组件名 | Intent filter action / category | 权限 | Extra 与直接调用判断 |
| ---: | --- | --- | --- | --- |
| 2659 | `com.coloros.ocs.carlink.inner.OplusCarReceiver` | `oppo.intent.action.opluscarlink.INIT_OPLUS_SDK` | `com.oppo.permission.safe.BLUETOOTH` | Extra 与权限授予条件未找到。声明指向 Oplus 车联 SDK 初始化，接收端检查 OEM 权限。 |
| 2794 | `androidx.work.impl.diagnostics.DiagnosticsReceiver` | `androidx.work.diagnostics.REQUEST_DIAGNOSTICS` | `android.permission.DUMP` | Extra 未找到。系统诊断权限入口，未见车控契约。 |
| 2843 | `androidx.profileinstaller.ProfileInstallReceiver` | `androidx.profileinstaller.action.INSTALL_PROFILE`、`androidx.profileinstaller.action.SKIP_FILE`、`androidx.profileinstaller.action.SAVE_PROFILE`、`androidx.profileinstaller.action.BENCHMARK_OPERATION` | `android.permission.DUMP` | 样本中的 Extra 未找到。运行时性能工具入口，与车控无关。 |
| 2944 | `com.alibaba.sdk.android.push.MiPushBroadcastReceiver` | `com.xiaomi.mipush.RECEIVE_MESSAGE`、`com.xiaomi.mipush.MESSAGE_ARRIVED`、`com.xiaomi.mipush.ERROR` | — | Extra 未找到。消息回调，未见车控契约。 |
| 2983 | `com.alibaba.sdk.android.push.MeizuPushReceiver` | `com.meizu.flyme.push.intent.MESSAGE`、`com.meizu.flyme.push.intent.REGISTER.FEEDBACK`、`com.meizu.flyme.push.intent.UNREGISTER.FEEDBACK`、`com.meizu.c2dm.intent.REGISTRATION`、`com.meizu.c2dm.intent.RECEIVE` + category `com.geelygalaxy.customer` | — | Extra 未找到。消息及注册回调，未见车控契约。 |
| 3092 | `com.huawei.hms.support.api.push.PushMsgReceiver` | `com.huawei.intent.action.PUSH_DELAY_NOTIFY`、`com.huawei.intent.action.PUSH` | `com.geelygalaxy.customer.permission.PROCESS_PUSH_MSG` | Extra 未找到。该权限在 APK 中声明为 `signature`，常规第三方签名不满足。 |
| 3105 | `com.huawei.hms.support.api.push.PushReceiver` | `com.huawei.android.push.intent.REGISTRATION`、`com.huawei.android.push.intent.RECEIVE` | `com.geelygalaxy.customer.permission.PROCESS_PUSH_MSG` | Extra 未找到。与上一项受相同签名权限保护。 |

### ContentProvider（1）

| 行 | 完整组件名 | Authority / Intent filter | 权限 | 参数与直接调用判断 |
| ---: | --- | --- | --- | --- |
| 3130 | `com.huawei.hms.support.api.push.PushProvider` | `com.geelygalaxy.customer.huawei.push.provider`，无 filter | 读 `com.geelygalaxy.customer.permission.PUSH_PROVIDER`，写 `com.geelygalaxy.customer.permission.PUSH_WRITE_PROVIDER`，两者均为 `signature` | Provider 通过 `ContentResolver` 和内容 URI 访问，无车控 Intent extra。车况路径和 provider 方法未找到。Manifest 未声明 URI 临时授权或路径级例外。 |

### 与车控最接近的样本证据

- `GalaxySplashActivity` 的自定义 scheme 是真实的系统匹配声明。其 `onCreate`、`onNewIntent` 及内部路由实现未找到。包内 `assets/mock/pageEntry.json`、`assets/mock/routeEntry.json` 主要包含 `LYNKCO_APP_*` 路由，未找到与这个启动页及 11 项车控相连的调用点。
- 两个银河 Widget Receiver（行 603、623）及 `WgConfigSettingActivity`（行 640）均为 `exported=false`。Widget 生命周期和点击 action 属于这些接收器，未见同名的导出车控入口。`RemoteViewsClickReceiver`（行 1404，action `com.geely.cockpit.ble.RemoteViewClick`）也明确非导出。
- `DigitalKeyManageActivity`（行 694）、`CarStatusActivityNew`（行 714）、`CarStatusInformActivity`（行 719）均为非导出。已查找导出声明及可见类，未找到对外的车况 URI、回传 Intent 或查询参数。Manifest 未发现 `android.app.shortcuts` 声明，运行时动态快捷方式未验证。
- MIUI、vivo、Oplus、魅族、三星和荣耀的条目表明 APK 声明了对应的车联或数字钥匙入口。当前证据可以列出系统层可见性及权限，无法核实绑定协议、车主会话和能执行的功能。尤其 `CONTROL` 这个 action 名称本身无法证明可控制星耀 8。

**任务一结论**：可落实的是由用户打开银河 App，使用其标准启动入口。11 项车控和四项车况均未找到经样本验证的公开直接 Intent 契约。目前也无法确认自定义 scheme 能否跳到指定的官方车控页。

## 任务二：云端登录、控车与车况

### 账号、令牌和证书

| 调研项 | 样本中找到的证据 | 云端契约结论 |
| --- | --- | --- |
| 银河账号和登录方式 | Manifest `com.geely.galaxy.uc.user.activity.LoginActivity`（行 969）、`OneKeyLoginDelayActivity`（行 1025）、`com.mobile.auth.gatewayauth.LoginAuthActivity`。`layout/fragment_sms_login`、`layout/fragment_password_login` 及 `text_use_self_phone_num_login` 表明有验证码、密码和本机号码一键登录界面。`login_username_hint` 提到手机号、邮箱名、用户名。 | 登录 UI 已确认。第三方授权协议、账号校验请求、接口方法及参数未找到。 |
| 验证码和设备身份 | `layout/fragment_sms_login_auth`、`layout/activity_secure_verify_message_black`；`login_device_login_title` 描述新设备登录银河账号需进行身份验证，`login_device_manage_title` 指向设备管理。 | 设备验证场景有静态证据。挑战发放、绑定方式和第三方设备准入条件未找到。 |
| Token 获取与刷新 | 直接扫描公开 DEX 的 1,036 条索引字符串，并检查包内明文配置和脚本。REA/JADX 未返回可定位的银河登录实现。 | 登录和刷新 endpoint、token 字段、作用域、有效期、轮换和撤销契约均未找到。 |
| 环境与地址 | `assets/conf/AppEnvConf.json` 有 `apiBaseUrl`、`httpBaseUrl`、`h5BaseUrl`，覆盖七个环境。前两项为根地址，H5 项带路径前缀。`assets/CarEnvConf.json` 与 `assets/CarEnvConfNew.json` 记录 `otaApiBaseUrl`。 | 只能确认环境配置字段，未找到根地址、客户端方法和具体的登录或车辆 API 路径之间的映射。 |
| 请求签名、证书与 TLS | Manifest 指向 `xml/network_security_config`（`res/4uC.xml`）。XML 的 `base-config` 信任系统 CA，存在两个域名配置，未见 `pin-set`。APK 另含 `.bks`、`.jks` 资产，名称分布在 GRS/HMS、NIM 和更新 SDK。 | 未找到银河业务请求的签名算法、签名输入或客户端证书需求。网络安全 XML 的系统 CA 和 APK 应用签名证书各有自己的用途。本轮未找到这些证书资产与云端车控客户端身份的调用关系。 |
| 车控操作密码或二次确认 | 样本有身份验证短信页面、组件确认布局和个别功能确认文案。`text_tips_input_password` 和 `tips_input_password` 的文案明确指向支付密码。 | 未找到可验证的车控操作密码或 PIN 字段、校验接口、适用车型和要求密码的操作列表。 |

`assets/car_control_config.json` 与 `assets/car_control_config_READEME.md` 记录超时、默认温度、BLE 状态判定等 SDK 配置。`assets/control/assets/js/main.bd702fffad44ac57edff.js` 中的 `openDoor`、`openWindow` 命中出现在三维场景或动画附近，同一资源包含 `GLTFLoader`。这些来源能说明配置和渲染用途，未提供请求契约。原生库定向字符串检查也未建立银河控车或车况请求的调用链。

### 11 项车控接口逐项核对

以下证据为 `resources.arsc` 中的 UI 资源标识。每项都检查了可见类、公开 DEX 字符串和明文资产中的业务调用点。HTTP 方法、路径、请求体、车辆授权参数及回执仍缺可信的样本证据。

| 功能 | APK 证据 | 云端 endpoint / 参数 |
| --- | --- | --- |
| 车锁 | `car_control_success_door_unlock`、`car_control_success_lock`，另有 `_ble` 文案 | 未找到。 |
| 空调 | `car_control_success_air_open`、`car_control_success_air_close`、`car_control_success_air_update` | 未找到。默认温度配置无法证明请求体字段。 |
| 车窗微开 | `car_control_success_win_micro_open` | 未找到。 |
| 后备箱 | `car_control_wg_trunk_open_title`、`car_control_wg_trunk_unlock_title`、`car_control_success_trunk_unlock_two` | 未找到。UI 区分打开与解锁，对应的业务参数待确认。 |
| 寻车 | `car_control_success_find_car`、`car_control_success_find_car_ble` | 未找到。 |
| 车窗全开 | `car_control_success_win_open`、`car_control_success_win_open_ble` | 未找到。 |
| 极速升温 | `car_control_success_fast_warming`、`car_control_success_fast_warming_close` | 未找到。 |
| 极速降温 | `car_control_success_fast_cooling`、`car_control_success_fast_cooling_close` | 未找到。 |
| 净化 | `car_control_success_purification_open`、`car_control_success_purification_close` | 未找到。 |
| 除霜 | `car_control_success_defrost_open`、`car_control_success_defrost_close`、`car_env_defrost_label` | 未找到。 |
| 哨兵模式 | `car_control_success_vsmode_open`、`car_control_success_vsmode_close` | 未找到。 |

### 车况查询逐项核对

| 字段 | APK 证据 | 云端 endpoint / 返回字段 |
| --- | --- | --- |
| 总续航 | `layout/car_view_mileage` 与 `layout/car_view_one_mileage` 的 `id/tv_mix_car_can_go_num`。 | 未找到。 |
| 纯电续航 | 同一布局的 `id/tv_car_can_go_num_electricity`，以及 `string/car_charge_mileage`。 | 未找到。 |
| 燃油续航 | 同一布局的 `id/tv_car_can_go_num_oil`。 | 未找到。 |
| 驻车状态 | 样本的 `layout/car_wg_item_layout_warn_text` 与 `id/wg_tv_warn_text` 有“已驻车”模板。独立车况页类名见 Manifest 行 714、719。 | 未找到。无法把 UI 文案绑定到状态接口、P 挡字段或某个服务端枚举。 |

### 可行性与风险

**任务二结论**：基于本样本证据，伴侣 App 直连只能保留为待接入路线。真正落地需要取得适用星耀 8 的官方第三方接入授权，及登录与续期、车辆授权、车控请求与回执、车况字段、签名和二次确认条件的文档。当前未找到能代表这些契约的类、方法或 endpoint，无法确认四项只读车况可否独立授权。

- **接口变更**。环境地址存在于包内配置，当前缺少对第三方的版本和兼容承诺。客户端升级后，路径、字段、签名或功能开关有变化风险，车控还需能区分受理、最终执行及失败。
- **账号风控**。样本明确存在新设备身份验证和登录设备管理界面。伴侣作为新客户端可能遇到身份校验或设备授权限制。触发条件、限流、会话失效和封禁策略未验证。
- **服务条款**。登录布局含 `textPWDAcceptPrivacyPolice` / `textSMSAcceptPrivacyPolice`，Manifest 有 `RegisterProtocolActivity`。公开的吉利银河应用介绍列出车控和车况，可用于核实产品能力。第三方直连、凭据共享、个人信息处理及分发的授权范围仍需核对该版本协议和厂商接入条款。[小米应用商店的吉利银河应用说明](https://app.mi.com/details?id=com.geelygalaxy.customer)

待验证项包括自定义 scheme 的实际路由、OEM 车钥匙组件的调用方与参数校验、星耀 8 的账户与车辆授权条件，以及云端接口和返回字段。以上只根据 APK 本地静态证据判断。公开网页只用于核对 Android 平台规则和应用公开说明。

## 参考

- [REA 静态 Android APK 分析](https://github.com/morluto/rea/blob/main/docs/guides/android/android-analysis.md)
- [Android Activity](https://developer.android.com/guide/topics/manifest/activity-element) · [Service](https://developer.android.com/guide/topics/manifest/service-element) · [Receiver](https://developer.android.com/guide/topics/manifest/receiver-element) · [Provider](https://developer.android.com/guide/topics/manifest/provider-element)
- [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
