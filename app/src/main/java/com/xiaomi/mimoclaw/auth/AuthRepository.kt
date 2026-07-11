package com.xiaomi.mimoclaw.auth

import retrofit2.Response
import retrofit2.http.*

/**
 * MiMo Claw API 接口
 *
 * 实际 Web 端接口基于 https://aistudio.xiaomimimo.com
 * 认证方式: 小米账号 SSO (account.xiaomi.com)
 *
 * 参见: API_DOC.md
 */
interface AuthRepository {

    /**
     * 获取当前登录用户信息
     * 需要 SSO 登录态 (Cookie)
     */
    @GET("open-apis/user/mi/get")
    suspend fun getUserInfo(): Response<UserInfoResponse>

    /**
     * 获取 Bot 配置
     */
    @GET("open-apis/bot/config")
    suspend fun getBotConfig(): Response<BotConfigResponse>

    /**
     * 获取频道登录二维码
     */
    @POST("open-apis/user/mimo-claw/channel-login/qrcode")
    suspend fun getChannelLoginQrcode(
        @Query("channel") channel: String
    ): Response<ChannelQrcodeResponse>

    /**
     * 查询频道登录状态
     */
    @GET("open-apis/user/mimo-claw/channel-login/status")
    suspend fun getChannelLoginStatus(
        @Query("channel") channel: String
    ): Response<ChannelLoginStatusResponse>

    /**
     * 频道登出
     */
    @POST("open-apis/user/mimo-claw/channel/logout")
    suspend fun channelLogout(
        @Query("channel") channel: String
    ): Response<Unit>

    /**
     * 获取 WebSocket 连接 ticket
     * 需要在 Cookie 中携带认证信息
     */
    @GET("open-apis/user/ws/ticket")
    suspend fun getWsTicket(
        @Query("xiaomichatbot_ph") ph: String
    ): Response<WsTicketResponse>

    companion object {
        const val BASE_URL = "https://aistudio.xiaomimimo.com/"
    }
}

// ── 响应数据模型 ──

data class UserInfoResponse(
    val code: Int = 0,
    val data: UserInfoData? = null
)

data class UserInfoData(
    val userId: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val isClawDisclaimerAgreed: Boolean = false
)

data class BotConfigResponse(
    val code: Int = 0,
    val data: BotConfigData? = null
)

data class BotConfigData(
    val model: String? = null
)

data class ChannelQrcodeResponse(
    val qrContent: String? = null,
    val qrVersion: Int = 0
)

data class ChannelLoginStatusResponse(
    val status: String? = null,  // QR_READY, SUCCESS, EXPIRED, ENDED, NO_POD, UNSUPPORTED
    val qrContent: String? = null,
    val qrVersion: Int = 0,
    val message: String? = null
)

data class WsTicketResponse(
    val code: Int = 0,
    val data: WsTicketData? = null,
    val ticket: String? = null  // 兼容扁平结构
)

data class WsTicketData(
    val ticket: String? = null
)
