package dev.mathalama.kycservice.infrastructure.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class S3Config {

    @Value("${s3.endpoint}")
    private String endpoint;

    @Value("${s3.access-key}")
    private String accessKey;

    @Value("${s3.secret-key}")
    private String secretKey;

    @Value("${s3.bucket.kyc}")
    private String kycBucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(kycBucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(kycBucket).build());
                log.info("KYC S3 bucket '{}' created successfully", kycBucket);
            }
        } catch (Exception e) {
            log.warn("Could not auto-create S3 bucket '{}': {}", kycBucket, e.getMessage());
        }

        return client;
    }
}
