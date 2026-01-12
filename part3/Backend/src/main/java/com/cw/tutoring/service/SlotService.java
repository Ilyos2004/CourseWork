package com.cw.tutoring.service;

import com.cw.tutoring.entity.Subject;
import com.cw.tutoring.entity.TimeSlot;
import com.cw.tutoring.entity.TutorProfile;
import com.cw.tutoring.repository.SubjectRepository;
import com.cw.tutoring.repository.TimeSlotRepository;
import com.cw.tutoring.repository.TutorProfileRepository;
import com.cw.tutoring.repository.projection.AvailableSlotProjection;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final SubjectRepository subjectRepository;

    public SlotService(TimeSlotRepository timeSlotRepository,
                       TutorProfileRepository tutorProfileRepository,
                       SubjectRepository subjectRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.subjectRepository = subjectRepository;
    }

    public List<AvailableSlotProjection> getAvailableSlots(OffsetDateTime from, OffsetDateTime to) {
        return timeSlotRepository.findAvailableSlots(from, to);
    }

    public TimeSlot createSlot(Integer tutorProfileId,
                               Integer subjectId,
                               OffsetDateTime start,
                               OffsetDateTime end,
                               Integer capacity,
                               String status) {
        TutorProfile tutor = tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Tutor profile not found"));

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        TimeSlot slot = new TimeSlot();
        slot.setTutor(tutor);
        slot.setSubject(subject);
        slot.setStartDt(start);
        slot.setEndDt(end);
        slot.setCapacity(capacity);
        slot.setStatus(status);

        return timeSlotRepository.save(slot);
    }
}
