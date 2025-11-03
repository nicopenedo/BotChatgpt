package com.bottrading.repository;

import com.bottrading.model.entity.TradeEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {
  List<TradeEntity> findByPositionId(Long positionId);

  @Query("""
    select t from TradeEntity t
    where t.tenantId = :tenantId
      and (:from is null or t.executedAt >= :from)
      and (:to is null or t.executedAt < :to)
    order by t.executedAt asc
  """)
  Stream<TradeEntity> streamByTenantAndRange(
      @Param("tenantId") UUID tenantId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
