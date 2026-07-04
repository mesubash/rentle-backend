package com.rentle.shared.storage;

import com.rentle.shared.exception.RentleException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

public final class ImageValidator {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private ImageValidator() {}

    public static void validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new RentleException("File is required");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new RentleException("Only JPEG, PNG and WebP images are allowed");
        }
        if (file.getSize() > maxBytes) {
            throw new RentleException("File exceeds maximum size of " + (maxBytes / (1024 * 1024)) + "MB");
        }
    }
}
