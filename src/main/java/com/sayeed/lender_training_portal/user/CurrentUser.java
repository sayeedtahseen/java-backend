package com.sayeed.lender_training_portal.user;

public record CurrentUser(Long id, Role role) {
    
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
