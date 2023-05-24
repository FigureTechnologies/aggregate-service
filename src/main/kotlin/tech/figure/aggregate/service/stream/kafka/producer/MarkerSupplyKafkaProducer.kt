package tech.figure.aggregate.service.stream.kafka.producer

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducer
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducerParam

class MarkerSupplyKafkaProducer(
    private val producerProps: Map<String, Any>,
    private val topicName: String,
    private val producer: KafkaProducer<String, ByteArray> = KafkaProducer(producerProps)
): BaseKafkaProducer {

    private val log = logger()
    fun publish(param: BaseKafkaProducerParam.MarkerSupplyParam) {
        log.info("publishing marker supply data to kafka - BlockHeight: ${param.markerSupplyData.blockHeight}" )
        runBlocking {
            producer.send(
                ProducerRecord(
                    topicName,
                    param.markerSupplyData.blockHeight.toString(),
                    Json.encodeToString(param.markerSupplyData).toByteArray()
                )
            )
        }
    }

}
