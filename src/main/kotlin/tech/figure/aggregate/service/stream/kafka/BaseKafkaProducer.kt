package tech.figure.aggregate.service.stream.kafka

import tech.figure.aggregate.common.models.stream.CoinTransfer
import tech.figure.aggregate.common.models.stream.MarkerSupply
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducerParam.CoinTransferParam
import tech.figure.aggregate.service.stream.kafka.BaseKafkaProducerParam.MarkerSupplyParam
import tech.figure.aggregate.service.stream.kafka.producer.CoinTxKafkaProducer
import tech.figure.aggregate.service.stream.kafka.producer.MarkerSupplyKafkaProducer

interface BaseKafkaProducer
sealed class BaseKafkaProducerParam {

    data class CoinTransferParam(val coinTransferData: CoinTransfer): BaseKafkaProducerParam()

    data class MarkerSupplyParam( val markerSupplyData: MarkerSupply): BaseKafkaProducerParam()
}

class KafkaProducerFactory(
    private val producers: List<BaseKafkaProducer>
) {

    suspend fun publish(param: BaseKafkaProducerParam) = when(param) {
        is CoinTransferParam -> producers.filterIsInstance<CoinTxKafkaProducer>().first().publish(param)
        is MarkerSupplyParam -> producers.filterIsInstance<MarkerSupplyKafkaProducer>().first().publish(param)
    }
}
