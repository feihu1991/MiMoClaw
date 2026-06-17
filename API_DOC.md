# MiMo Claw API 接口文档

> 基于 https://aistudio.xiaomimimo.com 前端源码分析

## 基础信息

| 项目 | 值 |
|------|-----|
| Base URL | `https://aistudio.xiaomimimo.com` |
| 认证方式 | Xiaomi SSO (小米账号统一登录) |
| 数据格式 | JSON |

## 认证流程

Web 端使用小米账号 SSO 登录，流程如下：

1. 用户点击登录 → 跳转 `https://account.xiaomi.com/fe/service/login/password`
2. 登录成功后回调 → `https://aistudio.xiaomimimo.com/sts?sign=xxx&followup=https://aistudio.xiaomimimo.com/`
3. STS 接口签发 Token → 前端存储到 Cookie
4. 后续请求自动携带 Cookie 中的 Token

## 接口列表

### 1. 获取用户信息

```
GET /open-apis/user/mi/get
```

**认证**: 需要登录态 (Cookie)

**响应**:
```json
{
  "code": 0,
  "data": {
    "userId": "xxx",
    "nickname": "xxx",
    "avatar": "xxx",
    "isClawDisclaimerAgreed": true
  }
}
```

### 2. 获取 Bot 配置

```
GET /open-apis/bot/config
```

**认证**: 需要登录态

**响应**:
```json
{
  "code": 0,
  "data": {
    "model": "MiMo-V2.5-Pro",
    "features": {}
  }
}
```

### 3. 获取频道登录二维码

```
POST /open-apis/user/mimo-claw/channel-login/qrcode?channel={channelId}
```

**认证**: 需要登录态

**参数**:
- `channel`: 频道ID (如 `openclaw-weixin`, `feishu`, `qqbot` 等)

**响应**:
```json
{
  "qrContent": "二维码内容",
  "qrVersion": 1
}
```

### 4. 查询频道登录状态

```
GET /open-apis/user/mimo-claw/channel-login/status?channel={channelId}
```

**认证**: 需要登录态

**响应**:
```json
{
  "status": "QR_READY|SUCCESS|EXPIRED|ENDED|NO_POD|UNSUPPORTED",
  "qrContent": "二维码URL (QR_READY时)",
  "qrVersion": 1,
  "message": "提示信息"
}
```

### 5. 频道登出

```
POST /open-apis/user/mimo-claw/channel/logout?channel={channelId}
```

**认证**: 需要登录态

### 6. 获取频道状态 (Gateway RPC)

通过 WebSocket Gateway 调用:

```
channels.status
```

**响应**:
```json
{
  "channels": {
    "openclaw-weixin": { "configured": true },
    "feishu": { "configured": false },
    "qqbot": { "configured": false }
  }
}
```

### 7. 启动频道 (Gateway RPC)

```
channels.start { "channel": "channelId" }
```

### 8. 获取配置 (Gateway RPC)

```
config.get {}
```

### 9. 更新配置 (Gateway RPC)

```
config.patch { "raw": "JSON字符串", "baseHash": "可选" }
```

## 前端资源

| 资源 | 路径 |
|------|------|
| 运行时 | `/runtime-main.d1213559.js` |
| 主入口 | `/main.ca240cb8.chunk.js` |
| 核心逻辑 | `/7480.3787df7b.chunk.js` |
| 静态资源 | `https://aistudio-cdn.xiaomimimo.com/` |

## 注意事项

1. **登录方式**: 仅支持小米账号 SSO，不支持用户名密码直接登录
2. **Token 管理**: Token 存储在 Cookie 中，过期后需重新 SSO
3. **STS 签发**: `/sts` 接口用于签发 Token，sign 参数为签名
4. **Gateway**: 部分功能通过 WebSocket Gateway RPC 调用
