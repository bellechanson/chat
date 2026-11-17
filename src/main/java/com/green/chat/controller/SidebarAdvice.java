package com.green.chat.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.green.chat.security.LoginUser;
import com.green.chat.service.SidebarService;

import jakarta.servlet.http.HttpServletRequest;

// 사이드바가 있는 화면들: 화면을 그릴 때마다 사이드바 내용을 모델에 넣어 줌
@ControllerAdvice(assignableTypes = { RoomController.class, DmController.class, MyPageController.class,
		AdminController.class })
public class SidebarAdvice {

	private final SidebarService sidebarService;

	public SidebarAdvice(SidebarService sidebarService) {
		this.sidebarService = sidebarService;
	}

	@ModelAttribute("sidebar")
	public SidebarService.View sidebar(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
		// 저장·삭제 같은 POST 요청은 화면을 그리지 않으니 건너뜀
		if (loginUser == null || !"GET".equalsIgnoreCase(request.getMethod()))
			return null;
		return sidebarService.view(loginUser);
	}
}