package com.example.marketsocial.controller;

import com.example.marketsocial.config.MediaStorageProperties;
import com.example.marketsocial.model.User;
import com.example.marketsocial.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024;

    private final UserRepository userRepository;
    private final MediaStorageProperties mediaStorageProperties;

    public MediaController(UserRepository userRepository, MediaStorageProperties mediaStorageProperties) {
        this.userRepository = userRepository;
        this.mediaStorageProperties = mediaStorageProperties;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication
    ) {
        requireUser(authentication);

        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one image");
        }
        if (files.size() > 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can upload up to 6 images at a time");
        }

        Path directory = mediaDirectory();
        try {
            Files.createDirectories(directory);
            List<String> urls = files.stream()
                    .map(file -> saveFile(directory, file))
                    .toList();
            return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponse(urls));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded image");
        }
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Path mediaDirectory() {
        LocalDate today = LocalDate.now();
        return mediaStorageProperties.uploadDir()
                .resolve(String.valueOf(today.getYear()))
                .resolve(String.format("%02d", today.getMonthValue()));
    }

    private String saveFile(Path directory, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded image was empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Images must be 8MB or smaller");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads are allowed");
        }

        String extension = extension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + extension;
        Path target = directory.resolve(filename).normalize();

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store uploaded image");
        }

        Path uploadsRoot = mediaStorageProperties.uploadDir();
        Path relativePath = Path.of("media").resolve(uploadsRoot.relativize(target.toAbsolutePath().normalize()));
        return "/" + relativePath.toString().replace('\\', '/');
    }

    private String extension(String originalFilename) {
        if (originalFilename == null) {
            return ".bin";
        }
        int index = originalFilename.lastIndexOf('.');
        if (index < 0) {
            return ".bin";
        }
        return originalFilename.substring(index).toLowerCase();
    }

    public record UploadResponse(List<String> urls) {
    }
}
