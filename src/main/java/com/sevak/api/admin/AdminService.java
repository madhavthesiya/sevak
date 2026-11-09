package com.sevak.api.admin;

import com.sevak.api.admin.dto.AdminBooking;
import com.sevak.api.admin.dto.CategoryRevenue;
import com.sevak.api.admin.dto.CityRevenue;
import com.sevak.api.admin.dto.ComplaintSummary;
import com.sevak.api.admin.dto.LeaderboardEntry;
import com.sevak.api.admin.dto.RefreshResult;
import com.sevak.api.common.KeysetPage;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final AdminRepository repo;

    public AdminService(AdminRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CategoryRevenue> revenueByCategory() {
        return repo.findRevenueByCategory();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(boolean fast) {
        return fast ? repo.findLeaderboardFromView() : repo.findLeaderboard();
    }

    @Transactional(readOnly = true)
    public List<ComplaintSummary> complaintSummary() {
        return repo.findComplaintSummary();
    }

    @Transactional(readOnly = true)
    public List<CityRevenue> cityRevenue() {
        return repo.findCityRevenue();
    }

    @Transactional(readOnly = true)
    public KeysetPage<AdminBooking> bookings(OffsetDateTime before, Integer beforeId, int limit) {
        if ((before == null) != (beforeId == null)) {
            throw new IllegalArgumentException("Send both 'before' and 'beforeId', or neither");
        }
        // One extra row tells us whether another page exists.
        List<AdminBooking> rows = before == null
                ? repo.findBookingsFirstPage(limit + 1)
                : repo.findBookingsBefore(before, beforeId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<AdminBooking> page = hasMore ? rows.subList(0, limit) : rows;
        AdminBooking last = hasMore ? page.get(page.size() - 1) : null;
        return new KeysetPage<>(
                page, hasMore, last == null ? null : last.scheduledStart(), last == null ? null : last.bookingId());
    }

    public RefreshResult refreshViews() {
        boolean refreshed = repo.refreshViewsIfNotRunning();
        return new RefreshResult(
                refreshed, refreshed ? "Materialized views refreshed" : "A refresh is already running");
    }
}
