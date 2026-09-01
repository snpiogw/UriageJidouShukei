package com.example.salesaggregation.infrastructure.google;

import com.example.salesaggregation.domain.ColumnMapping;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HeaderMappingResolver {
    public ResolvedColumnMapping resolve(List<Object> headerRow, ColumnMapping requested) throws IOException {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i) == null ? "" : headerRow.get(i).toString().trim();
            if (!requested.headers().contains(header)) continue;
            if (indexes.putIfAbsent(header, i) != null) {
                throw new IOException("対象の見出し「" + header + "」が複数あります");
            }
        }
        for (String header : requested.headers()) {
            if (!indexes.containsKey(header)) throw new IOException("見出し「" + header + "」が見つかりません");
        }
        return new ResolvedColumnMapping(indexes.get(requested.dateColumn()), indexes.get(requested.staffColumn()),
                indexes.get(requested.productColumn()), indexes.get(requested.quantityColumn()),
                indexes.get(requested.unitPriceColumn()));
    }
}
