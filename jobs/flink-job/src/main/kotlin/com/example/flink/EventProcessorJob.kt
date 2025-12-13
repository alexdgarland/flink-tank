package com.example.flink

import com.example.events.ErrorEvent
import com.example.events.InputEvent
import com.example.events.ProcessedEvent
import com.fasterxml.jackson.core.JsonProcessingException
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
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.util.OutputTag
import org.slf4j.LoggerFactory
import java.time.Instant


data class Connectors(
    val kafkaSource: KafkaSource<String>,
    val kafkaSink: KafkaSink<String>,
    val errorKafkaSink: KafkaSink<String>
)

data class OutputStreams(
    val enrichedValidEvents: DataStream<ProcessedEvent>,
    val errorEvents: DataStream<ErrorEvent>
)

object EventProcessorJob {
    private val logger = LoggerFactory.getLogger(EventProcessorJob::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val errorOutputTag = object : OutputTag<ErrorEvent>("error-output") {}

    fun getConnectors(args: Array<String>): Connectors {
        val params = ParameterTool.fromArgs(args)
        val kafkaBootstrap = params.get("kafka-bootstrap-servers", "my-cluster-kafka-bootstrap.kafka.svc:9092")
        val inputTopic = params.get("input-topic", "input-events")
        val outputTopic = params.get("output-topic", "output-results")
        val errorTopic = params.get("error-topic", "error-events")
        val consumerGroup = params.get("consumer-group", "flink-event-processor")

        logger.info("Configuration: kafka=$kafkaBootstrap, input=$inputTopic, output=$outputTopic, error=$errorTopic, group=$consumerGroup")

        val kafkaSource = KafkaSource.builder<String>()
            .setBootstrapServers(kafkaBootstrap)
            .setTopics(inputTopic)
            .setGroupId(consumerGroup)
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(SimpleStringSchema())
            .build()

        val getOutputSink = { topicName: String ->
            val serializationSchema = KafkaRecordSerializationSchema.builder<String>()
                .setTopic(topicName)
                .setValueSerializationSchema(SimpleStringSchema())
                .build()
            KafkaSink.builder<String>()
                .setBootstrapServers(kafkaBootstrap)
                .setRecordSerializer(serializationSchema)
                .build()
        }

        return Connectors(kafkaSource, kafkaSink=getOutputSink(outputTopic), errorKafkaSink=getOutputSink(errorTopic))
    }

    private class ParseAndRoute: ProcessFunction<String, InputEvent>() {
        override fun processElement(rawEvent: String, ctx: Context, out: Collector<InputEvent>) {
            logger.debug("Processing raw event: $rawEvent")
            try {
                val inputEvent: InputEvent = objectMapper.readValue(rawEvent)
                logger.debug("Parsed input event: {}", inputEvent)
                out.collect(inputEvent)
            } catch (e: JsonProcessingException) {
                logger.warn("Failed to parse event: $rawEvent", e)
                val errorEvent = ErrorEvent(
                    rawMessage = rawEvent,
                    errorType = "PARSE_ERROR",
                    errorMessage = e.message ?: "Unknown parse error",
                    timestamp = Instant.now().toString()
                )
                ctx.output(errorOutputTag, errorEvent)
            }
        }
    }

    private class EnrichValidEvent: KeyedProcessFunction<String, InputEvent, ProcessedEvent>() {

        private var latestSequence: ValueState<Int>? = null

        @Throws(Exception::class)
        override fun open(openContext: OpenContext?) {
            latestSequence = getRuntimeContext().getState<Int>(
                ValueStateDescriptor("sequence", Int::class.java)
            )
        }

        override fun processElement(validInputEvent: InputEvent, ctx: Context, out: Collector<ProcessedEvent>) {
            val nextSequence: Int = (latestSequence?.value() ?: 0) + 1
            val now = System.currentTimeMillis()
            val delay = if (validInputEvent.timestamp > 0) now - validInputEvent.timestamp else 0
            val enrichedData = validInputEvent.data.toMutableMap()
            enrichedData["original_timestamp"] = validInputEvent.timestamp
            enrichedData["processing_pipeline"] = "flink-event-processor"
            val processed = ProcessedEvent(
                originalId = validInputEvent.id,
                eventType = validInputEvent.type,
                processedAt = Instant.ofEpochMilli(now).toString(),
                processingDelay = delay,
                enrichedData = enrichedData,
                sequence = nextSequence
            )
            out.collect(processed)
            latestSequence?.update(nextSequence)
        }
    }

    fun getOutputStreams(rawEventStream: DataStream<String>): OutputStreams {
        val parsedRoutedStream = rawEventStream
            .process(ParseAndRoute())
            .name("Parse and Route")

        val processedEvents = parsedRoutedStream
            .keyBy { it.id }
            .process(EnrichValidEvent())
            .name("Enrich Events")

        val errorEvents = parsedRoutedStream.getSideOutput(errorOutputTag)

        return OutputStreams(processedEvents, errorEvents)
    }

    private fun <T> serializeToSink(stream: DataStream<T>, sink: KafkaSink<String>, eventType: String) {
        stream
            .map { event ->
                val json = objectMapper.writeValueAsString(event)
                logger.debug("Emitting $eventType event: $json")
                json
            }
            .sinkTo(sink)
            .name("Kafka Sink - ${eventType.replaceFirstChar(Char::titlecase)}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("Starting Event Processor Job")

        val env = StreamExecutionEnvironment.getExecutionEnvironment()
        env.enableCheckpointing(60000)

        val connectors = getConnectors(args)

        val rawEventStream = env.fromSource<String>(
            connectors.kafkaSource,
            WatermarkStrategy.noWatermarks(),
            "Kafka Source"
        )

        val outputStreams = getOutputStreams(rawEventStream)

        serializeToSink(outputStreams.enrichedValidEvents, connectors.kafkaSink, "enriched")
        serializeToSink(outputStreams.errorEvents, connectors.errorKafkaSink, "error")

        env.execute("Event Processor Job")
    }
}
