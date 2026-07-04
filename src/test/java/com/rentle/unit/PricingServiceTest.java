package com.rentle.unit;

import com.rentle.domain.booking.service.PricingService;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.PriceUnit;
import com.rentle.shared.exception.RentleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    private Listing listing(PriceUnit unit, String price) {
        Listing l = new Listing();
        l.setPriceUnit(unit);
        l.setPricePerUnit(new BigDecimal(price));
        return l;
    }

    @Test
    void perDayIsInclusiveOfBothEndpoints() {
        // 3 calendar days: 10th, 11th, 12th
        BigDecimal price = pricingService.calculate(
                listing(PriceUnit.PER_DAY, "1000.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), null, null);
        assertEquals(new BigDecimal("3000.00"), price);
    }

    @Test
    void perDaySingleDayChargesOneDay() {
        BigDecimal price = pricingService.calculate(
                listing(PriceUnit.PER_DAY, "1500.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), null, null);
        assertEquals(new BigDecimal("1500.00"), price);
    }

    @Test
    void perHourMultipliesWholeHours() {
        BigDecimal price = pricingService.calculate(
                listing(PriceUnit.PER_HOUR, "500.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                LocalTime.of(10, 0), LocalTime.of(14, 0));
        assertEquals(new BigDecimal("2000.00"), price);
    }

    @Test
    void perHourRequiresTimes() {
        assertThrows(RentleException.class, () -> pricingService.calculate(
                listing(PriceUnit.PER_HOUR, "500.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10), null, null));
    }

    @Test
    void perHourRejectsSubHourBookings() {
        assertThrows(RentleException.class, () -> pricingService.calculate(
                listing(PriceUnit.PER_HOUR, "500.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                LocalTime.of(10, 0), LocalTime.of(10, 30)));
    }

    @Test
    void flatIgnoresDuration() {
        BigDecimal price = pricingService.calculate(
                listing(PriceUnit.FLAT, "8000.00"),
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20), null, null);
        assertEquals(new BigDecimal("8000.00"), price);
    }
}
