package tech.figure.aggregate.service.test.mocks

import tech.figure.aggregate.common.aws.s3.client.DefaultS3Client
import tech.figure.aggregate.common.aws.s3.S3Bucket
import kotlinx.coroutines.future.await
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path

class LocalStackS3(s3Client: S3AsyncClient, bucket: S3Bucket) : DefaultS3Client(s3Client, bucket) {

    suspend fun createBucket(): CreateBucketResponse =
        s3Client.createBucket(
            CreateBucketRequest
                .builder()
                .bucket(bucket.value)
                .build()
        )
            .await()

    suspend fun getBucketObjects(): List<S3Object> =
        s3Client.listObjectsV2(
            ListObjectsV2Request
                .builder()
                .bucket(bucket.value)
                .build()
        ).await()
            .contents()

    suspend fun listBucketObjectKeys(): List<String> =
        getBucketObjects().map { it.key() }

    suspend fun deleteBucketObjects(): DeleteObjectsResponse? {
        val keys: List<String> = listBucketObjectKeys()
        val identifiers: List<ObjectIdentifier> = keys.map { ObjectIdentifier.builder().key(it).build() }
        return if (identifiers.isNotEmpty()) {
            s3Client.deleteObjects(
                DeleteObjectsRequest.builder()
                    .bucket(bucket.value)
                    .delete(Delete.builder().objects(identifiers).build())
                    .build()
            )
                .await()
        } else {
            null
        }
    }

    suspend fun readContent(key: String): List<CSVRecord> {
        val path = "/tmp/tmp_data.csv"

        GetObjectRequest.builder()
            .bucket(bucket.value)
            .key(key)
            .build()
            .also { request ->
                s3Client.getObject(request, Path(path)).await()
            }

        val reader = Files.newBufferedReader(Paths.get(path))
        val records = (CSVParser(reader, CSVFormat.DEFAULT).records)

        val file = File(path)
        if(file.exists()) {
            file.delete()
        }

        return records
    }

    suspend fun deleteBucket() {
        s3Client.deleteBucket(
            DeleteBucketRequest.builder()
                .bucket(bucket.value)
                .build()
        )
            .await()
    }

    suspend fun emptyAndDeleteBucket() {
        deleteBucketObjects()
        deleteBucket()
    }
}
