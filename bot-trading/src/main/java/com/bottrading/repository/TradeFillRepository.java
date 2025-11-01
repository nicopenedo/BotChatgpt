package com.bottrading.repository;

import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeFillRepository extends JpaRepository<TradeFillEntity, Long> {

  @Query(
      "SELECT f FROM TradeFillEntity f "
          + "WHERE f.symbol = :symbol AND f.orderType = :type AND f.executedAt BETWEEN :from AND :to")
  List<TradeFillEntity> findBySymbolAndTypeBetween(
      @Param("symbol") String symbol,
      @Param("type") OrderType type,
      @Param("from") Instant from,
      @Param("to") Instant to);

  List<TradeFillEntity> findTop200BySymbolOrderByExecutedAtDesc(String symbol);
}
