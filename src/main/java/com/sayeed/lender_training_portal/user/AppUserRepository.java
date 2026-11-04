package com.sayeed.lender_training_portal.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;


public interface  AppUserRepository extends JpaRepository<AppUser, Long> {
    
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
    
}
