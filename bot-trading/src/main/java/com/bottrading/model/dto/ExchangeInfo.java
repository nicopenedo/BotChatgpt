package com.bottrading.model.dto;

import java.math.BigDecimal;

public record ExchangeInfo(BigDecimal tickSize, BigDecimal stepSize, BigDecimal minNotional) {}
