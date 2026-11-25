# 领克 App 小组件发现文档

> 目的：把“领克小组件长什么样”从未知变成 `BrandConfig` 里能填的真实值。
> 状态（2026-11-25）：包名已核实；车主包 4.2.9 已静态解析并填入 `BrandConfig`；4.3.0 小组件面交叉核对一致；真机点按未做。
> 原则：**只填实测到的值，不编造 provider 名、资源名、包名。** 拿不到的留空，代码会退化为手动标记模式。

## 0. 已确认 vs 未确认

| 项目 | 状态 | 依据 |
| --- | --- | --- |
| 中国版领克 App 包名 `com.lynkco.customer` | ✅ 已确认 | 安信 APK 4.3.0（2026-11-03）、360 应用 4.2.5、小米应用商店详情页、应用宝搜索结果 |
| 中国版当前版本 | ✅ 映射以车主包 **4.2.9** 解析为准；4.3.0 镜像包小组件面交叉核对一致；已知 4.3.1（2026-11-17）未取到包，需复核 | 见 §1 |
| 桌面小组件**存在** | ✅ 已确认 | 车主 2026-11-25 确认。网上证据薄弱：2022 年博客称某版有车窗/解锁组件；2023 年车主反馈称华为/苹果端没有小组件；官方渠道未提及 |
| 海外版包名 `com.lynkco.prod.lynkco_app_android`（2.69.1）、`com.lynkco.intlCustomer` | ✅ 已确认 | apkfab / Google Play。**不同应用，不是本项目目标** |
| 米家包名 `com.xiaomi.smarthome` | ✅ 已确认 | 多来源；小米应用商店详情页 |
| 小米运动健康包名 `com.xiaomi.wearable`（现用 `com.mi.health`） | ✅ 已确认 | 多来源；`XiaomiLink` 会依次探测两个包名 |
| LynkCo Watch 手表 App 的 Android 包名 | ❌ 未找到 | 只有 App Store 列表（id1599229107，Halvie Network Technology，v1.2.3，2024-12-05）与车主论坛描述；没有任何 APK 镜像收录。需从车主已装手机 `adb shell pm list packages` 或手表配对二维码取得 |
| 小组件 provider 类名 | ✅ 已确认：`ListWidgetProvider`（中型）/ `SmallWidget`（小型） | 4.2.9 解析，见 §1 |
| 小组件布局名、槽位数、设置/刷新节点 | ✅ 已确认：`layout_widget`（62 节点/56 id，4 槽位）、`layout_small_widget`（42 节点/39 id，仅车况）；设置 `ic_bj_a`、刷新 `ic_widget_sx` | 4.2.9 解析，见 §1 |
| 车况字段资源名（续航、电量、驻车状态、更新时间） | ✅ 已确认：`tv_mileage`/`txt_km`、`progress_bar_electricity`/`tv_electricity`、`progress_bar_h`/`txt_bar`、`condition_txt`、`time` | 4.2.9 解析，见 §1 |
| 功能图标资源名前缀与名表 | ❌ 未确认（候选集 `selector_widget_control_*` 7 个；选择器是 StateListDrawable，不能直接作位图比对；kjck/ccqk 对应哪个待真机） | 待 §3 |
| 二次确认形态（A 卡内确认 / B 跳 App·指纹 / C 首次授权）与每动作范围 | ⚠️ **A 静态存在**（`verify` 浮层，标题“确认解锁车辆吗？”，确定/取消）；触发条件与是否还有跳 App/指纹待真机 | 见 §5、§4 的真机点按 |
| 4.3.0 与 4.2.9 小组件面差异 | ✅ **无差异**：provider/布局 id 集/确认视图/图标候选集完全一致 | androguard 4.1.4 交叉核对，见 §1 |
| 官方云 API（中国） | ❌ 无公开 API | 欧洲 HA 集成不适用中国车；中国云只有 2021–22 年 Scriptable 小组件证明可用 GKUI 账号登录，无可复用的 Android/Python 客户端 |

## 1. APK 取得与校验

中国版 4.3.0 APK（安信 APK 历史版本页）：

