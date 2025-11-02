package com.bottrading.config;

import com.bottrading.saas.security.MfaApiHeaderFilter;
import com.bottrading.saas.security.MfaUiSessionFilter;
import com.bottrading.saas.security.SanctionsFilter;
import com.bottrading.saas.security.TenantContextFilter;
import com.bottrading.saas.security.TenantUserDetails;
import com.bottrading.saas.security.TenantUserDetailsService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final TenantUserDetailsService userDetailsService;
  private final TenantContextFilter tenantContextFilter;
  private final MfaApiHeaderFilter mfaApiHeaderFilter;
  private final MfaUiSessionFilter mfaUiSessionFilter;
  private final SanctionsFilter sanctionsFilter;
  private final GrantedAuthoritiesMapper staffAuthoritiesMapper;

  public SecurityConfig(
      TenantUserDetailsService userDetailsService,
      TenantContextFilter tenantContextFilter,
      MfaApiHeaderFilter mfaApiHeaderFilter,
      MfaUiSessionFilter mfaUiSessionFilter,
      SanctionsFilter sanctionsFilter,
      GrantedAuthoritiesMapper staffAuthoritiesMapper) {
    this.userDetailsService = userDetailsService;
    this.tenantContextFilter = tenantContextFilter;
    this.mfaApiHeaderFilter = mfaApiHeaderFilter;
    this.mfaUiSessionFilter = mfaUiSessionFilter;
    this.sanctionsFilter = sanctionsFilter;
    this.staffAuthoritiesMapper = staffAuthoritiesMapper;
  }

  @Bean
  @Order(0)
  public SecurityFilterChain staffFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/staff/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("SUPPORT_READ", "OPS_ACTIONS", "FINANCE"))
        .oauth2Login(oauth -> oauth.userInfoEndpoint(info -> info.userAuthoritiesMapper(staffAuthoritiesMapper)))
        .logout(logout -> logout.logoutUrl("/staff/logout").logoutSuccessUrl("/staff/login?logout"))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS));
    return http.build();
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
    http.addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterAfter(sanctionsFilter, TenantContextFilter.class);
    http.addFilterAfter(mfaApiHeaderFilter, TenantContextFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
    CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    tokenRepository.setCookiePath("/");
    tokenRepository.setSecure(true);

    http.securityMatcher("/**")
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(tokenRepository)
                    .ignoringRequestMatchers(
                        new AntPathRequestMatcher("/webhooks/**"),
                        new AntPathRequestMatcher("/actuator/health")))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/",
                        "/login",
                        "/signup",
                        "/mfa",
                        "/onboarding/**",
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/webhooks/**",
                        "/static/**",
                        "/public/**",
                        "/blocked")
                    .permitAll()
                    .requestMatchers("/tenant/**").hasAnyRole("OWNER", "ADMIN", "VIEWER")
                    .anyRequest()
                    .denyAll())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          Object principal = authentication.getPrincipal();
                          if (principal instanceof TenantUserDetails details
                              && details.isMfaEnabled()) {
                            request.getSession(true).setAttribute("mfaVerified", Boolean.FALSE);
                            response.sendRedirect("/mfa");
                          } else {
                            response.sendRedirect("/tenant/dashboard");
                          }
                        })
                    .permitAll())
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll())
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self' 'unsafe-inline'"))
                    .frameOptions(frame -> frame.sameOrigin())
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                    .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "geolocation=()")))
        .requiresChannel(channel -> channel.anyRequest().requiresSecure())
        .httpBasic(Customizer.withDefaults());

    http.addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class);
    http.addFilterAfter(sanctionsFilter, TenantContextFilter.class);
    http.addFilterAfter(mfaUiSessionFilter, UsernamePasswordAuthenticationFilter.class);

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

  @Bean
  public GrantedAuthoritiesMapper staffAuthoritiesMapper() {
    return authorities -> {
      Set<GrantedAuthority> mapped = new HashSet<>();
      for (GrantedAuthority authority : authorities) {
        if (authority instanceof OAuth2UserAuthority oauth) {
          Object roles = oauth.getAttributes().getOrDefault("staff_roles", oauth.getAttributes().get("roles"));
          Collection<String> roleList =
              roles instanceof Collection<?> collection
                  ? collection.stream().map(Object::toString).toList()
                  : roles instanceof String str
                      ? List.of(str.split(","))
                      : List.of();
          Object mfa = oauth.getAttributes().get("mfa_verified");
          if (mfa instanceof Boolean b && !b) {
            throw new IllegalStateException("MFA requerido para personal");
          }
          if (roleList.isEmpty()) {
            mapped.add(new SimpleGrantedAuthority("ROLE_SUPPORT_READ"));
          } else {
            roleList.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .forEach(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role)));
          }
        } else {
          mapped.add(authority);
        }
      }
      return mapped;
    };
  }
}
