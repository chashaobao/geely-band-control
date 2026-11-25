# 小米通信 SDK 分发许可核查

核查日期为 2026 年 11 月 25 日。

## 结论

当前可取得的官方资料支持第三方 Android 应用集成通信 SDK，但尚未找到明确允许将 `xms-wearable-lib_1.4_release.aar` 随 APK 在 GitHub Releases 公开分发的许可。项目保留源码构建方式，公开双端安装包的条件尚未得到确认。

## 实际检查的材料

| 材料 | 结果 |
| --- | --- |
| Vela Interconnect 官方文档 | 提供官方样例下载、手机与快应用包名及签名一致等接入要求。页面没有明确的 SDK 再分发条款 |
| 官方 `interconnect_dev_test_demo.zip` | 查阅三份 PDF、样例 README 和归档清单，未发现 SDK LICENSE、NOTICE 或再分发协议 |
| `xms-wearable-lib_1.4_release.aar` | SHA-256 为 `9c40fd1c5409bb948523d503af71e2978ae522c35636afe7d64f474c0f6bc195`。检查 AAR 和内嵌 classes.jar 的清单，未发现许可声明 |
| 《快应用小米平台服务协议》 | 页面更新时间 2025-05-19。第三条说明开发者应用中使用授权技术的范围，并对单独复制、转授权和向第三方提供授权技术作出限制。该协议面向快应用平台，不能直接确定本 Android 通信 AAR 的具体许可 |

安装包编译时会将 Android SDK 类纳入 APK。项目自身的 Apache-2.0 许可证只覆盖项目有权授权的原创内容，小米 SDK 的授权条件需另行确认。

## 后续确认所需问题

向小米开发者支持或 Wearable SDK 维护方提供上述 SDK 名称、版本与哈希，询问是否允许第三方将其以编译集成形式包含在免费开源应用的 APK 中，并通过 GitHub Releases 向公众下载。还需确认署名、隐私披露、地区、开发者注册及审核要求，以及许可是否覆盖当前 SDK 版本。

获得适用条款或书面确认后，记录官方出处和范围，再按证书配对、版本及 SHA-256 校验结果发布安装包。

## 官方来源

- https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html
- https://cdn.cnbj3-fusion.fds.api.mi-img.com/quickapp-vela/interconnect_dev_test_demo.zip
- https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1667
