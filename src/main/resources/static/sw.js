// 서비스 워커: 서버 연결 실패 시 안내 화면 + 알림 클릭 처리 (채팅 데이터는 저장 안 함)
const CACHE = 'chat-offline-v7';   // 내용이 바뀌면 버전을 올려야 새로 설치됨
const OFFLINE_URL = '/offline.html';
const OFFLINE_ASSETS = [OFFLINE_URL, '/icons/icon-192.png'];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(OFFLINE_ASSETS)));
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then(keys =>
            Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    const req = event.request;

    // 페이지 이동: 서버가 응답하면 그대로, 실패하면 안내 화면
    if (req.mode === 'navigate') {
        event.respondWith(fetch(req).catch(() => caches.match(OFFLINE_URL)));
        return;
    }

    // 아이콘: 서버가 꺼졌으면 저장해 둔 것으로
    if (req.method === 'GET' && new URL(req.url).pathname.startsWith('/icons/')) {
        event.respondWith(fetch(req).catch(() => caches.match(req)));
    }
    // 나머지(API, WebSocket 등)는 건드리지 않음
});

// 알림 클릭: 열려 있는 창을 앞으로 가져와 해당 대화로 이동 (창이 없으면 새로 열기)
self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    const url = (event.notification.data && event.notification.data.url) || '/rooms';

    event.waitUntil((async () => {
        const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
        for (const client of windows) {
            if ('focus' in client) {
                await client.focus();
                if ('navigate' in client) await client.navigate(url).catch(() => {});
                return;
            }
        }
        await self.clients.openWindow(url);
    })());
});