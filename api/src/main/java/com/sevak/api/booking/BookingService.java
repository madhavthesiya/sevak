package com.sevak.api.booking;

import com.sevak.api.auth.CurrentUser;
import com.sevak.api.booking.dto.BookingActionResult;
import com.sevak.api.booking.dto.BookingParties;
import com.sevak.api.booking.dto.ComplaintCreated;
import com.sevak.api.booking.dto.ComplaintRequest;
import com.sevak.api.booking.dto.PaymentRequest;
import com.sevak.api.booking.dto.PaymentResult;
import com.sevak.api.booking.dto.ReviewRequest;
import com.sevak.api.booking.dto.ReviewResult;
import com.sevak.api.booking.dto.ServiceReviewRequest;
import com.sevak.api.common.ConflictException;
import com.sevak.api.common.NotFoundException;
import com.sevak.api.common.PgErrors;
import com.sevak.api.common.UnprocessableException;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything that happens to a booking after it is created. */
@Service
public class BookingService {

    private static final Duration CUSTOMER_CANCEL_CUTOFF = Duration.ofHours(2);

    private final BookingRepository repo;

    public BookingService(BookingRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public BookingActionResult cancel(CurrentUser user, int bookingId, String reason) {
        BookingParties booking = requireParty(user, bookingId);
        if ("pending".equals(booking.status()) || "confirmed".equals(booking.status())) {
            OffsetDateTime now = OffsetDateTime.now();
            if (user.isCustomer() && now.plus(CUSTOMER_CANCEL_CUTOFF).isAfter(booking.scheduledStart())) {
                throw new UnprocessableException("Bookings can't be cancelled within 2 hours of the start");
            }
            if (user.isProvider() && now.isAfter(booking.scheduledStart())) {
                throw new UnprocessableException("A booking can't be cancelled after its start time");
            }
        }
        return cancelAs(user, bookingId, reason, "cancelled");
    }

    @Transactional
    public BookingActionResult decline(CurrentUser provider, int bookingId) {
        BookingParties booking = requireProvider(provider, bookingId);
        if (!"pending".equals(booking.status())) {
            throw new ConflictException("Only a pending booking can be declined");
        }
        return cancelAs(provider, bookingId, "Declined by the provider", "declined");
    }

    private BookingActionResult cancelAs(CurrentUser user, int bookingId, String reason, String verb) {
        repo.setActor(user.userId());
        try {
            repo.cancel(bookingId, reason, user.userId());
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return new BookingActionResult(bookingId, "cancelled", "Booking " + bookingId + " " + verb);
    }

    @Transactional
    public BookingActionResult accept(CurrentUser provider, int bookingId) {
        requireProvider(provider, bookingId);
        repo.setActor(provider.userId());
        try {
            repo.confirm(bookingId);
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return new BookingActionResult(bookingId, "confirmed", "Booking " + bookingId + " confirmed");
    }

    @Transactional
    public BookingActionResult start(CurrentUser provider, int bookingId) {
        return moveTo(provider, bookingId, "in_progress", "started");
    }

    @Transactional
    public BookingActionResult complete(CurrentUser provider, int bookingId) {
        return moveTo(provider, bookingId, "completed", "completed");
    }

    private BookingActionResult moveTo(CurrentUser provider, int bookingId, String status, String verb) {
        requireProvider(provider, bookingId);
        repo.setActor(provider.userId());
        try {
            repo.setStatus(bookingId, status);
        } catch (DataAccessException ex) {
            throw translate(ex, bookingId);
        }
        return new BookingActionResult(bookingId, status, "Booking " + bookingId + " " + verb);
    }

    /** A mock gateway: "simulate" chooses the outcome. The database allows one success per booking. */
    @Transactional
    public PaymentResult pay(CurrentUser customer, int bookingId, PaymentRequest request) {
        BookingParties booking = requireCustomer(customer, bookingId);
        if ("cancelled".equals(booking.status())) {
            throw new ConflictException("A cancelled booking can't be paid");
        }
        boolean succeeds = !"failure".equals(request.simulate());
        try {
            int paymentId = repo.insertPayment(
                    bookingId,
                    request.paymentMethod(),
                    succeeds ? "success" : "failed",
                    booking.totalAmount(),
                    succeeds ? null : "Simulated gateway failure");
            return new PaymentResult(
                    paymentId, succeeds ? "success" : "failed", booking.totalAmount(), repo.findGatewayRef(paymentId));
        } catch (DataAccessException ex) {
            if (PgErrors.UNIQUE_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new ConflictException("This booking is already paid");
            }
            throw ex;
        }
    }

    @Transactional
    public ReviewResult review(CurrentUser customer, int bookingId, ReviewRequest request) {
        requireCustomer(customer, bookingId);
        try {
            repo.insertProviderReview(bookingId, request.rating(), request.comment());
            if (request.serviceReviews() != null) {
                for (ServiceReviewRequest review : request.serviceReviews()) {
                    repo.insertServiceReview(bookingId, review.serviceId(), review.rating(), review.comment());
                }
            }
        } catch (DataAccessException ex) {
            if (PgErrors.UNIQUE_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new ConflictException("This booking has already been reviewed");
            }
            throw translate(ex, bookingId);
        }
        return new ReviewResult(bookingId, "Thanks for your review");
    }

    @Transactional
    public ComplaintCreated complain(CurrentUser user, int bookingId, ComplaintRequest request) {
        requireParty(user, bookingId);
        int id = repo.insertComplaint(bookingId, request.subject(), request.description(), user.userId());
        return new ComplaintCreated(id, "open");
    }

    /** Runs on a schedule. Returns how many bookings expired. */
    @Transactional
    public int expirePendingBookings() {
        return repo.expirePendingBookings();
    }

    // Someone else's booking is reported as "not found", so its existence isn't revealed.

    private BookingParties requireParty(CurrentUser user, int bookingId) {
        return user.isCustomer() ? requireCustomer(user, bookingId) : requireProvider(user, bookingId);
    }

    private BookingParties requireCustomer(CurrentUser customer, int bookingId) {
        return repo.findParties(bookingId)
                .filter(booking -> booking.customerId() == customer.profileId())
                .orElseThrow(() -> notFound(bookingId));
    }

    private BookingParties requireProvider(CurrentUser provider, int bookingId) {
        return repo.findParties(bookingId)
                .filter(booking -> booking.providerId() == provider.profileId())
                .orElseThrow(() -> notFound(bookingId));
    }

    private static NotFoundException notFound(int bookingId) {
        return new NotFoundException("Booking " + bookingId + " not found");
    }

    /**
     * Maps the custom SQLSTATE codes raised by our procedures and triggers to API exceptions.
     * SV404 = not found, SV409 = wrong status, SV422 = business rule broken. Any other database
     * error is re-thrown and becomes a generic 500, with details only in the logs.
     */
    private static RuntimeException translate(DataAccessException ex, int bookingId) {
        String state = PgErrors.sqlState(ex);
        if (state == null) {
            return ex;
        }
        return switch (state) {
            case "SV404" -> notFound(bookingId);
            case "SV409" -> new ConflictException(PgErrors.procedureMessage(ex));
            case "SV422" -> new UnprocessableException(PgErrors.procedureMessage(ex));
            default -> ex;
        };
    }
}
