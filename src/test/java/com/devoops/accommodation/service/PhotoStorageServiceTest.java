package com.devoops.accommodation.service;

import com.devoops.accommodation.exception.PhotoStorageException;
import io.minio.*;
import io.minio.errors.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhotoStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private PhotoStorageService photoStorageService;

    private static final String BUCKET_NAME = "test-bucket";

    @BeforeEach
    void setUp() {
        photoStorageService = new PhotoStorageService(minioClient);
        ReflectionTestUtils.setField(photoStorageService, "bucketName", BUCKET_NAME);
    }

    @Nested
    @DisplayName("init")
    class InitTests {

        @Test
        @DisplayName("Creates bucket if it does not exist")
        void init_BucketDoesNotExist_CreatesBucket() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

            photoStorageService.init();

            verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Does not create bucket if it already exists")
        void init_BucketExists_DoesNotCreateBucket() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

            photoStorageService.init();

            verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        }

        @Test
        @DisplayName("Throws PhotoStorageException on MinIO error")
        void init_MinioError_ThrowsPhotoStorageException() throws Exception {
            when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                    .thenThrow(new RuntimeException("Connection failed"));

            assertThatThrownBy(() -> photoStorageService.init())
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("Failed to initialize photo storage");
        }
    }

    @Nested
    @DisplayName("store")
    class StoreTests {

        @Test
        @DisplayName("Stores file and returns object key with extension")
        void store_ValidFile_ReturnsObjectKeyWithExtension() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test-image.jpg", "image/jpeg", "test content".getBytes());

            String objectKey = photoStorageService.store(file);

            assertThat(objectKey).endsWith(".jpg");
            assertThat(objectKey).matches("[a-f0-9\\-]+\\.jpg");

            ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
            verify(minioClient).putObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
            assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Stores file without extension when original filename has no extension")
        void store_FileWithoutExtension_ReturnsObjectKeyWithoutExtension() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "testimage", "image/jpeg", "test content".getBytes());

            String objectKey = photoStorageService.store(file);

            assertThat(objectKey).doesNotContain(".");
            verify(minioClient).putObject(any(PutObjectArgs.class));
        }

        @Test
        @DisplayName("Throws PhotoStorageException on MinIO error")
        void store_MinioError_ThrowsPhotoStorageException() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.jpg", "image/jpeg", "test content".getBytes());

            doThrow(new RuntimeException("Upload failed"))
                    .when(minioClient).putObject(any(PutObjectArgs.class));

            assertThatThrownBy(() -> photoStorageService.store(file))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("Failed to store photo");
        }
    }

    @Nested
    @DisplayName("loadAsStream")
    class LoadAsStreamTests {

        @Test
        @DisplayName("Returns input stream for existing object")
        void loadAsStream_ExistingObject_ReturnsInputStream() throws Exception {
            String objectKey = "test-object.jpg";
            InputStream mockStream = new ByteArrayInputStream("test content".getBytes());
            GetObjectResponse mockResponse = mock(GetObjectResponse.class);

            when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

            InputStream result = photoStorageService.loadAsStream(objectKey);

            assertThat(result).isNotNull();
            verify(minioClient).getObject(any(GetObjectArgs.class));
        }

        @Test
        @DisplayName("Throws PhotoStorageException on MinIO error")
        void loadAsStream_MinioError_ThrowsPhotoStorageException() throws Exception {
            String objectKey = "nonexistent.jpg";

            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenThrow(new RuntimeException("Object not found"));

            assertThatThrownBy(() -> photoStorageService.loadAsStream(objectKey))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("Failed to load photo");
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("Deletes object from bucket")
        void delete_ExistingObject_DeletesFromBucket() throws Exception {
            String objectKey = "test-object.jpg";

            photoStorageService.delete(objectKey);

            ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
            verify(minioClient).removeObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET_NAME);
            assertThat(captor.getValue().object()).isEqualTo(objectKey);
        }

        @Test
        @DisplayName("Throws PhotoStorageException on MinIO error")
        void delete_MinioError_ThrowsPhotoStorageException() throws Exception {
            String objectKey = "test-object.jpg";

            doThrow(new RuntimeException("Delete failed"))
                    .when(minioClient).removeObject(any(RemoveObjectArgs.class));

            assertThatThrownBy(() -> photoStorageService.delete(objectKey))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("Failed to delete photo");
        }
    }
}
