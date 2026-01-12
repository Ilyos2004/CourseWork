package com.cw.tutoring.controller;

import com.cw.tutoring.entity.TimeSlot;
import com.cw.tutoring.repository.projection.AvailableSlotProjection;
import com.cw.tutoring.service.SlotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping("/available")
    public ResponseEntity<List<AvailableSlotProjection>> getAvailableSlots(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(slotService.getAvailableSlots(from, to));
    }

    @PostMapping
    public ResponseEntity<TimeSlot> createSlot(
            @RequestParam Integer tutorProfileId,
            @RequestParam Integer subjectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @RequestParam(defaultValue = "1") Integer capacity,
            @RequestParam(defaultValue = "published") String status
    ) {
        TimeSlot slot = slotService.createSlot(tutorProfileId, subjectId, start, end, capacity, status);
        return ResponseEntity.ok(slot);
    }
}
