package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ColumnMapping;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.infrastructure.google.ResolvedColumnMapping;
import com.example.salesaggregation.infrastructure.google.SalesSheetGateway;
import com.example.salesaggregation.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AggregationProfileServiceTest {
    @Test
    void locksTheSpreadsheetTransactionBeforeCreatingANewProfile() throws Exception {
        AggregationProfileRepository repository = mock(AggregationProfileRepository.class);
        AggregationProfileValidationService validation = mock(AggregationProfileValidationService.class);
        ProfileConfigurationLockService locks = mock(ProfileConfigurationLockService.class);
        SalesSheetGateway sheets = mock(SalesSheetGateway.class);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sheets.validateHeader(any())).thenReturn(new ResolvedColumnMapping(0, 1, 2, 3, 4));

        new AggregationProfileService(repository, validation, locks, sheets)
                .create(command("new-spreadsheet"), "admin");

        InOrder order = inOrder(locks, validation, sheets, repository);
        order.verify(locks).lockSpreadsheet("new-spreadsheet");
        order.verify(validation).validate(any(), eq(-1L));
        order.verify(sheets).validateHeader(any());
        order.verify(repository).saveAndFlush(any());
    }

    @Test
    void locksBothSpreadsheetIdsAndValidatesHeadersWhenMovingAProfile() throws Exception {
        AggregationProfileRepository repository = mock(AggregationProfileRepository.class);
        AggregationProfileValidationService validation = mock(AggregationProfileValidationService.class);
        ProfileConfigurationLockService locks = mock(ProfileConfigurationLockService.class);
        SalesSheetGateway sheets = mock(SalesSheetGateway.class);
        AggregationProfileEntity existing = new AggregationProfileEntity("店舗", "old-spreadsheet", "入力", "結果",
                "エラー", TaxMode.INCLUSIVE, BigDecimal.TEN, true, LocalTime.of(21, 0), "Asia/Tokyo",
                ColumnMapping.DEFAULT, "admin");
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sheets.validateHeader(any())).thenReturn(new ResolvedColumnMapping(0, 1, 2, 3, 4));

        new AggregationProfileService(repository, validation, locks, sheets)
                .update(7, 0, command("new-spreadsheet"), "admin");

        InOrder lockOrder = inOrder(locks);
        lockOrder.verify(locks).lockSpreadsheet("new-spreadsheet");
        lockOrder.verify(locks).lockSpreadsheet("old-spreadsheet");
        verify(validation).validate(any(), eq(7L));
        verify(sheets).validateHeader(any());
    }

    private ProfileCommand command(String spreadsheetId) {
        return new ProfileCommand("店舗", spreadsheetId, "入力", "結果", "エラー", TaxMode.INCLUSIVE,
                BigDecimal.TEN, true, LocalTime.of(21, 0), "Asia/Tokyo", ColumnMapping.DEFAULT);
    }
}
