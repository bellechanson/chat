package com.green.chat.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.green.chat.security.LoginUser;
import com.green.chat.service.FileStorage;
import com.green.chat.service.SidebarService;

import jakarta.servlet.http.HttpServletRequest;

// 사이드바가 있는 화면들: 화면을 그릴 때마다 사이드바 내용(+ 입력칸이 쓰는 값)을 모델에 넣어 줌
@ControllerAdvice(assignableTypes = { RoomController.class, DmController.class, MyPageController.class,
		AdminController.class })
public class SidebarAdvice {

	private final SidebarService sidebarService;
	private final FileStorage fileStorage;

	public SidebarAdvice(SidebarService sidebarService, FileStorage fileStorage) {
		this.sidebarService = sidebarService;
		this.fileStorage = fileStorage;
	}

	@ModelAttribute("sidebar")
	public SidebarService.View sidebar(@AuthenticationPrincipal LoginUser loginUser, HttpServletRequest request) {
		// 저장·삭제 같은 POST 요청은 화면을 그리지 않으니 건너뜀
		if (loginUser == null || !"GET".equalsIgnoreCase(request.getMethod()))
			return null;
		return sidebarService.view(loginUser);
	}

	// 입력칸(fragments/parts :: composer)의 파일 크기 안내·미리 검사용 (application.yml
	// chat.upload-max-mb)
	// 템플릿에서 ${@fileStorage...}처럼 빈을 직접 부르면 Thymeleaf 3.1 보안 제한에 걸려서 모델로 넘김
	@ModelAttribute("uploadMaxMb")
	public int uploadMaxMb() {
		return fileStorage.getMaxMb();
	}
}