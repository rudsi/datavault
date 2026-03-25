package com.scheduler.scheduler.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.scheduler.scheduler.model.FileMetadata;
import com.scheduler.scheduler.model.FileMetadataId;

@Repository
public interface FileMetadataRepository extends CrudRepository<FileMetadata, FileMetadataId> {

    FileMetadata findFirstByFilename(String fileName);

    List<FileMetadata> findAllByFileId(String fileId);

    Optional<FileMetadata> findByFileIdAndChunkId(String fileId, int chunkId);
}
