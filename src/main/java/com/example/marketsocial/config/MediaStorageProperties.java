package com.example.marketsocial.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class MediaStorageProperties {

    private final Path uploadDir;

    public MediaStorageProperties(@Value("${app.media.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path uploadDir() {
        return uploadDir;
    }
}
