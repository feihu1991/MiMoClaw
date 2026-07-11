# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson / Retrofit response models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.xiaomi.mimoclaw.auth.UserInfoResponse { *; }
-keep class com.xiaomi.mimoclaw.auth.UserInfoData { *; }
-keep class com.xiaomi.mimoclaw.auth.BotConfigResponse { *; }
-keep class com.xiaomi.mimoclaw.auth.BotConfigData { *; }
-keep class com.xiaomi.mimoclaw.auth.ChannelQrcodeResponse { *; }
-keep class com.xiaomi.mimoclaw.auth.ChannelLoginStatusResponse { *; }
-keep class com.xiaomi.mimoclaw.auth.WsTicketResponse { *; }
-keep class com.xiaomi.mimoclaw.auth.WsTicketData { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-dontwarn androidx.compose.**
