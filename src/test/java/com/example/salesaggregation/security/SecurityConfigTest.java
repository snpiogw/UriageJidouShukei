package com.example.salesaggregation.security;

import com.example.salesaggregation.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigTest {

    @Test
    void refusesToStartWithAnEmptyAdminPasswordHash() {
        AppProperties properties = new AppProperties(
                new AppProperties.Sheets("", "売上データ", "集計結果", "エラーログ", 10_000, 500),
                new AppProperties.Security("admin", ""),
                new AppProperties.Batch(500, 3));

        assertThatThrownBy(() -> new SecurityConfig().userDetailsService(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD_HASH");
    }
}
