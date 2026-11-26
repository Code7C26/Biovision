package com.example.data.remote.api

import com.example.data.remote.model.PizarraResponse
import retrofit2.http.GET
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import android.util.Log

interface GranosApiService {
    @GET("api/v1/pizarra")
    suspend fun getPizarraHoy(): PizarraResponse
}

object CurrentCropTracker {
    @Volatile
    var selectedCrop: String = "Soja"
}

object LatestRateTracker {
    @Volatile
    var exchangeRate: Double = 1000.0
}

class GranosApiInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        val urlStr = request.url.toString()
        if (urlStr.contains("granosar.lfcaucino.workers.dev")) {
            val body = response.body
            if (body != null) {
                val rawJson = body.string()
                try {
                    val rootJson = JSONObject(rawJson)
                    if (urlStr.contains("api/v1/pizarra")) {
                        val dataObj = rootJson.optJSONObject("data")
                        if (dataObj != null) {
                            val tc = dataObj.optDouble("tipo_cambio_bna_divisas", 1000.0)
                            LatestRateTracker.exchangeRate = tc
                            
                            val newResponseBody = dataObj.toString().toResponseBody("application/json".toMediaTypeOrNull())
                            return response.newBuilder().body(newResponseBody).build()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GranexPizarra", "Respuesta de granos.ar ilegible: ${rawJson.take(200)}", e)
                }
                // El cuerpo ya se leyó: se devuelve una copia (devolver el original fallaría al leerlo)
                return response.newBuilder().body(rawJson.toResponseBody(body.contentType())).build()
            }
        }
        return response
    }
}
