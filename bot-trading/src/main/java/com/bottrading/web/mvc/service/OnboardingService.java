package com.bottrading.web.mvc.service;

import com.bottrading.web.mvc.model.form.OnboardingApiKeysForm;
import com.bottrading.web.mvc.model.form.OnboardingBotForm;
import com.bottrading.web.mvc.model.form.OnboardingProfileForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OnboardingService {

  private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);

  public void saveProfile(OnboardingProfileForm form) {
    log.info("Onboarding profile guardado para {} con plan {}", form.getCompany(), form.getPlan());
  }

  public void saveApiKeys(OnboardingApiKeysForm form) {
    log.info("API key almacenada ({}...) con secret protegido", form.getApiKey().substring(0, Math.min(4, form.getApiKey().length())));
  }

  public void createBot(OnboardingBotForm form) {
    log.info("Bot demo creado: {} {} preset {} modo {}", form.getSymbol(), form.getInterval(), form.getPreset(), form.isShadowMode());
  }
}
