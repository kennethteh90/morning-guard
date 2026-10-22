package com.morningguard.data.di

import android.content.Context
import androidx.room.Room
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.morningguard.data.bible.ApiBibleApi
import com.morningguard.data.bible.ApiBibleAuthInterceptor
import com.morningguard.data.bible.BibleApi
import com.morningguard.data.bible.BibleProxyApi
import com.morningguard.data.bible.BibleProxyAuthInterceptor
import com.morningguard.data.bible.BibleProxyConfig
import com.morningguard.data.local.MorningGuardDatabase
import com.morningguard.data.security.HighlightNoteCipher
import com.morningguard.data.security.NoteEncryptionRepository
import com.morningguard.data.local.dao.BibleHighlightDao
import com.morningguard.data.local.dao.CapturedNotificationDao
import com.morningguard.domain.gate.GoalCompletionVerifier
import com.morningguard.domain.gate.MorningGateEvaluator
import com.morningguard.domain.security.EmergencyBypassPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MorningGuardDatabase =
        Room.databaseBuilder(context, MorningGuardDatabase::class.java, "morning_guard.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCapturedNotificationDao(db: MorningGuardDatabase): CapturedNotificationDao =
        db.capturedNotificationDao()

    @Provides
    fun provideBibleHighlightDao(db: MorningGuardDatabase): BibleHighlightDao =
        db.bibleHighlightDao()

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(@Named("httpLogging") httpLogging: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
        if (httpLogging) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideOpenBibleApi(client: OkHttpClient, json: Json): BibleApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://bible-api.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BibleApi::class.java)
    }

    @Provides
    @Singleton
    fun provideApiBibleApi(
        client: OkHttpClient,
        json: Json,
        authInterceptor: ApiBibleAuthInterceptor,
    ): ApiBibleApi {
        val contentType = "application/json".toMediaType()
        val apiClient = client.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.scripture.api.bible/v1/")
            .client(apiClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(ApiBibleApi::class.java)
    }

    @Provides
    @Singleton
    fun provideBibleProxyApi(
        client: OkHttpClient,
        json: Json,
        config: BibleProxyConfig,
        authInterceptor: BibleProxyAuthInterceptor,
    ): BibleProxyApi {
        val contentType = "application/json".toMediaType()
        val baseUrl = config.baseUrl.takeIf { it.isNotBlank() } ?: "https://invalid.local/"
        val proxyClient = client.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(proxyClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(BibleProxyApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMorningGateEvaluator(): MorningGateEvaluator = MorningGateEvaluator()

    @Provides
    @Singleton
    fun provideGoalCompletionVerifier(): GoalCompletionVerifier = GoalCompletionVerifier()

    @Provides
    @Singleton
    fun provideEmergencyBypassPolicy(): EmergencyBypassPolicy = EmergencyBypassPolicy()

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    fun provideHighlightNoteCipher(repository: NoteEncryptionRepository): HighlightNoteCipher = repository
}
