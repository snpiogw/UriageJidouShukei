package com.example.salesaggregation.application;

import com.example.salesaggregation.domain.*;
import com.example.salesaggregation.infrastructure.persistence.AggregationExecutionEntity;
import com.example.salesaggregation.infrastructure.persistence.AggregationProfileEntity;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public final class AdminViewModels {
    private AdminViewModels() {}

    public record ProfileView(long id, String profileName, String spreadsheetId, String sourceSheetName,
                              String resultSheetName, String errorSheetName, TaxMode taxMode,
                              BigDecimal taxRate, boolean active, boolean autoEnabled, LocalTime executionTime,
                              String timeZone, long version, ColumnMapping columnMapping,
                              ZonedDateTime nextExecution, ExecutionView latestExecution) {
        public static ProfileView from(AggregationProfileEntity entity, ZonedDateTime nextExecution) {
            return from(entity, nextExecution, null);
        }

        public static ProfileView from(AggregationProfileEntity entity, ZonedDateTime nextExecution,
                                       AggregationExecutionEntity latestExecution) {
            return new ProfileView(entity.getId(), entity.getProfileName(), entity.getSpreadsheetId(),
                    entity.getSourceSheetName(), entity.getResultSheetName(), entity.getErrorSheetName(),
                    entity.getTaxMode(), entity.getTaxRate(), entity.isActive(), entity.isAutoEnabled(),
                    entity.getExecutionTime(), entity.getTimeZone(), entity.getVersion(), entity.columnMapping(),
                    nextExecution, latestExecution == null ? null : ExecutionView.from(latestExecution));
        }

        public boolean configured() { return spreadsheetId != null && !spreadsheetId.isBlank(); }
        public boolean runnable() { return active && configured(); }
        public String stateLabel() {
            if (!active) return "無効";
            return configured() ? "設定済み" : "要設定";
        }
        public String stateCss() {
            if (!active) return "DISABLED";
            return configured() ? "SUCCESS" : "FAILED";
        }
        public String spreadsheetUrl() {
            return configured() ? "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/edit" : null;
        }
        public String spreadsheetIdDisplay() {
            if (!configured()) return "未設定";
            return spreadsheetId.length() <= 16 ? spreadsheetId
                    : spreadsheetId.substring(0, 8) + "…" + spreadsheetId.substring(spreadsheetId.length() - 6);
        }
    }

    public record ExecutionView(UUID id, long profileId, String profileName, String timeZone,
                                TriggerType triggerType, ExecutionStatus status,
                                TaxMode taxMode, BigDecimal taxRate, Instant requestedAt,
                                Instant startedAt, Instant completedAt, long sourceCount, long validCount,
                                long invalidCount, String errorCode, String summary) {
        public ExecutionView(UUID id, long profileId, String profileName, String timeZone,
                             TriggerType triggerType, ExecutionStatus status, TaxMode taxMode,
                             BigDecimal taxRate, Instant requestedAt, Instant completedAt,
                             long sourceCount, long validCount, long invalidCount,
                             String errorCode, String summary) {
            this(id, profileId, profileName, timeZone, triggerType, status, taxMode, taxRate,
                    requestedAt, null, completedAt, sourceCount, validCount, invalidCount, errorCode, summary);
        }

        public static ExecutionView from(AggregationExecutionEntity entity) {
            ExecutionProfileSnapshot profile = entity.profileSnapshot();
            return new ExecutionView(entity.getId(), entity.getProfileId(), entity.getProfileNameSnapshot(),
                    profile.timeZone(), entity.getTriggerType(), entity.getStatus(), entity.getTaxMode(),
                    entity.getTaxRate(), entity.getRequestedAt(), entity.getStartedAt(), entity.getCompletedAt(),
                    entity.getSourceCount(), entity.getValidCount(), entity.getInvalidCount(),
                    entity.getErrorCode(), entity.getSummary());
        }

        public ZonedDateTime requestedAtLocal() { return requestedAt.atZone(zone()); }
        public ZonedDateTime completedAtLocal() { return completedAt == null ? null : completedAt.atZone(zone()); }
        public String durationDisplay() {
            if (startedAt == null || completedAt == null) return "—";
            long seconds = Math.max(0, Duration.between(startedAt, completedAt).getSeconds());
            if (seconds < 60) return seconds + "秒";
            long minutes = seconds / 60;
            long remainder = seconds % 60;
            return minutes + "分" + remainder + "秒";
        }
        private ZoneId zone() {
            try { return ZoneId.of(timeZone); }
            catch (DateTimeException ex) { return ZoneId.of("Asia/Tokyo"); }
        }
        public boolean restartable() { return status == ExecutionStatus.FAILED; }
        public String taxRateDisplay() { return taxRate.stripTrailingZeros().toPlainString(); }
        public boolean hasIssueExplanation() {
            return status == ExecutionStatus.FAILED || status == ExecutionStatus.SUCCESS_WITH_WARNINGS
                    || status == ExecutionStatus.NO_VALID_DATA || status == ExecutionStatus.SKIPPED_CONCURRENT;
        }
        public String issueTitle() {
            return switch (status) {
                case SUCCESS_WITH_WARNINGS -> "一部の行が集計対象から除外されました";
                case NO_VALID_DATA -> "集計できる売上データがありませんでした";
                case SKIPPED_CONCURRENT -> "同じ集計設定の処理が実行中でした";
                case FAILED -> "集計処理に失敗しました";
                default -> "";
            };
        }
        public String issueGuidance() {
            if (status == ExecutionStatus.SUCCESS_WITH_WARNINGS || status == ExecutionStatus.NO_VALID_DATA) {
                return "下の入力エラー一覧で対象行と修正方法を確認し、元データを修正してから再実行してください。";
            }
            if (status == ExecutionStatus.SKIPPED_CONCURRENT) {
                return "同じ集計設定の実行が完了してから、もう一度実行してください。";
            }
            return switch (errorCode == null ? "" : errorCode) {
                case "SPREADSHEET_NOT_CONFIGURED" -> "集計設定を編集し、Spreadsheet IDを設定してください。";
                case "INVALID_SHEET_HEADER" -> "入力シートの1行目と列マッピングが一致しているか確認してください。初期値は「日付、担当者、商品名、数量、単価」です。";
                case "ROW_LIMIT_EXCEEDED" -> "入力件数を上限以内に減らすか、MAX_SALES_ROWS の設定を見直してください。";
                case "GOOGLE_SHEETS_UNAVAILABLE" -> "Google Sheetsへの接続、共有権限、サービスアカウント、APIの利用状況を確認してください。";
                case "DATABASE_ERROR" -> "PostgreSQLの起動状態と接続設定を確認し、復旧後に再開してください。";
                case "LAUNCH_FAILED", "RESTART_FAILED" -> "実行基盤の状態を確認してから、もう一度操作してください。";
                default -> "実行IDを添えてサーバーログを確認し、原因を解消してから再開してください。";
            };
        }
    }

    public record AttemptView(int attemptNumber, ExecutionStatus status, Instant requestedAt,
                              Instant startedAt, Instant completedAt, String errorCode, String summary,
                              String timeZone) {
        public static AttemptView from(com.example.salesaggregation.infrastructure.persistence.AggregationExecutionAttemptEntity entity,
                                       String timeZone) {
            return new AttemptView(entity.getAttemptNumber(), entity.getStatus(), entity.getRequestedAt(),
                    entity.getStartedAt(), entity.getCompletedAt(), entity.getErrorCode(), entity.getSummary(), timeZone);
        }

        public ZonedDateTime requestedAtLocal() { return requestedAt.atZone(zone()); }
        public ZonedDateTime completedAtLocal() { return completedAt == null ? null : completedAt.atZone(zone()); }
        public String durationDisplay() {
            if (startedAt == null || completedAt == null) return "—";
            long seconds = Math.max(0, Duration.between(startedAt, completedAt).getSeconds());
            if (seconds < 60) return seconds + "秒";
            return seconds / 60 + "分" + seconds % 60 + "秒";
        }
        private ZoneId zone() {
            try { return ZoneId.of(timeZone); }
            catch (DateTimeException ex) { return ZoneId.of("Asia/Tokyo"); }
        }
    }
}
