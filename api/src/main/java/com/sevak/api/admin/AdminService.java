package com.sevak.api.admin;

import com.sevak.api.admin.dto.AdminActionResult;
import com.sevak.api.admin.dto.AdminBooking;
import com.sevak.api.admin.dto.CategoryRevenue;
import com.sevak.api.admin.dto.CityRevenue;
import com.sevak.api.admin.dto.ComplaintBoardItem;
import com.sevak.api.admin.dto.ComplaintSummary;
import com.sevak.api.admin.dto.ComplaintUpdateRequest;
import com.sevak.api.admin.dto.LeaderboardEntry;
import com.sevak.api.admin.dto.RefreshResult;
import com.sevak.api.admin.dto.VerificationCandidate;
import com.sevak.api.common.ConflictException;
import com.sevak.api.common.KeysetPage;
import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.PgErrors;
import com.sevak.api.common.UnprocessableException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DataAccessException;
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
    public List<ComplaintBoardItem> complaintBoard() {
        return repo.findComplaintBoard();
    }

    @Transactional(readOnly = true)
    public List<VerificationCandidate> providersByVerification(String status) {
        return repo.findProvidersByVerification(status);
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

    @Transactional
    public AdminActionResult verifyProvider(int adminId, int providerId, String status) {
        if (repo.setProviderVerification(providerId, status) == 0) {
            throw new NotFoundException("Provider " + providerId + " not found");
        }
        repo.reviewProviderDocuments(providerId, status, adminId);
        return new AdminActionResult("Provider " + providerId + " is now " + status);
    }

    @Transactional
    public AdminActionResult updateComplaint(int complaintId, ComplaintUpdateRequest request) {
        try {
            if (repo.updateComplaint(
                            complaintId, request.status(), request.assignedAdminId(), request.resolutionNotes())
                    == 0) {
                throw new NotFoundException("Complaint " + complaintId + " not found");
            }
        } catch (DataAccessException ex) {
            if (PgErrors.FOREIGN_KEY_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new UnprocessableException("Admin " + request.assignedAdminId() + " does not exist");
            }
            throw ex;
        }
        return new AdminActionResult("Complaint " + complaintId + " is now " + request.status());
    }

    @Transactional
    public AdminActionResult processRefund(int refundId) {
        if (repo.processRefund(refundId) == 0) {
            if (repo.refundExists(refundId)) {
                throw new ConflictException("Refund " + refundId + " is not pending");
            }
            throw new NotFoundException("Refund " + refundId + " not found");
        }
        return new AdminActionResult("Refund " + refundId + " processed");
    }
}
