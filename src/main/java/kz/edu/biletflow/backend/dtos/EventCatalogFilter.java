package kz.edu.biletflow.backend.dtos;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

// Query parameters of GET /api/events; every one is optional and they combine with AND.
public record EventCatalogFilter(
        @Nullable String q,             // part of the title, category, venue name or venue address (case-insensitive)
        @Nullable String category,      // exact category, case-insensitive
        @Nullable Long venueId,
        @Nullable LocalDate from,       // events starting on or after this day (Asia/Almaty)
        @Nullable LocalDate to,         // events starting on or before this day, inclusive
        @Nullable BigDecimal priceMin,  // has a visible ticket type priced at least this
        @Nullable BigDecimal priceMax   // ... and at most this (0 = free events)
) {}
