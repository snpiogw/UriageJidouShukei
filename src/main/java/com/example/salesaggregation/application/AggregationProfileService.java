package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionProfileSnapshot;
import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileRepository;
import com.example.salesaggregation.infrastructure.persistence.ProfileConfigurationLockService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AggregationProfileService {
    private static final Logger log = LoggerFactory.getLogger(AggregationProfileService.class);
    public static final long LEGACY_PROFILE_ID = 1L;
    private final AggregationProfileRepository profiles;
    private final AggregationProfileValidationService validation;
    private final ProfileConfigurationLockService locks;
    private final SalesSheetGateway sheets;

    public AggregationProfileService(AggregationProfileRepository profiles,
                                     AggregationProfileValidationService validation,
                                     ProfileConfigurationLockService locks,
                                     SalesSheetGateway sheets) {
        this.profiles = profiles;
        this.validation = validation;
        this.locks = locks;
        this.sheets = sheets;
    }

    @Transactional(readOnly = true)
    public AggregationProfileEntity get(long id) {
        return profiles.findById(id).orElseThrow(() -> new ResourceNotFoundException("集計設定が見つかりません"));
    }

    @Transactional(readOnly = true)
    public List<AggregationProfileEntity> list() {
        return profiles.findAllByOrderByProfileNameAsc();
    }

    @Transactional
    public AggregationProfileEntity create(ProfileCommand command, String actor) {
        lockSpreadsheets(List.of(command.spreadsheetId()));
        validation.validate(command, -1L);
        validateHeader(snapshot(0, 0, command));
        AggregationProfileEntity entity = new AggregationProfileEntity(command.profileName(), command.spreadsheetId(),
                command.sourceSheetName(), command.resultSheetName(), command.errorSheetName(), command.taxMode(),
                command.taxRate(), command.active(), command.autoEnabled(), command.executionTime(), command.timeZone(),
                command.columnMapping(), actor);
        return save(entity);
    }

    @Transactional
    public AggregationProfileEntity update(long id, long expectedVersion, ProfileCommand command, String actor) {
        AggregationProfileEntity entity = get(id);
        lockSpreadsheets(List.of(entity.getSpreadsheetId(), command.spreadsheetId()));
        if (entity.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(AggregationProfileEntity.class, id);
        }
        validation.validate(command, id);
        boolean headerChanged = !entity.getSpreadsheetId().equals(command.spreadsheetId().trim())
                || !entity.getSourceSheetName().equals(command.sourceSheetName().trim())
                || !entity.columnMapping().equals(command.columnMapping());
        if (headerChanged) validateHeader(snapshot(id, expectedVersion, command));
        entity.update(command.profileName(), command.spreadsheetId(), command.sourceSheetName(),
                command.resultSheetName(), command.errorSheetName(), command.taxMode(), command.taxRate(),
                command.active(), command.autoEnabled(), command.executionTime(), command.timeZone(),
                command.columnMapping(), actor);
        return save(entity);
    }

    private AggregationProfileEntity save(AggregationProfileEntity entity) {
        try {
            return profiles.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("同じ設定名または入力シートの集計設定が既に登録されています", ex);
        }
    }

    private void validateHeader(ExecutionProfileSnapshot snapshot) {
        try {
            sheets.validateHeader(snapshot);
        } catch (IOException ex) {
            log.warn("Google Sheet header validation failed for profile {} ({})",
                    snapshot.profileId(), ex.getClass().getSimpleName());
            log.debug("Google Sheet header validation details", ex);
            throw new IllegalArgumentException(
                    "Google Sheetのヘッダーを確認できません。共有権限、入力シート名、列マッピングを確認してください", ex);
        }
    }

    private ExecutionProfileSnapshot snapshot(long id, long version, ProfileCommand command) {
        return new ExecutionProfileSnapshot(id, command.profileName().trim(), command.spreadsheetId().trim(),
                command.sourceSheetName().trim(), command.resultSheetName().trim(), command.errorSheetName().trim(),
                command.taxMode(), command.taxRate(), command.timeZone(), version, command.columnMapping());
    }

    private void lockSpreadsheets(List<String> spreadsheetIds) {
        List<String> distinct = new ArrayList<>(spreadsheetIds.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().sorted().toList());
        distinct.forEach(locks::lockSpreadsheet);
    }
}
