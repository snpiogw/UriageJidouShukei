package com.example.salesaggregation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregationSettingsRepository extends JpaRepository<AggregationSettingsEntity, Long> {}
