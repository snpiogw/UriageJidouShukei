package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.infrastructure.persistence.AggregationSettingsEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationSettingsRepository;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;

@Service
public class SettingsService {
    public static final long SETTINGS_ID = 1L;
    private final AggregationSettingsRepository repository;

    public SettingsService(AggregationSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AggregationSettingsEntity current() {
        return repository.findById(SETTINGS_ID)
                .orElseThrow(() -> new IllegalStateException("集計設定が初期化されていません"));
    }

    @Transactional
    public AggregationSettingsEntity update(TaxMode mode, BigDecimal taxRate, boolean enabled,
                                            LocalTime time, long expectedVersion, String actor) {
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0
                || taxRate.scale() > 4) {
            throw new IllegalArgumentException("税率は0〜100%の範囲で小数4桁以内にしてください");
        }
        AggregationSettingsEntity settings = current();
        if (settings.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(AggregationSettingsEntity.class, SETTINGS_ID);
        }
        settings.update(mode, taxRate, enabled, time, actor);
        try {
            return repository.saveAndFlush(settings);
        } catch (OptimisticLockException ex) {
            throw new ObjectOptimisticLockingFailureException(AggregationSettingsEntity.class, SETTINGS_ID, ex);
        }
    }
}
