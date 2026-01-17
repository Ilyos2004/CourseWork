package com.cw.tutoring.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserWithProfilesDto {

    private Integer userId;
    private String name;
    private String email;
    private String phone;
    private String role;

    private Integer studentProfileId;
    private Integer tutorProfileId;
}
