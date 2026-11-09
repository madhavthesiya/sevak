package com.sevak.api.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Keeps the materialized views fresh without anyone having to call the refresh endpoint. */
@Configuration
@EnableScheduling
public class ViewRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(ViewRefreshJob.class);

    private final AdminService service;

    public ViewRefreshJob(AdminService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${sevak.views.refresh-interval}",
            initialDelayString = "${sevak.views.refresh-interval}")
    public void refresh() {
        log.info("Scheduled view refresh: {}", service.refreshViews().message());
    }
}
