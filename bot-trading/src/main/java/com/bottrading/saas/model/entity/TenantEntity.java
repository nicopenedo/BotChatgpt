package com.bottrading.saas.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class TenantEntity {

  @Id @GeneratedValue private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "email_owner", nullable = false)
  private String emailOwner;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private TenantPlan plan;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private TenantStatus status = TenantStatus.PENDING;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deletion_requested_at")
  private Instant deletionRequestedAt;

  @Column(name = "purge_after")
  private Instant purgeAfter;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "billing_country")
  private String billingCountry;

  @PrePersist
  public void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmailOwner() {
    return emailOwner;
  }

  public void setEmailOwner(String emailOwner) {
    this.emailOwner = emailOwner;
  }

  public TenantPlan getPlan() {
    return plan;
  }

  public void setPlan(TenantPlan plan) {
    this.plan = plan;
  }

  public TenantStatus getStatus() {
    return status;
  }

  public void setStatus(TenantStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getDeletionRequestedAt() {
    return deletionRequestedAt;
  }

  public void setDeletionRequestedAt(Instant deletionRequestedAt) {
    this.deletionRequestedAt = deletionRequestedAt;
  }

  public Instant getPurgeAfter() {
    return purgeAfter;
  }

  public void setPurgeAfter(Instant purgeAfter) {
    this.purgeAfter = purgeAfter;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public String getBillingCountry() {
    return billingCountry;
  }

  public void setBillingCountry(String billingCountry) {
    this.billingCountry = billingCountry;
  }
}
