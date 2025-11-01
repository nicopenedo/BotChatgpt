package com.bottrading.repository;

import com.bottrading.model.entity.TradeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {
  List<TradeEntity> findByPositionId(Long positionId);
}
