# 开源准备记录

2026 年 11 月 25 日

## 已完成

- 从 geely-band-control 0.1.29 移植，品牌层抽到 `BrandConfig`，包名改为 `com.garage.lynkcontrol`，版本从 0.1.0 起。
- Apache-2.0 许可证、第三方说明、贡献指南、安全反馈和 Issue 模板（已改领克文案）。
- Windows CI 配置，覆盖 Android 构建、单元测试、lint 与手环构建、协议测试。
- 私钥、口令、SDK、安装包、缓存和运行日志列入 Git 忽略范围，导出脚本再次检查登记文件。
- 银河品牌素材已替换：星耀 8 车图改为脚本自绘的通用车辆示意（`car-placeholder.png`），Galaxy 小组件研究文档未带入本仓库。
- 领克包名已核实：中国版 `com.lynkco.customer` 4.3.0（安信 APK）、海外版两个包名、米家与小米运动健康包名；LynkCo Watch 手表 App 的 Android 包名未找到（见发现文档）。
- **手环端本地构建与测试已通过（2026-11-25）：** `npm ci` + `npm run build` 在 Linux 本机成功，生成调试 RPK `com.garage.lynkcontrol.debug.0.1.0.rpk`；`node --test test/lynkco.test.cjs` **29/29 通过**。修过一个移植引入的测试夹具问题：最低手机端版本从 0.1.14 降到 0.1.0 后，低于最低版本的测试夹具 `0.1.9` 不再触发“版本不符”，已改为 `0.0.9`（测试意图不变）。
- **小组件映射已填入（2026-11-25）：** 车主包 4.2.9 静态解析完成并填入 `BrandConfig`（provider `ListWidgetProvider`、布局 `layout_widget`、4 个槽位容器、车况字段、确认浮层 `verify`/`tv_verify`/`tv_cancel`）；4.3.0 镜像包小组件面交叉核对一致。图标指纹、kjck/ccqk 功能、状态文案、更新时间格式仍待真机点按验证，见发现文档 §4–§6。
- **GitHub 推送进行中（2026-11-25）：** 仓库创建被集成令牌 403 拒绝后，用户已手动建好空仓 `zjs215/lynkco-band-control`（公开）；映射填入后按快照流程推送。19 个二进制文件因连接器不支持二进制内容无法随快照推送（见下条）；推送时重新生成文件清单（备清单时 88 个文本文件、911,409 字节，此后文档有改动）。

## 发布前需处理

- **领克小组件映射已填、真机未验收**：provider、布局名、槽位容器、车况字段、确认浮层已由 4.2.9 静态解析填入（见 `docs/lynkco-app-widget-discovery.md`）；图标资源名、kjck/ccqk 功能、状态文案、更新时间格式仍待发现。在真机点按验收前，不要发布“可用”的安装包。
- **GitHub 仓库已由用户手动建好**：`create_repository`（zjs215/lynkco-band-control，公开）获批后仍返回 403（集成令牌无权创建仓库），用户已手动建空仓；推送按快照流程进行。不要另起 OAuth/PAT 流程。
- **19 个二进制文件无法经连接器推送**：`android/gradle/wrapper/gradle-wrapper.jar` 与 18 张手环 PNG（`band/src/common/icon.png` + `band/src/common/images/` 下 17 张）。`push_files` 快照只接受 UTF-8 文本，`create_or_update_file` 的 `content` 也是文本（服务器自行 base64），都不能传二进制。推送时先推 88 个文本文件，二进制文件需在仓库建好后另行补传；若无法补传，恢复方式：`gradle-wrapper.jar` 用 `gradle wrapper --gradle-version 8.13` 重新生成（见 `android/gradle/wrapper/gradle-wrapper.properties`），18 张 PNG 用 `scripts/generate-assets.ps1` 重新生成（全部由该脚本绘制）。
- 小米通信 SDK 样例归档中尚未发现明确的独立再分发授权。源代码提供官方依赖下载方法，发布含该 SDK 的 APK 前需要核对适用条款。
- GitHub 仓库待创建，Windows CI 首次运行待验证。
- 0.1.0 的 Android 单测待 CI 或本地构建验证；Linux 本机无 JDK/Android SDK，无法本地编译 Android 端。手环端测试已在本地通过（见上）。
- 二次确认降级路径已实现（沿用银河版“不自动点、请在手机确认”）；卡内确认自动点赞为未来选项，默认关闭，不绕指纹、不使用无障碍代点。

源码将发布至 https://github.com/zjs215/lynkco-band-control ，展示名称「腕上领克」，私密漏洞报告已开启。公开安装包的发布范围按第三方许可确定。

> 归档说明：此文件来自领克独立移植副本，是静态发现资料；当前单包实现与验收状态以 compatibility.md 为准。原副本中的包名、路径与测试描述为历史信息。
