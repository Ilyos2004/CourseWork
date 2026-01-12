package com.cw.tutoring.service;

import com.cw.tutoring.dto.BookingRequest;
import com.cw.tutoring.entity.Booking;
import com.cw.tutoring.entity.StudentProfile;
import com.cw.tutoring.entity.TimeSlot;
import com.cw.tutoring.repository.BookingRepository;
import com.cw.tutoring.repository.StudentProfileRepository;
import com.cw.tutoring.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final StudentProfileRepository studentProfileRepository;

    public BookingService(BookingRepository bookingRepository,
                          TimeSlotRepository timeSlotRepository,
                          StudentProfileRepository studentProfileRepository) {
        this.bookingRepository = bookingRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    public Booking createBooking(BookingRequest request) {
        TimeSlot slot = timeSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        StudentProfile student = studentProfileRepository.findById(request.getStudentProfileId())
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));

        Booking booking = new Booking();
        booking.setSlot(slot);
        booking.setStudent(student);
        booking.setBookedAt(OffsetDateTime.now());
        booking.setStatus("booked");

        return bookingRepository.save(booking);
    }
}
