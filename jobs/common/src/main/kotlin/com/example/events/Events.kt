package com.example.events

data class InputEvent(
    val id: String = "",
    val type: String = "",
    val timestamp: Long = 0,
    val data: Map<String, Any> = emptyMap()
)

data class ProcessedEvent(
    val originalId: String,
    val eventType: String,
    val processedAt: String,
    val processingDelay: Long,
    val enrichedData: Map<String, Any>,
    val sequence: Int
)

data class ErrorEvent(
    val rawMessage: String,
    val errorType: String,
    val errorMessage: String,
    val timestamp: String
)
