package com.bottrading.web.mvc.model;

public record OnboardingStepView(int order, String title, String description, boolean completed, boolean current) {}
