package com.example.flink

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.connector.datagen.source.GeneratorFunction
import org.apache.flink.connector.datagen.source.DataGeneratorSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventProcessorJobTest {

    /**
     * Helper to create a test stream from message strings using the modern Source API.
     * Creates a local environment with parallelism=1 for deterministic test execution.
     */
    private fun getTestStream(vararg messages: String): DataStream<String> {
        val env = StreamExecutionEnvironment.createLocalEnvironment()
        env.setParallelism(1)

        val generatorFunction = GeneratorFunction<Long, String> { index ->
            if (index < messages.size) {
                messages[index.toInt()]
            } else {
                null
            }
        }
        val source = DataGeneratorSource(generatorFunction, messages.size.toLong(), Types.STRING)
        return env.fromSource(source, WatermarkStrategy.noWatermarks(), "test-source")
    }

    @Test
    fun `should parse and enrich valid events`() {
        // Given: Test input data
        val validEvent = """{"id":"event-123","type":"user.signup","timestamp":1234567890,"data":{"userId":"user-456"}}"""
        val rawEventStream = getTestStream(validEvent)

        // When: Process through topology
        val streams = EventProcessorJob.getOutputStreams(rawEventStream)

        // Then: Collect and verify processed events
        val processedEvents = streams.enrichedValidEvents.executeAndCollect(10).toList()

        assertEquals(1, processedEvents.size)
        val processed = processedEvents[0]
        assertEquals("event-123", processed.originalId)
        assertEquals("user.signup", processed.eventType)
        assertTrue(processed.processingDelay >= 0)
        assertEquals(1234567890L, processed.enrichedData["original_timestamp"])
        assertEquals("flink-event-processor", processed.enrichedData["processing_pipeline"])
        assertEquals(1, processed.sequence)
    }

    @Test
    fun `should route invalid JSON to error stream`() {
        // Given: Invalid input
        val invalidEvent = """not valid json at all"""
        val rawEventStream = getTestStream(invalidEvent)

        // When: Process through topology
        val streams = EventProcessorJob.getOutputStreams(rawEventStream)

        // Then: Collect and verify error events
        val errorEvents = streams.errorEvents.executeAndCollect(10).toList()

        assertEquals(1, errorEvents.size)
        val error = errorEvents[0]
        assertEquals("not valid json at all", error.rawMessage)
        assertEquals("PARSE_ERROR", error.errorType)
        assertTrue(error.errorMessage.isNotEmpty())
    }

    @Test
    fun `should handle mixed valid and invalid events`() {
        // Given: Mixed valid and invalid input
        val validEvent1 = """{"id":"12","type":"order.created","timestamp":1000,"data":{}}"""
        val invalidEvent = """garbage"""
        val validEvent2 = """{"id":"23","type":"order.shipped","timestamp":2000,"data":{"orderId":"ord-123"}}"""

        val rawEventStream = getTestStream(validEvent1, invalidEvent, validEvent2)

        // When: Process through topology
        val streams = EventProcessorJob.getOutputStreams(rawEventStream)

        // Then: Verify both streams
        val processedEvents = streams.enrichedValidEvents.executeAndCollect(10).toList()
        val errorEvents = streams.errorEvents.executeAndCollect(10).toList()

        assertEquals(2, processedEvents.size)
        assertEquals(1, errorEvents.size)

        assertEquals("12", processedEvents[0].originalId)
        assertEquals("23", processedEvents[1].originalId)
        assertEquals("garbage", errorEvents[0].rawMessage)
    }

    @Test
    fun `should enrich events with processing metadata`() {
        // Given: Event data
        val event = """{"id":"meta-test","type":"test.event","timestamp":5000,"data":{"key":"value"}}"""
        val rawEventStream = getTestStream(event)

        // When: Process through topology
        val streams = EventProcessorJob.getOutputStreams(rawEventStream)

        // Then: Verify enrichment
        val processedEvents = streams.enrichedValidEvents.executeAndCollect(10).toList()

        assertEquals(1, processedEvents.size)
        val processed = processedEvents[0]

        // Original data preserved
        assertEquals("value", processed.enrichedData["key"])

        // Metadata added
        assertEquals(5000L, processed.enrichedData["original_timestamp"])
        assertEquals("flink-event-processor", processed.enrichedData["processing_pipeline"])

        // Processing delay calculated
        assertTrue(processed.processingDelay > 0)
    }

    @Test
    fun `should assign a sequence to valid events on a per-key basis`() {
        // Given: Valid inputs split across two distinct keys (id)
        val rawEventStream = getTestStream(
            """{"id":"12","type":"order.created","timestamp":1000,"data":{}}""",
            """{"id":"23","type":"order.shipped","timestamp":2000,"data":{"orderId":"ord-123"}}""",
            """{"id":"23","type":"order.shipped","timestamp":2000,"data":{"orderId":"ord-124"}}""",
            """{"id":"12","type":"order.shipped","timestamp":1000,"data":{"orderID":"ord-125"}}"""
        )

        // When: Process through topology
        val streams = EventProcessorJob.getOutputStreams(rawEventStream)

        // Then: Verify both streams
        val processedEvents = streams.enrichedValidEvents.executeAndCollect(10).toList()
        val errorEvents = streams.errorEvents.executeAndCollect(10).toList()

        assertEquals(4, processedEvents.size)
        assertEquals(0, errorEvents.size)

        assertEquals("12", processedEvents[0].originalId)
        assertEquals(1, processedEvents[0].sequence)
        assertEquals("23", processedEvents[1].originalId)
        assertEquals(1, processedEvents[1].sequence)
        assertEquals("23", processedEvents[2].originalId)
        assertEquals(2, processedEvents[2].sequence)
        assertEquals("12", processedEvents[3].originalId)
        assertEquals(2, processedEvents[3].sequence)
    }
}
