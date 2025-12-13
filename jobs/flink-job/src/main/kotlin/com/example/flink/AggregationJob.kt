package com.example.flink

import com.example.events.AggregatedMetrics
import com.example.events.ProcessedEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Aggregation job that consumes enriched events and produces sliding window metrics.
 *
 * IMPLEMENTATION NOTE: This job uses manual state management with KeyedProcessFunction
 * to explicitly demonstrate Flink's state primitives and timer mechanisms.
 *
 * In production, you would typically use Flink's declarative windowing API instead:
 * ```kotlin
 * stream
 *   .keyBy { it.originalId }
 *   .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.seconds(5)))
 *   .aggregate(object : AggregateFunction<ProcessedEvent, EventAccumulator, AggregatedMetrics> {
 *     override fun createAccumulator() = EventAccumulator()
 *     override fun add(event: ProcessedEvent, acc: EventAccumulator) = acc.apply {
 *       eventTypeCounts[event.eventType] = (eventTypeCounts[event.eventType] ?: 0) + 1
 *     }
 *     override fun getResult(acc: EventAccumulator) = AggregatedMetrics(...)
 *     override fun merge(a: EventAccumulator, b: EventAccumulator) = ...
 *   })
 * ```
 *
 * The declarative approach is more maintainable, better tested, and handles edge cases
 * (late events, watermarks, event-time vs processing-time) automatically.
 */
object AggregationJob {
    private val logger = LoggerFactory.getLogger(AggregationJob::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Window configuration
    private const val WINDOW_SIZE_MS = 10 * 60 * 1000L  // 10 minutes
    private const val EMIT_INTERVAL_MS = 5 * 1000L       // 5 seconds

    /**
     * Internal state representation: list of events within the window.
     * In production, consider using ListState or MapState for better performance.
     */
    data class EventRecord(
        val timestamp: Long,
        val eventType: String
    )

    /**
     * Wrapper for state to handle serialization.
     */
    data class WindowState(
        val events: MutableList<EventRecord> = mutableListOf()
    )

    /**
     * KeyedProcessFunction that manually manages sliding window state.
     *
     * State management:
     * - Uses ValueState to store list of events in current window
     * - Uses processing-time timers to emit aggregates every 5 seconds
     * - Prunes old events (outside 10-minute window) on each timer fire
     *
     * This demonstrates:
     * 1. State registration and access (getRuntimeContext().getState)
     * 2. Processing-time timers (registerProcessingTimeTimer)
     * 3. Manual event pruning and aggregation
     */
    private class SlidingWindowAggregator : KeyedProcessFunction<String, ProcessedEvent, AggregatedMetrics>() {

        private var windowState: ValueState<WindowState>? = null

        @Throws(Exception::class)
        override fun open(openContext: OpenContext?) {
            // Register state: this is managed by Flink's state backend (filesystem in our case)
            // State is keyed by userId, so each user has independent state
            windowState = getRuntimeContext().getState(
                ValueStateDescriptor("window-state", WindowState::class.java)
            )
        }

        override fun processElement(
            event: ProcessedEvent,
            ctx: Context,
            out: Collector<AggregatedMetrics>
        ) {
            // Get current state or initialize new one
            val state = windowState?.value() ?: WindowState()

            // Add this event to the window
            state.events.add(EventRecord(
                timestamp = System.currentTimeMillis(),
                eventType = event.eventType
            ))

            // Update state
            windowState?.update(state)

            // Register a timer for the next emission if one isn't already set
            // We emit every 5 seconds, so round up to next 5-second boundary
            val currentTime = ctx.timerService().currentProcessingTime()
            val nextEmitTime = ((currentTime / EMIT_INTERVAL_MS) + 1) * EMIT_INTERVAL_MS
            ctx.timerService().registerProcessingTimeTimer(nextEmitTime)
        }

        override fun onTimer(
            timestamp: Long,
            ctx: OnTimerContext,
            out: Collector<AggregatedMetrics>
        ) {
            val state = windowState?.value() ?: return

            val now = System.currentTimeMillis()
            val windowStart = now - WINDOW_SIZE_MS

            // Prune events outside the 10-minute window
            state.events.removeIf { it.timestamp < windowStart }

            // Only emit if we have events in the window
            if (state.events.isNotEmpty()) {
                // Aggregate: count total and by event type
                val totalCount = state.events.size
                val typeCounts = state.events
                    .groupingBy { it.eventType }
                    .eachCount()

                // Emit aggregated metrics
                val metrics = AggregatedMetrics(
                    userId = ctx.currentKey,
                    windowStart = Instant.ofEpochMilli(windowStart).toString(),
                    windowEnd = Instant.ofEpochMilli(now).toString(),
                    totalEventCount = totalCount,
                    eventTypeCounts = typeCounts
                )
                out.collect(metrics)

                logger.debug("Emitted metrics for user ${ctx.currentKey}: $totalCount events, types: $typeCounts")
            }

            // Update state with pruned events
            windowState?.update(state)

            // Register next timer (5 seconds from now)
            ctx.timerService().registerProcessingTimeTimer(timestamp + EMIT_INTERVAL_MS)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Starting Aggregation Job")

        val params = ParameterTool.fromArgs(args)
        val kafkaBootstrap = params.get("kafka-bootstrap-servers", "my-cluster-kafka-bootstrap.kafka.svc:9092")
        val inputTopic = params.get("input-topic", "output-results")
        val outputTopic = params.get("output-topic", "aggregated-metrics")
        val consumerGroup = params.get("consumer-group", "flink-aggregation")

        logger.info("Configuration: kafka=$kafkaBootstrap, input=$inputTopic, output=$outputTopic, group=$consumerGroup")

        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.enableCheckpointing(60000)

        // Kafka source: consume enriched events from EventProcessorJob
        val kafkaSource = KafkaSource.builder<String>()
            .setBootstrapServers(kafkaBootstrap)
            .setTopics(inputTopic)
            .setGroupId(consumerGroup)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(SimpleStringSchema())
            .build()

        // Kafka sink: emit aggregated metrics
        val serializationSchema = KafkaRecordSerializationSchema.builder<String>()
            .setTopic(outputTopic)
            .setValueSerializationSchema(SimpleStringSchema())
            .build()

        val kafkaSink = KafkaSink.builder<String>()
            .setBootstrapServers(kafkaBootstrap)
            .setRecordSerializer(serializationSchema)
            .build()

        // Build processing pipeline
        env.fromSource(kafkaSource, WatermarkStrategy.noWatermarks(), "Kafka Source")
            // Parse JSON to ProcessedEvent
            .map { json ->
                try {
                    objectMapper.readValue<ProcessedEvent>(json)
                } catch (e: Exception) {
                    logger.warn("Failed to parse ProcessedEvent: $json", e)
                    null
                }
            }
            .name("Parse ProcessedEvents")
            // Filter out parse failures (use filterNotNull to get non-nullable type)
            .filter { it != null }
            .map { it!! }  // Cast to non-null type for type safety
            // Key by user ID
            .keyBy { it.originalId }
            // Apply manual windowing aggregator
            .process(SlidingWindowAggregator())
            .name("Sliding Window Aggregator")
            // Serialize to JSON
            .map { metrics: AggregatedMetrics ->
                val json = objectMapper.writeValueAsString(metrics)
                logger.debug("Emitting aggregated metrics: $json")
                json
            }
            .name("Serialize to JSON")
            // Write to Kafka
            .sinkTo(kafkaSink)
            .name("Kafka Sink - Aggregated Metrics")

        env.execute("Aggregation Job")
    }
}
