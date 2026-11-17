// CSRF 토큰을 붙여서 JSON으로 POST 요청
async function postJson(url, body) {
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = { 'Content-Type': 'application/json' };
    if (token && header) headers[header] = token;

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