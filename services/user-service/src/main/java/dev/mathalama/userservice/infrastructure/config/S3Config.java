package dev.mathalama.userservice.infrastructure.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
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

    @Value("${s3.bucket.avatars}")
    private String avatarsBucket;

    @Bean
    public MinioClient s3Client() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        initAvatarsBucket(client);
        return client;
    }

    private void initAvatarsBucket(MinioClient client) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(avatarsBucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(avatarsBucket).build());
                log.info("Created S3 bucket: {}", avatarsBucket);

                String policy = """
                        {
                          "Version": "2012-10-17",
                          "Statement": [
                            {
                              "Effect": "Allow",
                              "Principal": "*",
                              "Action": ["s3:GetObject"],
                              "Resource": ["arn:aws:s3:::%s/*"]
                            }
                          ]
                        }
                        """.formatted(avatarsBucket);

                client.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(avatarsBucket)
                                .config(policy)
                                .build());
                log.info("Applied public read policy to S3 bucket: {}", avatarsBucket);
            }
        } catch (Exception e) {
            log.error("Failed to initialize S3 bucket {}: {}", avatarsBucket, e.getMessage(), e);
        }
    }
}
