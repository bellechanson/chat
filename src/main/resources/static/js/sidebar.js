// ───────── 공통 사이드바: 실시간 갱신 ─────────
// socket.js 다음에 불러옴. 연결되면 socket.js가 watchSidebar(client)를 부름

// 아바타 색 번호, 첫 글자 (서버 Formats.tint / initial과 같은 규칙)
function tintOf(name) {
    return name ? name.codePointAt(0) % 6 : 0;
}

// "(탈퇴한 사용자)"처럼 괄호로 시작하면 괄호는 건너뜀 (서버 Formats.initial과 같은 규칙)
function initialOf(name) {
    const n = name && name.startsWith('(') ? name.slice(1) : name;
    return n ? String.fromCodePoint(n.codePointAt(0)) : '?';
}

function sidebarInfo() {
    const side = document.getElementById('sidebar');
    if (!side) return null;
    return {
        side,
        myId: Number(side.dataset.myId),
        myNickname: side.dataset.myNickname,
        active: side.dataset.active,
        activeId: side.dataset.activeId ? Number(side.dataset.activeId) : null
    };
}

// 방 접속 수: 사이드바와 첫 화면 목록 모두 (.js-room-count)
function updateRoomCounts(counts) {
    document.querySelectorAll('.js-room-count[data-room-id]').forEach(el => {
        const n = counts[el.dataset.roomId] || 0;
        el.textContent = n + (el.dataset.suffix || '명');
        el.classList.toggle('on', n > 0);
        if (el.classList.contains('hide-zero')) el.classList.toggle('hidden', n === 0);
    });
}

function addSideRoom(room) {
    const list = document.getElementById('sideRooms');
    if (!list || list.querySelector(`li[data-room-id="${room.id}"]`)) return;

    const li = document.createElement('li');
    li.dataset.roomId = room.id;
    li.dataset.joined = 'false';   // 새 방은 아직 안 들어가 봄
    li.dataset.name = room.name;
    li.innerHTML = `
        <a class="side-item" href="/rooms/${room.id}">
            <span class="hash">#</span>
            <span class="side-name"></span>
			<span class="side-at hidden" title="나를 부른 메시지가 있어요">@</span>
            <span class="unread hidden" data-n="0"></span>
            <span class="side-count js-room-count hide-zero hidden" data-room-id="${room.id}">0명</span>
        </a>`;
    li.querySelector('.side-name').textContent = room.name;
    list.prepend(li);
    document.getElementById('sideRoomEmpty')?.classList.add('hidden');
}

function removeSideRoom(roomId) {
    document.querySelector(`#sideRooms li[data-room-id="${roomId}"]`)?.remove();
    const list = document.getElementById('sideRooms');
    if (list && list.children.length === 0) document.getElementById('sideRoomEmpty')?.classList.remove('hidden');
}

function createSideDm(convId, partnerId, partnerNickname) {
    const li = document.createElement('li');
    li.dataset.convId = convId;
    li.dataset.partnerId = partnerId;
    li.dataset.name = partnerNickname;
    li.innerHTML = `
        <a class="side-item" href="/dm/${convId}">
            <span class="avatar avatar-sm av-${tintOf(partnerNickname)}"><span class="ini"></span><span class="presence on"></span></span>
            <span class="side-text"><span class="side-name"></span><span class="side-preview"></span></span>
            <span class="unread hidden" data-n="0"></span>
        </a>`;
    li.querySelector('.ini').textContent = initialOf(partnerNickname);
    li.querySelector('.side-name').textContent = partnerNickname;
    return li;
}

// 새 DM → 미리보기 갱신, 맨 위로, (보고 있지 않으면) 안 읽은 수 +1
function onSideDm(m, info) {
    const list = document.getElementById('sideDms');
    if (!list) return;
    let li = list.querySelector(`li[data-conv-id="${m.conversationId}"]`);
    if (!li) {
        if (m.senderId === info.myId) return;   // 내가 새로 시작한 대화는 다음 화면부터 보임
        li = createSideDm(m.conversationId, m.senderId, m.senderNickname);
    }
    li.querySelector('.side-preview').textContent = m.deleted ? '삭제된 메시지입니다' : m.content;

    const viewing = info.active === 'dm' && info.activeId === m.conversationId;
    if (m.senderId !== info.myId && !viewing) addCount(li.querySelector('.unread'));

    list.prepend(li);
    document.getElementById('sideDmEmpty')?.classList.add('hidden');
}

