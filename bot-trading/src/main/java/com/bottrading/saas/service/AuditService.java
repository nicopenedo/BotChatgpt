package com.bottrading.saas.service;

import com.bottrading.saas.model.entity.AuditEventEntity;
import com.bottrading.saas.repository.AuditEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private final AuditEventRepository repository;
  private final ObjectMapper objectMapper;

  public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void record(UUID tenantId, UUID userId, String type, Map<String, Object> payload) {
    try {
      AuditEventEntity entity = new AuditEventEntity();
      entity.setTenantId(tenantId);
      entity.setUserId(userId);
      entity.setType(type);
      entity.setTimestamp(Instant.now());
      entity.setPayloadJson(objectMapper.writeValueAsString(payload));
      repository.save(entity);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize audit payload", e);
      throw new IllegalStateException("Unable to serialize audit payload", e);
    }
  }
}
