package com.cw.tutoring.service;

import com.cw.tutoring.dto.RegisterStudentRequest;
import com.cw.tutoring.dto.RegisterTutorRequest;
import com.cw.tutoring.entity.Role;
import com.cw.tutoring.entity.StudentProfile;
import com.cw.tutoring.entity.TutorProfile;
import com.cw.tutoring.entity.User;
import com.cw.tutoring.repository.RoleRepository;
import com.cw.tutoring.repository.StudentProfileRepository;
import com.cw.tutoring.repository.TutorProfileRepository;
import com.cw.tutoring.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       StudentProfileRepository studentProfileRepository,
                       TutorProfileRepository tutorProfileRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerStudent(RegisterStudentRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already used");
        }

        Role studentRole = roleRepository.findByFullName("student")
                .orElseThrow(() -> new IllegalStateException("Role 'student' not found"));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(studentRole);

        user = userRepository.save(user);

        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        studentProfileRepository.save(profile);

        return user;
    }

    public User registerTutor(RegisterTutorRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already used");
        }

        Role tutorRole = roleRepository.findByFullName("tutor")
                .orElseThrow(() -> new IllegalStateException("Role 'tutor' not found"));

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(tutorRole);

        user = userRepository.save(user);

        TutorProfile profile = new TutorProfile();
        profile.setUser(user);
        profile.setExperienceYears(request.getExperienceYears());
        profile.setInfo(request.getInfo());
        profile.setLanguages(request.getLanguages());
        profile.setRatingCount(0);
        tutorProfileRepository.save(profile);

        return user;
    }
}
