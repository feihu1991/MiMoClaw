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

    companion object {
        const val BASE_URL = "https://aistudio.xiaomimimo.com/"

        /**
         * 小米账号 SSO 登录地址
         * 登录成功后会回调到 STS 接口签发 Token
         */
        const val SSO_LOGIN_URL =
            "https://account.xiaomi.com/fe/service/login/password" +
            "?sid=xiaomichatbot" +
            "&callback=https%3A%2F%2Faistudio.xiaomimimo.com%2Fsts" +
            "&_group=DEFAULT"
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
