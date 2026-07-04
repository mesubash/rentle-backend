package com.rentle.shared.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentle.config.CloudinaryProperties;
import com.rentle.shared.exception.RentleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Signed Cloudinary upload via the plain REST API — no SDK dependency.
 * Signature: SHA-1 hex of "folder=...&timestamp=...<api_secret>".
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rentle.storage", havingValue = "cloudinary")
public class CloudinaryStorageService implements StorageService {

    private final CloudinaryProperties props;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CloudinaryStorageService(CloudinaryProperties props) {
        this.props = props;
    }

    @Override
    public String upload(MultipartFile file, String folder) {
        try {
            String fullFolder = "rentle/" + folder;
            long timestamp = System.currentTimeMillis() / 1000;
            String toSign = "folder=" + fullFolder + "&timestamp=" + timestamp + props.apiSecret();
            String signature = sha1Hex(toSign);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", resource);
            form.add("api_key", props.apiKey());
            form.add("timestamp", String.valueOf(timestamp));
            form.add("folder", fullFolder);
            form.add("signature", signature);

            String response = restClient.post()
                    .uri("https://api.cloudinary.com/v1_1/{cloud}/image/upload", props.cloudName())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(response);
            String url = json.path("secure_url").asText(null);
            if (url == null) {
                throw new RentleException("File upload failed");
            }
            return url;
        } catch (RentleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary upload failed", e);
            throw new RentleException("File upload failed");
        }
    }

    private String sha1Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
