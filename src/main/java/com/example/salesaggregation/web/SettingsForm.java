package com.example.salesaggregation.web;

import com.example.salesaggregation.domain.TaxMode;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalTime;

public class SettingsForm {
    @NotNull
    private TaxMode taxMode;
    @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") @Digits(integer = 3, fraction = 4)
    private BigDecimal taxRate;
    private boolean autoEnabled;
    @NotNull @DateTimeFormat(pattern = "HH:mm")
    private LocalTime executionTime;
    private long version;

    public TaxMode getTaxMode() { return taxMode; }
    public void setTaxMode(TaxMode taxMode) { this.taxMode = taxMode; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public void setAutoEnabled(boolean autoEnabled) { this.autoEnabled = autoEnabled; }
    public LocalTime getExecutionTime() { return executionTime; }
    public void setExecutionTime(LocalTime executionTime) { this.executionTime = executionTime; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
