package com.allianzes.picker;

import java.io.Serializable;

/**
 * Media File Info
 */
@SuppressWarnings("WeakerAccess")
public class MediaFileInfo implements Serializable {
    private String fileName,filePath,fileType;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

}