- 详情页：https://anxinapk.com/app/52570286/%E9%A2%86%E5%85%8B（v4.3.0，2026-11-03，295.7 MB，开发商杭州吉利易云科技有限公司）
- 直链（腾讯应用宝 CDN）：`https://imtt2.dd.qq.com/sjy.00009/sjy.00004/16891/apk/9935F48B999F6E8C7D82BF3EF221F80B.apk?fsname=com.lynkco.customer_4.3.0.apk`
- 期望 MD5：`9935F48B999F6E8C7D82BF3EF221F80B`（与手手机网列表交叉核对一致）
- 4.2.8 直链（备用）：`https://imtt2.dd.qq.com/sjy.00009/sjy.00004/16891/apk/E3897991DD5C6E47419F59819CCE79C0.apk?fsname=com.lynkco.customer_4.2.8.apk`

**下载注意：该 CDN 有防盗链。** 不带 Referer 或 Referer 为非 qq.com 域名时返回 HTTP 567（7 KB 错误页）；Referer 用 `https://sj.qq.com/appdetail/com.lynkco.customer` 或 `https://imtt.qq.com/` 时返回 200。下载命令：

```bash
curl -L --fail -A "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36" \
  -e "https://sj.qq.com/appdetail/com.lynkco.customer" \
  -o com.lynkco.customer_4.3.0.apk \
  "https://imtt2.dd.qq.com/sjy.00009/sjy.00004/16891/apk/9935F48B999F6E8C7D82BF3EF221F80B.apk?fsname=com.lynkco.customer_4.3.0.apk"
md5sum com.lynkco.customer_4.3.0.apk   # 应为 9935F48B999F6E8C7D82BF3EF221F80B
file com.lynkco.customer_4.3.0.apk     # 应为 Android application package
```

**试过但走不通的渠道（不要重复踩）：** 酷安详情页直接打开返回 567；豌豆荚下载页连接挂起无响应；安信 APK 的下载按钮本身就是上面这条应用宝直链（先 567、加 Referer 后通）。如果直链失效，备选是请领克车主从自己手机导出已安装 APK（`adb pull` 或应用备份），与从应用宝下载的 1.59.0 银河 `base.apk` 来源相同。

### APK 解析结果（车主包 4.2.9，2026-11-25 完成）

> **下载已完成并校验通过（2026-11-25 15:13 CST）：** 上述直链下载得到 `com.lynkco.customer_4.3.0.apk`，310,048,868 字节，`file` 判定为 Android package (APK)，MD5 `9935f48b999f6e8c7d82bf3ef221f80b` 与期望值一致，SHA-256 `4c5855a73b2fd955aae51a0693414fdc2ea41d41b7891fbb7af9188f9d13acd3`。该镜像包仅作交叉核对（见下），映射以车主包为准。
>
> **解析来源：** 车主提供的领克 App 安装包 `C:\Users\zoy\Desktop\base.apk.1`，在 zoy-laptop 上由 Codex 用 aapt2 + Node 静态解析（未安装、未运行、未上传）。解析报告与原始 XML 在 `~/workspace/lynkco-apk-analysis/parse-4.2.9/`（`lynk-widget-parse.md`、`summary.json`、`drawable-names.txt`、`xml-widget_provider.xml`、`xml-widget_small_provider.xml`、`layout-layout_widget.xml`、`layout-layout_small_widget.xml`）。
>
> 注意：领克 App 疑似加固（APKiD 曾把 3.4.3 识别为几维安全 KiwiVM 壳，与银河 1.56.1 相同）。加固只影响 dex 静态分析，**不影响** AndroidManifest.xml 与 resources.arsc 的读取——provider、布局名、资源名都在这两处，不需要脱壳。

- [x] 包名 / versionName / versionCode / 文件校验：`com.lynkco.customer`，**4.2.9**，versionCode 402090100，317,216,307 字节，SHA-256 `62f87947a43090620fb96d058a5092a037093f1a57cfc37966a2bea4f3ab53c3`，MD5 `4bc359ea35688e5d86b1a50d38a9c3e9`，15,906 个 ZIP 条目，CRC32 全部通过，1 个 DEX（`classes.dex`），minSdk 24 / targetSdk 30。
- [x] AppWidgetProvider receiver：两个，均为 `exported=false`：
  - `com.lynkco.vehicle.widget.ListWidgetProvider`（中型）— meta-data `@xml/widget_provider`（res/VER.xml）
  - `com.lynkco.vehicle.widget.SmallWidget`（小型）— meta-data `@xml/widget_small_provider`（res/FLF.xml）
