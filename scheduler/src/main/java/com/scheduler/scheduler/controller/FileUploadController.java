package com.scheduler.scheduler.controller;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.scheduler.scheduler.config.RabbitMQConfig;
import com.scheduler.scheduler.model.ChunkTask;
import com.scheduler.scheduler.model.FileMetadata;
import com.scheduler.scheduler.repository.FileMetadataRepository;
import com.scheduler.scheduler.service.CreateMetadataService;

import io.datavault.common.grpc.RetrieveChunkRequest;
import io.datavault.common.grpc.RetrieveChunkResponse;
import io.datavault.common.grpc.WorkerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

@RestController
@RequestMapping("/files")
public class FileUploadController {

    @Autowired
    private FileMetadataRepository metadataRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CreateMetadataService createMetadataService;

    @PostMapping("/uploadFile")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        int chunkSize = 128 * 1024;
        byte[] buffer = new byte[chunkSize];
        int chunkId = 0;

        // Compute total number of chunks upfront
        long fileSize = file.getSize();
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);
        if (totalChunks == 0) {
            totalChunks = 1; // At least one chunk for empty files
        }

        createMetadataService.createMetadata(fileId,
                file.getOriginalFilename(),
                file.getSize(),
                LocalDateTime.now(),
                totalChunks);

        try (InputStream inputStream = file.getInputStream()) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byte[] actualChunk = Arrays.copyOf(buffer, bytesRead);
                String encodedData = java.util.Base64.getEncoder().encodeToString(actualChunk);
                ChunkTask chunkTask = new ChunkTask(fileId, chunkId, encodedData);
                rabbitTemplate.convertAndSend(RabbitMQConfig.CHUNK_QUEUE, chunkTask);
                chunkId++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error occurred while uploading the file: " + e.getMessage();
        }
        return "Upload successful. Total chunks sent: " + chunkId;
    }

    @GetMapping("/getFile")
    public ResponseEntity<byte[]> getFile(@RequestParam String name) {
        FileMetadata metadata = metadataRepository.findFirstByFilename(name);
        if (metadata == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        String fileId = metadata.getFileId();

        try {
            byte[] fileContent = chunkAssembler(fileId);
            if (fileContent == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", name);

            return ResponseEntity.ok().headers(headers).body(fileContent);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    private byte[] chunkAssembler(String fileId) throws IOException {
        List<FileMetadata> metadataList = metadataRepository.findAllByFileId(fileId);

        if (metadataList.isEmpty()) {
            throw new FileNotFoundException("No metadata found for file: " + fileId);
        }

        // Filter out the initial metadata record (chunkId=0 with no worker assignment)
        // and sort remaining by chunkId
        List<FileMetadata> chunkRecords = metadataList.stream()
                .filter(m -> m.getWorkerId() != null && !m.getWorkerId().isEmpty()
                        && m.getWorkerAddress() != null && !m.getWorkerAddress().isEmpty())
                .sorted(Comparator.comparingInt(FileMetadata::getChunkId))
                .toList();

        if (chunkRecords.isEmpty()) {
            throw new FileNotFoundException("No chunk assignments found for file: " + fileId);
        }

        // Cache gRPC channels per worker address to avoid creating duplicate connections
        Map<String, ManagedChannel> channelCache = new HashMap<>();
        Map<String, WorkerServiceGrpc.WorkerServiceBlockingStub> stubCache = new HashMap<>();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            for (FileMetadata chunkMeta : chunkRecords) {
                int chunkId = chunkMeta.getChunkId();
                String workerId = chunkMeta.getWorkerId();
                String workerAddress = chunkMeta.getWorkerAddress();

                // Get or create a gRPC stub for this worker
                WorkerServiceGrpc.WorkerServiceBlockingStub stub = stubCache.get(workerAddress);
                if (stub == null) {
                    ManagedChannel channel = ManagedChannelBuilder.forTarget(workerAddress)
                            .usePlaintext()
                            .build();
                    channelCache.put(workerAddress, channel);
                    stub = WorkerServiceGrpc.newBlockingStub(channel);
                    stubCache.put(workerAddress, stub);
                }

                RetrieveChunkRequest request = RetrieveChunkRequest.newBuilder()
                        .setWorkerId(workerId)
                        .setFileId(fileId)
                        .setChunkId(chunkId)
                        .build();

                RetrieveChunkResponse response = stub.retrieveChunk(request);

                if (!response.getFound()) {
                    throw new FileNotFoundException(
                            "Chunk " + chunkId + " not found on worker " + workerId + " at " + workerAddress);
                }

                outputStream.write(response.getChunkData().toByteArray());
            }
        } finally {
            // Clean up all gRPC channels
            for (ManagedChannel channel : channelCache.values()) {
                channel.shutdownNow();
            }
        }
        return outputStream.toByteArray();
    }
}
