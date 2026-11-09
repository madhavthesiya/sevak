package com.sevak.api.customer;

import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.PageResponse;
import com.sevak.api.common.PgErrors;
import com.sevak.api.common.UnprocessableException;
import com.sevak.api.customer.dto.BookingDetail;
import com.sevak.api.customer.dto.BookingSummary;
import com.sevak.api.customer.dto.NewAddressRequest;
import com.sevak.api.customer.dto.PaymentRecord;
import com.sevak.api.customer.dto.SavedAddress;
import com.sevak.api.customer.dto.SpendingSummary;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A customer's own data. The customer ID always comes from the login token. */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) {
        this.repo = repo;
    }

    public PageResponse<BookingSummary> bookings(int customerId, int page, int limit) {
        return PageResponse.of(
                repo.findBookings(customerId, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countBookings(customerId));
    }

    /** Someone else's booking is reported as "not found", so its existence isn't revealed. */
    public BookingDetail booking(int customerId, int bookingId) {
        return repo.findBookingHeader(customerId, bookingId)
                .map(header ->
                        new BookingDetail(header, repo.findBookingLines(bookingId), repo.findTimeline(bookingId)))
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
    }

    public PageResponse<PaymentRecord> payments(int customerId, int page, int limit) {
        return PageResponse.of(
                repo.findPayments(customerId, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countPayments(customerId));
    }

    public SpendingSummary spending(int customerId) {
        return repo.findSpending(customerId);
    }

    public List<SavedAddress> addresses(int customerId) {
        return repo.findAddresses(customerId);
    }

    @Transactional
    public List<SavedAddress> addAddress(int customerId, NewAddressRequest request) {
        try {
            int locationId = repo.upsertLocation(request);
            repo.upsertAddress(customerId, locationId, request.label(), request.houseNo());
        } catch (DataAccessException ex) {
            if (PgErrors.FOREIGN_KEY_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new UnprocessableException("Area " + request.areaId() + " does not exist");
            }
            throw ex;
        }
        return repo.findAddresses(customerId);
    }

    /** Soft delete: bookings made at this address keep working. */
    @Transactional
    public void removeAddress(int customerId, int locationId) {
        if (repo.deactivateAddress(customerId, locationId) == 0) {
            throw new NotFoundException("Address " + locationId + " not found");
        }
    }
}
