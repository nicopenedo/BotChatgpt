package com.bottrading.saas.security;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.model.entity.TenantUserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class TenantUserDetails implements UserDetails {

  private final UUID id;
  private final UUID tenantId;
  private final String email;
  private final String passwordHash;
  private final TenantUserRole role;
  private final boolean mfaEnabled;
  private final String mfaSecret;

  public TenantUserDetails(TenantUserEntity entity) {
    this.id = entity.getId();
    TenantEntity tenant = entity.getTenant();
    this.tenantId = tenant != null ? tenant.getId() : null;
    this.email = entity.getEmail();
    this.passwordHash = entity.getPasswordHash();
    this.role = entity.getRole();
    this.mfaEnabled = entity.isMfaEnabled();
    this.mfaSecret = entity.getMfaSecret();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public TenantUserRole getRole() {
    return role;
  }

  public boolean isMfaEnabled() {
    return mfaEnabled;
  }

  public String getMfaSecret() {
    return mfaSecret;
  }
}
