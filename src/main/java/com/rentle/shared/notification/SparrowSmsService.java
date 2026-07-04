package com.rentle.shared.notification;

import com.rentle.config.SparrowSmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@ConditionalOnProperty(name = "rentle.sms", havingValue = "sparrow")
public class SparrowSmsService implements SmsService {

    private static final String SPARROW_URL = "https://api.sparrowsms.com/v2/sms/";

    private final SparrowSmsProperties props;
    private final RestClient restClient = RestClient.create();

    public SparrowSmsService(SparrowSmsProperties props) {
        this.props = props;
    }

    @Override
    public void send(String toPhone, String message) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", props.token());
            form.add("from", props.from());
            form.add("to", toPhone);
            form.add("text", message);

            restClient.post()
                    .uri(SPARROW_URL)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // SMS failure must never break the business transaction
            log.error("Sparrow SMS send failed for {}: {}", toPhone, e.getMessage());
        }
    }
}
