package com.rentle.domain.platform.settings;

import com.rentle.config.RentleProperties;
import com.rentle.shared.exception.RentleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-editable platform settings (Layer 1, docs/12 §2b). Values live in one table and fall
 * back to {@link RentleProperties} (yml) defaults when unset, so a fresh deploy behaves exactly
 * as configured and an admin can turn a knob (e.g. the platform fee) without a redeploy.
 * ponytail: no cache yet — settings reads are infrequent; add a short-TTL cache if it ever matters.
 */
@Service
public class PlatformSettingsService {

    public static final String PLATFORM_FEE_PERCENT = "platform_fee_percent";
    public static final String REVIEW_WINDOW_DAYS = "review_window_days";

    private final PlatformSettingRepository repository;
    private final RentleProperties props;

    public PlatformSettingsService(PlatformSettingRepository repository, RentleProperties props) {
        this.repository = repository;
        this.props = props;
    }

    /** The commission percent applied to a completed booking (default from yml; 0 at free launch). */
    @Transactional(readOnly = true)
    public BigDecimal platformFeePercent() {
        return getDecimal(PLATFORM_FEE_PERCENT, BigDecimal.valueOf(props.platformFeePercent()));
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put(PLATFORM_FEE_PERCENT, platformFeePercent().toPlainString());
        out.put(REVIEW_WINDOW_DAYS, getInt(REVIEW_WINDOW_DAYS, props.reviewWindowDays()).toString());
        return out;
    }

    @Transactional
    public void set(String key, String value, UUID adminId) {
        validate(key, value);
        PlatformSetting setting = repository.findById(key).orElseGet(() -> {
            PlatformSetting s = new PlatformSetting();
            s.setKey(key);
            return s;
        });
        setting.setValue(value.trim());
        setting.setUpdatedBy(adminId);
        setting.setUpdatedAt(Instant.now());
        repository.save(setting);
    }

    private void validate(String key, String value) {
        switch (key) {
            case PLATFORM_FEE_PERCENT -> {
                BigDecimal pct = parseDecimal(value);
                if (pct == null || pct.signum() < 0 || pct.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new RentleException("Platform fee percent must be between 0 and 100");
                }
            }
            case REVIEW_WINDOW_DAYS -> {
                try {
                    if (Integer.parseInt(value.trim()) < 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    throw new RentleException("Review window days must be a positive integer");
                }
            }
            default -> throw new RentleException("Unknown setting: " + key);
        }
    }

    private BigDecimal getDecimal(String key, BigDecimal fallback) {
        return repository.findById(key).map(s -> parseDecimal(s.getValue())).filter(v -> v != null).orElse(fallback);
    }

    private Integer getInt(String key, int fallback) {
        return repository.findById(key).map(s -> {
            try { return Integer.parseInt(s.getValue().trim()); } catch (NumberFormatException e) { return fallback; }
        }).orElse(fallback);
    }

    private BigDecimal parseDecimal(String v) {
        try { return new BigDecimal(v.trim()); } catch (Exception e) { return null; }
    }
}
