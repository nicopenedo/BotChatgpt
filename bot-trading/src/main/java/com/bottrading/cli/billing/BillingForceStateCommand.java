package com.bottrading.cli.billing;

import com.bottrading.saas.model.entity.TenantBillingEntity;
import com.bottrading.saas.model.entity.TenantBillingEntity.BillingState;
import com.bottrading.saas.model.entity.TenantStatus;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(name = "force-state", description = "Forzar el estado de billing")
public class BillingForceStateCommand implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(BillingForceStateCommand.class);

  @CommandLine.Option(names = "--tenant", required = true, description = "Tenant ID")
  private UUID tenantId;

  @CommandLine.Option(names = "--state", required = true, description = "Estado: ACTIVE,GRACE,PAST_DUE,DOWNGRADED")
  private BillingState state;

  private final TenantBillingRepository billingRepository;
  private final TenantRepository tenantRepository;

  public BillingForceStateCommand(
      TenantBillingRepository billingRepository, TenantRepository tenantRepository) {
    this.billingRepository = billingRepository;
    this.tenantRepository = tenantRepository;
  }

  @Override
  public void run() {
    TenantBillingEntity billing =
        billingRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant sin billing"));
    billing.setBillingState(state);
    billing.setUpdatedAt(Instant.now());
    billingRepository.save(billing);
    tenantRepository
        .findById(tenantId)
        .ifPresent(
            tenant -> {
              switch (state) {
                case ACTIVE -> tenant.setStatus(TenantStatus.ACTIVE);
                case GRACE -> tenant.setStatus(TenantStatus.GRACE);
                case PAST_DUE -> tenant.setStatus(TenantStatus.PAST_DUE);
                case DOWNGRADED -> tenant.setStatus(TenantStatus.DOWNGRADED);
              }
              tenant.setUpdatedAt(Instant.now());
              tenantRepository.save(tenant);
            });
    log.info("Tenant {} actualizado a {}", tenantId, state);
  }
}