- [x] appwidget-provider XML：中型 350×110dp，小型 110×110dp；两者 updatePeriodMillis=1200000（20 分钟），widgetCategory=home_screen（0x00000001），**无 configure**，无 APPWIDGET_CONFIGURE activity，无 targetCellWidth/Height、previewLayout。预览图：中型 `@mipmap/pic_02`，小型 `@mipmap/pic_01`。
- [x] 初始布局与控件 ID：
  - `layout/layout_widget`（中型，res/rcW.xml，62 节点、56 个 id）：4 个车控槽位 `fr_unlock`（+`pb_unlock`、`unlock`）、`fr_window`（+`pb_window`、`window`）、`fr_kjck`（+`pb_kjck`、`kjck`）、`fr_ccqk`（+`pb_ccqk_on`、`ccqk_on`，初始隐藏）；车况 `tv_mileage`/`txt_km`（续航）、`progress_bar_h`/`txt_bar`（燃油）、`progress_bar_electricity`/`tv_electricity`（纯电）、`time`、`address`、`widget_name`、`condition_txt`、`car_img`；刷新 `fr_sx`/`ic_sdsx`/`ic_widget_sx`；设置 `ic_bj_a`；确认浮层 `verify`（见 §5）；占位文本 `time` "--"、`address` "--"、`tv_mileage` "--"、`txt_bar` "--L"、`tv_electricity` "--%"、`widget_name` "我的爱车"、`tv_loading` "正在恢复连接..."。初始隐藏（gone）：`rl_widget_cal_state`、`fr_sx`、`fr_ccqk`、`verify`（clickable=true）、全部 `pb_*`；`ll_loading_auth` 初始 gone、内层可见。
  - `layout/layout_small_widget`（小型，res/k8B.xml，42 节点、39 个 id）：仅车况（`small_widget_button` "探索领克" + 状态字段），无车控槽位——**不是本项目目标**。
  - 槽位与功能（静态证据）：`fr_unlock`/`unlock` → 车锁；`fr_window`/`window` → 车窗；`fr_kjck`/`kjck` 与 `fr_ccqk`/`ccqk_on` 功能未证实（拼音首字母猜测未经证实），以真机点按为准。
  - 主启动 Activity：`com.geely.lynkco.main.activity.LynkCoEntranceActivity`。导出组件：25 个 Activity、14 个 Service、10 个 Receiver、1 个 Provider。
  - Receiver action 集（静态）：中型 43 个（含 Lock/Locks、`remote_control_Lock(s)`、window(s)、`honk.light(s)`、REFRESH_WIDGET、LOCK_ACTION/UNLOCK_ACTION、Confirm/cancel、verify、fun1/fun2/fun3 等），小型 32 个（无 fun1/fun2/fun3、verify、Confirm、cancel 等）。action 名不等于已验证的控制协议（无 dex 业务分析）。
- [x] 图标资源名前缀与功能图标名表：候选集 `selector_widget_control_*`（lock / window / sky_window / trunk / engine / fast_up_temperature / fast_down_temperature）+ `icon_widget_empty_fun` / `icon_widget_empty_fun2`（687 个关键词匹配的 drawable 名见 `drawable-names.txt`）。选择器是 StateListDrawable，不是位图，且哪个选择器对应 kjck/ccqk 是运行时状态——`ICON_RESOURCE_PREFIX` 仍为 null，见 §3。

### 4.3.0 交叉核对（2026-11-25 完成，androguard 4.1.4）

镜像包 `com.lynkco.customer_4.3.0.apk`（310,048,868 字节，MD5 `9935f48b999f6e8c7d82bf3ef221f80b`，SHA-256 `4c5855a73b2fd955aae51a0693414fdc2ea41d41b7891fbb7af9188f9d13acd3`，versionCode 403101250）与 4.2.9 的小组件面**完全一致**：

