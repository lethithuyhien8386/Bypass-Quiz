package com.example.aiquiz

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object GeminiClient {
    private val client = OkHttpClient()
    private const val MODEL = "gemini-2.5-flash"

    suspend fun solve(apiKey: String, bitmap: Bitmap): Int = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)
        val image64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        val prompt = """
You are an assistant for a multiple-choice question displayed in the screenshot.
Identify the question and its answer choices. Return ONLY valid JSON in exactly this format:
{"answer_index":1}
Use 1 for the first visible choice, 2 for the second, 3 for the third, 4 for the fourth, etc.
If there is no clear multiple-choice question, return {"answer_index":0}.
Do not explain anything outside the JSON.
""".trimIndent()
        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", image64)))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
        val body = JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json"))
            .toString().toRequestBody("application/json".toMediaType())
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Gemini HTTP ${response.code}: ${response.body?.string()?.take(300)}")
            val root = JSONObject(response.body!!.string())
            val text = root.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
            val cleaned = text.trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim()
            JSONObject(cleaned).optInt("answer_index", 0)
        }
    }
}
