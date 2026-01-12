package com.cw.tutoring.repository;

import com.cw.tutoring.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Integer> {


    void deleteBySlot_Id(Integer slotId);
    void deleteByStudent_Id(Integer studentId);
}
