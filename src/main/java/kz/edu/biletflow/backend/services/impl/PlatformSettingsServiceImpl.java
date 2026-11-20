package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.PlatformSettingsResponse;
import kz.edu.biletflow.backend.dtos.UpdatePlatformSettingsRequest;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.PlatformSettings;
import kz.edu.biletflow.backend.repositories.PlatformSettingsRepository;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.PlatformSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlatformSettingsServiceImpl implements PlatformSettingsService {
    private final PlatformSettingsRepository settingsRepository;
    private final AuditService auditService;

    // Placeholders: the SRS leaves the real amounts undecided. Admins can override both at runtime.
    @Value("${app.payments.activation-fee-kzt:5000}")
    private BigDecimal defaultActivationFee;

    @Value("${app.payments.processing-fee-percent:2.5}")
    private BigDecimal defaultProcessingFeePercent;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal activationFee() {
        BigDecimal value = settings().getActivationFeeKzt();
        return money(value != null ? value : defaultActivationFee);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal processingFeePercent() {
        BigDecimal value = settings().getProcessingFeePercent();
        return value != null ? value : defaultProcessingFeePercent;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformSettingsResponse getSettings() {
        return toDto(settings());
    }

    @Override
    @Transactional
    public PlatformSettingsResponse updateSettings(Long adminId, UpdatePlatformSettingsRequest request) {
        PlatformSettings settings = settings();
        BigDecimal oldFee = activationFee();
        BigDecimal oldPercent = processingFeePercent();

        List<String> changes = new ArrayList<>();
        if (request.getActivationFeeKzt() != null) {
            BigDecimal fee = money(request.getActivationFeeKzt());
            settings.setActivationFeeKzt(fee);
            if (fee.compareTo(oldFee) != 0) {
                changes.add("activation fee " + oldFee + " → " + fee + " KZT");
            }
        }
        if (request.getProcessingFeePercent() != null) {
            BigDecimal percent = request.getProcessingFeePercent().setScale(2, RoundingMode.HALF_UP);
            settings.setProcessingFeePercent(percent);
            if (percent.compareTo(oldPercent) != 0) {
                changes.add("processing fee " + oldPercent.stripTrailingZeros().toPlainString() + "% → "
                        + percent.stripTrailingZeros().toPlainString() + "%");
            }
        }
        if (changes.isEmpty()) {
            return toDto(settings);
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings.setUpdatedById(adminId);
        PlatformSettings saved = settingsRepository.save(settings);
        auditService.record(adminId, AuditAction.PLATFORM_SETTINGS_UPDATED, PlatformSettings.ID, null,
                "Platform settings changed: " + String.join("; ", changes));
        return toDto(saved);
    }

    private PlatformSettings settings() {
        // The migration inserts the row; a missing one (e.g. deleted by hand) just means "all defaults".
        return settingsRepository.findById(PlatformSettings.ID).orElseGet(() -> {
            PlatformSettings settings = new PlatformSettings();
            settings.setId(PlatformSettings.ID);
            return settings;
        });
    }

    private PlatformSettingsResponse toDto(PlatformSettings settings) {
        PlatformSettingsResponse dto = new PlatformSettingsResponse();
        dto.setActivationFeeKzt(money(Objects.requireNonNullElse(settings.getActivationFeeKzt(), defaultActivationFee)));
        dto.setProcessingFeePercent(Objects.requireNonNullElse(settings.getProcessingFeePercent(), defaultProcessingFeePercent));
        dto.setDefaultActivationFeeKzt(money(defaultActivationFee));
        dto.setDefaultProcessingFeePercent(defaultProcessingFeePercent);
        dto.setUpdatedAt(settings.getUpdatedAt());
        return dto;
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
