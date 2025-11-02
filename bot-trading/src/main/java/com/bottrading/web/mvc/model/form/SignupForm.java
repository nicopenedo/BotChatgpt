package com.bottrading.web.mvc.model.form;

import com.bottrading.saas.model.entity.TenantPlan;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SignupForm {

  @NotBlank(message = "Ingrese un nombre para el tenant")
  private String tenantName;

  @Email(message = "Email inválido")
  @NotBlank(message = "Ingrese un email corporativo")
  private String email;

  @NotBlank(message = "Ingrese su nombre")
  private String firstName;

  @NotBlank(message = "Ingrese su apellido")
  private String lastName;

  @NotBlank(message = "Ingresá una contraseña")
  @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
  private String password;

  @NotNull(message = "Seleccione un plan")
  private TenantPlan plan = TenantPlan.STARTER;

  @AssertTrue(message = "Debe aceptar los términos para continuar")
  private boolean acceptedTerms;

  public String getTenantName() {
    return tenantName;
  }

  public void setTenantName(String tenantName) {
    this.tenantName = tenantName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public TenantPlan getPlan() {
    return plan;
  }

  public void setPlan(TenantPlan plan) {
    this.plan = plan;
  }

  public boolean isAcceptedTerms() {
    return acceptedTerms;
  }

  public void setAcceptedTerms(boolean acceptedTerms) {
    this.acceptedTerms = acceptedTerms;
  }
}
