package com.sevak.api.customer;

import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.PageResponse;
import com.sevak.api.customer.dto.BookingDetail;
import com.sevak.api.customer.dto.BookingSummary;
import com.sevak.api.customer.dto.PaymentRecord;
import com.sevak.api.customer.dto.SavedAddress;
import com.sevak.api.customer.dto.SpendingSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) {
        this.repo = repo;
    }

    public PageResponse<BookingSummary> bookings(int customerId, int page, int limit) {
        requireCustomer(customerId);
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
        requireCustomer(customerId);
        return PageResponse.of(
                repo.findPayments(customerId, limit, PageResponse.offset(page, limit)),
                page,
                limit,
                repo.countPayments(customerId));
    }

    public SpendingSummary spending(int customerId) {
        requireCustomer(customerId);
        return repo.findSpending(customerId);
    }

    public List<SavedAddress> addresses(int customerId) {
        requireCustomer(customerId);
        return repo.findAddresses(customerId);
    }

    private void requireCustomer(int customerId) {
        if (!repo.exists(customerId)) {
            throw new NotFoundException("Customer " + customerId + " not found");
        }
    }
}
