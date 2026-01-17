package com.cw.tutoring.dto;

import lombok.Data;

@Data
public class RegisterTutorRequest {
    private String name;
    private String email;
    private String password;
    private String phone;

    private Integer experienceYears;
    private String info;
    private String languages;
}
