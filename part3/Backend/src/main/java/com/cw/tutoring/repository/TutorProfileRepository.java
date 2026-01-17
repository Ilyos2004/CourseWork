package com.cw.tutoring.repository;

import com.cw.tutoring.entity.TutorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TutorProfileRepository extends JpaRepository<TutorProfile, Integer> {

    Optional<TutorProfile> findByUser_Id(Integer userId);
}
