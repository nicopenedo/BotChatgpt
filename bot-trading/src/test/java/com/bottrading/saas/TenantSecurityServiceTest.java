package com.bottrading.saas;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bottrading.saas.config.SaasProperties;
import com.bottrading.saas.model.dto.ApiKeyRequest;
import com.bottrading.saas.model.entity.TenantApiKeyEntity;
import com.bottrading.saas.repository.TenantApiKeyRepository;
import com.bottrading.saas.service.AuditService;
import com.bottrading.saas.service.SecretEncryptionService;
import com.bottrading.saas.service.TenantSecurityService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantSecurityServiceTest {

  @Mock private TenantApiKeyRepository repository;
  @Mock private SecretEncryptionService encryptionService;
  @Mock private AuditService auditService;

  private TenantSecurityService service;
  private SaasProperties properties;

  @BeforeEach
  void setup() {
    properties = new SaasProperties();
    service = new TenantSecurityService(properties, repository, encryptionService, auditService);
    when(encryptionService.encrypt(any())).thenReturn(new byte[] {1, 2, 3});
  }

  @Test
  void rejectsWithdrawWhenEnforced() {
    ApiKeyRequest request = new ApiKeyRequest();
    request.setExchange("binance");
    request.setApiKey("k");
    request.setSecret("s");
    request.setCanWithdraw(true);
    request.setIpWhitelist(List.of("1.1.1.1"));

    assertThatThrownBy(() -> service.storeApiKey(UUID.randomUUID(), UUID.randomUUID(), request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("withdrawal");
  }

  @Test
  void rejectsMissingWhitelistWhenRequired() {
    ApiKeyRequest request = new ApiKeyRequest();
    request.setExchange("binance");
    request.setApiKey("k");
    request.setSecret("s");
    request.setCanWithdraw(false);
    request.setIpWhitelist(List.of());

    assertThatThrownBy(() -> service.storeApiKey(UUID.randomUUID(), UUID.randomUUID(), request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("allowlist");
  }

  @Test
  void storesKeyWhenValid() {
    ApiKeyRequest request = new ApiKeyRequest();
    request.setExchange("binance");
    request.setApiKey("k");
    request.setSecret("s");
    request.setCanWithdraw(false);
    request.setIpWhitelist(List.of("1.1.1.1"));

    when(repository.findByTenantIdAndExchange(any(), any())).thenReturn(Optional.empty());

    TenantApiKeyEntity saved = new TenantApiKeyEntity();
    when(repository.save(any())).thenReturn(saved);

    TenantApiKeyEntity result =
        service.storeApiKey(UUID.randomUUID(), UUID.randomUUID(), request);

    assertThat(result).isNotNull();
  }
}
