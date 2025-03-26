package com.worker.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.worker.worker.model.FileEntity;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, String> {

}
