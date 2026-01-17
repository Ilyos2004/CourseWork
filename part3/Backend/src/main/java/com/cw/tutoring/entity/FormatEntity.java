package com.cw.tutoring.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "format")
@Data
public class FormatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String type;
}
