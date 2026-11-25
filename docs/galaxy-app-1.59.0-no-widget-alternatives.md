# 无小组件集成路线可行性评估摘要

2026 年 11 月 24 日 · Android 手机伴侣应用

本文档记录「腕上银河」在选型阶段为何采用 Android 系统 `AppWidgetHost` 托管官方小组件，而不直接对接官方 App 内部页面或云端接口。

## 路线评估结论

### 1. 跨应用 Intent / Deep Link 路线（不可行）

- 吉利银河 App（`com.geelygalaxy.customer`）仅对外提供了常规桌面启动入口与通用业务回调；未发现面向第三方应用的公开车控 Intent 契约、状态查询 URI 或桌面动态快捷方式接口。
- 面向车厂或手机厂商系统钱包/车联框架的专用服务均受系统级或签名级权限保护，普通第三方 Android 应用无法直接绑定或调用。
- **结论**：除引导用户打开吉利银河 App 本体外，无法通过 Intent 在后台安全读取车况或触发单步车控。

### 2. 云端账号直连路线（暂不采用）

- 官方未向独立第三方开发者公开车辆认证、Token 续期、操作签发、二次密码确认及车况读取的开放 API 文档与授权体系。
- 若绕过官方客户端尝试模拟登录或抓包重放接口，不仅存在极高的接口变更与账号风控限制，也与官方服务条款及安全合规原则相冲突。
- **结论**：不采集用户账号凭据，也不模拟官方网络请求；除非未来取得车厂正式的开放平台授权，否则不采用直连路线。

## 最终选型

基于上述边界，项目统一采用 Android 标准 [`AppWidgetHost`](https://developer.android.com/develop/ui/views/appwidgets/host) 机制：由车主在本地主动完成小组件绑定授权与车辆配置，伴侣应用仅在本地读取已授权卡片的视图读数，并在穿戴端确认后代为触发对应的官方按钮，所有账号会话、认证与二次安全确认均保留在吉利银河官方 App 体系内完成。

## 参考

- [Android 组件与 Intent 匹配规则](https://developer.android.com/guide/components/intents-filters)
- [Android 自定义 AppWidgetHost](https://developer.android.com/develop/ui/views/appwidgets/host)
- [吉利银河应用说明（小米应用商店）](https://app.mi.com/details?id=com.geelygalaxy.customer)
