package com.rentle.domain.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Column(length = 60)
    private String district;

    @Column(length = 80)
    private String municipality;

    private Integer ward;

    @Column(length = 120)
    private String tole;

    public Address(String district, String municipality, Integer ward, String tole) {
        this.district = district;
        this.municipality = municipality;
        this.ward = ward;
        this.tole = tole;
    }
}
