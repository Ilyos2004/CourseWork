package com.cw.tutoring.repository;

import com.cw.tutoring.entity.TutorSubject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TutorSubjectRepository extends JpaRepository<TutorSubject, Integer> {

    void deleteByTutor_Id(Integer tutorId);
}
