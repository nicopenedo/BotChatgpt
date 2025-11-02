package com.bottrading.saas.service;

import com.bottrading.saas.model.dto.TenantStatusResponse;
import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantSettingsEntity;
import com.bottrading.saas.repository.TenantRepository;
import com.bottrading.saas.repository.TenantSettingsRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantStatusService {

  private final TenantRepository tenantRepository;
  private final TenantSettingsRepository tenantSettingsRepository;

  public TenantStatusService(
      TenantRepository tenantRepository, TenantSettingsRepository tenantSettingsRepository) {
    this.tenantRepository = tenantRepository;
    this.tenantSettingsRepository = tenantSettingsRepository;
  }

  public Optional<TenantStatusResponse> getStatus(UUID tenantId) {
    return tenantRepository
        .findById(tenantId)
        .map(
            tenant -> {
              TenantStatusResponse response = new TenantStatusResponse();
              response.setPlan(tenant.getPlan().name());
              response.setStatus(tenant.getStatus().name());
              response.setCreatedAt(tenant.getCreatedAt());
              response.setKpis(Map.of("pnlDaily", 0, "maxDd", 0, "var", 0));
              response.setFeatureFlags(Map.of());
              Optional<TenantSettingsEntity> settings = tenantSettingsRepository.findById(tenantId);
              settings.ifPresent(
                  s ->
                      response.setFeatureFlags(
                          Map.of(
                              "bandit",
                              s.getBanditJson() != null && !"{}".equals(s.getBanditJson()),
                              "tca",
                              s.getExecJson() != null && !"{}".equals(s.getExecJson()))));
              return response;
            });
  }
}
