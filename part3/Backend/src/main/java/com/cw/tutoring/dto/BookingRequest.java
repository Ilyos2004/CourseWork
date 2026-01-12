package com.cw.tutoring.dto;

import lombok.Data;

@Data
public class BookingRequest {
    private Integer slotId;
    private Integer studentProfileId;
}
