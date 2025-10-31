package com.bottrading.model.dto;

import java.math.BigDecimal;

public record PriceTicker(String symbol, BigDecimal price) {}
