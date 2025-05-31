package com.example.fridgetime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class NiimbotRequest(val oneCode: String)

@Serializable
data class NiimbotTemplateData(
    val width: Int,
    val height: Int
)

@Serializable
data class NiimbotResponse(
    val data: NiimbotTemplateData,
    val code: Int,
    val message: String
)

class NiimbotApiService {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getLabelWidth(barcode: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val requestBody = NiimbotRequest(oneCode = barcode)
            val jsonBody = json.encodeToString(NiimbotRequest.serializer(), requestBody)

            val request = Request.Builder()
                .url("https://print.niimbot.com/api/template/getCloudTemplateByOneCode")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("niimbot-user-agent", "AppVersionName/999.0.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP error: ${response.code}"))
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val niimbotResponse = json.decodeFromString(NiimbotResponse.serializer(), responseBody)

            if (niimbotResponse.code == 1) {
                Result.success(niimbotResponse.data.width)
            } else {
                Result.failure(Exception("API error: ${niimbotResponse.message}"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Usage example:
suspend fun example() {
    val apiService = NiimbotApiService()
    val result = apiService.getLabelWidth("6972842743596")

    result.fold(
        onSuccess = { width ->
            println("Label width: $width mm")
        },
        onFailure = { error ->
            println("Error: ${error.message}")
        }
    )
}