package com.rentle.domain.booking.service;

import com.rentle.domain.listing.model.Listing;
import com.rentle.shared.exception.RentleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Service
public class PricingService {

    public BigDecimal calculate(Listing listing, LocalDate start, LocalDate end,
                                LocalTime startTime, LocalTime endTime) {
        return switch (listing.getPriceUnit()) {
            case PER_DAY -> {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                yield listing.getPricePerUnit().multiply(BigDecimal.valueOf(days));
            }
            case PER_HOUR -> {
                if (startTime == null || endTime == null) {
                    throw new RentleException("Start and end time are required for hourly listings");
                }
                long hours = ChronoUnit.HOURS.between(startTime, endTime);
                if (hours < 1) {
                    throw new RentleException("Booking must be at least one hour");
                }
                yield listing.getPricePerUnit().multiply(BigDecimal.valueOf(hours));
            }
            case FLAT -> listing.getPricePerUnit();
        };
    }
}
