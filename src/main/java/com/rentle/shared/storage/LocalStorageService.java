package com.rentle.shared.storage;

import com.rentle.config.RentleProperties;
import com.rentle.shared.exception.RentleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Dev storage backend: writes under the configured upload dir, served
 * back at /files/**. Swap to Cloudinary via rentle.storage=cloudinary.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rentle.storage", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final Path baseDir;

    public LocalStorageService(RentleProperties props) {
        this.baseDir = Paths.get(props.localUploadDir()).toAbsolutePath();
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        try {
            String ext = extensionOf(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            Path dir = baseDir.resolve(folder);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename));
            return "/files/" + folder + "/" + filename;
        } catch (Exception e) {
            log.error("Local file store failed", e);
            throw new RentleException("File upload failed");
        }
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }
}
