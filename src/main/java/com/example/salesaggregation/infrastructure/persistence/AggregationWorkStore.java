package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.RowError;
import com.example.salesaggregation.domain.ValidatedSalesRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
public class AggregationWorkStore {
    private static final int MAX_SAVED_ERRORS = 500;
    private final JdbcTemplate jdbc;

    public AggregationWorkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void clear(UUID executionId) {
        clearAggregates(executionId);
        jdbc.update("delete from aggregation_row_error where execution_id = ?", executionId);
        jdbc.update("update aggregation_execution set source_count=0, valid_count=0, invalid_count=0 where id=?", executionId);
    }

    @Transactional
    public void clearAggregates(UUID executionId) {
        jdbc.update("delete from aggregation_product_work where execution_id = ?", executionId);
        jdbc.update("delete from aggregation_staff_work where execution_id = ?", executionId);
        jdbc.update("delete from aggregation_monthly_work where execution_id = ?", executionId);
    }

    @Transactional
    public void writeChunk(UUID executionId, List<? extends ValidatedSalesRow> items) {
        long valid = 0;
        long invalid = 0;
        for (ValidatedSalesRow item : items) {
            if (item.valid()) {
                valid++;
                upsert("aggregation_product_work", "aggregation_key", executionId,
                        item.row().productName(), item.amount());
                upsert("aggregation_staff_work", "aggregation_key", executionId,
                        item.row().staffName(), item.amount());
                upsert("aggregation_monthly_work", "aggregation_key", executionId,
                        item.row().salesDate().toString().substring(0, 7), item.amount());
            } else {
                invalid++;
                saveErrors(executionId, item.errors());
            }
        }
        jdbc.update("""
                update aggregation_execution
                   set source_count = source_count + ?, valid_count = valid_count + ?, invalid_count = invalid_count + ?
                 where id = ?
                """, items.size(), valid, invalid, executionId);
    }

    private void upsert(String table, String keyColumn, UUID executionId, String key, BigDecimal amount) {
        jdbc.update("insert into " + table + " (execution_id," + keyColumn + ",amount) values (?,?,?) " +
                        "on conflict (execution_id," + keyColumn + ") do update set amount=" + table + ".amount+excluded.amount",
                executionId, key, amount);
    }

    private void saveErrors(UUID executionId, List<RowError> errors) {
        Integer saved = jdbc.queryForObject(
                "select count(*) from aggregation_row_error where execution_id=?", Integer.class, executionId);
        int remaining = Math.max(0, MAX_SAVED_ERRORS - Objects.requireNonNullElse(saved, 0));
        for (RowError error : errors.stream().limit(remaining).toList()) {
            jdbc.update("""
                    insert into aggregation_row_error(execution_id,row_number,field,error_code,guidance)
                    values (?,?,?,?,?)
                    """, executionId, error.rowNumber(), error.field(), error.code(), error.guidance());
        }
    }

    public Map<String, BigDecimal> productTotals(UUID executionId) {
        return totals("aggregation_product_work", executionId);
    }

    public Map<String, BigDecimal> staffTotals(UUID executionId) {
        return totals("aggregation_staff_work", executionId);
    }

    public Map<String, BigDecimal> monthlyTotals(UUID executionId) {
        return totals("aggregation_monthly_work", executionId);
    }

    private Map<String, BigDecimal> totals(String table, UUID executionId) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        List<Map.Entry<String, BigDecimal>> rows = jdbc.query(
                "select aggregation_key,amount from " + table + " where execution_id=? order by aggregation_key",
                (rs, rowNum) -> Map.entry(rs.getString(1), rs.getBigDecimal(2)), executionId);
        rows.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    public List<RowError> errors(UUID executionId) {
        return jdbc.query("""
                        select row_number,field,error_code,guidance
                          from aggregation_row_error where execution_id=? order by row_number,id
                        """, this::mapError, executionId);
    }

    private RowError mapError(ResultSet rs, int rowNum) throws SQLException {
        return new RowError(rs.getInt("row_number"), rs.getString("field"),
                rs.getString("error_code"), rs.getString("guidance"));
    }
}
