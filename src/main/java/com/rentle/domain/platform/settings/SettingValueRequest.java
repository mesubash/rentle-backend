package com.rentle.domain.platform.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettingValueRequest(@NotBlank @Size(max = 200) String value) {}
