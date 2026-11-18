// ───────── 모든 화면 공통: 실시간 연결 (WebSocket + STOMP) ─────────
// 이 파일보다 stomp.umd.min.js를 먼저 불러와야 함

// 연결 + 공통 구독(계정 변경, 안 읽은 수 뱃지, 데스크톱 알림) + 페이지 이동 처리
//   onConnect(client) : 연결될 때마다(재연결 포함) 실행 → 화면별 구독은 여기서
//   excludeRoom       : 뱃지 셈에서 뺄 방 번호 (지금 보고 있는 채팅방)
function connectSocket(onConnect, excludeRoom = null) {
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new StompJs.Client({
        brokerURL: `${protocol}://${location.host}/ws-chat`,
        reconnectDelay: 5000
    });

    client.onConnect = async () => {
        watchAccount(client);
        watchBadge(client, excludeRoom);
        watchNotify(client);
        if (typeof watchSidebar === 'function') watchSidebar(client);   // 사이드바가 있는 화면
        if (onConnect) await onConnect(client);
    };
    client.onStompError = (frame) => console.error('STOMP 에러', frame);
    client.activate();

    // 페이지를 떠날 때 연결 정리 (채팅방이면 퇴장 처리됨)
    window.addEventListener('pagehide', () => client.deactivate());
    // 뒤로가기로 돌아온 화면은 연결이 끊긴 상태라 새로고침
    window.addEventListener('pageshow', (e) => { if (e.persisted) location.reload(); });

    return client;
}

// ───── 계정 감시: 관리자가 내 계정을 바꾸면(권한 변경, 정지, 강제 로그아웃) 로그인 화면으로 ─────
function watchAccount(client) {
    client.subscribe('/user/queue/account', async (frame) => {
        const event = JSON.parse(frame.body);
        try {
            await client.deactivate();
        } catch (e) { /* 이미 끊겼으면 무시 */ }
        alert(event.message);
        location.href = '/login?expired';
    });
}

// ───── 작업 표시줄 앱 아이콘 + 탭 제목에 안 읽은 수 표시 ─────
let badgeBaseTitle = null;   // 원래 제목 (숫자 붙이기 전)
let badgeExcludeRoom = null; // 지금 보고 있는 채팅방 (셈에서 제외)
let badgeTimer = null;

async function refreshBadge() {
    try {
        const url = badgeExcludeRoom ? `/api/unread?excludeRoom=${badgeExcludeRoom}` : '/api/unread';
        const res = await fetch(url);
        if (!res.ok) return;
        const { total } = await res.json();

        // 작업 표시줄 아이콘 (설치한 앱에서만 지원)
        if ('setAppBadge' in navigator) {
            if (total > 0) navigator.setAppBadge(total).catch(() => {});
            else navigator.clearAppBadge().catch(() => {});
        }

        // 탭 제목 앞에 (숫자)
        if (badgeBaseTitle === null) {
            badgeBaseTitle = document.title.replace(/^\(\d+\+?\)\s*/, '');
        }
        const label = total > 99 ? '99+' : total;
        document.title = (total > 0 ? `(${label}) ` : '') + badgeBaseTitle;
    } catch (e) { /* 실패해도 화면 동작에는 영향 없음 */ }
}

// 신호가 연달아 와도 0.3초에 한 번만 서버에 물어봄
function scheduleBadge() {
    clearTimeout(badgeTimer);
    badgeTimer = setTimeout(refreshBadge, 300);
}

function watchBadge(client, excludeRoomId) {
    if (excludeRoomId) badgeExcludeRoom = excludeRoomId;
    client.subscribe('/topic/rooms/activity', scheduleBadge);  // 채팅방 새 메시지
    client.subscribe('/user/queue/dm', scheduleBadge);         // DM 새 메시지
    client.subscribe('/user/queue/dm-read', scheduleBadge);    // DM 읽음 처리
    client.subscribe('/user/queue/mute', scheduleBadge);       // 방 알림 끄기/켜기 (꺼 둔 방은 셈에서 빠짐)
    refreshBadge();
}

// ───── 데스크톱 알림 ─────
const NOTIFY_KEY = 'chat.notify';   // 'off'면 이 기기에서 알림 끔

