package com.xiaomi.mimoclaw.core.network

import android.content.Context
import android.webkit.CookieManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xiaomi.mimoclaw.auth.AuthRepository
import com.xiaomi.mimoclaw.core.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    /**
     * 提供带 Cookie 管理的 OkHttpClient
     *
     * SSO 登录后，Cookie 中会包含认证信息。
     * 与 Web 端保持一致，使用 Cookie 而非 Bearer Token。
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                val cookieHeader = CookieManager.getInstance().getCookie(url)
                val request = if (cookieHeader != null) {
                    chain.request().newBuilder()
                        .header("Cookie", cookieHeader)
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 无 Cookie 的 OkHttpClient，供 GitHub API 等外部请求使用
     */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AuthRepository.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthRepository(retrofit: Retrofit): AuthRepository {
        return retrofit.create(AuthRepository::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateChecker(@Named("plain") okHttpClient: OkHttpClient, gson: Gson): UpdateChecker {
        return UpdateChecker(okHttpClient, gson)
    }
}
