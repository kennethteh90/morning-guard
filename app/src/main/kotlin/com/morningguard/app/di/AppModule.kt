package com.morningguard.app.di

import android.content.Context
import com.morningguard.app.auth.SupabaseAuthBackend
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.analytics.LoggingAnalyticsTracker
import com.morningguard.data.auth.AuthBackend
import com.morningguard.data.auth.UnconfiguredAuthBackend
import com.morningguard.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker = LoggingAnalyticsTracker()

    @Provides
    @Singleton
    fun provideAuthBackend(
        @ApplicationContext context: Context,
        unconfigured: UnconfiguredAuthBackend,
    ): AuthBackend {
        return if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            SupabaseAuthBackend(context, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        } else {
            unconfigured
        }
    }
}
