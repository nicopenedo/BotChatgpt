package com.bottrading.cli.security;

import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.repository.TenantUserRepository;
import com.bottrading.saas.service.TotpService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

@Component
@CommandLine.Command(
    name = "migrate-totp-secrets",
    mixinStandardHelpOptions = true,
    description = "Normaliza secretos TOTP guardados en Base32")
public class MigrateTotpSecretsCommand implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(MigrateTotpSecretsCommand.class);

  private final TenantUserRepository tenantUserRepository;
  private final TotpService totpService;

  public MigrateTotpSecretsCommand(
      TenantUserRepository tenantUserRepository, TotpService totpService) {
    this.tenantUserRepository = tenantUserRepository;
    this.totpService = totpService;
  }

  @Override
  @Transactional
  public void run() {
    List<TenantUserEntity> users = tenantUserRepository.findAll();
    int scanned = 0;
    int updated = 0;
    for (TenantUserEntity user : users) {
      String secret = user.getMfaSecret();
      if (secret == null || secret.isBlank()) {
        continue;
      }
      SecretKey key = totpService.fromBase32(secret);
      if (key == null) {
        continue;
      }
      scanned++;
      String canonical = totpService.toBase32(key);
      if (!canonical.equals(secret)) {
        user.setMfaSecret(canonical);
        user.setUpdatedAt(Instant.now());
        tenantUserRepository.save(user);
        updated++;
      }
    }
    log.info("TOTP secrets scanned: {} updated: {}", scanned, updated);
  }
}
