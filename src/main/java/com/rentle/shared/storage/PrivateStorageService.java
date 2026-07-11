package com.rentle.shared.storage;

import com.rentle.config.RentleProperties;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Sensitive files (identity documents) stored outside the publicly-served
 * upload root. Never exposed via {@code /files/**}; only streamed back through
 * authenticated, ownership-checked endpoints.
 */
@Slf4j
@Service
public class PrivateStorageService {

    private final Path baseDir;

    public PrivateStorageService(RentleProperties props) {
        this.baseDir = Paths.get(props.privateUploadDir()).toAbsolutePath();
    }

    /**
     * Store a file under {@code folder} keyed by a stable name (e.g. the user id),
     * replacing any previous file with the same key. Returns the storage reference.
     */
    public String store(MultipartFile file, String folder, String key) {
        try {
            String ext = extensionOf(file.getOriginalFilename());
            Path dir = baseDir.resolve(folder);
            Files.createDirectories(dir);
            // Drop any prior file for this key (extension may differ)
            deleteExisting(dir, key);
            String filename = key + ext;
            file.transferTo(dir.resolve(filename));
            return folder + "/" + filename;
        } catch (IOException e) {
            log.error("Private file store failed", e);
            throw new RentleException("File upload failed");
        }
    }

    /** Resolve a stored reference to a readable resource, guarding against traversal. */
    public Resource load(String ref) {
        Path path = baseDir.resolve(ref).normalize();
        if (!path.startsWith(baseDir) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("File not found");
        }
        return new FileSystemResource(path);
    }

    public String contentType(String ref) {
        try {
            String type = Files.probeContentType(baseDir.resolve(ref).normalize());
            return type != null ? type : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private void deleteExisting(Path dir, String key) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            entries.filter(p -> {
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                return (dot >= 0 ? name.substring(0, dot) : name).equals(key);
            }).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort; a stale file is overwritten by name on next store
                }
            });
        }
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
