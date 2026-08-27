package com.example.salesaggregation.security;

import com.example.salesaggregation.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(AppProperties properties) {
        String hash = properties.security().adminPasswordHash();
        if (hash == null || hash.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASHをBCrypt形式で設定してください");
        } else if (hash.startsWith("$2y$")) {
            hash = "$2a$" + hash.substring(4);
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.security().adminUsername())
                .password(hash)
                .roles("ADMIN")
                .build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/actuator/health", "/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/admin", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'"))
                        .frameOptions(frame -> frame.deny()))
                .build();
    }
}
