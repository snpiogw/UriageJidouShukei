package com.example.salesaggregation.domain;

public enum TriggerType {
    MANUAL("手動実行"),
    SCHEDULED("自動実行");

    private final String label;

    TriggerType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
