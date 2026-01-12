package com.cw.tutoring.repository;

import com.cw.tutoring.entity.TimeSlot;
import com.cw.tutoring.repository.projection.AvailableSlotProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Integer> {

    @Query(value = "SELECT * FROM fn_get_available_slots(:from, :to)", nativeQuery = true)
    List<AvailableSlotProjection> findAvailableSlots(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    void deleteByTutor_Id(Integer tutorId);
}
