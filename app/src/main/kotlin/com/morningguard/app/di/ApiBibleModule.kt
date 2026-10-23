package com.morningguard.app.di

import com.morningguard.app.BuildConfig
import com.morningguard.data.bible.ApiBibleConfig
import com.morningguard.data.bible.BibleProxyConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiBibleModule {

    @Provides
    @Singleton
    fun provideApiBibleConfig(): ApiBibleConfig =
        ApiBibleConfig(apiKey = "", bibleIdOverrides = emptyMap())

    @Provides
    @Singleton
    fun provideBibleProxyConfig(): BibleProxyConfig {
        val base = BuildConfig.BIBLE_PROXY_BASE_URL.trim().trimEnd('/')
        val url = if (base.isBlank()) "" else "$base/"
        val projectNumber = BuildConfig.PLAY_CLOUD_PROJECT_NUMBER.trim().toLongOrNull() ?: 0L
        return BibleProxyConfig(
            baseUrl = url,
            playCloudProjectNumber = projectNumber,
        )
    }
}
