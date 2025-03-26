package com.worker.worker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FileEntity {

    @Id
    private String fileId;
    private byte[] fileContent;

    public FileEntity(String fileId, byte[] fileContent) {
        this.fileId = fileId;
        this.fileContent = fileContent;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public byte[] getFileContent() {
        return fileContent;
    }

    public void setFileContent(byte[] fileContent) {
        this.fileContent = fileContent;
    }

}
