package com.bottrading.bandit;

import com.bottrading.model.enums.OrderSide;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BanditPullRepository extends JpaRepository<BanditPullEntity, Long> {
  Optional<BanditPullEntity> findByDecisionId(String decisionId);

  List<BanditPullEntity> findBySymbolAndRegimeAndSideOrderByTimestampDesc(
      String symbol, String regime, OrderSide side, Pageable pageable);

  long countBySymbolAndTimestampBetween(String symbol, Instant from, Instant to);

  long countBySymbolAndRoleAndTimestampBetween(
      String symbol, BanditArmRole role, Instant from, Instant to);
}
