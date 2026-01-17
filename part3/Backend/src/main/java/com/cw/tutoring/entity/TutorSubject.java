package com.cw.tutoring.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tutor_subjects")
@Data
public class TutorSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private TutorProfile tutor;

    @Column
    private String info;

    @Column(name = "count_students")
    private Integer countStudents;

    @Column
    private String languages;
}
