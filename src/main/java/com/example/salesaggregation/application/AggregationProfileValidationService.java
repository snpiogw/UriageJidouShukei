package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AggregationProfileValidationService {
    private static final Pattern INVALID_SHEET_CHARACTERS = Pattern.compile("[\\\\/:?*\\[\\]]");
    private static final Pattern SPREADSHEET_ID = Pattern.compile("[A-Za-z0-9_-]{20,255}");
    private final AggregationProfileRepository profiles;

    public AggregationProfileValidationService(AggregationProfileRepository profiles) {
        this.profiles = profiles;
    }

    public void validate(ProfileCommand command, long excludedId) {
        validateText(command.profileName(), 100, "設定名");
        validateText(command.spreadsheetId(), 255, "Spreadsheet ID");
        if (!SPREADSHEET_ID.matcher(command.spreadsheetId().trim()).matches()) {
            throw new IllegalArgumentException("Spreadsheet IDはURLではなく、/d/ と /edit の間のIDを入力してください");
        }
        validateSheetName(command.sourceSheetName(), "入力シート名");
        validateSheetName(command.resultSheetName(), "集計結果シート名");
        validateSheetName(command.errorSheetName(), "エラーログシート名");
        if (command.taxMode() == null) throw new IllegalArgumentException("税区分を選択してください");
        BigDecimal taxRate = command.taxRate();
        if (taxRate == null || taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0
                || taxRate.scale() > 4) {
            throw new IllegalArgumentException("税率は0〜100%の範囲で小数4桁以内にしてください");
        }
        if (command.executionTime() == null) throw new IllegalArgumentException("実行時刻を入力してください");
        try {
            ZoneId.of(command.timeZone());
        } catch (DateTimeException | NullPointerException ex) {
            throw new IllegalArgumentException("有効なタイムゾーンを入力してください");
        }

        ColumnMapping mapping = command.columnMapping();
        if (mapping == null || mapping.headers().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("列名をすべて入力してください");
        }
        if (mapping.headers().stream().anyMatch(value -> value.length() > 100
                || value.chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("列名は100文字以内の文字列で入力してください");
        }
        if (new HashSet<>(mapping.headers()).size() != mapping.headers().size()) {
            throw new IllegalArgumentException("列マッピングには異なる列名を指定してください");
        }

        Set<String> ownSheets = new HashSet<>(List.of(command.sourceSheetName().trim(),
                command.resultSheetName().trim(), command.errorSheetName().trim()));
        if (ownSheets.size() != 3) {
            throw new IllegalArgumentException("入力・集計結果・エラーログには異なるシート名を指定してください");
        }

        List<AggregationProfileEntity> all = profiles.findAll();
        if (all.stream().anyMatch(p -> p.getId() != excludedId
                && p.getProfileName().trim().equalsIgnoreCase(command.profileName().trim()))) {
            throw new IllegalArgumentException("同じ設定名が既に登録されています");
        }
        for (AggregationProfileEntity other : profiles.findConflicts(command.spreadsheetId().trim(), excludedId)) {
            Set<String> otherSheets = Set.of(other.getSourceSheetName(), other.getResultSheetName(), other.getErrorSheetName());
            for (String sheet : ownSheets) {
                if (otherSheets.contains(sheet)) {
                    throw new IllegalArgumentException("同じSpreadsheet内でシート「" + sheet
                            + "」が別の集計設定に使用されています");
                }
            }
        }
    }

    private void validateSheetName(String value, String label) {
        require(value, label + "を入力してください");
        String normalized = value.trim();
        if (normalized.length() > 100 || normalized.chars().anyMatch(Character::isISOControl)
                || INVALID_SHEET_CHARACTERS.matcher(normalized).find()) {
            throw new IllegalArgumentException(label + "は100文字以内で、\\ / : ? * [ ] を含めず入力してください");
        }
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private void validateText(String value, int maxLength, String label) {
        require(value, label + "を入力してください");
        String normalized = value.trim();
        if (normalized.length() > maxLength || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + "は" + maxLength + "文字以内の文字列で入力してください");
        }
    }
}
