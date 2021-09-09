package io.provenance.aggregate.service.mocks

import io.provenance.aggregate.service.S3Config
import io.provenance.aggregate.service.aws.LocalStackAwsInterface
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.s3.model.*

class MockAwsInterface(s3Config: S3Config) : LocalStackAwsInterface(s3Config) {

    suspend fun createBucket(): CreateBucketResponse =
        s3Client.createBucket(
            CreateBucketRequest
                .builder()
                .bucket(s3Config.bucket)
                .build()
        )
            .await()

    suspend fun getBucketObjects(): List<S3Object> =
        s3Client.listObjectsV2(
            ListObjectsV2Request
                .builder()
                .bucket(s3Config.bucket)
                .build()
        ).await()
            .contents()

    suspend fun listBucketObjectKeys(): List<String> =
        getBucketObjects().map { it.key() }

    suspend fun deleteBucketObjects(): DeleteObjectsResponse? {
        val keys: List<String> = listBucketObjectKeys()
        val identifiers: List<ObjectIdentifier> = keys.map { ObjectIdentifier.builder().key(it).build() }
        return s3Client.deleteObjects(
            DeleteObjectsRequest.builder()
                .bucket(s3Config.bucket)
                .delete(Delete.builder().objects(identifiers).build())
                .build()
        )
            .await()
    }

    suspend fun deleteBucket() {
        s3Client.deleteBucket(
            DeleteBucketRequest.builder()
                .bucket(s3Config.bucket)
                .build()
        )
            .await()
    }

    suspend fun emptyAndDeleteBucket() {
        deleteBucketObjects()
        deleteBucket()
    }
}