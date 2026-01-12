package com.cw.tutoring.repository;

import com.cw.tutoring.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByFullName(String fullName);
}
