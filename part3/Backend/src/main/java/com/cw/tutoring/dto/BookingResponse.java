package com.cw.tutoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
public class BookingResponse {
    private Integer id;
    private Integer slotId;
    private Integer studentProfileId;
    private String status;
    private OffsetDateTime bookedAt;
}
