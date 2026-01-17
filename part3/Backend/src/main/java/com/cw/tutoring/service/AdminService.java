package com.cw.tutoring.service;

import com.cw.tutoring.dto.AdminUserWithProfilesDto;
import com.cw.tutoring.dto.SubjectCreateRequest;
import com.cw.tutoring.entity.StudentProfile;
import com.cw.tutoring.entity.Subject;
import com.cw.tutoring.entity.TutorProfile;
import com.cw.tutoring.entity.User;
import com.cw.tutoring.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final ReviewRepository reviewRepository;
    private final TutorSubjectRepository tutorSubjectRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;


    public AdminService(TimeSlotRepository timeSlotRepository,
                        BookingRepository bookingRepository,
                        StudentProfileRepository studentProfileRepository,
                        TutorProfileRepository tutorProfileRepository,
                        ReviewRepository reviewRepository,
                        TutorSubjectRepository tutorSubjectRepository,
                        SubjectRepository subjectRepository,
                        UserRepository userRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.reviewRepository = reviewRepository;
        this.tutorSubjectRepository = tutorSubjectRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
    }

    // Админ удаляет слот
    @Transactional
    public void deleteSlot(Integer slotId) {
        bookingRepository.deleteBySlot_Id(slotId);
        timeSlotRepository.deleteById(slotId);
    }

    @Transactional
    public SubjectCreateRequest createSubject(String name){
        Subject subject = new Subject();
        subject.setName(name);
        var sint = subjectRepository.save(subject);
        SubjectCreateRequest subjectCreateRequest = new SubjectCreateRequest();
        subjectCreateRequest.setName(sint.getName());
        subjectCreateRequest.setId(sint.getId());
        return  subjectCreateRequest;
    }
    @Transactional
    public void deleteStudentProfile(Integer studentProfileId) {
        StudentProfile profile = studentProfileRepository.findById(studentProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));


        reviewRepository.deleteByStudent_Id(studentProfileId);
        bookingRepository.deleteByStudent_Id(studentProfileId);
        studentProfileRepository.delete(profile);
    }

    @Transactional
    public void deleteTutorProfile(Integer tutorProfileId) {
        TutorProfile profile = tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow(() -> new IllegalArgumentException("Tutor profile not found"));

        reviewRepository.deleteByTutorSubject_Tutor_Id(tutorProfileId);


        tutorSubjectRepository.deleteByTutor_Id(tutorProfileId);

        timeSlotRepository.findAll().stream()
                .filter(ts -> ts.getTutor().getId().equals(tutorProfileId))
                .forEach(ts -> bookingRepository.deleteBySlot_Id(ts.getId()));

        // 4) теперь слоты
        timeSlotRepository.deleteByTutor_Id(tutorProfileId);

        // 5) сам профиль
        tutorProfileRepository.delete(profile);
    }

    @Transactional
    public Subject createSubject(SubjectCreateRequest request) {
        subjectRepository.findByName(request.getName())
                .ifPresent(s -> {
                    throw new IllegalArgumentException("Subject already exists: " + s.getName());
                });

        Subject subject = new Subject();
        subject.setName(request.getName());
        return subjectRepository.save(subject);
    }

    @Transactional(readOnly = true)
    public List<AdminUserWithProfilesDto> listUsersWithProfiles() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(user -> {
                    Integer studentProfileId = studentProfileRepository.findByUser_Id(user.getId())
                            .map(sp -> sp.getId())
                            .orElse(null);

                    Integer tutorProfileId = tutorProfileRepository.findByUser_Id(user.getId())
                            .map(tp -> tp.getId())
                            .orElse(null);

                    return new AdminUserWithProfilesDto(
                            user.getId(),
                            user.getName(),
                            user.getEmail(),
                            user.getPhone(),
                            user.getRole() != null ? user.getRole().getFullName() : null,
                            studentProfileId,
                            tutorProfileId
                    );
                })
                .toList();
    }

}
