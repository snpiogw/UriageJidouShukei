package com.example.salesaggregation.domain;

public enum ExecutionStatus {
    QUEUED("受付済み"),
    RUNNING("実行中"),
    SUCCESS("成功"),
    SUCCESS_WITH_WARNINGS("一部除外ありで成功"),
    NO_VALID_DATA("有効データなし"),
    FAILED("失敗"),
    SKIPPED_CONCURRENT("他の処理を実行中"),
    UNKNOWN("状態不明");

    private final String label;

    ExecutionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}
