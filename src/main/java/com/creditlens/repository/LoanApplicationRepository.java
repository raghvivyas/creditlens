package com.creditlens.repository;

import com.creditlens.entity.LoanApplicationEntity;
import com.creditlens.model.Decision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplicationEntity, Long> {

    Page<LoanApplicationEntity> findAllByOrderByAssessedAtDesc(Pageable pageable);

    Page<LoanApplicationEntity> findByDecisionOrderByAssessedAtDesc(Decision decision, Pageable pageable);

    @Query("SELECT a FROM LoanApplicationEntity a WHERE " +
           "(:decision IS NULL OR a.decision = :decision) AND " +
           "(:from IS NULL OR a.assessedAt >= :from) AND " +
           "(:to IS NULL OR a.assessedAt <= :to) " +
           "ORDER BY a.assessedAt DESC")
    Page<LoanApplicationEntity> findWithFilters(
            @Param("decision") Decision decision,
            @Param("from")     Instant from,
            @Param("to")       Instant to,
            Pageable pageable);

    long countByDecision(Decision decision);

    @Query("SELECT AVG(a.riskScore) FROM LoanApplicationEntity a")
    BigDecimal findAvgRiskScore();

    @Query("SELECT AVG(a.loanAmount) FROM LoanApplicationEntity a")
    BigDecimal findAvgLoanAmount();

    /** Returns the most common flag description among rejected applications */
    @Query(value =
        "SELECT flag, COUNT(*) AS cnt FROM (" +
        "  SELECT TRIM(unnest(string_to_array(flag_descriptions, ','))) AS flag " +
        "  FROM loan_applications WHERE decision = 'REJECTED'" +
        ") t WHERE flag <> '' GROUP BY flag ORDER BY cnt DESC LIMIT 1",
        nativeQuery = true)
    List<Object[]> findTopRejectionFlag();

    List<LoanApplicationEntity> findByApplicantNameContainingIgnoreCase(String name);
}