// 사이드바에서 대화 한 줄 지우기 (비면 안내 문구)
function removeSideDm(convId) {
    document.querySelector(`#sideDms li[data-conv-id="${convId}"]`)?.remove();
    const list = document.getElementById('sideDms');
    if (list && list.children.length === 0) document.getElementById('sideDmEmpty')?.classList.remove('hidden');
}

// ───── 1:1 대화방 나가기 ─────
// 내 목록에서만 사라지고 상대에게는 알리지 않음. 상대가 다시 보내면 이전 대화와 함께 다시 나타남
//   (대화 화면 머리의 [나가기] 버튼도 이 함수를 씀)
async function leaveDm(convId, partnerName) {
    const ok = await askConfirm(`${partnerName || '이'}님과의 대화방에서 나갈까요?\n\n`
        + '· 내 대화 목록에서만 사라지고, 상대에게는 알리지 않아요.\n'
        + '· 상대가 다시 메시지를 보내면 이전 대화와 함께 다시 나타나요.', { ok: '나가기' });   // dialog.js
    if (!ok) return;

    const form = document.getElementById('dmLeaveForm');
    if (!form) return;
    form.action = `/dm/${convId}/leave`;
    form.elements.back.value = location.pathname;   // 나간 뒤 지금 화면으로 돌아오기 (그 대화를 보고 있었으면 홈)
    form.submit();
}

// ───── 작은 팝업 닫기 공통 (우클릭 메뉴, 반응 고르는 창, 채팅방 접속자 목록) ─────
// 팝업 밖을 누르거나 Esc를 누르면 close()
//   ignore  : (누른 요소) → true면 닫지 않음 (팝업을 여는 버튼은 그 버튼이 직접 열고 닫게)
//   floating: true면 스크롤·창 크기 변경·창 전환 때도 닫음 (화면 좌표로 띄운 창이 엉뚱한 곳에 남지 않게)
function closeOnOutside(popup, close, { ignore = null, floating = false } = {}) {
    document.addEventListener('mousedown', (e) => {
        if (popup.contains(e.target) || (ignore && ignore(e.target))) return;
        close();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') close();
    });
    if (floating) {
        window.addEventListener('resize', close);
        window.addEventListener('blur', close);
        document.addEventListener('scroll', close, true);
    }
}

// 우클릭 메뉴 (한 개를 만들어 두고 위치만 옮겨 씀)
let sideMenu = null;

function closeSideMenu() {
    if (sideMenu) sideMenu.classList.add('hidden');
}

function openSideMenu(x, y, items) {
    if (!sideMenu) {
        sideMenu = document.createElement('div');
        sideMenu.className = 'ctx-menu hidden';
        sideMenu.setAttribute('role', 'menu');
        document.body.appendChild(sideMenu);
        closeOnOutside(sideMenu, closeSideMenu, { floating: true });   // 메뉴 밖 클릭, Esc, 스크롤·창 크기 변경·창 전환
    }
    sideMenu.replaceChildren(...items.map(item => {
        const b = document.createElement('button');
        b.type = 'button';
        b.setAttribute('role', 'menuitem');
        b.className = item.danger ? 'danger' : '';
        b.textContent = item.label;
        b.addEventListener('click', () => {
            closeSideMenu();
            item.action();
        });
        return b;
    }));

    // 화면 밖으로 나가지 않게 위치 맞추기
    sideMenu.classList.remove('hidden');
    const w = sideMenu.offsetWidth, h = sideMenu.offsetHeight;
    sideMenu.style.left = Math.min(x, window.innerWidth - w - 8) + 'px';
    sideMenu.style.top = Math.min(y, window.innerHeight - h - 8) + 'px';
    sideMenu.querySelector('button')?.focus();
}

