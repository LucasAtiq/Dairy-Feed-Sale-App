package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DiscordAuditNotifier {

    suspend fun sendActivityNotification(
        webhookUrl: String,
        eventType: String,
        userEmail: String,
        businessName: String,
        details: String,
        totalOrders: Int,
        totalSales: Double,
        currencySymbol: String = "Rs."
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = webhookUrl.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid or empty Discord Webhook URL"))
        }

        try {
            val url = URL(trimmed)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("User-Agent", "WandaBookPro-AuditBot/1.0")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val formattedSales = try {
                String.format(Locale.getDefault(), "%,.0f", totalSales)
            } catch (e: Exception) {
                totalSales.toString()
            }

            val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val jsonPayload = JSONObject().apply {
                put("username", "WandaBook Pro Monitor")
                put("avatar_url", "https://cdn-icons-png.flaticon.com/512/2371/2371661.png")

                val embed = JSONObject().apply {
                    put("title", "⚡ Activity Alert: $eventType")
                    put("color", 0x0F9D58) // WandaBook Pro Green
                    put("description", "Live user action logged from WandaBook Pro mobile application.")

                    val fields = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", "👤 Attached Account")
                            put("value", if (userEmail.isNotBlank()) "`$userEmail`" else "*Not Attached*")
                            put("inline", true)
                        })
                        put(JSONObject().apply {
                            put("name", "🏢 Business / Farm")
                            put("value", businessName.ifBlank { "WandaBook Dairy" })
                            put("inline", true)
                        })
                        put(JSONObject().apply {
                            put("name", "📦 Live Orders")
                            put("value", "$totalOrders Orders")
                            put("inline", true)
                        })
                        put(JSONObject().apply {
                            put("name", "💰 Total Sales")
                            put("value", "$currencySymbol $formattedSales")
                            put("inline", true)
                        })
                        put(JSONObject().apply {
                            put("name", "ℹ️ Action Details")
                            put("value", details.ifBlank { "Cloud synchronization completed." })
                            put("inline", false)
                        })
                    }
                    put("fields", fields)

                    val footer = JSONObject().apply {
                        put("text", "WandaBook Pro Cloud Monitor • $currentTime")
                    }
                    put("footer", footer)
                }

                put("embeds", JSONArray().apply { put(embed) })
            }

            conn.outputStream.use { os ->
                val input = jsonPayload.toString().toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Discord returned HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
