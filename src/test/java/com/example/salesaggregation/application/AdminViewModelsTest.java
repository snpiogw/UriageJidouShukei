package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminViewModelsTest {

    @Test
    void exposesJapaneseLabelsForExecutionHistory() {
        assertThat(TriggerType.SCHEDULED.getLabel()).isEqualTo("自動実行");
        assertThat(TriggerType.MANUAL.getLabel()).isEqualTo("手動実行");
        assertThat(ExecutionStatus.SUCCESS_WITH_WARNINGS.getLabel()).isEqualTo("一部除外ありで成功");
        assertThat(ExecutionStatus.FAILED.getLabel()).isEqualTo("失敗");
    }

    @Test
    void formatsTaxRateWithoutUnnecessaryTrailingZeros() {
        AdminViewModels.ExecutionView view = executionView(
                ExecutionStatus.SUCCESS, new BigDecimal("10.0000"), null, "正常に更新しました");

        assertThat(view.taxRateDisplay()).isEqualTo("10");
    }

    @Test
    void providesActionableFailureGuidance() {
        AdminViewModels.ExecutionView view = executionView(
                ExecutionStatus.FAILED, new BigDecimal("10"), "INVALID_SHEET_HEADER",
                "売上データシートの見出しが想定と一致しません");

        assertThat(view.hasIssueExplanation()).isTrue();
        assertThat(view.issueTitle()).isEqualTo("集計処理に失敗しました");
        assertThat(view.issueGuidance()).contains("日付、担当者、商品名、数量、単価");
    }

    private AdminViewModels.ExecutionView executionView(ExecutionStatus status, BigDecimal taxRate,
                                                         String errorCode, String summary) {
        return new AdminViewModels.ExecutionView(UUID.randomUUID(), 1L, "既存設定", "Asia/Tokyo", TriggerType.MANUAL, status,
                TaxMode.INCLUSIVE, taxRate, Instant.now(), Instant.now(), 1, 1, 0, errorCode, summary);
    }
}
