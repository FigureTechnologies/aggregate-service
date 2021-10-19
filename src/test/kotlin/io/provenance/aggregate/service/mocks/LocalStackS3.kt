package io.provenance.aggregate.service.test.mocks

import io.provenance.aggregate.service.aws.s3.client.DefaultS3Client
import io.provenance.aggregate.service.aws.s3.S3Bucket
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.*

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
