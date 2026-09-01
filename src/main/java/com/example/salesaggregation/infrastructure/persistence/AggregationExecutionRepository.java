package com.example.salesaggregation.infrastructure.persistence;

import com.example.salesaggregation.domain.ExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface AggregationExecutionRepository extends JpaRepository<AggregationExecutionEntity, UUID> {
    List<AggregationExecutionEntity> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Page<AggregationExecutionEntity> findByProfileIdOrderByRequestedAtDesc(long profileId, Pageable pageable);
    Page<AggregationExecutionEntity> findByProfileIdAndStatusOrderByRequestedAtDesc(
            long profileId, ExecutionStatus status, Pageable pageable);
    List<AggregationExecutionEntity> findByProfileId(long profileId);
    Optional<AggregationExecutionEntity> findFirstByStatusInOrderByRequestedAtDesc(List<ExecutionStatus> statuses);
    Optional<AggregationExecutionEntity> findFirstByStatusInOrderByCompletedAtDesc(List<ExecutionStatus> statuses);
    Optional<AggregationExecutionEntity> findFirstByProfileIdAndStatusInOrderByRequestedAtDesc(long profileId, List<ExecutionStatus> statuses);
    Optional<AggregationExecutionEntity> findFirstByProfileIdAndStatusInOrderByCompletedAtDesc(long profileId, List<ExecutionStatus> statuses);
    Optional<AggregationExecutionEntity> findFirstByProfileIdOrderByRequestedAtDesc(long profileId);
    long deleteByStatusInAndCompletedAtBefore(List<ExecutionStatus> statuses, Instant completedAt);
}
