package com.storytime.app.network

import com.storytime.app.model.GenerationEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class SSEClient(
    private val client: OkHttpClient,
    private val json: Json
) {
    fun connect(url: String): Flow<GenerationEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val call = client.newCall(request)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        trySend(
                            GenerationEvent(
                                type = "error",
                                message = "Server returned an error (HTTP ${response.code}). Please try again."
                            )
                        )
                        response.close()
                        channel.close()
                        return
                    }

                    val body = response.body ?: run {
                        trySend(
                            GenerationEvent(
                                type = "error",
                                message = "Empty response from server"
                            )
                        )
                        channel.close()
                        return
                    }

                    val reader = BufferedReader(InputStreamReader(body.byteStream()))
                    var receivedTerminal = false

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue

                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.removePrefix("data: ").trim()
                            if (data.isNotEmpty()) {
                                try {
                                    val event = json.decodeFromString<GenerationEvent>(data)
                                    trySend(event)

                                    // Close on terminal events
                                    if (event.type == "complete" || event.type == "error") {
                                        receivedTerminal = true
                                        break
                                    }
                                } catch (e: Exception) {
                                    // Skip malformed events
                                }
                            }
                        }
                        // Ignore other lines (event:, id:, empty lines)
                    }

                    // Stream ended without a complete/error event — server likely crashed
                    if (!receivedTerminal && !call.isCanceled()) {
                        trySend(
                            GenerationEvent(
                                type = "error",
                                message = "Lost connection to the server. Please try again."
                            )
                        )
                    }

                    reader.close()
                    response.close()
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        trySend(
                            GenerationEvent(
                                type = "error",
                                message = "Stream error: ${e.message}"
                            )
                        )
                    }
                }
                channel.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    trySend(
                        GenerationEvent(
                            type = "error",
                            message = "Connection failed: ${e.message}"
                        )
                    )
                }
                channel.close()
            }
        })

        awaitClose { call.cancel() }
    }
}
