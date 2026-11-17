// ───────── fetch로 POST할 때 쓰는 공통 함수 (회원가입·아이디 찾기·비밀번호 재설정, 채팅방·1:1 파일 보내기) ─────────

// CSRF 토큰 헤더 (페이지의 <meta name="_csrf">, <meta name="_csrf_header">에서 읽음)
//   extra: 같이 보낼 다른 헤더 — 메타 태그가 없는 화면이면 extra만 돌려줌
function csrfHeaders(extra = {}) {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    return token && header ? { ...extra, [header]: token } : { ...extra };
}

// CSRF 토큰을 붙여서 JSON으로 POST 요청
async function postJson(url, body) {
    const headers = csrfHeaders({ 'Content-Type': 'application/json' });
    try {
        const res = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body) });
        return await res.json();
    } catch (e) {
        return { ok: false, message: '요청 중 오류가 발생했습니다. 새로고침 후 다시 시도해 주세요.' };
    }
}

// 재발송 버튼 카운트다운 (타이머 id 반환)
function cooldown(button, seconds, label) {
    let left = seconds;
    button.disabled = true;
    button.textContent = `재발송 (${left})`;
    const timer = setInterval(() => {
        left--;
        if (left <= 0) {
            clearInterval(timer);
            button.disabled = false;
            button.textContent = label;
        } else {
            button.textContent = `재발송 (${left})`;
        }
    }, 1000);
    return timer;
}

// 안내 문구 표시 (ok=true 초록, false 빨강)
function setHint(el, text, ok) {
    el.textContent = text;
    el.className = 'hint ' + (ok ? 'ok' : 'err');
}