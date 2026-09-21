package com.bankflow.auth.modules.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Application role (e.g. {@code CUSTOMER}, {@code ADMIN}).
 * Rows are seeded in the Flyway migration; no runtime creation.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;
}
