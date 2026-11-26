package com.example.data.remote.api

import com.example.data.remote.model.DolarRateDto
import retrofit2.http.GET

interface DolarApiService {
    @GET("v1/dolares")
    suspend fun getDolarRates(): List<DolarRateDto>
}
