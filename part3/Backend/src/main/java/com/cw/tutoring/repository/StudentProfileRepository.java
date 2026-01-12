package com.cw.tutoring.repository;

import com.cw.tutoring.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Integer> {

    Optional<StudentProfile> findByUser_Id(Integer userId);
}
