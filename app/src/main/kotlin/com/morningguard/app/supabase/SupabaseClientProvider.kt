package com.morningguard.app.supabase

import com.morningguard.app.BuildConfig
import com.morningguard.app.auth.KeystoreSessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor(
    private val keystoreSessionManager: KeystoreSessionManager,
) {

    val client: SupabaseClient? =
        if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
                install(Auth) {
                    flowType = FlowType.PKCE
                    scheme = "morningguard"
                    host = "auth"
                    sessionManager = keystoreSessionManager
                }
                install(Postgrest)
            }
        } else {
            null
        }
}
