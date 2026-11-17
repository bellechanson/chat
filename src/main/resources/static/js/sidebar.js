// ───────── 공통 사이드바: 실시간 갱신 ─────────
// socket.js 다음에 불러옴. 연결되면 socket.js가 watchSidebar(client)를 부름

// 아바타 색 번호, 첫 글자 (서버 Formats.tint / initial과 같은 규칙)
function tintOf(name) {
    return name ? name.codePointAt(0) % 6 : 0;
}

function initialOf(name) {
    return name ? String.fromCodePoint(name.codePointAt(0)) : '?';
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

    client.subscribe('/user/queue/dm', f => onSideDm(JSON.parse(f.body), info));

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