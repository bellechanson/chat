package com.green.chat.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.green.chat.entity.ChatUser;
import com.green.chat.security.LoginUser;
import com.green.chat.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/me")
public class MyPageController {

	private final UserService userService;
	// 로그인 정보를 세션에 다시 저장하는 도구
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public MyPageController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public String me(@AuthenticationPrincipal LoginUser loginUser, Model model) {
		model.addAttribute("user", userService.getUser(loginUser.getId()));
		return "me";
	}

	@PostMapping("/nickname")
	public String changeNickname(@RequestParam String nickname, @AuthenticationPrincipal LoginUser loginUser,
			HttpServletRequest request, HttpServletResponse response, RedirectAttributes ra) {
		try {
			ChatUser updated = userService.changeNickname(loginUser.getId(), nickname);
			refreshLogin(updated, request, response); // 다시 로그인 안 해도 새 닉네임 적용
			ra.addFlashAttribute("nicknameMsg", "닉네임이 변경되었습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("nicknameError", e.getMessage());
		}
		return "redirect:/me";
	}

	@PostMapping("/password")
	public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
			@RequestParam String newPasswordConfirm, @AuthenticationPrincipal LoginUser loginUser,
			HttpServletRequest request, HttpServletResponse response, RedirectAttributes ra) {
		try {
			ChatUser updated = userService.changePassword(loginUser.getId(), currentPassword, newPassword,
					newPasswordConfirm);
			refreshLogin(updated, request, response);
			ra.addFlashAttribute("passwordMsg", "비밀번호가 변경되었습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("passwordError", e.getMessage());
		}
		return "redirect:/me";
	}

	// 세션에 들어 있는 로그인 정보(LoginUser)를 최신 값으로 교체
	private void refreshLogin(ChatUser user, HttpServletRequest request, HttpServletResponse response) {
		LoginUser updated = LoginUser.from(user);
		Authentication current = SecurityContextHolder.getContext().getAuthentication();

		UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(updated, null,
				updated.getAuthorities());
		auth.setDetails(current.getDetails());

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(auth);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);
	}
}