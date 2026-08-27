package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.domain.ColumnMapping;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

class HeaderMappingResolverTest {
    private final HeaderMappingResolver resolver = new HeaderMappingResolver();

    @Test
    void resolvesConfiguredHeadersRegardlessOfOrderAndKeepsCanonicalRowOrder() throws Exception {
        ColumnMapping requested = new ColumnMapping("販売日", "担当", "品目", "個数", "価格");
        ResolvedColumnMapping mapping = resolver.resolve(
                List.of("メモ", "価格", "品目", "販売日", "個数", "担当"), requested);

        assertThat(mapping.canonicalRow(7, List.of("x", 1200, "商品A", "2026-08-27", 2, "田中")).cells())
                .containsExactly("2026-08-27", "田中", "商品A", 2, 1200);
    }

    @Test
    void defaultMappingRemainsBackwardCompatible() throws Exception {
        ResolvedColumnMapping mapping = resolver.resolve(new ArrayList<>(ColumnMapping.DEFAULT.headers()), ColumnMapping.DEFAULT);
        assertThat(mapping).isEqualTo(new ResolvedColumnMapping(0, 1, 2, 3, 4));
    }

    @Test
    void rejectsMissingOrAmbiguousConfiguredHeaders() {
        assertThatThrownBy(() -> resolver.resolve(List.of("日付", "担当者"), ColumnMapping.DEFAULT))
                .isInstanceOf(IOException.class).hasMessageContaining("商品名");
        assertThatThrownBy(() -> resolver.resolve(
                List.of("日付", "担当者", "商品名", "数量", "単価", "単価"), ColumnMapping.DEFAULT))
                .isInstanceOf(IOException.class).hasMessageContaining("複数");
    }
}
