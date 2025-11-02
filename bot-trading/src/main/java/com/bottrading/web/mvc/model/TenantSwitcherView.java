package com.bottrading.web.mvc.model;

import java.util.List;

public record TenantSwitcherView(String currentTenant, List<String> tenants) {}
