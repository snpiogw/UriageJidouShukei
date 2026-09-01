package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AggregationProfileValidationServiceTest {
    private static final String VALID_SPREADSHEET_ID = "same-sheet-id-1234567890";
    private final AggregationProfileRepository repository = mock(AggregationProfileRepository.class);
    private final AggregationProfileValidationService validation = new AggregationProfileValidationService(repository);

    @Test
    void allowsDifferentInputAndOutputSheetsInTheSameSpreadsheet() {
        AggregationProfileEntity other = mock(AggregationProfileEntity.class);
        when(other.getSourceSheetName()).thenReturn("売上A");
        when(other.getResultSheetName()).thenReturn("結果A");
        when(other.getErrorSheetName()).thenReturn("エラーA");
        when(repository.findConflicts(VALID_SPREADSHEET_ID, -1L)).thenReturn(List.of(other));

        validation.validate(command("店舗B", VALID_SPREADSHEET_ID, "売上B", "結果B", "エラーB"), -1L);
    }

    @Test
    void rejectsOutputCrossRoleAndInputCollisions() {
        AggregationProfileEntity other = mock(AggregationProfileEntity.class);
        when(other.getSourceSheetName()).thenReturn("売上A");
        when(other.getResultSheetName()).thenReturn("共通出力");
        when(other.getErrorSheetName()).thenReturn("エラーA");
        when(repository.findConflicts(VALID_SPREADSHEET_ID, -1L)).thenReturn(List.of(other));

        assertThatThrownBy(() -> validation.validate(
                command("店舗B", VALID_SPREADSHEET_ID, "売上B", "結果B", "共通出力"), -1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("共通出力");
        assertThatThrownBy(() -> validation.validate(
                command("店舗B", VALID_SPREADSHEET_ID, "売上A", "結果B", "エラーB"), -1L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("売上A");
    }

    @Test
    void rejectsDuplicateColumnNamesAndInvalidTimeZone() {
        ProfileCommand duplicateColumns = new ProfileCommand("店舗", VALID_SPREADSHEET_ID, "入力", "結果", "エラー",
                TaxMode.INCLUSIVE, BigDecimal.TEN, true, LocalTime.NOON, "Asia/Tokyo",
                new ColumnMapping("日付", "担当者", "商品", "数量", "数量"));
        assertThatThrownBy(() -> validation.validate(duplicateColumns, -1L))
                .hasMessageContaining("異なる列名");

        ProfileCommand invalidZone = new ProfileCommand("店舗", VALID_SPREADSHEET_ID, "入力", "結果", "エラー",
                TaxMode.INCLUSIVE, BigDecimal.TEN, true, LocalTime.NOON, "invalid-zone", ColumnMapping.DEFAULT);
        assertThatThrownBy(() -> validation.validate(invalidZone, -1L))
                .hasMessageContaining("タイムゾーン");
    }

    @Test
    void explainsThatTheSpreadsheetIdIsNotTheFullUrl() {
        ProfileCommand command = command("店舗", "https://docs.google.com/spreadsheets/d/abc/edit",
                "入力", "結果", "エラー");

        assertThatThrownBy(() -> validation.validate(command, -1L))
                .hasMessageContaining("/d/").hasMessageContaining("/edit");
    }

    private ProfileCommand command(String name, String spreadsheet, String source, String result, String error) {
        when(repository.findAll()).thenReturn(List.of());
        return new ProfileCommand(name, spreadsheet, source, result, error, TaxMode.INCLUSIVE,
                BigDecimal.TEN, true, LocalTime.of(21, 0), "Asia/Tokyo", ColumnMapping.DEFAULT);
    }
}
