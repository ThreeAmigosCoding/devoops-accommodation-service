package com.devoops.accommodation.service;

import com.devoops.accommodation.exception.PhotoStorageException;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("Created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket already exists: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", bucketName, e);
            throw new PhotoStorageException("Failed to initialize photo storage", e);
        }
    }

    public String store(MultipartFile file) {
        String objectKey = generateObjectKey(file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.debug("Stored file {} as {} in bucket {}", file.getOriginalFilename(), objectKey, bucketName);
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to store file: {}", file.getOriginalFilename(), e);
            throw new PhotoStorageException("Failed to store photo", e);
        }
    }

    public InputStream loadAsStream(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to load file: {}", objectKey, e);
            throw new PhotoStorageException("Failed to load photo", e);
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            log.debug("Deleted file {} from bucket {}", objectKey, bucketName);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectKey, e);
            throw new PhotoStorageException("Failed to delete photo", e);
        }
    }

    private String generateObjectKey(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return UUID.randomUUID() + extension;
    }
}
