package com.bottrading.repository;

import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.model.enums.OrderType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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

  TradeFillEntity findTopByOrderIdOrderByExecutedAtDesc(String orderId);

  @Query("""
    select f from TradeFillEntity f
    where f.tenantId = :tenantId
      and (:from is null or f.executedAt >= :from)
      and (:to is null or f.executedAt < :to)
    order by f.executedAt asc
  """)
  Stream<TradeFillEntity> streamByTenantAndRange(
      @Param("tenantId") UUID tenantId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
