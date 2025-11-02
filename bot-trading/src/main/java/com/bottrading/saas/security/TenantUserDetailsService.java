package com.bottrading.saas.security;

import com.bottrading.saas.model.entity.TenantUserEntity;
import com.bottrading.saas.repository.TenantUserRepository;
import java.util.Optional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TenantUserDetailsService implements UserDetailsService {

  private final TenantUserRepository tenantUserRepository;

  public TenantUserDetailsService(TenantUserRepository tenantUserRepository) {
    this.tenantUserRepository = tenantUserRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    Optional<TenantUserEntity> user = tenantUserRepository.findByEmail(username);
    return user.map(TenantUserDetails::new)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }
}
