package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.AnalyticsResponse;

public interface AnalyticsService {
    AnalyticsResponse eventAnalytics(Long organizerId, Long eventId);
}
