package com.sevak.api.booking;

import com.sevak.api.auth.CurrentUser;
import com.sevak.api.booking.dto.BookingCreated;
import com.sevak.api.booking.dto.BookingItemRequest;
import com.sevak.api.booking.dto.CreateBookingRequest;
import com.sevak.api.booking.dto.IdempotencyRecord;
import com.sevak.api.booking.dto.PricedItem;
import com.sevak.api.booking.dto.RedeemedCoupon;
import com.sevak.api.common.ConflictException;
import com.sevak.api.common.PgErrors;
import com.sevak.api.common.UnprocessableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a booking in one transaction. Java coordinates the steps; the database enforces the rules
 * as a second line of defence, including the guarantee that a slot can't be double-booked.
 */
@Service
public class BookingCreationService {

    /** The result, and whether it came from an earlier request with the same idempotency key. */
    public record Outcome(BookingCreated booking, boolean replayed) {}

    private record Line(BookingItemRequest item, PricedItem price) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BookingRepository repo;

    public BookingCreationService(BookingRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Outcome create(CurrentUser customer, CreateBookingRequest request, String idempotencyKey) {
        repo.setActor(customer.userId());

        if (idempotencyKey != null) {
            String hash = sha256(request.toString());
            if (!repo.claimIdempotencyKey(customer.userId(), idempotencyKey, hash)) {
                return replay(customer, idempotencyKey, hash);
            }
        }

        int areaId = repo.findActiveAddressArea(customer.profileId(), request.locationId())
                .orElseThrow(() -> new UnprocessableException("That address is not one of your saved addresses"));
        if (!repo.providerServesArea(request.providerId(), areaId)) {
            throw new UnprocessableException("This provider does not work in that area");
        }

        repo.lockProviderBookings(request.providerId());

        List<Line> lines = priceItems(request);
        BigDecimal subtotal = BigDecimal.ZERO;
        int minutes = 0;
        for (Line line : lines) {
            subtotal = subtotal.add(line.price()
                    .unitPrice()
                    .multiply(BigDecimal.valueOf(line.item().quantity())));
            minutes += line.price().duration() * line.item().quantity();
        }

        OffsetDateTime start = request.scheduledStart();
        OffsetDateTime end = start.plusMinutes(minutes);
        if (!repo.providerAvailable(request.providerId(), start, end)) {
            throw new UnprocessableException("The provider is not available for the whole of that time");
        }

        RedeemedCoupon coupon = request.couponCode() == null ? null : redeem(request.couponCode(), subtotal);
        BigDecimal discount = coupon == null ? BigDecimal.ZERO : discountFor(coupon, subtotal);

        int bookingId = insertBooking(customer, request, coupon, start, end, subtotal, discount);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            repo.insertItem(
                    bookingId,
                    i + 1,
                    line.item().serviceId(),
                    line.item().variantId(),
                    line.item().quantity(),
                    line.price().unitPrice());
        }

        if (coupon != null && !repo.recordRedemption(coupon.couponId(), customer.profileId(), bookingId)) {
            throw new UnprocessableException("coupon-invalid", "You have already used this coupon");
        }
        if (idempotencyKey != null) {
            repo.attachBookingToKey(customer.userId(), idempotencyKey, bookingId);
        }
        return new Outcome(repo.findCreated(bookingId), false);
    }

    private Outcome replay(CurrentUser customer, String key, String hash) {
        IdempotencyRecord earlier = repo.findIdempotencyKey(customer.userId(), key);
        if (!earlier.requestHash().equals(hash)) {
            throw new ConflictException(
                    "idempotency-conflict", "This Idempotency-Key was used for a different request");
        }
        return new Outcome(repo.findCreated(earlier.bookingId()), true);
    }

    private List<Line> priceItems(CreateBookingRequest request) {
        List<Line> lines = new ArrayList<>();
        for (BookingItemRequest item : request.items()) {
            PricedItem price = repo.priceItem(request.providerId(), item.serviceId(), item.variantId())
                    .orElseThrow(() -> new UnprocessableException(
                            "This provider does not offer service " + item.serviceId() + " with that option"));
            lines.add(new Line(item, price));
        }
        return lines;
    }

    private RedeemedCoupon redeem(String code, BigDecimal subtotal) {
        return repo.redeemCoupon(code.trim(), subtotal)
                .orElseThrow(() -> new UnprocessableException(
                        "coupon-invalid", "This coupon is invalid, used up, or below its minimum order"));
    }

    private static BigDecimal discountFor(RedeemedCoupon coupon, BigDecimal subtotal) {
        BigDecimal discount = "percentage".equals(coupon.discountType())
                ? subtotal.multiply(coupon.discountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : coupon.discountValue();
        if (coupon.maxDiscount() != null) {
            discount = discount.min(coupon.maxDiscount());
        }
        return discount.min(subtotal);
    }

    private int insertBooking(
            CurrentUser customer,
            CreateBookingRequest request,
            RedeemedCoupon coupon,
            OffsetDateTime start,
            OffsetDateTime end,
            BigDecimal subtotal,
            BigDecimal discount) {
        try {
            return repo.insertBooking(
                    customer.profileId(),
                    request.providerId(),
                    request.locationId(),
                    coupon == null ? null : coupon.couponId(),
                    start,
                    end,
                    subtotal,
                    discount,
                    request.specialInstructions());
        } catch (DataAccessException ex) {
            if (PgErrors.EXCLUSION_VIOLATION.equals(PgErrors.sqlState(ex))) {
                throw new ConflictException("slot-taken", "That time slot has just been taken");
            }
            throw ex;
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
