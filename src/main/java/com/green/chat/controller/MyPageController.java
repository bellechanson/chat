package com.green.chat.controller;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.green.chat.dto.AccountEvent;
import com.green.chat.dto.NicknameEvent;
import com.green.chat.entity.ChatUser;
import com.green.chat.security.LoginSessionManager;
import com.green.chat.security.LoginUser;
import com.green.chat.service.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/me")
public class MyPageController {

	private final UserService userService;
	private final LoginSessionManager sessionManager;
	private final SimpMessageSendingOperations messagingTemplate;
	// 로그인 정보를 세션에 다시 저장하는 도구
	private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

	public MyPageController(UserService userService, LoginSessionManager sessionManager,
			SimpMessageSendingOperations messagingTemplate) {
		this.userService = userService;
		this.sessionManager = sessionManager;
		this.messagingTemplate = messagingTemplate;
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
			// 모든 화면의 @멘션 글자를 새 이름으로
			messagingTemplate.convertAndSend("/topic/nickname",
					new NicknameEvent(updated.getId(), updated.getNickname(), loginUser.getNickname()));
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

	// 회원 탈퇴 → 모든 기기 로그아웃 → 로그인 화면
	@PostMapping("/withdraw")
	public String withdraw(@RequestParam String password, @AuthenticationPrincipal LoginUser loginUser,
			HttpServletRequest request, HttpServletResponse response, RedirectAttributes ra) {
		String oldUsername;
		try {
			oldUsername = userService.withdraw(loginUser.getId(), password);
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("withdrawError", e.getMessage());
			return "redirect:/me";
		}

		// 다른 탭·기기: 세션 만료 + 열려 있는 화면에 알림 (socket.js가 받아서 로그인 화면으로 보냄)
		sessionManager.expireUser(loginUser.getId());
		messagingTemplate.convertAndSendToUser(oldUsername, "/queue/account",
				new AccountEvent("회원 탈퇴가 완료되어 로그아웃되었습니다."));
		// 모든 화면의 @멘션 글자를 "(탈퇴한 사용자)"로
		messagingTemplate.convertAndSend("/topic/nickname",
				new NicknameEvent(loginUser.getId(), ChatUser.WITHDRAWN_NAME, loginUser.getNickname()));

		// 지금 이 브라우저: 세션 끝내기 + 로그인 유지 쿠키 지우기
		new SecurityContextLogoutHandler().logout(request, response,
				SecurityContextHolder.getContext().getAuthentication());
		Cookie remember = new Cookie("remember-me", null);
		remember.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
		remember.setMaxAge(0);
		response.addCookie(remember);

		return "redirect:/login?withdrawn";
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