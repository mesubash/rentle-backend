package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BookingPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                new PermissionDefinition(PermissionKeys.BOOKING_BOOKING_READ, "booking", "booking", "read", "View all bookings")
        );
    }
}
