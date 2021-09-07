package io.provenance.aggregate.service

import cloud.localstack.ServiceName
import cloud.localstack.docker.LocalstackDockerExtension
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(LocalstackDockerExtension::class)
@LocalstackDockerProperties(services = [ServiceName.S3, ServiceName.DYNAMO])
class AWSTests {
    @Test
    fun testSetup() {

    }
}