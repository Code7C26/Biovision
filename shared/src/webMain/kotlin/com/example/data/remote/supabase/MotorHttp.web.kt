package com.example.data.remote.supabase

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

// fetch del navegador: la CSP de index.html tiene que permitir el dominio del proyecto (connect-src)
internal actual fun motorHttpSupabase(): HttpClientEngine = Js.create()
