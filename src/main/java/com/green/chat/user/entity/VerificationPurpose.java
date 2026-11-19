package com.green.chat.user.entity;

// 인증번호 용도
public enum VerificationPurpose {
	SIGNUP("회원가입"), 
	FIND_ID("아이디 찾기"), 
	RESET_PASSWORD("비밀번호 재설정");

	private final String label;

	VerificationPurpose(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}