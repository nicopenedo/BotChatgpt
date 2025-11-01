package com.bottrading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/market/**")
                    .hasAnyRole("VIEWER", "READ")
                    .requestMatchers("/api/reports/**").hasRole("VIEWER")
                    .requestMatchers("/ui/**").hasRole("VIEWER")
                    .requestMatchers("/api/trade/**").hasRole("TRADE")
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(Customizer.withDefaults());
    return http.build();
  }

  @Bean
  public UserDetailsService users(PasswordEncoder passwordEncoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername("viewer")
            .password(passwordEncoder.encode("viewerPass"))
            .roles("VIEWER")
            .build(),
        User.withUsername("reader")
            .password(passwordEncoder.encode("readerPass"))
            .roles("READ")
            .build(),
        User.withUsername("trader")
            .password(passwordEncoder.encode("traderPass"))
            .roles("READ", "VIEWER", "TRADE")
            .build(),
        User.withUsername("admin")
            .password(passwordEncoder.encode("adminPass"))
            .roles("READ", "VIEWER", "TRADE", "ADMIN")
            .build());
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
