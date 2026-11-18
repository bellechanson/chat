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


// ───────── 이메일 인증번호 받기 · 확인 (회원가입, 아이디 찾기, 비밀번호 재설정 공통) ─────────
// 화면에 #email #sendBtn #codeRow #code #verifyBtn #emailHint 가 있어야 함
//   purpose    : 'SIGNUP' / 'FIND_ID' / 'RESET_PASSWORD'
//   sendBody   : () => 인증번호를 보낼 때 같이 보낼 값 { email, ... }, 입력이 모자라면 안내 문구(문자열)
//   onVerified : 인증 성공 → onVerified(서버 응답) (실패 안내는 여기서 함)
// 돌려주는 stop(): 재발송 카운트다운 멈춤 (인증이 끝났을 때)
function setupEmailVerify({ purpose, sendBody, onVerified }) {
    const el = id => document.getElementById(id);
    const hint = el('emailHint');
    let timer = null;

    el('sendBtn').onclick = async () => {
        const body = sendBody();
        if (typeof body === 'string') return setHint(hint, body, false);

        el('sendBtn').disabled = true;
        const r = await postJson('/api/email/send', { ...body, purpose });
        setHint(hint, r.message, r.ok);
        if (r.ok) {
            el('codeRow').classList.remove('hidden');
            el('code').focus();
            timer = cooldown(el('sendBtn'), 60, '인증번호 받기');
        } else {
            el('sendBtn').disabled = false;
        }
    };

    async function verify() {
        const r = await postJson('/api/email/verify', {
            email: el('email').value.trim(), purpose, code: el('code').value.trim()
        });
        if (!r.ok) return setHint(hint, r.message, false);
        onVerified(r);
    }

    el('verifyBtn').onclick = verify;
    el('code').addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.isComposing) {
            e.preventDefault();
            verify();
        }
    });

    return {
        stop() {
            if (timer) clearInterval(timer);
        }
    };
}