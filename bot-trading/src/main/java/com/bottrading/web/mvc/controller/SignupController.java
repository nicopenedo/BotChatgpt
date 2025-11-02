package com.bottrading.web.mvc.controller;

import com.bottrading.web.mvc.model.form.SignupForm;
import com.bottrading.web.mvc.service.TenantSignupService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SignupController {

  private final TenantSignupService signupService;

  public SignupController(TenantSignupService signupService) {
    this.signupService = signupService;
  }

  @GetMapping("/signup")
  public String signup(Model model) {
    if (!model.containsAttribute("signupForm")) {
      model.addAttribute("signupForm", new SignupForm());
    }
    return "signup";
  }

  @PostMapping("/signup")
  public String handleSignup(
      @Valid @ModelAttribute("signupForm") SignupForm signupForm, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
      return "signup";
    }
    signupService.registerTenant(signupForm);
    return "redirect:/login?ok";
  }
}
