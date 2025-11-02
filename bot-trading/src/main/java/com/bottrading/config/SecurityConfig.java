package com.bottrading.config;

import com.bottrading.saas.security.MfaFilter;
import com.bottrading.saas.security.TenantContextFilter;
import com.bottrading.saas.security.TenantUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final TenantUserDetailsService userDetailsService;
  private final TenantContextFilter tenantContextFilter;
  private final MfaFilter mfaFilter;

  public SecurityConfig(
      TenantUserDetailsService userDetailsService,
      TenantContextFilter tenantContextFilter,
      MfaFilter mfaFilter) {
    this.userDetailsService = userDetailsService;
    this.tenantContextFilter = tenantContextFilter;
    this.mfaFilter = mfaFilter;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**", "/actuator/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/tenant/status").hasAnyRole("OWNER", "ADMIN", "VIEWER")
                    .requestMatchers(HttpMethod.POST, "/api/tenant/api-keys").hasAnyRole("OWNER", "ADMIN")
                    .requestMatchers("/api/bots/**").hasAnyRole("OWNER", "ADMIN")
                    .requestMatchers(
                        HttpMethod.GET, "/api/pnl/**", "/api/bandit/**", "/api/reports/**", "/api/audit/**")
                    .hasAnyRole("OWNER", "ADMIN", "VIEWER")
                    .requestMatchers(HttpMethod.POST, "/api/notifications/test").hasAnyRole("OWNER", "ADMIN")
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults());
    http.addFilterAfter(tenantContextFilter, BasicAuthenticationFilter.class);
    http.addFilterAfter(mfaFilter, TenantContextFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/login",
                        "/signup",
                        "/onboarding/**",
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/webjars/**",
                        "/static/**",
                        "/public/**")
                    .permitAll()
                    .requestMatchers("/tenant/**").hasAnyRole("OWNER", "ADMIN", "VIEWER")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .defaultSuccessUrl("/tenant/dashboard", true)
                    .permitAll())
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  public DaoAuthenticationProvider daoAuthenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setPasswordEncoder(passwordEncoder());
    provider.setUserDetailsService(userDetailsService);
    provider.setHideUserNotFoundExceptions(false);
    return provider;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
