package com.scheduler.scheduler.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.grpc.Status;

import com.scheduler.scheduler.model.FileMetadata;
import com.scheduler.scheduler.model.WorkerInfo;
import com.scheduler.scheduler.repository.FileMetadataRepository;

import io.datavault.common.grpc.AssignWorkerRequest;
import io.datavault.common.grpc.AssignWorkerResponse;
import io.datavault.common.grpc.HeartbeatRequest;
import io.datavault.common.grpc.HeartbeatResponse;
import io.datavault.common.grpc.SchedulerServiceGrpc.SchedulerServiceImplBase;

import io.grpc.stub.StreamObserver;

@Service
public class SchedulerServiceImpl extends SchedulerServiceImplBase {

    private final Map<String, WorkerInfo> workerPool = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final long timeout = 5 * 1000;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    @Autowired
    FileMetadataRepository fileMetadataRepository;

    public void updateWorker(String workerId, String workerAddress) {
        long currentTime = Instant.now().toEpochMilli();
        lock.lock();
        try {
            WorkerInfo worker = workerPool.get(workerId);
            if (worker == null) {
                worker = new WorkerInfo(workerId, workerAddress);
                workerPool.put(workerId, worker);
                System.out.printf("New worker %s added with address %s at time: %d%n", workerId, workerAddress,
                        currentTime);
            } else {
                worker.updateHeartbeat();
                System.out.printf("Heartbeat updated for worker %s at time: %d%n", workerId, currentTime);
            }
        } finally {
            lock.unlock();
        }
    }

    public void cleanInactiveWorkers() {
        long now = Instant.now().toEpochMilli();
        lock.lock();
        try {
            workerPool.entrySet().removeIf(entry -> (now - entry.getValue().getLastHeartbeat()) > timeout);
        } finally {
            lock.unlock();
        }
    }

    public Map<String, String> getActiveWorkers() {
        Map<String, String> activeWorkers = new HashMap<>();
        long now = Instant.now().toEpochMilli();
        lock.lock();
        try {
            workerPool.forEach((workerId, workerInfo) -> {
                if ((now - workerInfo.getLastHeartbeat()) <= timeout) {
                    activeWorkers.put(workerId, workerInfo.getAddress());
                }
            });
        } finally {
            lock.unlock();
        }
        return activeWorkers;
    }

    @Override
    public void sendHeartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> response) {
        String workerId = request.getWorkerId();
        String address = request.getAddress();
        updateWorker(workerId, address);
        HeartbeatResponse heartbeatResponse = HeartbeatResponse.newBuilder().setAcknowledged(true)
                .setMessage("Heartbeat received for worker: " + workerId).build();
        response.onNext(heartbeatResponse);
        response.onCompleted();
    }

    @Override
    public void assignWorkerForChunk(AssignWorkerRequest request,
            StreamObserver<AssignWorkerResponse> responseObserver) {

        Map<String, String> activeWorkers = getActiveWorkers();

        if (activeWorkers.isEmpty()) {
            responseObserver.onError(
                    Status.UNAVAILABLE.withDescription("No active workers").asRuntimeException());
            return;
        }

        List<String> workerIdList = new ArrayList<>(activeWorkers.keySet());
        int index = roundRobinCounter.getAndUpdate(i -> (i + 1) % workerIdList.size());
        String selectedWorkerId = workerIdList.get(index);
        String address = activeWorkers.get(selectedWorkerId);

        String fileId = request.getFileId();
        int chunkId = request.getChunkId();

        Optional<FileMetadata> existingMetadata = fileMetadataRepository.findByFileIdAndChunkId(fileId, chunkId);

        if (existingMetadata.isPresent()) {
            // Update existing record (e.g., initial metadata created during upload for chunk 0)
            FileMetadata existing = existingMetadata.get();
            existing.setWorkerId(selectedWorkerId);
            existing.setWorkerAddress(address);
            fileMetadataRepository.save(existing);

            AssignWorkerResponse existingResponse = AssignWorkerResponse.newBuilder()
                    .setAssignedWorkerId(selectedWorkerId)
                    .setAssignedWorkerAddress(address)
                    .build();
            responseObserver.onNext(existingResponse);
            responseObserver.onCompleted();

            System.out.printf("Updated existing metadata: assigned worker %s for file %s chunk %d%n",
                    selectedWorkerId, fileId, chunkId);
            return;
        }

        // Persist the chunk-to-worker assignment (only fileId, chunkId, worker info)
        // Filename and size are only stored on the initial record (chunk 0) to avoid
        // NonUniqueResultException on findByFilename queries
        FileMetadata chunkMetadata = new FileMetadata();
        chunkMetadata.setFileId(fileId);
        chunkMetadata.setChunkId(chunkId);
        chunkMetadata.setWorkerId(selectedWorkerId);
        chunkMetadata.setWorkerAddress(address);
        fileMetadataRepository.save(chunkMetadata);

        AssignWorkerResponse response = AssignWorkerResponse.newBuilder()
                .setAssignedWorkerId(selectedWorkerId)
                .setAssignedWorkerAddress(address)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        System.out.printf("Assigned worker %s for file %s chunk %d%n",
                selectedWorkerId, request.getFileId(), request.getChunkId());
    }

}
