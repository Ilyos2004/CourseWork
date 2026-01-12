package com.cw.tutoring.controller;

import com.cw.tutoring.dto.RegisterStudentRequest;
import com.cw.tutoring.dto.RegisterTutorRequest;
import com.cw.tutoring.entity.User;
import com.cw.tutoring.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-student")
    public ResponseEntity<User> registerStudent(@RequestBody RegisterStudentRequest request) {
        User user = authService.registerStudent(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/register-tutor")
    public ResponseEntity<User> registerTutor(@RequestBody RegisterTutorRequest request) {
        User user = authService.registerTutor(request);
        return ResponseEntity.ok(user);
    }

}
