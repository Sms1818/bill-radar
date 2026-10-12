package com.billradar.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecutionEntity, Long> {

    Optional<WorkflowExecutionEntity> findByExecutionId(String executionId);

    List<WorkflowExecutionEntity> findAllByOrderByStartTimeDesc();

    Optional<WorkflowExecutionEntity> findFirstByOrderByStartTimeDesc();
}
