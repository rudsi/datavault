package com.common.common.grpc;

import com.google.protobuf.ByteString;
import io.datavault.common.grpc.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Protocol Buffer message serialization and deserialization.
 * Tests verify that all protobuf messages can be correctly serialized to bytes
 * and deserialized back to their original form.
 */
class ProtobufSerializationTest {

    @Test
    void testHeartbeatRequest_Serialization() throws Exception {
        // Arrange
        HeartbeatRequest original = HeartbeatRequest.newBuilder()
                .setWorkerId("worker-123")
                .setAddress("localhost:9090")
                .build();

        // Act
        byte[] bytes = original.toByteArray();

        // Assert
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(0);
    }

    @Test
    void testHeartbeatRequest_Deserialization() throws Exception {
        // Arrange
        HeartbeatRequest original = HeartbeatRequest.newBuilder()
                .setWorkerId("worker-456")
                .setAddress("192.168.1.100:9091")
                .build();
        byte[] bytes = original.toByteArray();

        // Act
        HeartbeatRequest deserialized = HeartbeatRequest.parseFrom(bytes);

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getWorkerId()).isEqualTo("worker-456");
        assertThat(deserialized.getAddress()).isEqualTo("192.168.1.100:9091");
    }

    @Test
    void testAssignWorkerRequest_SerializationAndDeserialization() throws Exception {
        // Arrange
        AssignWorkerRequest original = AssignWorkerRequest.newBuilder()
                .setRequesterWorkerId("requester-worker-1")
                .setFileId("file-abc-123")
                .setChunkId(5)
                .build();

        // Act
        byte[] bytes = original.toByteArray();
        AssignWorkerRequest deserialized = AssignWorkerRequest.parseFrom(bytes);

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getRequesterWorkerId()).isEqualTo("requester-worker-1");
        assertThat(deserialized.getFileId()).isEqualTo("file-abc-123");
        assertThat(deserialized.getChunkId()).isEqualTo(5);
    }

    @Test
    void testStoreChunkRequest_WithBinaryData() throws Exception {
        // Arrange
        byte[] chunkData = "This is test chunk data with binary content".getBytes();
        StoreChunkRequest original = StoreChunkRequest.newBuilder()
                .setWorkerId("worker-789")
                .setFileId("file-xyz-456")
                .setChunkId(10)
                .setChunkData(ByteString.copyFrom(chunkData))
                .build();

        // Act
        byte[] bytes = original.toByteArray();
        StoreChunkRequest deserialized = StoreChunkRequest.parseFrom(bytes);

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getWorkerId()).isEqualTo("worker-789");
        assertThat(deserialized.getFileId()).isEqualTo("file-xyz-456");
        assertThat(deserialized.getChunkId()).isEqualTo(10);
        assertThat(deserialized.getChunkData().toByteArray()).isEqualTo(chunkData);
    }

    @Test
    void testRetrieveChunkRequest_RoundTrip() throws Exception {
        // Arrange
        RetrieveChunkRequest original = RetrieveChunkRequest.newBuilder()
                .setWorkerId("worker-retrieve-1")
                .setFileId("file-retrieve-123")
                .setChunkId(7)
                .build();

        // Act
        byte[] bytes = original.toByteArray();
        RetrieveChunkRequest deserialized = RetrieveChunkRequest.parseFrom(bytes);

        // Assert
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getWorkerId()).isEqualTo("worker-retrieve-1");
        assertThat(deserialized.getFileId()).isEqualTo("file-retrieve-123");
        assertThat(deserialized.getChunkId()).isEqualTo(7);
    }

    @Test
    void testAllMessageTypes_SerializationRoundTrip() throws Exception {
        // Test HeartbeatResponse
        HeartbeatResponse heartbeatResponse = HeartbeatResponse.newBuilder()
                .setAcknowledged(true)
                .setMessage("Heartbeat received")
                .build();
        byte[] hbBytes = heartbeatResponse.toByteArray();
        HeartbeatResponse hbDeserialized = HeartbeatResponse.parseFrom(hbBytes);
        assertThat(hbDeserialized.getAcknowledged()).isTrue();
        assertThat(hbDeserialized.getMessage()).isEqualTo("Heartbeat received");

        // Test AssignWorkerResponse
        AssignWorkerResponse assignResponse = AssignWorkerResponse.newBuilder()
                .setAssignedWorkerId("assigned-worker-1")
                .setAssignedWorkerAddress("10.0.0.5:9090")
                .build();
        byte[] awBytes = assignResponse.toByteArray();
        AssignWorkerResponse awDeserialized = AssignWorkerResponse.parseFrom(awBytes);
        assertThat(awDeserialized.getAssignedWorkerId()).isEqualTo("assigned-worker-1");
        assertThat(awDeserialized.getAssignedWorkerAddress()).isEqualTo("10.0.0.5:9090");

        // Test StoreChunkResponse
        StoreChunkResponse storeResponse = StoreChunkResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Chunk stored successfully")
                .build();
        byte[] scBytes = storeResponse.toByteArray();
        StoreChunkResponse scDeserialized = StoreChunkResponse.parseFrom(scBytes);
        assertThat(scDeserialized.getSuccess()).isTrue();
        assertThat(scDeserialized.getMessage()).isEqualTo("Chunk stored successfully");

        // Test RetrieveChunkResponse
        byte[] retrievedData = "Retrieved chunk data".getBytes();
        RetrieveChunkResponse retrieveResponse = RetrieveChunkResponse.newBuilder()
                .setChunkData(ByteString.copyFrom(retrievedData))
                .setFound(true)
                .build();
        byte[] rcBytes = retrieveResponse.toByteArray();
        RetrieveChunkResponse rcDeserialized = RetrieveChunkResponse.parseFrom(rcBytes);
        assertThat(rcDeserialized.getFound()).isTrue();
        assertThat(rcDeserialized.getChunkData().toByteArray()).isEqualTo(retrievedData);
    }
}
