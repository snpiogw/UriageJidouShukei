package com.example.salesaggregation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AggregationProfileRepository extends JpaRepository<AggregationProfileEntity, Long> {
    List<AggregationProfileEntity> findAllByOrderByProfileNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AggregationProfileEntity p where p.id = :id")
    Optional<AggregationProfileEntity> findForUpdateById(long id);

    @Query("""
            select p from AggregationProfileEntity p
             where p.spreadsheetId = :spreadsheetId
               and p.id <> :excludedId
            """)
    List<AggregationProfileEntity> findConflicts(String spreadsheetId, long excludedId);
}
