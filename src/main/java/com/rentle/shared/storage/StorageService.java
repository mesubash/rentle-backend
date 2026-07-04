package com.rentle.shared.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** Stores the file and returns its public URL. */
    String upload(MultipartFile file, String folder);
}
