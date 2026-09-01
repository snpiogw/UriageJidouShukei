package com.example.salesaggregation.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Configuration
public class GoogleSheetsConfig {
    @Bean
    @Lazy
    Sheets sheetsClient(AppProperties properties) throws IOException, GeneralSecurityException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of(SheetsScopes.SPREADSHEETS));
        HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);
        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                request -> {
                    credentialsAdapter.initialize(request);
                    request.setConnectTimeout(properties.sheets().connectTimeoutMillis());
                    request.setReadTimeout(properties.sheets().readTimeoutMillis());
                })
                .setApplicationName("sales-aggregation")
                .build();
    }
}
