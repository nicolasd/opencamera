package com.allianzes.picker.utils;

import java.io.Serializable;

/**
 * Media File Info
 */
@SuppressWarnings("WeakerAccess")
public class MediaFileInfo implements Serializable {
    private String fileName,filePath;

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

}
