package com.amqhi.services

import com.amqhi.services.ObjectMetadata
import io.vertx.core.Future
import io.vertx.core.WorkerExecutor
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.ChecksumMode
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload
import software.amazon.awssdk.services.s3.model.CompletedPart
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest
import java.net.URI
import java.nio.file.Path
import java.time.Duration

class StorageService(
    endpoint: String = System.getenv("STORAGE_ENDPOINT"),
    private val region: String = System.getenv("STORAGE_REGION"),
    accessKey: String = System.getenv("STORAGE_ACCESS_KEY"),
    secretKey: String = System.getenv("STORAGE_SECRET_KEY"),
    private val bucketName: String = System.getenv("STORAGE_BUCKET_NAME"),
    private val workerExecutor: WorkerExecutor
) {

    private val client: S3AsyncClient = S3AsyncClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        )
        .httpClientBuilder(AwsCrtAsyncHttpClient.builder())
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(System.getenv("STORAGE_USE_PATH_STYLE")?.toBoolean() ?: false).build()
        )
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            )
        )
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(System.getenv("STORAGE_USE_PATH_STYLE")?.toBoolean() ?: false).build()
        )
        .build()

    fun getDownloadUrl(key: String, contentType: String, duration: Duration): Future<String> {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .responseContentType(contentType)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .getObjectRequest(getObjectRequest)
            .build()

        return workerExecutor.executeBlocking {
            presigner.presignGetObject(presignRequest).url().toString()
        }
    }

    fun getObject(key: String, path: Path): Future<GetObjectResponse> {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()

        return Future.fromCompletionStage(client.getObject(getObjectRequest, path))
    }

    fun putObject(key: String, contentType: String, path: Path): Future<PutObjectResponse> {
        val request = PutObjectRequest.builder()
            .bucket(bucketName)
            .contentType(contentType)
            .key(key)
            .build()

        return Future.fromCompletionStage(client.putObject(request, path))
    }

    fun initiateMultipartUpload(key: String, contentType: String): Future<String> {
        return workerExecutor.executeBlocking {
            val request = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build()
            val response = client.createMultipartUpload(request)
            response.get().uploadId()
        }
    }
    val chunkSize = 5L * 1024L * 1024L
    fun getUploadPartUrl(key: String, uploadId: String, partNumber: Int, duration: Duration, fileSize: Long): Future<PresignedPart> {
        return workerExecutor.executeBlocking {
            val uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build()

            val presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(duration)
                .uploadPartRequest(uploadPartRequest)
                .build()

            val startOffset = (partNumber - 1) * chunkSize
            val size = minOf(chunkSize, fileSize - startOffset)
            val presignedUrl = presigner.presignUploadPart(presignRequest)
            PresignedPart(
                partNumber = partNumber,
                url = presignedUrl.url().toString(),
                startOffset = startOffset,
                size = size
            )
        }
    }

    fun getUploadUrl(
        key: String,
        contentType: String,
        duration: Duration,
        contentLength: Long
    ): Future<String> {
        return workerExecutor.executeBlocking {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build()
            val presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(duration)
                .putObjectRequest(putObjectRequest)
                .build()

            val presignedUrl = presigner.presignPutObject(presignRequest)
            presignedUrl.url().toString()
        }
    }

    fun delete(key: String): Future<DeleteObjectResponse> {
        val request = DeleteObjectRequest.builder().bucket(bucketName).key(key).build()

        return Future.fromCompletionStage(client.deleteObject(request))
    }

    fun getObjectMetadata(key: String): Future<ObjectMetadata> {
        val headRequest = HeadObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .checksumMode(ChecksumMode.ENABLED)
            .build()

        return Future.fromCompletionStage(client.headObject(headRequest)).map { response ->
            ObjectMetadata(
                mimeType = response.contentType(),
                size = response.contentLength(),
                checksum = response.checksumSHA256()
            )
        }
    }

    fun completeUpload(key: String, uploadId: String, parts: List<UploadedPart>): Future<Void> {
            val completedParts = parts.map { part ->
                CompletedPart.builder()
                    .partNumber(part.partNumber)
                    .eTag(part.eTag)
                    .build()
            }

            val completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build()

            val completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(key)
                .uploadId(uploadId)
                .multipartUpload(completedMultipartUpload)
                .build()

            return Future.fromCompletionStage(
                client.completeMultipartUpload(completeRequest)
            ).mapEmpty()
    }
}

data class UploadedPart(
    val partNumber: Int,
    val eTag: String
)

data class PresignedPart(
    val partNumber: Int,
    val url: String,
    val startOffset: Long,
    val size: Long
)

data class ObjectMetadata(
    val mimeType: String?,
    val size: Long?,
    val checksum: String?,
)