- 同两个 provider 类名、同 meta-data 资源名（`widget_provider` / `widget_small_provider`）、同 provider XML 属性（350×110 / 110×110、1200000、预览图 mipmap id `@0x7f0f07ba` / `@0x7f0f07b9` 与 4.2.9 相同）。
- 同两个 receiver action 集（中型 43 个、小型 32 个，逐项相同）。
- 同两个布局文件（res/rcW.xml、res/k8B.xml），id 集逐项相同（中型 56 个、小型 39 个，`identical=True`）。
- 确认浮层（`verify`/`tv_verify`/`tv_cancel`）与 9 个候选图标名均存在。
- 差异仅限版本与资源编号重排：versionName 4.3.0 vs 4.2.9、versionCode 403101250 vs 402090100、布局资源 id 数值变化（中型 initialLayout `@0x7f0d078d` vs 4.2.9 `@0x7f0d0788`；小型 `@0x7f0d0761` vs `@0x7f0d075c`，R8 重排，非布局内容变化）、包大小/MD5 不同。
- 结论：同一份映射适用于 4.2.9 与 4.3.0。4.3.1 未取到包，升级后仍需复核。

## 2. APK 解析步骤（androguard）

环境：Python 3 + androguard 4.1.4（`python3 -m venv venv && ./venv/bin/pip install androguard`；不要用系统 pip，PEP 668 会拦截）。

```python
from androguard.misc import AnalyzeAPK
a, d, dx = AnalyzeAPK("com.lynkco.customer_4.3.0.apk")
print(a.get_package(), a.get_androidversion_name(), a.get_androidversion_code())
# 1. 列出全部 receiver，找 intent-filter 含 android.appwidget.action.APPWIDGET_UPDATE 的
for name in a.get_receivers():
    print(name)
# 2. 对命中的 receiver 读 meta-data：android.appwidget.provider 指向的 XML 文件
# 3. 用 a.get_android_resources() 转储 layout / mipmap / drawable 的资源名，
#    筛含 widget / wg / fun / car / lock / climate 等关键字的条目
# 4. 解析命中的 appwidget-provider XML 与初始布局 XML，记录 §1 表格的字段
```

输出物：把 §1 的待补充表格填满，并把 provider XML 与初始布局的关键片段（去敏后）贴到本节下方。**只记录真实解析到的名字**；解析不到就写“未找到”，不要猜。

## 3. 图标资源转储（填 `WidgetIcons` 名表用）

**4.2.9 静态解析已给出的候选集（2026-11-25）：** `selector_widget_control_*`——lock、window、sky_window、trunk、engine、fast_up_temperature、fast_down_temperature——另有 `icon_widget_empty_fun`、`icon_widget_empty_fun2`（空槽位占位图标）。687 个关键词匹配的 drawable 名见 `~/workspace/lynkco-apk-analysis/parse-4.2.9/drawable-names.txt`，另有 `icon_car_control_*` / `icon_usp_car_control_*` 系列（lock_open/close、window_open/close、trunk_open/close、defrost_open/close、sentinel_open/close、fast_cool、fast_up/down_climate、find_car 等）。

**为什么还不能填 `ICON_RESOURCE_PREFIX`：** `selector_widget_control_*` 是 StateListDrawable 选择器（XML），不是位图；`WidgetIcons` 逐像素比较需要 BitmapDrawable。且哪个选择器对应 kjck/ccqk 是运行时状态——静态解析只能列出候选，不能定映射。填前缀前必须完成下面的目视比对：

1. 转储 APK 的全部 mipmap / drawable 资源名（androguard `get_android_resources()` 或解压后读 `resources.arsc`）。
2. 筛出疑似功能图标的名字（通常带 `fun` / `icon` / `lock` / `climate` / `window` / `trunk` / `find` / `heat` / `cool` / `clean` / `defrost` / `sentry` 等词），按前缀分组，确认公共前缀（即 `BrandConfig.ICON_RESOURCE_PREFIX`）。
3. 目视比对：把每个候选图标解出来，与真机卡片上该功能的开/关状态逐一对照，确认“开/关”两态分别对应哪个资源名，再写进 `WidgetIcons.load()` 的名表（开态与关态分开列，方向用 `OPEN` / `CLOSE` 区分）。
4. 领克的锁是**单按钮切换**：先确认锁图标有几种状态（开/关各一，还是同一图标靠文字区分），再决定 `Match` 的 `lockState` 怎么填，不要照搬银河的双图标逻辑。

