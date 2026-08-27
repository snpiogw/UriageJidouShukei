package com.example.salesaggregation.domain;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class SalesRowValidator {
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern INTEGER = Pattern.compile("[0-9]+");
    private static final long MAX_QUANTITY = 1_000_000_000L;
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("1000000000000");

    public ValidationOutcome validate(RawSalesRow raw) {
        List<RowError> errors = new ArrayList<>();
        LocalDate date = parseDate(raw, errors);
        String staff = parseName(raw, 1, "担当者", errors);
        String product = parseName(raw, 2, "商品名", errors);
        Long quantity = parseInteger(raw, 3, "数量", MAX_QUANTITY, errors);
        BigDecimal unitPrice = parseMoney(raw, 4, "単価", errors);

        if (!errors.isEmpty()) {
            return new ValidationOutcome(null, errors);
        }
        return new ValidationOutcome(
                new SalesRow(raw.rowNumber(), date, staff, product, quantity, unitPrice),
                List.of());
    }

    private LocalDate parseDate(RawSalesRow raw, List<RowError> errors) {
        Object value = raw.cell(0);
        if (value == null || value.toString().isBlank()) {
            errors.add(error(raw, "日付", "REQUIRED", "日付を入力してください"));
            return null;
        }
        try {
            if (value instanceof Number number) {
                BigDecimal serial = new BigDecimal(number.toString());
                if (serial.stripTrailingZeros().scale() > 0) {
                    throw new DateTimeParseException("time component", value.toString(), 0);
                }
                return LocalDate.of(1899, 12, 30).plusDays(serial.longValueExact());
            }
            return LocalDate.parse(value.toString().trim(), ISO_DATE);
        } catch (RuntimeException ex) {
            errors.add(error(raw, "日付", "INVALID_DATE", "日付セルまたはyyyy-MM-ddで入力してください"));
            return null;
        }
    }

    private String parseName(RawSalesRow raw, int index, String field, List<RowError> errors) {
        Object value = raw.cell(index);
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            errors.add(error(raw, field, "REQUIRED", field + "を入力してください"));
            return null;
        }
        if (text.length() > 100 || text.chars().anyMatch(Character::isISOControl)) {
            errors.add(error(raw, field, "INVALID_TEXT", "100文字以内の文字列を入力してください"));
            return null;
        }
        return text;
    }

    private Long parseInteger(RawSalesRow raw, int index, String field, long max, List<RowError> errors) {
        String text = normalizedNumber(raw.cell(index));
        if (text == null) {
            errors.add(error(raw, field, "REQUIRED", field + "を入力してください"));
            return null;
        }
        try {
            if (!INTEGER.matcher(text).matches()) throw new NumberFormatException();
            long value = Long.parseLong(text);
            if (value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            errors.add(error(raw, field, "INVALID_INTEGER", "0以上の整数を入力してください"));
            return null;
        }
    }

    private BigDecimal parseMoney(RawSalesRow raw, int index, String field, List<RowError> errors) {
        String text = normalizedNumber(raw.cell(index));
        if (text == null) {
            errors.add(error(raw, field, "REQUIRED", field + "を入力してください"));
            return null;
        }
        try {
            if (!INTEGER.matcher(text).matches()) throw new NumberFormatException();
            BigDecimal value = new BigDecimal(text).setScale(0, RoundingMode.UNNECESSARY);
            if (value.compareTo(MAX_UNIT_PRICE) > 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException ex) {
            errors.add(error(raw, field, "INVALID_INTEGER", "0以上の整数円を入力してください"));
            return null;
        }
    }

    private String normalizedNumber(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        return value.toString().trim();
    }

    private RowError error(RawSalesRow raw, String field, String code, String guidance) {
        return new RowError(raw.rowNumber(), field, code, guidance);
    }

    public record ValidationOutcome(SalesRow row, List<RowError> errors) {}
}
