package com.bottrading.repository;

import com.bottrading.model.entity.DecisionEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DecisionRepository extends JpaRepository<DecisionEntity, String> {
  Optional<DecisionEntity> findTopBySymbolOrderByDecidedAtDesc(String symbol);

  Optional<DecisionEntity> findByDecisionKey(String decisionKey);

  long countByDecidedAtAfter(Instant timestamp);
}