## 4. 真机三步测试与点按矩阵

在一台已安装**中国版领克 App 4.3.x**、已登录、已绑定实车的 Android 手机上（最好是车主自己的主力机与车型）做以下三步：

1. **长按桌面 → 小组件列表**，看有没有“领克”；或用伴侣 App 的组件枚举打印 `com.lynkco.customer` 的 provider 列表（ComponentName、尺寸）。——回答“有没有小组件、provider 是谁”。
2. 把小组件加到桌面，**逐个点每个控件**，记录每次的结果（见下表）。——回答“二次确认是哪种形态、对哪些动作、每次都有还是首次”。
3. 若第 1 步有小组件，把该卡托管进一个测试宿主（可用本项目的 `LynkWidgets`），看 RemoteViews 树能否读到文本/图标节点。——回答“映射工作量有多大”。

### 点按矩阵（每格填：直接执行 / 卡内确认 / 跳 App / 指纹·密码 / 首次授权 / 无此控件）

| 动作 | 桌面点按结果 | 备注 |
| --- | --- | --- |
| 车锁 | | 单按钮切换；2023 年车主反馈“个别情况需要连续按两下” |
| 空调 | | App 内远程空调本身就有“点击确认”步骤（汽车之家领克 08 帖），需确认小组件是否同样 |
| 车窗微开 | | |
| 后备箱 | | 远程开后备箱尚未在可靠来源中确认为 App 功能，先确认卡上有无此控件 |
| 寻车 | | |
| 车窗全开 | | |
| 极速升温 | | |
| 极速降温 | | |
| 净化 | | |
| 除霜 | | |
| 哨兵模式 | | |
| 刷新（更新时间） | | |
| 设置（齿轮） | | |

同时记录：卡片尺寸（dp）、槽位数、车况字段原文（总续航/纯电/燃油/驻车状态/更新时间的显示方式）、占位卡（未登录/无车时）的显示值、锁图标有几种状态。

## 5. 二次确认：形态与代码现状

| 形态 | 表现 | 代码现状 |
| --- | --- | --- |
| A. 卡内确认 | 点控件 → 卡片翻转为确认态，卡上有“确认/取消”按钮 | **降级路径已实现**：`LynkWidgets.hasOfficialConfirmation()` 检测到确认态后，`LynkApp` 报「请在手机确认领克操作」，把托管卡留在前台 + 发通知入口，由车主在手机上点确认/取消。**不自动点**。 |
| B. 跳 App / 指纹·密码 | 点控件 → 打开领克 App 或弹出指纹/密码验证 | **无法代点**：performClick 只能点托管视图内的节点；指纹/密码不能代为完成。退化为与 A 相同的“手环发起、手机确认”。**不实现无障碍代点、不绕过指纹。** |
| C. 首次授权 | 只在首次绑定或登录过期时出现一次 | 绑定时在手机上完成一次即可。 |

**未来选项（需产品决策，默认关闭）：形态 A 的自动点卡内确认。** 确认按钮仍是 RemoteViews 内的节点，performClick 能点到，代价是多一次点击 + 一次卡片刷新等待，并要识别确认态布局、处理超时/取消/状态变化后旧确认失效。安全上：手环已有一次确认，再自动点官方确认等于用一次手环确认抵两次官方确认——**对车锁默认不自动点**；低风险动作是否开启，由车主在设置里明确授权后才能做。实现前先在 `BrandConfig` 加开关，并在设置页写明含义。

**Form A 的静态证据（4.2.9，2026-11-25）：** `layout_widget` 内存在确认浮层：`RelativeLayout @id/verify`（初始 gone、clickable=true）→ `ly_confirm` → 标题 `ty_confirm_title`（静态文本“确认解锁车辆吗？”）、`tv_verify`（“确定”）、`tv_cancel`（“取消”）。`BrandConfig.CONFIRM_LAYOUT/ACCEPT/CANCEL/TITLE` 已填入该组节点名，`LynkWidgets.hasOfficialConfirmation()` 的检测已激活。**仍需真机验证：** 哪些动作会触发该浮层（目前只静态证明了解锁的标题）、触发条件（每次/首次/仅特定状态）、是否还有其他动作跳 App 或弹指纹/密码——见 §4 的点按矩阵。确认浮层存在不等于每次点击都会经过它。

