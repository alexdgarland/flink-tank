package com.example.flink

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.util.OutputTag
import org.slf4j.LoggerFactory
import java.time.Instant

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
    val enrichedData: Map<String, Any>
)

data class ErrorEvent(
    val rawMessage: String,
    val errorType: String,
    val errorMessage: String,
    val timestamp: String
)

data class ProcessingStreams(
    val processedEvents: DataStream<ProcessedEvent>,
    val errorEvents: DataStream<ErrorEvent>
)

data class Connectors(
    val kafkaSource: KafkaSource<String>,
    val kafkaSink: KafkaSink<String>,
    val errorKafkaSink: KafkaSink<String>
)

object EventProcessorJob {
    private val logger = LoggerFactory.getLogger(EventProcessorJob::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Output tag for side output (errors) - must be anonymous class for type inference
    private val errorOutputTag = object : OutputTag<ErrorEvent>("error-output") {}

    fun getConnectors(args: Array<String>): Connectors {
        // Parse command line arguments using Flink's ParameterTool
        val params = ParameterTool.fromArgs(args)

        val kafkaBootstrap = params.get("kafka-bootstrap-servers", "my-cluster-kafka-bootstrap.kafka.svc:9092")
        val inputTopic = params.get("input-topic", "input-events")
        val outputTopic = params.get("output-topic", "output-results")
        val errorTopic = params.get("error-topic", "error-events")
        val consumerGroup = params.get("consumer-group", "flink-event-processor")

        logger.info("Configuration: kafka=$kafkaBootstrap, input=$inputTopic, output=$outputTopic, error=$errorTopic, group=$consumerGroup")

        // Kafka source configuration
        val kafkaSource = KafkaSource.builder<String>()
            .setBootstrapServers(kafkaBootstrap)
            .setTopics(inputTopic)
            .setGroupId(consumerGroup)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(SimpleStringSchema())
            .build()

        // Kafka sink for successful events
        val kafkaSink = KafkaSink.builder<String>()
            .setBootstrapServers(kafkaBootstrap)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder<String>()
                    .setTopic(outputTopic)
                    .setValueSerializationSchema(SimpleStringSchema())
                    .build()
            )
            .build()

        // Kafka sink for error events
        val errorKafkaSink = KafkaSink.builder<String>()
            .setBootstrapServers(kafkaBootstrap)
            .setRecordSerializer(
                KafkaRecordSerializationSchema.builder<String>()
                    .setTopic(errorTopic)
                    .setValueSerializationSchema(SimpleStringSchema())
                    .build()
            )
            .build()

        return Connectors(kafkaSource, kafkaSink, errorKafkaSink)
    }

    fun getOutputStreams(rawEventStream: DataStream<String>): ProcessingStreams {
        // Parse and route events
        val parsedRoutedStream = rawEventStream
            .process(object : ProcessFunction<String, InputEvent>() {
                override fun processElement(
                    rawEvent: String,
                    ctx: Context,
                    out: Collector<InputEvent>
                ) {
                    logger.debug("Processing raw event: $rawEvent")
                    try {
                        val inputEvent: InputEvent = objectMapper.readValue(rawEvent)
                        logger.debug("Parsed input event: {}", inputEvent)
                        out.collect(inputEvent)
                    } catch (e: JsonProcessingException) {
                        logger.warn("Failed to parse event: $rawEvent", e)
                        // Send to error side output
                        val errorEvent = ErrorEvent(
                            rawMessage = rawEvent,
                            errorType = "PARSE_ERROR",
                            errorMessage = e.message ?: "Unknown parse error",
                            timestamp = Instant.now().toString()
                        )
                        ctx.output(errorOutputTag, errorEvent)
                    }
                }
            })
            .name("Parse and Route")

        // Main stream: process valid events
        val processedEvents = parsedRoutedStream
            .map { event ->
                // Process the event - add some enrichment
                val now = System.currentTimeMillis()
                val delay = if (event.timestamp > 0) now - event.timestamp else 0

                val enrichedData = event.data.toMutableMap()
                enrichedData["original_timestamp"] = event.timestamp
                enrichedData["processing_pipeline"] = "flink-event-processor"

                ProcessedEvent(
                    originalId = event.id,
                    eventType = event.type,
                    processedAt = Instant.ofEpochMilli(now).toString(),
                    processingDelay = delay,
                    enrichedData = enrichedData
                )
            }
            .name("Enrich Events")

        // Error stream
        val errorEvents = parsedRoutedStream.getSideOutput(errorOutputTag)

        return ProcessingStreams(processedEvents, errorEvents)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Starting Event Processor Job")

        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.enableCheckpointing(60000) // Checkpoint every 60 seconds

        val connectors = getConnectors(args)

        // Create the raw input stream from Kafka
        val rawEventStream = env.fromSource<String>(
            connectors.kafkaSource,
            WatermarkStrategy.noWatermarks(),
            "Kafka Source"
        )

        // Process events and get back typed streams
        val streams = getOutputStreams(rawEventStream)

        // Serialize and sink processed events
        streams.processedEvents
            .map { processedEvent ->
                val json = objectMapper.writeValueAsString(processedEvent)
                logger.debug("Emitting processed event: $json")
                json
            }
            .sinkTo(connectors.kafkaSink)
            .name("Kafka Sink - Results")

        // Serialize and sink error events
        streams.errorEvents
            .map { errorEvent ->
                objectMapper.writeValueAsString(errorEvent)
            }
            .sinkTo(connectors.errorKafkaSink)
            .name("Kafka Sink - Errors")

        env.execute("Event Processor Job")
    }
}
