package com.sevak.api.provider;

import com.sevak.api.common.ConflictException;
import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.PageResponse;
import com.sevak.api.common.PgErrors;
import com.sevak.api.provider.dto.EarningsByMethod;
import com.sevak.api.provider.dto.FreeSlot;
import com.sevak.api.provider.dto.JobSummary;
import com.sevak.api.provider.dto.MonthlyEarnings;
import com.sevak.api.provider.dto.NewAvailabilityRequest;
import com.sevak.api.provider.dto.OfferedService;
import com.sevak.api.provider.dto.OwnAvailabilitySlot;
import com.sevak.api.provider.dto.OwnServiceRequest;
import com.sevak.api.provider.dto.ProviderOffering;
import com.sevak.api.provider.dto.ProviderProfile;
import com.sevak.api.provider.dto.ProviderReview;
import com.sevak.api.provider.dto.TopProvider;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProviderService {

    private final ProviderRepository repo;

    public ProviderService(ProviderRepository repo) {
        this.repo = repo;
    }

    public PageResponse<ProviderOffering> search(String service, String city, String day, int page, int limit) {
        String pattern = service == null ? null : "%" + escapeLike(service.trim()) + "%";
        Integer isoDay = day == null ? null : toIsoDay(day);
        return PageResponse.of(
                repo.findOfferings(pattern, city, isoDay, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countOfferings(pattern, city, isoDay));
    }

    public List<TopProvider> top(int limit) {
        return repo.findTop(limit);
    }

    public ProviderProfile profile(int providerId) {
        return repo.findBasics(providerId)
                .map(basics -> new ProviderProfile(
                        basics,
                        repo.findServices(providerId),
                        repo.findAvailability(providerId),
                        repo.findVariants(providerId)))
                .orElseThrow(() -> notFound(providerId));
    }

    public PageResponse<ProviderReview> reviews(int providerId, int page, int limit) {
        requireProvider(providerId);
        return PageResponse.of(
                repo.findReviews(providerId, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countReviews(providerId));
    }

    public PageResponse<JobSummary> jobs(int providerId, String status, int page, int limit) {
        return PageResponse.of(
                repo.findJobs(providerId, status, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countJobs(providerId, status));
    }

    public List<EarningsByMethod> earnings(int providerId, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        return repo.findEarnings(providerId, from, to);
    }

    public List<FreeSlot> freeSlots(int providerId, LocalDate from, int days, int minutes) {
        requireProvider(providerId);
        return repo.findFreeSlots(providerId, from, days, minutes);
    }

    public List<OfferedService> ownServices(int providerId) {
        return repo.findServices(providerId);
    }

    @Transactional
    public void offerService(int providerId, OwnServiceRequest request) {
        if (!repo.serviceExists(request.serviceId())) {
            throw new NotFoundException("Service " + request.serviceId() + " not found");
        }
        repo.upsertService(providerId, request.serviceId(), request.price());
    }

    @Transactional
    public void stopOffering(int providerId, int serviceId) {
        if (repo.deactivateService(providerId, serviceId) == 0) {
            throw new NotFoundException("You don't offer service " + serviceId);
        }
    }

    public List<MonthlyEarnings> monthlyEarnings(int providerId) {
        return repo.findMonthlyEarnings(providerId);
    }

    public List<OwnAvailabilitySlot> ownAvailability(int providerId) {
        return repo.findOwnAvailability(providerId);
    }

    @Transactional
    public OwnAvailabilitySlot addAvailability(int providerId, NewAvailabilityRequest request) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("The end time must be after the start time");
        }
        try {
            return repo.insertAvailability(providerId, request.dayOfWeek(), request.startTime(), request.endTime());
        } catch (DataAccessException ex) {
            if (PgErrors.EXCLUSION_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new ConflictException("slot-overlap", "These hours overlap hours you already have on that day");
            }
            throw ex;
        }
    }

    @Transactional
    public void removeAvailability(int providerId, long slotId) {
        if (repo.deleteAvailability(providerId, slotId) == 0) {
            throw new NotFoundException("Those hours were not found");
        }
    }

    private void requireProvider(int providerId) {
        if (!repo.exists(providerId)) {
            throw notFound(providerId);
        }
    }

    private static NotFoundException notFound(int providerId) {
        return new NotFoundException("Provider " + providerId + " not found");
    }

    /** Stops a user's % or _ from acting as a wildcard in ILIKE. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Spring's default String-to-enum conversion is case-sensitive, so the day is parsed here. */
    private static int toIsoDay(String day) {
        try {
            return DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)).getValue();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid day '" + day + "'. Use a day name such as Monday.");
        }
    }
}
