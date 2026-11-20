package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.PlatformSettingsResponse;
import kz.edu.biletflow.backend.dtos.UpdatePlatformSettingsRequest;

import java.math.BigDecimal;

public interface PlatformSettingsService {
    // Values in effect now; read wherever a fee is charged.
    BigDecimal activationFee();

    BigDecimal processingFeePercent();

    PlatformSettingsResponse getSettings();

    PlatformSettingsResponse updateSettings(Long adminId, UpdatePlatformSettingsRequest request);
}
