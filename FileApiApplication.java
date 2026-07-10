package com.example.files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SpringBootApplication
public class FileApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApiApplication.class, args);
    }

    @Bean
    FileService fileService() {
        return new FileService();
    }
}

@RestController
@RequestMapping("/api/files")
class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final FileService fileService;

    FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required and cannot be empty");
        }

        FileRecord saved = fileService.save(file);
        log.info("UPLOAD_SUCCESS id={} originalName={} size={}", saved.id(), saved.originalFilename(), saved.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id", saved.id(),
            "filename", saved.originalFilename(),
            "contentType", saved.contentType(),
            "size", saved.size()
        ));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable String id) {
        FileReadResult read = fileService.read(id);
        log.info("DOWNLOAD_SUCCESS id={} originalName={}", id, read.record().originalFilename());

        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(read.record().originalFilename())
            .build();

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(read.record().contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(read.record().size()))
            .body(read.resource());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> maxSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("error", "File too large. Max allowed is 10 MB"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("error", ex.getReason() == null ? "Request failed" : ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("UNEXPECTED_ERROR", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", "Internal server error"));
    }
}

class FileService {
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("txt", "pdf", "png", "jpg", "jpeg");

    private final Path storageDir = Paths.get("uploads").toAbsolutePath().normalize();
    private final Map<String, FileRecord> metadata = new HashMap<>();

    FileService() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize upload directory", e);
        }
    }

    FileRecord save(MultipartFile file) {
        String originalName = sanitizeOriginalFilename(file.getOriginalFilename());
        String ext = extensionOf(originalName);

        if (!ALLOWED_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File too large. Max allowed is 10 MB");
        }

        String id = UUID.randomUUID().toString();
        String serverName = id + "." + ext;
        Path target = storageDir.resolve(serverName).normalize();

        if (!target.startsWith(storageDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        FileRecord record = new FileRecord(id, originalName, serverName, contentType, file.getSize());
        metadata.put(id, record);
        return record;
    }

    FileReadResult read(String id) {
        FileRecord record = metadata.get(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }

        Path path = storageDir.resolve(record.serverFilename()).normalize();
        if (!path.startsWith(storageDir) || !Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }

        try {
            Resource resource = new UrlResource(path.toUri());
            return new FileReadResult(record, resource);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    private static String sanitizeOriginalFilename(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filename is required");
        }

        String cleaned = name.replace("\\", "/");
        cleaned = cleaned.substring(cleaned.lastIndexOf('/') + 1);

        if (cleaned.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        return cleaned;
    }

    private static String extensionOf(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }
}

record FileRecord(
    String id,
    String originalFilename,
    String serverFilename,
    String contentType,
    long size
) {}

record FileReadResult(
    FileRecord record,
    Resource resource
) {}
