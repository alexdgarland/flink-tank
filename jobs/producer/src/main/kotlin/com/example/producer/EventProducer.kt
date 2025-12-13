package com.example.producer

import com.example.events.InputEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import kotlin.random.Random

object EventProducer {
    private val logger = LoggerFactory.getLogger(EventProducer::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    // Pool of 50 user keys for bounded key space
    private val keyPool = (1..50).map { "user-$it" }

    // Event types to randomly select from
    private val eventTypes = listOf("login", "purchase", "view", "click", "logout")

    private const val ERROR_RATE = 0.05  // 5% of events are malformed
    private const val EVENT_INTERVAL_MS = 500L  // 2 events per second

    private fun generateValidEvent(): InputEvent {
        val userId = keyPool.random()
        return InputEvent(
            id = userId,
            type = eventTypes.random(),
            timestamp = Instant.now().toEpochMilli(),
            data = mapOf(
                "session_id" to UUID.randomUUID().toString(),
                "value" to Random.nextDouble(1.0, 100.0),
                "category" to listOf("electronics", "books", "clothing", "food").random()
            )
        )
    }

    private fun generateInvalidEvent(): String {
        // Generate various types of invalid events
        return when (Random.nextInt(4)) {
            0 -> "{invalid json"  // Malformed JSON
            1 -> "{}"  // Empty object
            2 -> """{"id": "user-1"}"""  // Missing required fields
            3 -> """{"id": "user-1", "type": "", "timestamp": "not-a-number"}"""  // Wrong types
            else -> "{}"
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // Parse command line arguments
        val argsMap = args.toList().windowed(2, 2)
            .filter { it.size == 2 && it[0].startsWith("--") }
            .associate { it[0].removePrefix("--") to it[1] }

        val kafkaBootstrap = argsMap["kafka-bootstrap-servers"] ?: "my-cluster-kafka-bootstrap.kafka.svc:9092"
        val topic = argsMap["topic"] ?: "input-events"

        logger.info("Starting Event Producer")
        logger.info("Configuration: kafka=$kafkaBootstrap, topic=$topic, rate=${1000 / EVENT_INTERVAL_MS} events/sec")

        // Configure Kafka producer
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
        }

        val producer = KafkaProducer<String, String>(props)

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down producer...")
            producer.close()
            logger.info("Producer closed")
        })

        var eventCount = 0
        var errorCount = 0

        try {
            while (true) {
                val (key, event, isValid) = if (Random.nextDouble() < ERROR_RATE) {
                    Triple(null, generateInvalidEvent(), false)
                } else {
                    val validEvent = generateValidEvent()
                    Triple(validEvent.id, objectMapper.writeValueAsString(validEvent), true)
                }

                val record = ProducerRecord(topic, key, event)
                producer.send(record) { metadata, exception ->
                    if (exception != null) {
                        logger.error("Failed to send event", exception)
                    } else {
                        eventCount++
                        if (!isValid) errorCount++

                        if (eventCount % 100 == 0) {
                            logger.info("Sent $eventCount events ($errorCount invalid) - latest offset: ${metadata.offset()}")
                        } else {
                            logger.debug("Sent ${if (isValid) "valid" else "invalid"} event to ${metadata.topic()}-${metadata.partition()}@${metadata.offset()}")
                        }
                    }
                }

                Thread.sleep(EVENT_INTERVAL_MS)
            }
        } catch (e: InterruptedException) {
            logger.info("Producer interrupted")
        } catch (e: Exception) {
            logger.error("Producer failed", e)
            throw e
        } finally {
            producer.close()
            logger.info("Final stats: $eventCount events sent ($errorCount invalid)")
        }
    }
}
