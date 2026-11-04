package com.sayeed.lender_training_portal.user;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String passwordHash;
    
    @Column(nullable = false, unique = true)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Role role;

    @Column(nullable = false)
    private Instant created_at;

    protected AppUser() {};

    public AppUser(String email, String password_hash, String full_name, Role role, Instant created_at) {
        this.email = email;
        this.passwordHash = password_hash;
        this.fullName = full_name;
        this.role = role;
        this.created_at = created_at;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword_hash() {
        return passwordHash;
    }

    public String getFull_name() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreated_at() {
        return created_at;
    }
    
}
