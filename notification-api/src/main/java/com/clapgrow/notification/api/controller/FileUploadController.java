package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@RestController
@RequestMapping("/admin/api/files")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Upload", description = "File upload endpoints for media files")
public class FileUploadController {
    
    private final FileStorageService fileStorageService;
    
    @PostMapping("/upload/image")
    @Operation(
            summary = "Upload an image file",
            description = "Uploads an image file (JPEG, PNG, max 5MB) and returns a public URL"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        try {
            // Check authentication
            if (session.getAttribute("userId") == null) {
                return ResponseEntity.status(401).body(
                    java.util.Map.of("success", false, "error", "Unauthorized"));
            }
            
            String fileUrl = fileStorageService.storeFile(file, "image");
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "url", fileUrl,
                "filename", file.getOriginalFilename()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid image upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading image", e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                "success", false,
                "error", "Failed to upload file: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/upload/video")
    @Operation(
            summary = "Upload a video file",
            description = "Uploads a video file (MP4, 3GPP, max 50MB) and returns a public URL"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> uploadVideo(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        try {
            // Check authentication
            if (session.getAttribute("userId") == null) {
                return ResponseEntity.status(401).body(
                    java.util.Map.of("success", false, "error", "Unauthorized"));
            }
            
            String fileUrl = fileStorageService.storeFile(file, "video");
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "url", fileUrl,
                "filename", file.getOriginalFilename()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid video upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading video", e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                "success", false,
                "error", "Failed to upload file: " + e.getMessage()
            ));
        }
    }
    
    @PostMapping("/upload/document")
    @Operation(
            summary = "Upload a document file",
            description = "Uploads a document file (PDF, DOCX, XLSX, etc., max 100MB) and returns a public URL"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        try {
            // Check authentication
            if (session.getAttribute("userId") == null) {
                return ResponseEntity.status(401).body(
                    java.util.Map.of("success", false, "error", "Unauthorized"));
            }
            
            String fileUrl = fileStorageService.storeFile(file, "document");
            String originalFilename = file.getOriginalFilename();
            return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "url", fileUrl,
                "filename", originalFilename != null ? originalFilename : "document"
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading document", e);
            return ResponseEntity.status(500).body(java.util.Map.of(
                "success", false,
                "error", "Failed to upload file: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping("/{filename:.+}")
    @Operation(
            summary = "Download a file",
            description = "Downloads a previously uploaded file. NOTE: Files are publicly accessible - no authentication required. " +
                    "This is intentional for media files used in notifications. If you need private file access, " +
                    "consider implementing signed URLs or authentication checks."
    )
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = fileStorageService.loadFile(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                String contentType = determineContentType(filename);
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error downloading file: {}", filename, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    private String determineContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "mp4" -> "video/mp4";
            case "3gpp" -> "video/3gpp";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "txt" -> "text/plain";
            case "ppt", "pptx" -> "application/vnd.ms-powerpoint";
            default -> "application/octet-stream";
        };
    }
}

