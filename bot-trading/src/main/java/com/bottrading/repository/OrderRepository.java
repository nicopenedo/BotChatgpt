package com.bottrading.repository;

import com.bottrading.model.entity.OrderEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
  Optional<OrderEntity> findByOrderId(String orderId);

  @Query("""
    select o from OrderEntity o
    where o.tenantId = :tenantId
      and (:from is null or o.transactTime >= :from)
      and (:to is null or o.transactTime < :to)
    order by o.transactTime asc
  """)
  Stream<OrderEntity> streamByTenantAndRange(
      @Param("tenantId") UUID tenantId,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
