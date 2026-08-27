package com.example.salesaggregation.domain;

public enum TaxMode {
    EXCLUSIVE("税抜"),
    INCLUSIVE("税込");

    private final String label;

    TaxMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String getLabel() {
        return label;
    }
}
