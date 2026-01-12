package com.cw.tutoring.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "student_profiles")
@Data
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    @Column
    private String goals;

    @Column
    private Integer age;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
