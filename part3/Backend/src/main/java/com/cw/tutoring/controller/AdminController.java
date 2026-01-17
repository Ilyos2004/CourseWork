package com.cw.tutoring.controller;

import com.cw.tutoring.dto.SubjectCreateRequest;
import com.cw.tutoring.entity.Subject;
import com.cw.tutoring.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cw.tutoring.dto.AdminUserWithProfilesDto;
import java.util.List;


@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    //Создать предмет
    @PostMapping("/subjects")
    public ResponseEntity<Subject> createSubject(@RequestBody SubjectCreateRequest request) {
        Subject subject = adminService.createSubject(request);
        return ResponseEntity.ok(subject);
    }

    //Удалить слот
    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<Void> deleteSlot(@PathVariable Integer slotId) {
        adminService.deleteSlot(slotId);
        return ResponseEntity.noContent().build();
    }

    // Удалить профиль студента
    @DeleteMapping("/students/{studentProfileId}")
    public ResponseEntity<Void> deleteStudentProfile(@PathVariable Integer studentProfileId) {
        adminService.deleteStudentProfile(studentProfileId);
        return ResponseEntity.noContent().build();
    }

    // Удалить профиль репетитора
    @DeleteMapping("/tutors/{tutorProfileId}")
    public ResponseEntity<Void> deleteTutorProfile(@PathVariable Integer tutorProfileId) {
        adminService.deleteTutorProfile(tutorProfileId);
        return ResponseEntity.noContent().build();
    }

    // Список всех пользователей с информацией о профилях
    @GetMapping("/users")
    public ResponseEntity<List<AdminUserWithProfilesDto>> listUsersWithProfiles() {
        return ResponseEntity.ok(adminService.listUsersWithProfiles());
    }

    @PostMapping("/subject/{name}")
    public ResponseEntity<SubjectCreateRequest> createSubject(@PathVariable String name) {
        var sub = adminService.createSubject(name);
        return ResponseEntity.ok(sub);
    }

}
