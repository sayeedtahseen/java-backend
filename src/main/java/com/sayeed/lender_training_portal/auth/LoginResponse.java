package com.sayeed.lender_training_portal.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) { }