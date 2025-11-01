package com.bottrading.bandit;

import com.bottrading.model.enums.OrderSide;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BanditArmRepository extends JpaRepository<BanditArmEntity, UUID> {
  List<BanditArmEntity> findBySymbolAndRegimeAndSide(String symbol, String regime, OrderSide side);

  Optional<BanditArmEntity> findBySymbolAndRegimeAndSideAndPresetId(
      String symbol, String regime, OrderSide side, UUID presetId);
}
