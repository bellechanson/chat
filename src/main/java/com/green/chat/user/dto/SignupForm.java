package com.green.chat.user.dto;

public record SignupForm(
        String username,
        String password,
        String passwordConfirm,
        String nickname,
        String email
) {}