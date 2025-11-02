package com.bottrading.web.mvc.controller;

import com.bottrading.web.mvc.model.OnboardingStepView;
import com.bottrading.web.mvc.model.form.OnboardingApiKeysForm;
import com.bottrading.web.mvc.model.form.OnboardingBotForm;
import com.bottrading.web.mvc.model.form.OnboardingProfileForm;
import com.bottrading.web.mvc.service.OnboardingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;

@Controller
@RequestMapping("/onboarding")
@SessionAttributes({"profileForm", "apiKeysForm", "botForm"})
public class OnboardingController {

  private final OnboardingService onboardingService;

  public OnboardingController(OnboardingService onboardingService) {
    this.onboardingService = onboardingService;
  }

  @ModelAttribute("profileForm")
  public OnboardingProfileForm profileForm() {
    return new OnboardingProfileForm();
  }

  @ModelAttribute("apiKeysForm")
  public OnboardingApiKeysForm apiKeysForm() {
    return new OnboardingApiKeysForm();
  }

  @ModelAttribute("botForm")
  public OnboardingBotForm botForm() {
    return new OnboardingBotForm();
  }

  @GetMapping
  public String onboarding(@RequestParam(name = "step", defaultValue = "1") int step, Model model) {
    model.addAttribute("currentStep", step);
    model.addAttribute("steps", steps(step));
    return "onboarding/index";
  }

  @PostMapping("/profile")
  public String handleProfile(
      @Valid @ModelAttribute("profileForm") OnboardingProfileForm profileForm,
      BindingResult bindingResult,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("currentStep", 1);
      model.addAttribute("steps", steps(1));
      return "onboarding/index";
    }
    onboardingService.saveProfile(profileForm);
    return "redirect:/onboarding?step=2";
  }

  @PostMapping("/api-keys")
  public String handleApiKeys(
      @Valid @ModelAttribute("apiKeysForm") OnboardingApiKeysForm apiKeysForm,
      BindingResult bindingResult,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("currentStep", 2);
      model.addAttribute("steps", steps(2));
      return "onboarding/index";
    }
    onboardingService.saveApiKeys(apiKeysForm);
    return "redirect:/onboarding?step=3";
  }

  @PostMapping("/bot")
  public String handleBot(
      @Valid @ModelAttribute("botForm") OnboardingBotForm botForm,
      BindingResult bindingResult,
      Model model,
      SessionStatus sessionStatus) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("currentStep", 3);
      model.addAttribute("steps", steps(3));
      return "onboarding/index";
    }
    onboardingService.createBot(botForm);
    sessionStatus.setComplete();
    return "redirect:/tenant/dashboard";
  }

  private List<OnboardingStepView> steps(int currentStep) {
    return List.of(
        new OnboardingStepView(1, "Perfil", "Configuración inicial", currentStep > 1, currentStep == 1),
        new OnboardingStepView(2, "API keys", "Conectá Binance testnet", currentStep > 2, currentStep == 2),
        new OnboardingStepView(3, "Primer bot", "Creá tu estrategia shadow", currentStep > 3, currentStep == 3));
  }
}
