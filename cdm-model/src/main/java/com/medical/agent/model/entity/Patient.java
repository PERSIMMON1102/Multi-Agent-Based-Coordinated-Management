package com.medical.agent.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "patient")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 10)
    private String gender;

    private LocalDate birthDate;

    @Column(length = 20)
    private String phone;

    @Column(length = 20, unique = true)
    private String idCard;

    @Column(length = 200)
    private String address;

    @Column(length = 100)
    private String fhirId;

    @Column(length = 50, unique = true)
    private String username;

    @Column(length = 100)
    private String password;

    @Column(length = 500)
    private String avatar;

    @Column(length = 50)
    private String emergencyContact;

    @Column(length = 20)
    private String emergencyPhone;

    private Long assignedDoctorId;

    @Builder.Default
    private Boolean profileCompleted = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
