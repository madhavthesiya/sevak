package com.sevak.api.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Frees the slots held by requests that providers never answered. */
@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService service;

    public BookingExpiryJob(BookingService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${sevak.bookings.expiry-interval}",
            initialDelayString = "${sevak.bookings.expiry-interval}")
    public void expire() {
        int expired = service.expirePendingBookings();
        if (expired > 0) {
            log.info("Expired {} unanswered booking requests", expired);
        }
    }
}
