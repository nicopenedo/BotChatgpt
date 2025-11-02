package com.bottrading.web.mvc.model;

import java.util.List;

public record BillingView(
    String plan,
    String status,
    String liveUsage,
    String storageUsage,
    List<InvoiceView> invoices) {}
