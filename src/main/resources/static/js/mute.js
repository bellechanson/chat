// ───────── 방별 알림 끄기: 대화방 머리글의 [알림] 버튼 (채팅방 chat.html, 1:1 dm-room.html) ─────────
// sidebar.js(openSideMenu)·api.js(csrfHeaders) 다음에 불러옴
// 꺼 둔 방은 서버가 데스크톱 알림을 보내지 않고, 앱 아이콘 뱃지·탭 제목 숫자에서도 빠짐
//   button   : 머리글의 [알림] 버튼
//   scope    : 'ROOM'(채팅방) / 'DM'(1:1 대화)
//   targetId : 채팅방 번호 / 1:1 대화방 번호
//   url      : 저장 주소 ('/api/rooms/3/mute', '/api/dm/3/mute')
// 돌려주는 값의 watch(client)를 연결될 때마다(재연결 포함) 부르면 내 다른 탭·기기에서 바꾼 것도 바로 반영
function setupMute({ button, scope, targetId, url }) {
    let state = { muted: false, until: null };

    // "2026-11-18 18:30" → 오늘이면 "18:30까지", 아니면 "11/19 09:00까지"
    function untilLabel(until) {
        const [day, time] = until.split(' ');
        const now = new Date();
        const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
        if (day === today) return `${time}까지`;
        const [, m, d] = day.split('-');
        return `${Number(m)}/${Number(d)} ${time}까지`;
    }

    function render() {
        button.classList.toggle('muted', state.muted);
        button.setAttribute('aria-pressed', state.muted);
        if (!state.muted) {
            button.textContent = '알림 켜짐';
            button.title = '이 방 알림 끄기';
        } else {
            button.textContent = state.until ? `알림 꺼짐 · ${untilLabel(state.until)}` : '알림 꺼짐';
            button.title = '알림 다시 켜기 / 시간 바꾸기';
        }
    }

    // 서버에서 온 상태(MuteState) 중 이 방 것만 반영
    function apply(s) {
        if (s.scope !== scope || s.targetId !== targetId) return;
        state = { muted: s.muted, until: s.until };
        render();
    }

    // 지금 상태 불러오기 (처음, 재연결 때 — 끊긴 사이에 다른 탭에서 바꿨을 수 있음)
    async function load() {
        try {
            const res = await fetch('/api/mutes');
            if (!res.ok) return;
            const list = await res.json();
            const found = list.find(s => s.scope === scope && s.targetId === targetId);
            apply(found || { scope, targetId, muted: false, until: null });
        } catch (e) { /* 못 불러오면 그대로 둠 */ }
    }

    async function choose(duration) {
        try {
            const res = await fetch(url, {
                method: 'POST',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ duration })
            });
            const data = await res.json();
            if (!res.ok) return alert(data.message || '알림 설정을 바꾸지 못했어요.');
            apply(data);   // 내 다른 탭에는 서버가 /user/queue/mute로 알려 줌
        } catch (e) {
            alert('알림 설정을 바꾸지 못했어요. 새로고침 후 다시 시도해 주세요.');
        }
    }

    // [알림] 누르면 시간 고르는 메뉴 (사이드바 우클릭 메뉴를 같이 씀 → sidebar.js openSideMenu)
    button.addEventListener('click', () => {
        const items = [
            { label: '1시간 동안 끄기', action: () => choose('1h') },
            { label: '8시간 동안 끄기', action: () => choose('8h') },
            { label: '아침 9시까지 끄기', action: () => choose('morning') },
            { label: '계속 끄기', action: () => choose('forever') }
        ];
        if (state.muted) items.unshift({ label: '알림 다시 켜기', action: () => choose('off') });
        const r = button.getBoundingClientRect();
        openSideMenu(r.left, r.bottom + 4, items);
    });

    render();   // 처음 상태는 연결되면 watch()가 불러옴

    return {
        watch(client) {
            client.subscribe('/user/queue/mute', f => apply(JSON.parse(f.body)));
            load();
        }
    };
}