// 연결될 때마다 (재연결 포함) socket.js가 부름
function watchSidebar(client) {
    const info = sidebarInfo();
    if (!info) return;
    const side = info.side;

    client.subscribe('/topic/rooms/counts', f => updateRoomCounts(JSON.parse(f.body)));
    client.subscribe('/topic/rooms/created', f => addSideRoom(JSON.parse(f.body)));
    client.subscribe('/topic/rooms/deleted', f => removeSideRoom(JSON.parse(f.body)));

    // 채팅방 새 메시지 → 들어가 본 방이고, 지금 보고 있는 방이 아니면 +1
    client.subscribe('/topic/rooms/activity', f => {
        const a = JSON.parse(f.body);
        if (a.sender === info.myNickname) return;
        if (info.active === 'room' && info.activeId === a.roomId) return;
        const li = side.querySelector(`#sideRooms li[data-room-id="${a.roomId}"]`);
        if (li && li.dataset.joined === 'true') addCount(li.querySelector('.unread'));
    });

    // 다른 채팅방에서 나를 부름(@멘션·답장) → 그 방에 "@" (지금 보고 있는 방이면 그대로)
    client.subscribe('/user/queue/mention', f => {
        const e = JSON.parse(f.body);
        if (info.active === 'room' && info.activeId === e.roomId) return;
        side.querySelector(`#sideRooms li[data-room-id="${e.roomId}"] .side-at`)?.classList.remove('hidden');
    });

    client.subscribe('/user/queue/dm', f => onSideDm(JSON.parse(f.body), info));

    // DM 수정 → 마지막 메시지였으면 미리보기 글만 바꿈 (순서·안 읽은 수는 그대로)
    client.subscribe('/user/queue/dm-edited', f => {
        const e = JSON.parse(f.body);
        if (!e.last) return;
        const preview = side.querySelector(`#sideDms li[data-conv-id="${e.conversationId}"] .side-preview`);
        if (preview) preview.textContent = e.content;
    });

    // 내가 (다른 탭에서) 대화방을 나감 → 목록에서 지우기, 그 대화를 보고 있었으면 홈으로
    client.subscribe('/user/queue/dm-hidden', f => {
        const e = JSON.parse(f.body);
        removeSideDm(e.conversationId);
        if (info.active === 'dm' && info.activeId === e.conversationId) location.href = '/rooms';
    });

    // 내가 읽음 → 그 대화 뱃지 지우기
    client.subscribe('/user/queue/dm-read', f => {
        const e = JSON.parse(f.body);
        if (e.readerId !== info.myId) return;
        const li = side.querySelector(`#sideDms li[data-conv-id="${e.conversationId}"]`);
        if (li) setCount(li.querySelector('.unread'), 0);
    });

    // 상대 접속 점
    client.subscribe('/topic/online', f => {
        const online = new Set(JSON.parse(f.body).map(String));
        side.querySelectorAll('#sideDms li[data-partner-id]').forEach(li =>
            li.querySelector('.presence').classList.toggle('on', online.has(li.dataset.partnerId)));
    });
}

// ───── 화면이 그려지면: 새 채팅방, 찾기, 알림 버튼 ─────
document.addEventListener('DOMContentLoaded', () => {
    const info = sidebarInfo();
    if (!info) return;

    // + → 새 채팅방 입력칸 열기/닫기 (Esc로 닫기)
    const newBtn = document.getElementById('newRoomBtn');
    const newForm = document.getElementById('newRoomForm');
    if (newBtn && newForm) {
        const setOpen = (open) => {
            newForm.classList.toggle('hidden', !open);
            newBtn.setAttribute('aria-expanded', open);
            if (open) newForm.querySelector('input').focus();
        };
        newBtn.addEventListener('click', () => setOpen(newForm.classList.contains('hidden')));
        newForm.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                setOpen(false);
                newBtn.focus();
            }
        });
    }

    // 1:1 대화 우클릭 → [대화방 나가기] (키보드 메뉴 키 / Shift+F10도 같은 이벤트)
    document.getElementById('sideDms')?.addEventListener('contextmenu', (e) => {
        const li = e.target.closest('li[data-conv-id]');
        if (!li) return;
        e.preventDefault();

        // 키보드로 연 경우(좌표 0)는 그 줄 옆에 띄움
        let x = e.clientX, y = e.clientY;
        if (!x && !y) {
            const r = li.getBoundingClientRect();
            x = r.left + 24;
            y = r.bottom;
        }
        openSideMenu(x, y, [
            { label: '대화방 나가기', danger: true, action: () => leaveDm(li.dataset.convId, li.dataset.name) }
        ]);
    });

    // 찾기: 이름에 글자가 들어간 방·대화만 보이기
    document.getElementById('sideFind')?.addEventListener('input', (e) => {
        const q = e.target.value.trim().toLowerCase();
        info.side.querySelectorAll('#sideRooms li, #sideDms li').forEach(li => {
            li.classList.toggle('hidden', !!q && !(li.dataset.name || '').toLowerCase().includes(q));
        });
    });

    // 데스크톱 알림 켜기/끄기 (socket.js의 toggleNotify, notifyLabel)
    const btn = document.getElementById('notifyBtn');
    if (btn) {
        const render = () => {
            const label = notifyLabel();
            btn.classList.toggle('on', label === '켜짐');
            btn.title = '데스크톱 알림: ' + label;
            btn.setAttribute('aria-label', btn.title);
        };
        btn.addEventListener('click', async () => {
            await toggleNotify();
            render();
        });
        render();
    }
});