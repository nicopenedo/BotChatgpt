package com.bottrading.web.mvc.model.form;

import com.bottrading.saas.model.entity.TenantPlan;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OnboardingProfileForm {

  @NotBlank(message = "Ingresá el nombre de la compañía")
  private String company;

  @NotNull(message = "Seleccioná un plan")
  private TenantPlan plan = TenantPlan.STARTER;

  @AssertTrue(message = "Necesitamos tu aceptación de riesgos")
  private boolean acceptedRisk;

  public String getCompany() {
    return company;
  }

  public void setCompany(String company) {
    this.company = company;
  }

  public TenantPlan getPlan() {
    return plan;
  }

  public void setPlan(TenantPlan plan) {
    this.plan = plan;
  }

  public boolean isAcceptedRisk() {
    return acceptedRisk;
  }

  public void setAcceptedRisk(boolean acceptedRisk) {
    this.acceptedRisk = acceptedRisk;
  }
}
