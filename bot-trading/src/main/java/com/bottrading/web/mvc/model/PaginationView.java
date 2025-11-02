package com.bottrading.web.mvc.model;

public record PaginationView(int number, int totalPages, boolean hasPrevious, boolean hasNext, String prevUrl, String nextUrl) {}
