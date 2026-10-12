package com.billradar.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface BillingRecordRepository extends JpaRepository<BillingRecordEntity, Long> {

    boolean existsByRecordFingerprint(String recordFingerprint);

    @Query("SELECT b.recordFingerprint FROM BillingRecordEntity b WHERE b.recordFingerprint IN :fingerprints")
    Set<String> findExistingFingerprints(Collection<String> fingerprints);

    @Query("SELECT b.recordFingerprint FROM BillingRecordEntity b")
    Set<String> findAllFingerprints();

    List<BillingRecordEntity> findAllByOrderByUsageDateAsc();

    List<BillingRecordEntity> findByWorkloadIgnoreCase(String workload);
}
