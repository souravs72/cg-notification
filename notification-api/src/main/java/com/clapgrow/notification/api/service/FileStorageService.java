package com.clapgrow.notification.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {
    
    private final Path uploadDir;
    private final String baseUrl;
    
    public FileStorageService(
            @Value("${file.upload.dir:uploads}") String uploadDirPath,
            @Value("${file.upload.base-url:http://localhost:8080/files}") String baseUrl) {
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        
        try {
            Files.createDirectories(this.uploadDir);
            log.info("File upload directory initialized: {}", this.uploadDir);
        } catch (IOException e) {
            log.error("Could not create upload directory: {}", this.uploadDir, e);
            throw new RuntimeException("Could not create upload directory", e);
        }
    }
    
    public String storeFile(MultipartFile file, String fileType) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        // Validate file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("File name is empty");
        }
        
        // Validate file size based on type
        long fileSize = file.getSize();
        long maxSize = getMaxFileSize(fileType);
        if (fileSize > maxSize) {
            throw new IllegalArgumentException(
                String.format("File size %d bytes exceeds maximum allowed size of %d bytes for %s", 
                    fileSize, maxSize, fileType));
        }
        
        // Validate file extension
        String extension = getFileExtension(originalFilename);
        if (!isValidExtension(extension, fileType)) {
            throw new IllegalArgumentException(
                String.format("File extension .%s is not allowed for %s", extension, fileType));
        }
        
        // Generate unique filename
        String uniqueFilename = UUID.randomUUID().toString() + "." + extension;
        Path targetLocation = this.uploadDir.resolve(uniqueFilename);
        
        // Copy file to target location
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        // Generate public URL
        String fileUrl = baseUrl + "/" + uniqueFilename;
        
        log.info("File stored successfully: {} -> {}", originalFilename, fileUrl);
        return fileUrl;
    }
    
    private long getMaxFileSize(String fileType) {
        return switch (fileType.toLowerCase()) {
            case "image" -> 5 * 1024 * 1024; // 5MB
            case "video" -> 50 * 1024 * 1024; // 50MB
            case "document" -> 100 * 1024 * 1024; // 100MB
            default -> 100 * 1024 * 1024; // Default 100MB
        };
    }
    
    private boolean isValidExtension(String extension, String fileType) {
        String ext = extension.toLowerCase();
        return switch (fileType.toLowerCase()) {
            case "image" -> ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png");
            case "video" -> ext.equals("mp4") || ext.equals("3gpp");
            case "document" -> ext.equals("pdf") || ext.equals("docx") || ext.equals("xlsx") || 
                              ext.equals("doc") || ext.equals("xls") || ext.equals("txt") ||
                              ext.equals("ppt") || ext.equals("pptx");
            default -> false;
        };
    }
    
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            throw new IllegalArgumentException("File must have a valid extension");
        }
        return filename.substring(lastDot + 1);
    }
    
    public Path loadFile(String filename) {
        return uploadDir.resolve(filename).normalize();
    }
}









