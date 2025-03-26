package com.worker.worker.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.protobuf.ByteString;
import com.worker.worker.model.FileEntity;
import com.worker.worker.repository.FileRepository;

import io.grpc.stub.StreamObserver;
import io.grpc.worker.service.RetrieveFileRequest;
import io.grpc.worker.service.RetrieveFileResponse;
import io.grpc.worker.service.StoreFileRequest;
import io.grpc.worker.service.StoreFileResponse;
import io.grpc.worker.service.WorkerServiceGrpc.WorkerServiceImplBase;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class WorkerServiceImpl extends WorkerServiceImplBase {

    @Autowired
    private FileRepository fileRepository;

    @Override
    public void storeFile(StoreFileRequest request, StreamObserver<StoreFileResponse> responseObserver) {
        String fileId = request.getFileId();
        byte[] fileContent = request.getFileContent().toByteArray();

        FileEntity fileEntity = new FileEntity(fileId, fileContent);

        try {
            fileRepository.save(fileEntity);
            StoreFileResponse response = StoreFileResponse.newBuilder().setSuccess(true)
                    .setMessage("File stored successfully").build();
            responseObserver.onNext(response);
        } catch (Exception e) {
            responseObserver.onError(e);
        } finally {
            responseObserver.onCompleted();
        }

    }

    @Override
    public void retrieveFile(RetrieveFileRequest request, StreamObserver<RetrieveFileResponse> responseObserver) {
        String fileId = request.getFileId();

        try {
            Optional<FileEntity> fileEntityOpt = fileRepository.findById(fileId);
            if (fileEntityOpt.isPresent()) {
                FileEntity fileEntity = fileEntityOpt.get();
                RetrieveFileResponse response = RetrieveFileResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("File retrieved successfully.")
                        .setFileContent(ByteString.copyFrom(fileEntity.getFileContent()))
                        .build();
                responseObserver.onNext(response);
            } else {
                RetrieveFileResponse response = RetrieveFileResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("File not found.")
                        .build();
                responseObserver.onNext(response);
            }
        } catch (Exception e) {
            responseObserver.onError(e);
        } finally {
            responseObserver.onCompleted();
        }
    }
}
