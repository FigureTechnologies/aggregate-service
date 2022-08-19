package tech.figure.aggregate.repository.database.dynamo.client

import tech.figure.aggregate.common.DynamoConfig
import tech.figure.aggregate.common.aws.AwsClient
import tech.figure.aggregate.common.logger
import tech.figure.aggregate.repository.RepositoryBase
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.net.URI
import java.time.Duration

class DynamoClient(
    val dynamoConfig: DynamoConfig,
    val region: String? = null
) {

    private val log = logger()

    val dynamoClient: DynamoDbAsyncClient by lazy {
        createDynamoDbClient()
    }

    protected open fun getRegion(): Region = region?.let { Region.of(it) } ?: AwsClient.DEFAULT_REGION

    protected open fun getCredentialsProvider(): AwsCredentialsProvider = DefaultCredentialsProvider.create()

    protected fun getAcquisitionTimeout(): Duration = Duration.ofSeconds(60)

    /**
     * Overrides the default endpoint used by the AWS SDK. This is useful when using something like LocalStack which
     * emulates various AWS services running locally.
     */
    protected open fun getEndpointOverride(): URI? = null

    /**
     * Returns a client for interacting with AWS DynamoDB.
     */
    open fun dynamo(): RepositoryBase {
        return DefaultDynamoClient(
            dynamoClient,
            dynamoConfig.blockBatchTable,
            dynamoConfig.blockMetadataTable,
            dynamoConfig.serviceMetadataTable
        )
    }

    private fun createNettyClient(): SdkAsyncHttpClient {
        return NettyNioAsyncHttpClient.builder()
            .connectionAcquisitionTimeout(getAcquisitionTimeout())
            .build()
    }

    private fun createDynamoDbClient(): DynamoDbAsyncClient {
        val override = getEndpointOverride()
        val httpClient = createNettyClient()
        return DynamoDbAsyncClient.builder()
            .credentialsProvider(getCredentialsProvider())
            .region(getRegion())
            .httpClient(httpClient)
            .apply {
                if (override != null) {
                    log.info("Using endpoint override: $override")
                    endpointOverride(override)
                }
            }
            .build()
    }
}
