package com.cw.tutoring.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tutor_profiles")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class

TutorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column
    private String info;

    @Column(name = "rating_count")
    private Integer ratingCount;

    @Column
    private String languages;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;
}
