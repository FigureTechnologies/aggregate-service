package tech.figure.aggregate.service.stream.kafka.producer

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducer
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducerParam
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MarkerSupplyKafkaProducer(
    private val producerProps: Map<String, Any>,
    private val topicName: String,
    private val producer: KafkaProducer<String, ByteArray> = KafkaProducer(producerProps)
): BaseKafkaProducer {

    private val log = logger()
    suspend fun publish(param: BaseKafkaProducerParam.MarkerSupplyParam) {
        log.info("publishing marker supply data to kafka - BlockHeight: ${param.markerSupplyData.blockHeight}" )
//        runBlocking {
//            producer.send(
//                ProducerRecord(
//                    topicName,
//                    1,
//                    param.markerSupplyData.blockHeight.toString(),
//                    Json.encodeToString(param.markerSupplyData).toByteArray()
//                )
//            )
//        }


        val producerRecord = ProducerRecord(
            topicName,
            1,
            param.markerSupplyData.blockHeight.toString(),
            Json.encodeToString(param.markerSupplyData).toByteArray()
        )

//        producer.send(producerRecord) { recordMetadata, _ ->
//            // executes every time a record is successfully sent or an exception is thrown
//            //if (e == null) {
//                // the record was successfully sent
//                println(
//                    "Received new metadata. \n" +
//                            "Topic:" + recordMetadata.topic() + "\n" +
//                            "Partition: " + recordMetadata.partition() + "\n" +
//                            "Offset: " + recordMetadata.offset() + "\n" +
//                            "Timestamp: " + recordMetadata.timestamp()
//                )
//           // } else {
//
//           // }
//        }

        producer.dispatch(producerRecord)
    }

    suspend inline fun <reified K : Any, reified V : Any> KafkaProducer<K, V>.dispatch(record: ProducerRecord<K, V>) =
        suspendCoroutine<RecordMetadata> { continuation ->
            val callback = Callback { metadata, exception ->
                if (metadata == null) {
                    continuation.resumeWithException(exception!!)
                } else {
                    continuation.resume(metadata)
                }
            }
            this.send(record, callback)
        }

}