## 6. 填入 `BrandConfig` 的检查清单

按 §1–§4 的实测结果填 `android/app/src/main/java/com/garage/lynkcontrol/BrandConfig.java`，填一项勾一项：

- [x] `WIDGET_PROVIDER_CLASS` ← `com.lynkco.vehicle.widget.ListWidgetProvider`（4.2.9 解析）
- [x] `LAYOUT_NORMAL` / `LAYOUT_SETUP` ← `layout_widget` / null（无独立引导布局；未绑定态是布局内的 `rl_widget_state`）
- [x] `SETTINGS_VIEW` / `REFRESH_VIEW` ← `ic_bj_a` / `ic_widget_sx`
- [x] `SLOT_CONTAINERS` ← `fr_unlock`、`fr_window`、`fr_kjck`、`fr_ccqk`（按显示顺序）
- [x] `MILEAGE_CONTAINER` / `MILEAGE_VALUE` / `MILEAGE_UNIT` / `ENERGY_*` / `ALARM_CONTAINER` / `MOTION_TEXT` / `UPDATE_TIME_TEXT` / `UPDATE_LOADING_VIEWS` / `SLOT_LOADING_VIEW` / `SLOT_ICON_VIEW` ← §2 的车况字段资源名（已填；`ENERGY_VALUE` / `ENERGY_UNIT` / `SLOT_LOADING_VIEW` / `SLOT_ICON_VIEW` 为 null，理由见 `BrandConfig` 注释）
- [x] `CONFIRM_LAYOUT` / `CONFIRM_ACCEPT` / `CONFIRM_CANCEL` / `CONFIRM_TITLE` ← `verify` / `tv_verify` / `tv_cancel` / `ty_confirm_title`（Form A 静态存在；触发条件待 §4）
- [ ] `ICON_RESOURCE_PREFIX` 与 `WidgetIcons.load()` 名表 ← §3 的图标转储结果（候选集已列出，仍需真机目视比对）
- [x] `HEIGHT_DP` ← 155（4.2.9 布局内容高 dp_155；provider 声明 minHeight 110dp）
- [ ] `LockControl` / `WindowControl` 的状态关键词 ← §4 记录的领克卡片实际状态文案（当前是从银河沿用的）
- [x] `WidgetReadings` 的占位卡过滤值 ← 领克占位文本是 "--"（已在代码注释中说明，过滤对领克是惰性的）
- [x] `TARGET_VERSION` ← 4.2.9（车主包解析版本；4.3.0 交叉核对一致）
- [ ] `WidgetReadings` 的更新时间格式 ← §4 记录 `time` 节点的运行时格式（当前正则要求 "MM-DD HH:mm" 式；若领克用其他格式，车况会读不到）

填完后验收：绑定卡片 → 车况自动读取 → 每项动作逐一实测（点按矩阵）→ 米家语音 `LYNKCO_V1:*` 逐项触发 → 前台/后台/锁屏各走一遍。只将实际执行并核对的功能标为通过，记入 `docs/compatibility.md`。

## 7. 失败渠道记录（不要重复踩）

- 酷安 `coolapk.com/apk/com.lynkco.customer`：直接打开返回 HTTP 567。
- 豌豆荚 `wandoujia.com/apps/com.lynkco.customer/download`：连接挂起无响应。
- 应用宝 CDN 直链不带 Referer：HTTP 567（7 KB 错误页）；Referer 用 `sj.qq.com` 或 `imtt.qq.com` 域名后返回 200。见 §1。
- APKMirror / APKPure / apkcombo：只收录海外版（`com.lynkco.prod.lynkco_app_android`），没有中国版 `com.lynkco.customer`。
- 360 应用详情页（v4.2.5）与小米应用商店详情页可核对版本与包名，但页面文本不暴露直链。

> 归档说明：此文件来自领克独立移植副本，是静态发现资料；当前单包实现与验收状态以 compatibility.md 为准。原副本中的包名、路径与测试描述为历史信息。
