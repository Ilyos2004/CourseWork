package com.cw.tutoring.controller;

import com.cw.tutoring.dto.BookingRequest;
import com.cw.tutoring.dto.BookingResponse;
import com.cw.tutoring.entity.Booking;
import com.cw.tutoring.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@RequestBody BookingRequest request) {
        Booking booking = bookingService.createBooking(request);

        BookingResponse response = new BookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getStudent().getId(),
                booking.getStatus(),
                booking.getBookedAt()
        );

        return ResponseEntity.ok(response);
    }
}
