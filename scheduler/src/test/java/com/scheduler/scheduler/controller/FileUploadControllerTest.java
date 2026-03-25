package com.scheduler.scheduler.controller;

import com.scheduler.scheduler.model.FileMetadata;
import com.scheduler.scheduler.repository.FileMetadataRepository;
import com.scheduler.scheduler.service.CreateMetadataService;
import com.scheduler.scheduler.util.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for FileUploadController.
 * Tests REST endpoints for file upload and retrieval operations.
 * Uses @WebMvcTest to test only the web layer with mocked dependencies.
 *
 * Note: File download tests verify error handling since no gRPC workers are
 * available in unit test context. Full download flow is covered by integration tests.
 */
@WebMvcTest(controllers = FileUploadController.class)
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileMetadataRepository metadataRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private CreateMetadataService createMetadataService;

    // Mock configuration beans to prevent them from being loaded
    @MockBean(name = "grpcServerConfig")
    private com.scheduler.scheduler.config.GrpcServerConfig grpcServerConfig;

    @MockBean
    private com.scheduler.scheduler.service.SchedulerServiceImpl schedulerServiceImpl;

    /**
     * Cleanup method to reset mocks after each test.
     * Ensures no test data leakage between test methods.
     */
    @AfterEach
    void cleanup() {
        // Reset all mocks to ensure clean state for next test
        reset(metadataRepository, rabbitTemplate, createMetadataService);
    }

    /**
     * Tests successful file upload with MockMultipartFile.
     * Verifies that the controller processes the file and returns success message.
     */
    @Test
    void testUploadFile_Success() throws Exception {
        // Arrange
        byte[] content = "Test file content".getBytes();
        MockMultipartFile file = TestDataBuilder.createMockMultipartFile("test.txt", content);

        FileMetadata mockMetadata = TestDataBuilder.createFileMetadata("file-123", "test.txt", 0);
        when(createMetadataService.createMetadata(anyString(), eq("test.txt"), eq((long) content.length), any(LocalDateTime.class), anyInt()))
                .thenReturn(mockMetadata);

        // Act & Assert
        mockMvc.perform(multipart("/files/uploadFile")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload successful")));

        // Verify interactions
        verify(createMetadataService, times(1)).createMetadata(anyString(), eq("test.txt"), eq((long) content.length), any(LocalDateTime.class), anyInt());
        verify(rabbitTemplate, atLeastOnce()).convertAndSend(anyString(), any(Object.class));
    }

    /**
     * Tests handling of empty file upload.
     * Verifies that the controller processes empty files without errors.
     */
    @Test
    void testUploadFile_EmptyFile() throws Exception {
        // Arrange
        MockMultipartFile emptyFile = TestDataBuilder.createEmptyMockMultipartFile("empty.txt");

        FileMetadata mockMetadata = TestDataBuilder.createFileMetadata("file-456", "empty.txt", 0);
        when(createMetadataService.createMetadata(anyString(), eq("empty.txt"), eq(0L), any(LocalDateTime.class), anyInt()))
                .thenReturn(mockMetadata);

        // Act & Assert
        mockMvc.perform(multipart("/files/uploadFile")
                        .file(emptyFile))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload successful. Total chunks sent: 0")));

        // Verify metadata creation was called but no chunks were sent
        verify(createMetadataService, times(1)).createMetadata(anyString(), eq("empty.txt"), eq(0L), any(LocalDateTime.class), anyInt());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    /**
     * Tests chunking logic for large files.
     * Verifies that files larger than chunk size are split into multiple chunks.
     */
    @Test
    void testUploadFile_LargeFile_ChunksCorrectly() throws Exception {
        // Arrange - Create a file larger than chunk size (128KB)
        int chunkSize = 128 * 1024;
        byte[] largeContent = new byte[chunkSize * 2 + 1000]; // 2.5 chunks
        Arrays.fill(largeContent, (byte) 'A');
        MockMultipartFile largeFile = TestDataBuilder.createMockMultipartFile("large.txt", largeContent);

        FileMetadata mockMetadata = TestDataBuilder.createFileMetadata("file-789", "large.txt", 0);
        when(createMetadataService.createMetadata(anyString(), eq("large.txt"), eq((long) largeContent.length), any(LocalDateTime.class), anyInt()))
                .thenReturn(mockMetadata);

        // Act & Assert
        mockMvc.perform(multipart("/files/uploadFile")
                        .file(largeFile))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload successful. Total chunks sent: 3")));

        // Verify 3 chunks were sent to RabbitMQ
        verify(rabbitTemplate, times(3)).convertAndSend(anyString(), any(Object.class));
    }

    /**
     * Tests 404 response when file is not found.
     * Verifies that the controller returns NOT_FOUND status for non-existent files.
     */
    @Test
    void testGetFile_NotFound() throws Exception {
        // Arrange
        String filename = "nonexistent.txt";
        when(metadataRepository.findFirstByFilename(filename)).thenReturn(null);

        // Act & Assert
        mockMvc.perform(get("/files/getFile")
                        .param("name", filename))
                .andExpect(status().isNotFound());

        // Verify repository was queried
        verify(metadataRepository, times(1)).findFirstByFilename(filename);
        verify(metadataRepository, never()).findAllByFileId(anyString());
    }

    /**
     * Tests that getFile returns 500 when no chunk assignments exist.
     * This happens when metadata exists but no chunks have been assigned to workers yet.
     */
    @Test
    void testGetFile_NoChunkAssignments_Returns500() throws Exception {
        // Arrange
        String filename = "test.txt";
        String fileId = "file-123";
        // Create metadata with no worker assignment (workerId is null)
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(fileId);
        metadata.setFilename(filename);
        metadata.setChunkId(0);
        metadata.setSize(1024L);

        when(metadataRepository.findFirstByFilename(filename)).thenReturn(metadata);
        when(metadataRepository.findAllByFileId(fileId)).thenReturn(List.of(metadata));

        // Act & Assert - should return 500 since no chunks have worker assignments
        mockMvc.perform(get("/files/getFile")
                        .param("name", filename))
                .andExpect(status().isInternalServerError());

        // Verify repository interactions
        verify(metadataRepository, times(1)).findFirstByFilename(filename);
        verify(metadataRepository, times(1)).findAllByFileId(fileId);
    }

    /**
     * Tests that getFile returns 500 when no metadata records exist for the fileId.
     * This covers the edge case where findFirstByFilename returns a result but findAllByFileId is empty.
     */
    @Test
    void testGetFile_EmptyMetadataList_Returns500() throws Exception {
        // Arrange
        String filename = "test.txt";
        String fileId = "file-123";
        FileMetadata metadata = TestDataBuilder.createFileMetadata(fileId, filename, 0);

        when(metadataRepository.findFirstByFilename(filename)).thenReturn(metadata);
        when(metadataRepository.findAllByFileId(fileId)).thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/files/getFile")
                        .param("name", filename))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Tests that getFile returns 500 when gRPC worker is unreachable.
     * In unit tests, no gRPC workers are running, so retrieval will fail.
     * Full download flow is verified in integration tests.
     */
    @Test
    void testGetFile_WorkerUnreachable_Returns500() throws Exception {
        // Arrange
        String filename = "multipart.txt";
        String fileId = "file-999";

        // Create metadata with worker assignments pointing to unreachable addresses
        FileMetadata chunk0 = TestDataBuilder.createFileMetadata(fileId, filename, 0, 1024L, "worker-1", "localhost:19090");
        FileMetadata chunk1 = TestDataBuilder.createFileMetadata(fileId, filename, 1, 1024L, "worker-1", "localhost:19090");

        List<FileMetadata> chunks = Arrays.asList(chunk0, chunk1);

        when(metadataRepository.findFirstByFilename(filename)).thenReturn(chunk0);
        when(metadataRepository.findAllByFileId(fileId)).thenReturn(chunks);

        // Act & Assert - gRPC connection will fail, resulting in 500
        mockMvc.perform(get("/files/getFile")
                        .param("name", filename))
                .andExpect(status().isInternalServerError());

        // Verify repository was queried
        verify(metadataRepository, times(1)).findFirstByFilename(filename);
        verify(metadataRepository, times(1)).findAllByFileId(fileId);
    }
}
