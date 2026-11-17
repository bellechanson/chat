package com.green.chat.controller;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.green.chat.dto.AccountEvent;
import com.green.chat.entity.ChatUser;
import com.green.chat.entity.Role;
import com.green.chat.security.JpaTokenRepository;
import com.green.chat.security.LoginSessionManager;
import com.green.chat.security.LoginUser;
import com.green.chat.service.AdminService;
import com.green.chat.service.RoomNotifier;
import com.green.chat.service.RoomService;

@Controller
@RequestMapping("/admin")
public class AdminController {

	private final AdminService adminService;
	private final RoomService roomService;
	private final RoomNotifier notifier;
	private final LoginSessionManager sessionManager;
	private final JpaTokenRepository tokenRepository;
	private final SimpMessageSendingOperations messagingTemplate;

	public AdminController(AdminService adminService, RoomService roomService, RoomNotifier notifier,
			LoginSessionManager sessionManager, JpaTokenRepository tokenRepository,
			SimpMessageSendingOperations messagingTemplate) {
		this.adminService = adminService;
		this.roomService = roomService;
		this.notifier = notifier;
		this.sessionManager = sessionManager;
		this.tokenRepository = tokenRepository;
		this.messagingTemplate = messagingTemplate;
	}

	// ───────── 채팅방 / 강퇴 ─────────
	@GetMapping
	public String admin(Model model) {
		model.addAttribute("rooms", adminService.rooms());
		model.addAttribute("bans", adminService.bans());
		return "admin";
	}

	@PostMapping("/rooms/{id}/delete")
	public String deleteRoom(@PathVariable Long id, @AuthenticationPrincipal LoginUser loginUser,
			RedirectAttributes ra) {
		try {
			roomService.delete(id, loginUser.getId(), loginUser.isAdmin());
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin";
		}
		notifier.roomDeleted(id, loginUser.getNickname(), "관리자가 채팅방을 삭제했습니다.");
		ra.addFlashAttribute("message", "채팅방을 삭제했습니다.");
		return "redirect:/admin";
	}

	@PostMapping("/bans/{id}/delete")
	public String unban(@PathVariable Long id, RedirectAttributes ra) {
		try {
			adminService.unban(id);
			ra.addFlashAttribute("message", "강퇴를 해제했습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin";
	}

	// ───────── 회원 관리 ─────────
	@GetMapping("/users")
	public String users(@RequestParam(required = false) String q, @AuthenticationPrincipal LoginUser loginUser,
			Model model) {
		model.addAttribute("users", adminService.users(q, loginUser.getId(), sessionManager.onlineUserIds()));
		model.addAttribute("q", q);
		return "admin-users";
	}

	// 관리자 지정/해제 → 대상 즉시 로그아웃
	@PostMapping("/users/{id}/role")
	public String changeRole(@PathVariable Long id, @RequestParam Role role, @RequestParam(required = false) String q,
			@AuthenticationPrincipal LoginUser loginUser, RedirectAttributes ra) {
		try {
			ChatUser target = adminService.changeRole(loginUser.getId(), id, role);
			signOut(target, role == Role.ADMIN ? "관리자 권한이 부여되었습니다. 다시 로그인해 주세요." : "관리자 권한이 해제되었습니다. 다시 로그인해 주세요.");
			ra.addFlashAttribute("message",
					target.getNickname() + "님을 " + (role == Role.ADMIN ? "관리자로 지정했습니다." : "일반 회원으로 변경했습니다."));
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return back(q, ra);
	}

	// 정지 → 대상 즉시 로그아웃
	@PostMapping("/users/{id}/suspend")
	public String suspend(@PathVariable Long id, @RequestParam(required = false) String q,
			@AuthenticationPrincipal LoginUser loginUser, RedirectAttributes ra) {
		try {
			ChatUser target = adminService.setSuspended(loginUser.getId(), id, true);
			signOut(target, "관리자에 의해 계정이 정지되었습니다.");
			ra.addFlashAttribute("message", target.getNickname() + "님을 정지했습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return back(q, ra);
	}

	@PostMapping("/users/{id}/unsuspend")
	public String unsuspend(@PathVariable Long id, @RequestParam(required = false) String q,
			@AuthenticationPrincipal LoginUser loginUser, RedirectAttributes ra) {
		try {
			ChatUser target = adminService.setSuspended(loginUser.getId(), id, false);
			ra.addFlashAttribute("message", target.getNickname() + "님의 정지를 해제했습니다.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return back(q, ra);
	}

	// 강제 로그아웃
	@PostMapping("/users/{id}/logout")
	public String forceLogout(@PathVariable Long id, @RequestParam(required = false) String q,
			@AuthenticationPrincipal LoginUser loginUser, RedirectAttributes ra) {
		try {
			ChatUser target = adminService.checkLogoutTarget(loginUser.getId(), id);
			int count = signOut(target, "관리자에 의해 로그아웃되었습니다.");
			ra.addFlashAttribute("message", target.getNickname() + "님을 로그아웃시켰습니다. (세션 " + count + "개)");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("error", e.getMessage());
		}
		return back(q, ra);
	}

	// 대상 회원을 모든 기기에서 로그아웃: 로그인 유지 해제 + 세션 만료 + 열린 화면에 알림
	private int signOut(ChatUser target, String message) {
		tokenRepository.removeUserTokens(target.getUsername());
		int count = sessionManager.expireUser(target.getId());
		messagingTemplate.convertAndSendToUser(target.getUsername(), "/queue/account", new AccountEvent(message));
		return count;
	}

	// 검색어를 유지한 채 목록으로 (addAttribute는 한글도 알아서 인코딩)
	private String back(String q, RedirectAttributes ra) {
		if (q != null && !q.isBlank())
			ra.addAttribute("q", q);
		return "redirect:/admin/users";
	}
}