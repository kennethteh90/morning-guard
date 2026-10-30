package com.morningguard.app.di

import com.morningguard.app.BuildConfig
import com.morningguard.app.platform.AppDebugFlags
import com.morningguard.app.auth.SupabaseAuthBackend
import com.morningguard.app.supabase.SupabaseClientProvider
import com.morningguard.data.analytics.AnalyticsTracker
import com.morningguard.data.analytics.LoggingAnalyticsTracker
import com.morningguard.data.analytics.NoOpAnalyticsTracker
import com.morningguard.data.auth.AuthBackend
import com.morningguard.data.auth.UnconfiguredAuthBackend
import com.morningguard.app.monitoring.SentryErrorReporter
import com.morningguard.data.monitoring.ErrorReporter
import com.morningguard.data.monitoring.NoOpErrorReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationCoroutineScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideAnalyticsTracker(): AnalyticsTracker =
        if (AppDebugFlags.verboseDiagnostics) LoggingAnalyticsTracker() else NoOpAnalyticsTracker()

    @Provides
    @Singleton
    fun provideErrorReporter(): ErrorReporter =
        if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryErrorReporter()
        } else {
            NoOpErrorReporter()
        }

    @Provides
    @Named("httpLogging")
    fun provideHttpLoggingEnabled(): Boolean = AppDebugFlags.verboseDiagnostics

    @Provides
    @Singleton
    fun provideAuthBackend(
        clientProvider: SupabaseClientProvider,
        unconfigured: UnconfiguredAuthBackend,
        okHttpClient: OkHttpClient,
    ): AuthBackend {
        return if (clientProvider.client != null) {
            SupabaseAuthBackend(clientProvider, okHttpClient)
        } else {
            unconfigured
        }
    }
}
