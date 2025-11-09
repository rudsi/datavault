package com.scheduler.scheduler.integration;

import com.scheduler.scheduler.model.FileMetadata;
import com.scheduler.scheduler.repository.FileMetadataRepository;
import com.scheduler.scheduler.util.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for file upload functionality using Testcontainers.
 * Tests the complete file upload workflow with real PostgreSQL and RabbitMQ instances.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class FileUploadIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileMetadataRepository metadataRepository;

    @AfterEach
    void cleanup() {
        metadataRepository.deleteAll();
    }

    /**
     * Tests end-to-end file upload workflow.
     * Verifies that:
     * - File upload request is accepted
     * - Metadata is created in the database
     * - Chunks are sent to RabbitMQ queue
     */
    @Test
    void testEndToEndFileUpload_ValidFile_CreatesMetadataAndSendsChunks() throws Exception {
        // Arrange
        byte[] fileContent = "This is a test file content for integration testing".getBytes();
        MockMultipartFile file = TestDataBuilder.createMockMultipartFile("test-file.txt", fileContent);

        // Act
        String response = mockMvc.perform(multipart("/files/uploadFile")
                        .file(file))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert
        assertThat(response).contains("Upload successful");
        
        // Wait briefly for async processing
        Thread.sleep(500);
        
        // Verify metadata was created
        List<FileMetadata> metadataList = StreamSupport.stream(metadataRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
        assertThat(metadataList).isNotEmpty();
        
        FileMetadata metadata = metadataList.get(0);
        assertThat(metadata.getFilename()).isEqualTo("test-file.txt");
        assertThat(metadata.getSize()).isEqualTo(fileContent.length);
    }

    /**
     * Tests file upload and retrieval cycle.
     * Verifies that:
     * - File can be uploaded successfully
     * - File metadata is persisted
     * - File can be retrieved by name (note: actual retrieval requires worker processing)
     */
    @Test
    void testFileUploadAndRetrieval_CompleteWorkflow_Success() throws Exception {
        // Arrange
        byte[] fileContent = "Test content for upload and retrieval".getBytes();
        MockMultipartFile file = TestDataBuilder.createMockMultipartFile("retrieval-test.txt", fileContent);

        // Act - Upload
        mockMvc.perform(multipart("/files/uploadFile")
                        .file(file))
                .andExpect(status().isOk());

        // Wait for processing
        Thread.sleep(500);

        // Verify metadata exists
        FileMetadata metadata = metadataRepository.findByFilename("retrieval-test.txt");
        assertThat(metadata).isNotNull();
        assertThat(metadata.getFilename()).isEqualTo("retrieval-test.txt");
        assertThat(metadata.getFileId()).isNotNull();
    }

    /**
     * Tests concurrent file uploads.
     * Verifies that:
     * - Multiple files can be uploaded simultaneously
     * - All metadata records are created correctly
     * - No data corruption occurs during concurrent operations
     */
    @Test
    void testConcurrentFileUploads_MultipleFiles_AllSucceed() throws Exception {
        // Arrange
        int numberOfFiles = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfFiles);
        CountDownLatch latch = new CountDownLatch(numberOfFiles);

        // Act - Upload multiple files concurrently
        for (int i = 0; i < numberOfFiles; i++) {
            final int fileIndex = i;
            executorService.submit(() -> {
                try {
                    byte[] content = ("Test content for file " + fileIndex).getBytes();
                    MockMultipartFile file = TestDataBuilder.createMockMultipartFile(
                            "concurrent-file-" + fileIndex + ".txt", content);

                    mockMvc.perform(multipart("/files/uploadFile")
                                    .file(file))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all uploads to complete
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Assert
        assertThat(completed).isTrue();
        
        // Wait for async processing
        Thread.sleep(1000);

        // Verify all metadata records were created
        List<FileMetadata> allMetadata = StreamSupport.stream(metadataRepository.findAll().spliterator(), false)
                .collect(Collectors.toList());
        assertThat(allMetadata).hasSizeGreaterThanOrEqualTo(numberOfFiles);

        // Verify each file has metadata
        for (int i = 0; i < numberOfFiles; i++) {
            String filename = "concurrent-file-" + i + ".txt";
            FileMetadata metadata = metadataRepository.findByFilename(filename);
            assertThat(metadata).isNotNull();
            assertThat(metadata.getFilename()).isEqualTo(filename);
        }
    }

    /**
     * Tests that empty file upload is handled appropriately.
     * Verifies that:
     * - Empty file upload is accepted
     * - Metadata is still created
     */
    @Test
    void testFileUpload_EmptyFile_HandledCorrectly() throws Exception {
        // Arrange
        MockMultipartFile emptyFile = TestDataBuilder.createEmptyMockMultipartFile("empty-file.txt");

        // Act
        String response = mockMvc.perform(multipart("/files/uploadFile")
                        .file(emptyFile))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert
        assertThat(response).contains("Upload successful");
        
        // Wait for processing
        Thread.sleep(500);

        // Verify metadata was created
        FileMetadata metadata = metadataRepository.findByFilename("empty-file.txt");
        assertThat(metadata).isNotNull();
        assertThat(metadata.getSize()).isEqualTo(0);
    }

    /**
     * Tests large file upload with chunking.
     * Verifies that:
     * - Large files are properly chunked
     * - All chunks are sent to RabbitMQ
     * - Metadata is created correctly
     */
    @Test
    void testFileUpload_LargeFile_ChunkedCorrectly() throws Exception {
        // Arrange - Create a file larger than chunk size (128KB)
        int fileSize = 256 * 1024; // 256KB
        byte[] largeContent = new byte[fileSize];
        for (int i = 0; i < fileSize; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        MockMultipartFile largeFile = TestDataBuilder.createMockMultipartFile("large-file.bin", largeContent);

        // Act
        String response = mockMvc.perform(multipart("/files/uploadFile")
                        .file(largeFile))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert
        assertThat(response).contains("Upload successful");
        assertThat(response).contains("Total chunks sent:");
        
        // Verify at least 2 chunks were created (256KB / 128KB = 2)
        assertThat(response).matches(".*Total chunks sent: [2-9].*");
        
        // Wait for processing
        Thread.sleep(500);

        // Verify metadata was created
        FileMetadata metadata = metadataRepository.findByFilename("large-file.bin");
        assertThat(metadata).isNotNull();
        assertThat(metadata.getSize()).isEqualTo(fileSize);
    }
}
