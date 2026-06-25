package com.talentpredict.shared.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * Stores uploaded files (photos, CVs) to the local file system.
 * Files are accessible as static resources via /uploads/{subDir}/{filename}.
 */
@Service
@Slf4j
public class FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * Stores a multipart file under uploads/{subDir}/ and returns the public URL path.
     * Example: /uploads/photos/uuid.jpg
     */
    public String store(MultipartFile file, String subDir) throws IOException {
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.'))
                : "";
        String filename = UUID.randomUUID() + ext;

        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        Path target = dir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        log.info("File stored: {}", target);
        return "/uploads/" + subDir + "/" + filename;
    }
}
