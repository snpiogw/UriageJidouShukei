package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.ExecutionStatus;
import com.example.salesaggregation.domain.TaxMode;
import com.example.salesaggregation.domain.TriggerType;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationSettingsEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Read-only DTOs returned by the application layer to the web layer.
 * Persistence entities remain an implementation detail of application services.
 */
public final class AdminViewModels {
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    private AdminViewModels() {}

    public record SettingsView(TaxMode taxMode, BigDecimal taxRate, boolean autoEnabled,
                               LocalTime executionTime, long version) {
        static SettingsView from(AggregationSettingsEntity entity) {
            return new SettingsView(entity.getTaxMode(), entity.getTaxRate(), entity.isAutoEnabled(),
                    entity.getExecutionTime(), entity.getVersion());
        }
    }

    public record ExecutionView(UUID id, TriggerType triggerType, ExecutionStatus status,
                                TaxMode taxMode, BigDecimal taxRate, Instant requestedAt,
                                Instant completedAt, long sourceCount, long validCount,
                                long invalidCount, String errorCode, String summary) {
        static ExecutionView from(AggregationExecutionEntity entity) {
            return new ExecutionView(entity.getId(), entity.getTriggerType(), entity.getStatus(),
                    entity.getTaxMode(), entity.getTaxRate(), entity.getRequestedAt(), entity.getCompletedAt(),
                    entity.getSourceCount(), entity.getValidCount(), entity.getInvalidCount(),
                    entity.getErrorCode(), entity.getSummary());
        }

        public ZonedDateTime requestedAtJst() {
            return requestedAt.atZone(JAPAN);
        }

        public ZonedDateTime completedAtJst() {
            return completedAt == null ? null : completedAt.atZone(JAPAN);
        }

        public boolean restartable() {
            return status == ExecutionStatus.FAILED;
        }

        public String taxRateDisplay() {
            return taxRate.stripTrailingZeros().toPlainString();
        }

        public boolean hasIssueExplanation() {
            return status == ExecutionStatus.FAILED
                    || status == ExecutionStatus.SUCCESS_WITH_WARNINGS
                    || status == ExecutionStatus.NO_VALID_DATA
                    || status == ExecutionStatus.SKIPPED_CONCURRENT;
        }

        public String issueTitle() {
            return switch (status) {
                case SUCCESS_WITH_WARNINGS -> "一部の行が集計対象から除外されました";
                case NO_VALID_DATA -> "集計できる売上データがありませんでした";
                case SKIPPED_CONCURRENT -> "別の集計処理が実行中でした";
                case FAILED -> "集計処理に失敗しました";
                default -> "";
            };
        }

        public String issueGuidance() {
            if (status == ExecutionStatus.SUCCESS_WITH_WARNINGS || status == ExecutionStatus.NO_VALID_DATA) {
                return "下の入力エラー一覧で対象行と修正方法を確認し、元データを修正してから再実行してください。";
            }
            if (status == ExecutionStatus.SKIPPED_CONCURRENT) {
                return "実行中の集計が完了してから、もう一度実行してください。";
            }
            return switch (errorCode == null ? "" : errorCode) {
                case "SPREADSHEET_NOT_CONFIGURED" -> "環境変数 GOOGLE_SPREADSHEET_ID を設定し、アプリを再起動してください。";
                case "INVALID_SHEET_HEADER" -> "売上データシートの1行目を「日付、担当者、商品名、数量、単価」の順に修正してください。";
                case "ROW_LIMIT_EXCEEDED" -> "入力件数を上限以内に減らすか、MAX_SALES_ROWS の設定を見直してください。";
                case "GOOGLE_SHEETS_UNAVAILABLE" -> "Google Sheetsへの接続、共有権限、サービスアカウント、APIの利用状況を確認してから再開してください。";
                case "DATABASE_ERROR" -> "PostgreSQLの起動状態と接続設定を確認し、復旧後に再開してください。";
                case "LAUNCH_FAILED" -> "実行基盤の状態を確認してから、もう一度手動実行してください。";
                case "RESTART_FAILED" -> "実行基盤の状態を確認してから、再度「失敗地点から再開」を押してください。";
                default -> "実行IDを添えてサーバーログを確認し、原因を解消してから再開してください。";
            };
        }
    }
}
