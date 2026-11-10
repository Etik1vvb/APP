package com.example.app.vehicle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Local-development client for the MOA FastAPI story endpoint.
 * The OpenAI API key MUST stay on the Python server, never in the Android app.
 * Replace HTTP loopback with an authenticated HTTPS endpoint for real deployment.
 */
data class MoaDocentStory(
    val placeName: String,
    val storyText: String,
    val sourceName: String?,
    val model: String?,
    val status: String
)

class MoaDocentApiClient(
    private val baseUrl: String = "http://127.0.0.1:8000"
) {
    suspend fun generateStory(
        placeName: String,
        region: String,
        topic: String,
        userInterests: String,
        verifiedFacts: List<String>,
        sourceName: String
    ): MoaDocentStory = withContext(Dispatchers.IO) {
        val request = JSONObject().apply {
            put("placeName", placeName)
            put("region", region)
            put("topic", topic)
            put("userInterests", userInterests)
            put("factSnippets", JSONArray(verifiedFacts))
            put("sourceName", sourceName)
        }

        val connection = (URL("$baseUrl/api/docent/story").openConnection()
                as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 45_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(request.toString().toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseText = (if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IOException("MOA Docent HTTP $statusCode: ${responseText.take(250)}")
            }

            val response = JSONObject(responseText)
            MoaDocentStory(
                placeName = response.optString("placeName"),
                storyText = response.optString("storyText"),
                sourceName = response.optString("sourceName").takeIf { it.isNotBlank() },
                model = response.optString("model").takeIf { it.isNotBlank() },
                status = response.optString("status")
            )
        } finally {
            connection.disconnect()
        }
    }
}
