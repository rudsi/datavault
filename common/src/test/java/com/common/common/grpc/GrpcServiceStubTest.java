package com.common.common.grpc;

import io.datavault.common.grpc.SchedulerServiceGrpc;
import io.datavault.common.grpc.WorkerServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for gRPC service stub creation and verification.
 * Tests verify that gRPC stubs can be created correctly and all RPC methods are available.
 */
class GrpcServiceStubTest {

    private Server server;
    private ManagedChannel channel;
    private String serverName;

    @BeforeEach
    void setUp() throws IOException {
        // Generate unique server name for in-process testing
        serverName = InProcessServerBuilder.generateName();
        
        // Create in-process server (no actual service implementation needed for stub testing)
        server = InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .build()
                .start();
        
        // Create in-process channel
        channel = InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build();
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void testSchedulerServiceStub_Creation() {
        // Act
        SchedulerServiceGrpc.SchedulerServiceBlockingStub blockingStub = 
                SchedulerServiceGrpc.newBlockingStub(channel);

        // Assert
        assertThat(blockingStub).isNotNull();
    }

    @Test
    void testWorkerServiceStub_Creation() {
        // Act
        WorkerServiceGrpc.WorkerServiceBlockingStub blockingStub = 
                WorkerServiceGrpc.newBlockingStub(channel);

        // Assert
        assertThat(blockingStub).isNotNull();
    }

    @Test
    void testStubMethods_AllRPCMethodsAvailable() {
        // Act - Create stubs
        SchedulerServiceGrpc.SchedulerServiceBlockingStub schedulerStub = 
                SchedulerServiceGrpc.newBlockingStub(channel);
        WorkerServiceGrpc.WorkerServiceBlockingStub workerStub = 
                WorkerServiceGrpc.newBlockingStub(channel);

        // Assert - Verify SchedulerService methods exist
        assertThat(schedulerStub).isNotNull();
        // The stub has methods: sendHeartbeat and assignWorkerForChunk
        // We verify by checking the stub class has these methods via reflection
        assertThat(schedulerStub.getClass().getMethods())
                .extracting("name")
                .contains("sendHeartbeat", "assignWorkerForChunk");

        // Assert - Verify WorkerService methods exist
        assertThat(workerStub).isNotNull();
        // The stub has methods: storeChunk and retrieveChunk
        assertThat(workerStub.getClass().getMethods())
                .extracting("name")
                .contains("storeChunk", "retrieveChunk");
    }

    @Test
    void testBlockingStub_Creation() {
        // Act - Create blocking stubs for both services
        SchedulerServiceGrpc.SchedulerServiceBlockingStub schedulerBlockingStub = 
                SchedulerServiceGrpc.newBlockingStub(channel);
        WorkerServiceGrpc.WorkerServiceBlockingStub workerBlockingStub = 
                WorkerServiceGrpc.newBlockingStub(channel);

        // Assert
        assertThat(schedulerBlockingStub).isNotNull();
        assertThat(workerBlockingStub).isNotNull();
        
        // Verify they are blocking stubs
        assertThat(schedulerBlockingStub.getClass().getSimpleName())
                .contains("BlockingStub");
        assertThat(workerBlockingStub.getClass().getSimpleName())
                .contains("BlockingStub");
    }

    @Test
    void testAsyncStub_Creation() {
        // Act - Create async stubs for both services
        SchedulerServiceGrpc.SchedulerServiceStub schedulerAsyncStub = 
                SchedulerServiceGrpc.newStub(channel);
        WorkerServiceGrpc.WorkerServiceStub workerAsyncStub = 
                WorkerServiceGrpc.newStub(channel);

        // Assert
        assertThat(schedulerAsyncStub).isNotNull();
        assertThat(workerAsyncStub).isNotNull();
        
        // Verify they are async stubs (not blocking)
        assertThat(schedulerAsyncStub.getClass().getSimpleName())
                .doesNotContain("Blocking");
        assertThat(workerAsyncStub.getClass().getSimpleName())
                .doesNotContain("Blocking");
    }
}
