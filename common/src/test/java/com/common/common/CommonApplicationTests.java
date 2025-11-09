package com.common.common;

import io.datavault.common.grpc.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Common module Spring Boot application.
 * Tests verify that the application context loads successfully and
 * that protobuf classes are generated correctly.
 */
@SpringBootTest
class CommonApplicationTests {

	@Test
	void testCommonModuleApplicationContext_Loads() {
		// This test verifies that the Spring Boot application context
		// loads successfully without any configuration errors
		// If context loading fails, this test will fail
	}

	@Test
	void testProtobufClasses_GeneratedCorrectly() {
		// Verify HeartbeatRequest is generated and can be instantiated
		HeartbeatRequest heartbeatRequest = HeartbeatRequest.newBuilder()
				.setWorkerId("test-worker")
				.setAddress("localhost:9090")
				.build();
		assertThat(heartbeatRequest).isNotNull();
		assertThat(heartbeatRequest.getWorkerId()).isEqualTo("test-worker");

		// Verify HeartbeatResponse is generated
		HeartbeatResponse heartbeatResponse = HeartbeatResponse.newBuilder()
				.setAcknowledged(true)
				.setMessage("Test message")
				.build();
		assertThat(heartbeatResponse).isNotNull();
		assertThat(heartbeatResponse.getAcknowledged()).isTrue();

		// Verify AssignWorkerRequest is generated
		AssignWorkerRequest assignWorkerRequest = AssignWorkerRequest.newBuilder()
				.setRequesterWorkerId("requester-1")
				.setFileId("file-123")
				.setChunkId(1)
				.build();
		assertThat(assignWorkerRequest).isNotNull();
		assertThat(assignWorkerRequest.getFileId()).isEqualTo("file-123");

		// Verify AssignWorkerResponse is generated
		AssignWorkerResponse assignWorkerResponse = AssignWorkerResponse.newBuilder()
				.setAssignedWorkerId("worker-1")
				.setAssignedWorkerAddress("localhost:9091")
				.build();
		assertThat(assignWorkerResponse).isNotNull();
		assertThat(assignWorkerResponse.getAssignedWorkerId()).isEqualTo("worker-1");

		// Verify StoreChunkRequest is generated
		StoreChunkRequest storeChunkRequest = StoreChunkRequest.newBuilder()
				.setWorkerId("worker-2")
				.setFileId("file-456")
				.setChunkId(2)
				.build();
		assertThat(storeChunkRequest).isNotNull();
		assertThat(storeChunkRequest.getChunkId()).isEqualTo(2);

		// Verify StoreChunkResponse is generated
		StoreChunkResponse storeChunkResponse = StoreChunkResponse.newBuilder()
				.setSuccess(true)
				.setMessage("Success")
				.build();
		assertThat(storeChunkResponse).isNotNull();
		assertThat(storeChunkResponse.getSuccess()).isTrue();

		// Verify RetrieveChunkRequest is generated
		RetrieveChunkRequest retrieveChunkRequest = RetrieveChunkRequest.newBuilder()
				.setWorkerId("worker-3")
				.setFileId("file-789")
				.setChunkId(3)
				.build();
		assertThat(retrieveChunkRequest).isNotNull();
		assertThat(retrieveChunkRequest.getChunkId()).isEqualTo(3);

		// Verify RetrieveChunkResponse is generated
		RetrieveChunkResponse retrieveChunkResponse = RetrieveChunkResponse.newBuilder()
				.setFound(true)
				.build();
		assertThat(retrieveChunkResponse).isNotNull();
		assertThat(retrieveChunkResponse.getFound()).isTrue();
	}

}
