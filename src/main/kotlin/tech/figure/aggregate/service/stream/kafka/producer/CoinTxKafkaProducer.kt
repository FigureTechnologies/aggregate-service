package tech.figure.aggregate.service.stream.kafka.producer

import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducer
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducerParam

class CoinTxKafkaProducer(
    private val producerProps: Map<String, Any>,
    private val topicName: String,
    private val producer: KafkaProducer<String, ByteArray> = KafkaProducer(producerProps)
): BaseKafkaProducer {

    private val log = logger()
    fun publish(param: BaseKafkaProducerParam.CoinTransferParam) {
        log.info("publishing coin transfer data to kafka - BlockHeight: ${param.coinTransferData.blockHeight}" )
        runBlocking {
            producer.send(
                ProducerRecord(
                    topicName,
                    param.coinTransferData.blockHeight.toString(),
                    Json.encodeToString(param.coinTransferData).toByteArray()
                )
            )
        }
    }
}
