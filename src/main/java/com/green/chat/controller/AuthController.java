package com.green.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.green.chat.dto.SignupForm;
import com.green.chat.entity.VerificationPurpose;
import com.green.chat.service.UserService;
import com.green.chat.service.VerificationService;

import jakarta.servlet.http.HttpSession;

@Controller
public class AuthController {

	private final UserService userService;
	private final VerificationService verificationService;

	public AuthController(UserService userService, VerificationService verificationService) {
		this.userService = userService;
		this.verificationService = verificationService;
	}

	@GetMapping("/login")
	public String login() {
		return "login";
	}

	// ── 회원가입 ──
	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("emailVerified", false);
		return "signup";
	}

	@PostMapping("/signup")
	public String signup(@ModelAttribute("form") SignupForm form, HttpSession session, Model model) {
		String email = VerificationService.normalize(form.email());
		boolean verified = verificationService.isVerified(session, VerificationPurpose.SIGNUP, email);
		try {
			if (!verified)
				throw new IllegalArgumentException("이메일 인증을 완료해 주세요.");
			userService.register(form);
			verificationService.clear(session, VerificationPurpose.SIGNUP);
			return "redirect:/login?signup";
		} catch (IllegalArgumentException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("emailVerified", verified); // 인증은 유지
			return "signup";
		}
	}

	// ── 아이디 찾기 (화면만, 처리는 EmailApiController) ──
	@GetMapping("/find-id")
	public String findId() {
		return "find-id";
	}

	// ── 비밀번호 재설정 ──
	@GetMapping("/reset-password")
	public String resetForm(Model model) {
		model.addAttribute("emailVerified", false);
		return "reset-password";
	}

	@PostMapping("/reset-password")
	public String reset(@RequestParam String username, @RequestParam String email,
			@RequestParam(required = false) String password, @RequestParam(required = false) String passwordConfirm,
			HttpSession session, Model model) {
		String normalized = VerificationService.normalize(email);
		boolean verified = verificationService.isVerified(session, VerificationPurpose.RESET_PASSWORD, normalized);
		try {
			if (!verified)
				throw new IllegalArgumentException("이메일 인증을 완료해 주세요.");
			userService.resetPassword(username, normalized, password, passwordConfirm);
			verificationService.clear(session, VerificationPurpose.RESET_PASSWORD);
			return "redirect:/login?reset";
		} catch (IllegalArgumentException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("username", username);
			model.addAttribute("email", email);
			model.addAttribute("emailVerified", verified);
			return "reset-password";
		}
	}
}