function notifySupported() {
    return 'Notification' in window;
}

// 브라우저 허용 + 내 설정이 켜져 있을 때만
function notifyEnabled() {
    if (!notifySupported() || Notification.permission !== 'granted') return false;
    try {
        return localStorage.getItem(NOTIFY_KEY) !== 'off';
    } catch (e) {
        return true;
    }
}

function setNotifyPref(on) {
    try {
        localStorage.setItem(NOTIFY_KEY, on ? 'on' : 'off');
    } catch (e) { /* 저장 못 해도 이번 화면에서는 동작 */ }
}

// 메뉴에 보여 줄 상태 글자
function notifyLabel() {
    if (!notifySupported()) return '지원 안 함';
    if (Notification.permission === 'denied') return '차단됨';
    return notifyEnabled() ? '켜짐' : '꺼짐';
}

// 메뉴의 "데스크톱 알림" 누르면: 처음엔 허용 요청, 그다음부터는 켜기/끄기
async function toggleNotify() {
    if (!notifySupported()) {
        alert('이 브라우저는 데스크톱 알림을 지원하지 않습니다.');
        return;
    }
    if (Notification.permission === 'denied') {
        alert('알림이 차단되어 있습니다.\n'
            + '주소창 왼쪽의 사이트 정보 아이콘(앱이면 오른쪽 위 ⋯ 메뉴 → 앱 정보)에서\n'
            + '알림을 "허용"으로 바꾼 뒤 새로고침해 주세요.');
        return;
    }
    if (Notification.permission === 'default') {
        const result = await Notification.requestPermission();
        if (result !== 'granted') return;
        setNotifyPref(true);
    } else {
        setNotifyPref(!notifyEnabled());
    }

    if (notifyEnabled()) {
        displayNotify('알림이 켜졌습니다', {
            body: '창을 보고 있지 않을 때 새 메시지를 알려 드려요.',
            icon: '/icons/icon-192.png',
            tag: 'notify-test'
        });
    }
}

// 알림 띄우기: 서비스 워커로 (클릭하면 앱 창으로 이동), 안 되면 기본 방식
async function displayNotify(title, options) {
    try {
        const reg = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : null;
        if (reg) {
            await reg.showNotification(title, options);
            return;
        }
    } catch (e) { /* 아래 기본 방식으로 */ }

    const n = new Notification(title, options);
    n.onclick = () => {
        window.focus();
        if (options.data && options.data.url) location.href = options.data.url;
        n.close();
    };
}

// 같은 메시지를 다른 창이 이미 띄웠는지 (창 여러 개 열어 둔 경우 중복 방지)
async function alreadyShown(e) {
    try {
        const reg = 'serviceWorker' in navigator ? await navigator.serviceWorker.getRegistration() : null;
        if (!reg) return false;
        const shown = await reg.getNotifications({ tag: e.tag });
        return shown.some(n => n.data && n.data.messageId === e.messageId);
    } catch (err) {
        return false;
    }
}

function watchNotify(client) {
    client.subscribe('/user/queue/notify', async (frame) => {
        if (!notifyEnabled()) return;
        // 지금 이 창을 보고 있으면 알림 안 띄움 (화면에서 바로 보이니까)
        if (document.visibilityState === 'visible' && document.hasFocus()) return;

        const e = JSON.parse(frame.body);
        if (await alreadyShown(e)) return;

        displayNotify(e.title, {
            body: e.body,
            icon: '/icons/icon-192.png',
            tag: e.tag,          // 대화방 단위 → 같은 방 알림은 최신 것으로 바로 교체
            renotify: true,      // 교체될 때도 다시 알림
            data: { url: e.url, messageId: e.messageId }
        });
    });
}

// ───── 목록의 숫자 뱃지 (빨간 동그라미) ─────
// n이 0이면 숨김, 100 이상이면 99+
function setCount(badge, n) {
    badge.dataset.n = n;
    badge.textContent = n > 99 ? '99+' : n;
    badge.classList.toggle('hidden', n === 0);
}

function addCount(badge) {
    setCount(badge, parseInt(badge.dataset.n || '0', 10) + 1);
}