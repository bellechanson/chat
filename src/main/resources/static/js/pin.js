// ───────── 공지(고정 메시지): 대화 위 공지 띠 (채팅방 chat.html, 1:1 dm-room.html) ─────────
// messages.js(fillContent) · dialog.js(askConfirm, toast) · api.js(csrfHeaders) 다음에 불러옴
//   messages : 메시지 목록 상자 (#messages) — 바로 위에 띠를 붙임
//   pinUrl   : '/api/rooms/3/pin' 또는 '/api/dm/3/pin' (GET 지금 공지, POST 고정, DELETE 해제)
//   canPin   : 고정·해제할 수 있는지 (채팅방은 방장·관리자, 1:1은 둘 다)
//   list     : createMessageList가 돌려준 값 (띠를 누르면 그 메시지로 jumpTo)
//   foldKey  : 접기를 기억할 localStorage 키 — 내 화면에서만 접힘, 새 공지가 오면 다시 펼쳐짐
// 돌려주는 값:
//   load()       지금 공지를 서버에서 다시 불러옴 (연결·재연결 때 — 끊긴 사이에 바뀌었을 수 있음)
//   apply(pin)   공지 바뀜 신호 반영 (null이면 해제)
//   pin(id)      이 메시지를 공지로 (메시지의 [고정] 버튼)
//   onEdited(id, content, mentions)  고정된 메시지가 수정됨 → 띠 글자도
//   onDeleted(id)                    고정된 메시지가 삭제됨 → 띠 내림
//   onRenamed(oldName, newName, userId) 닉네임 바뀜 → 보낸 사람·고정한 사람·@멘션 이름
function setupPin({ messages, pinUrl, canPin, list, foldKey }) {
    let current = null;   // 지금 공지 PinDto (없으면 null)

    // ── 띠: [📌 공지  내용  영희 · 11월 19일 09:30] [해제] [접기] ──
    const bar = makeEl('div', 'pin-bar hidden');
    bar.setAttribute('role', 'region');
    bar.setAttribute('aria-label', '공지');
    const main = makeEl('button', 'pin-main');
    main.type = 'button';
    const icon = makeEl('span', 'pin-icon', '📌');
    icon.setAttribute('aria-hidden', 'true');
    const label = makeEl('span', 'pin-label', '공지');
    const text = makeEl('span', 'pin-text');
    const meta = makeEl('span', 'pin-meta');
    main.append(icon, label, text, meta);
    bar.appendChild(main);
    if (canPin) {
        const unpinBtn = makeEl('button', 'pin-unpin', '해제');
        unpinBtn.type = 'button';
        unpinBtn.title = '공지 내리기';
        unpinBtn.onclick = unpin;
        bar.appendChild(unpinBtn);
    }
    const foldBtn = makeEl('button', 'icon-btn sm pin-fold');
    foldBtn.type = 'button';
    foldBtn.onclick = () => setFolded(!bar.classList.contains('folded'));
    bar.appendChild(foldBtn);
    messages.before(bar);

    // 띠 누르기: 접혀 있으면 펼치고, 펼쳐져 있으면 그 메시지로 이동
    main.onclick = () => {
        if (!current) return;
        if (bar.classList.contains('folded')) return setFolded(false);
        list.jumpTo(current.messageId, '공지 메시지를 찾을 수 없어요.');
    };

    function render() {
        bar.classList.toggle('hidden', !current);
        if (!current) return;
        fillContent(text, current.content, current.mentions || {});   // @멘션 강조 (messages.js)
        text.querySelectorAll('a').forEach(a => a.replaceWith(a.textContent));   // 띠 안에선 링크 대신 글자 (누르면 이동)
        meta.textContent = `${current.sender} · ${dateTimeLabel(current.date, current.time, true)}`;   // panel.js
        main.title = '공지 메시지로 이동' + (current.pinnedBy ? ` (${current.pinnedBy}님이 고정)` : '');
        setFolded(readFold() === String(current.messageId), false);
    }

    // 접기 (내 화면에서만, 이 공지 번호를 기억 → 다른 공지로 바뀌면 다시 펼쳐짐)
    function setFolded(folded, remember = true) {
        bar.classList.toggle('folded', folded);
        foldBtn.textContent = folded ? '▾' : '▴';
        foldBtn.title = folded ? '공지 펼치기' : '공지 접기';
        foldBtn.setAttribute('aria-label', foldBtn.title);
        foldBtn.setAttribute('aria-expanded', String(!folded));
        main.title = folded ? '공지 펼치기' : main.title;
        if (!remember || !current) return;
        try {
            if (folded) localStorage.setItem(foldKey, String(current.messageId));
            else localStorage.removeItem(foldKey);
        } catch (e) { /* 저장 못 해도 지금 화면에서는 접힘 */ }
        if (!folded) render();   // 펼치면 글자·이동 안내 다시
    }

    function readFold() {
        try {
            return localStorage.getItem(foldKey);
        } catch (e) {
            return null;
        }
    }

    function apply(pin) {
        current = pin || null;
        render();
    }

    async function load() {
        try {
            const res = await fetch(pinUrl);
            if (res.ok) apply((await res.json()).pin);
        } catch (e) { /* 못 불러오면 그대로 둠 */ }
    }

    // 서버에 고정·해제 요청 → 성공하면 바로 반영 (다른 사람·내 다른 탭에는 서버가 실시간으로 알려 줌)
    async function send(method, body) {
        try {
            const res = await fetch(pinUrl, {
                method,
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),   // api.js
                body: body ? JSON.stringify(body) : undefined
            });
            const data = await res.json();
            if (!res.ok) return toast(data.message || '공지를 바꾸지 못했어요.');   // dialog.js
            apply(data.pin);
        } catch (e) {
            toast('공지를 바꾸지 못했어요. 새로고침 후 다시 시도해 주세요.');
        }
    }

    async function pin(messageId) {
        if (current && current.messageId === messageId) return toast('이미 공지로 고정된 메시지예요.');
        if (current && !await askConfirm('지금 공지를 이 메시지로 바꿀까요?', { ok: '바꾸기' })) return;
        send('POST', { messageId });
    }

    async function unpin() {
        if (!await askConfirm('공지를 내릴까요?\n메시지는 대화에 그대로 남아요.', { ok: '내리기' })) return;
        send('DELETE');
    }

    return {
        load,
        apply,
        pin,
        onEdited(id, content, mentions = {}) {
            if (!current || current.messageId !== id) return;
            current = { ...current, content, mentions };
            render();
        },
        onDeleted(id) {
            if (current && current.messageId === id) apply(null);   // 서버도 삭제된 메시지는 공지로 안 줌
        },
        onRenamed(oldName, newName, userId) {
            if (!current) return;
            if (current.sender === oldName) current.sender = newName;
            if (current.pinnedBy === oldName) current.pinnedBy = newName;
            if (userId != null && current.mentions && current.mentions[userId] !== undefined) {
                current.mentions = { ...current.mentions, [userId]: newName };
            }
            render();
        }
    };
}