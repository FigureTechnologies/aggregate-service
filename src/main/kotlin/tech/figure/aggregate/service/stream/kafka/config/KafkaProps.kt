package tech.figure.aggregate.service.stream.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import tech.figure.aggregate.common.KafkaConfig
import java.io.Serializable

class KafkaProps(
    config: KafkaConfig
) {

    private val producerConfig = mapOf(
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to config.bootstrapServer,
        ProducerConfig.ACKS_CONFIG to "all",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
    )

    private val saslProperties = when(config.securityProtocol) {
        "SASL_SSL" -> mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to config.securityProtocol,
            CommonClientConfigs.CLIENT_DNS_LOOKUP_CONFIG to config.confluentClientDnsLookup,
            SaslConfigs.SASL_JAAS_CONFIG to config.saslJaasConfig,
            SaslConfigs.SASL_MECHANISM to config.saslMechanism
        )
        else -> emptyMap<String, Serializable>()
    }

    fun producerProperties(clientId: String) = producerConfig + saslProperties + (ProducerConfig.CLIENT_ID_CONFIG to clientId)

}
