package com.example.data.remote.supabase

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android

// HttpURLConnection: usa el network_security_config de la app (solo HTTPS)
internal actual fun motorHttpSupabase(): HttpClientEngine = Android.create()
