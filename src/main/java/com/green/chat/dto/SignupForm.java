package com.green.chat.dto;

public record SignupForm(
        String username,
        String password,
        String passwordConfirm,
        String nickname,
        String email
) {}