package com.morningguard.app.di

import com.morningguard.app.bible.PlayIntegrityBibleProxyLicensedAuth
import com.morningguard.data.bible.BibleProxyLicensedAuth
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BibleProxyModule {
    @Binds
    @Singleton
    abstract fun bindLicensedAuth(impl: PlayIntegrityBibleProxyLicensedAuth): BibleProxyLicensedAuth
}
