// ───────── 사진 모아보기: 오른쪽 패널 (채팅방 chat.html, 1:1 dm-room.html) ─────────
// files.js(fileElement) · lightbox.js(크게 보기) 다음에 불러옴
//   button   : 머리글 [사진] 버튼
//   pane     : 대화 화면 (main.pane) — 패널을 이 안 오른쪽에 붙임 (검색 패널과 같은 자리)
//   mediaUrl : '/api/rooms/3/media' 또는 '/api/dm/3/media'
//   onOpen   : 열 때 부름 (검색 패널 닫기)
// 사진을 누르면 크게 보기 → 모아본 사진들을 ←/→로 넘겨 봄 (lightbox.js가 .gallery 안의 .att-img를 넘김)
// 돌려주는 값: 패널을 열어 둔 동안 실시간 반영
//   onMessage({id, sender, date, time, file, photos}) 새 사진(사진 파일·묶음) → 맨 위에
//   onDeleted(id) 메시지 삭제 → 그 메시지 사진 빠짐
//   onPhotosChanged(id, photos) 묶음에서 한 장 삭제 → 남은 사진만
//   onRenamed(oldName, newName) 닉네임 바뀜 → 크게 보기에 나오는 보낸 사람 이름도
//   close() 닫기
function setupGallery({ button, pane, mediaUrl, onOpen = null }) {
    const el = (tag, cls, text) => {
        const e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text != null) e.textContent = text;
        return e;
    };

    // ── 패널: 머리(사진 128 · 닫기) / 상태 안내 / 월별 격자 ──
    const panel = el('aside', 'pane-panel hidden');
    panel.setAttribute('aria-label', '사진 모아보기');
    const head = el('div', 'panel-head');
    const title = el('h2', 'panel-title', '사진');
    const count = el('span', 'panel-count');
    title.appendChild(count);
    const closeBtn = el('button', 'icon-btn sm', '✕');
    closeBtn.type = 'button';
    closeBtn.title = '닫기 (Esc)';
    closeBtn.setAttribute('aria-label', '사진 모아보기 닫기');
    head.append(title, closeBtn);
    const status = el('div', 'panel-status');
    status.setAttribute('aria-live', 'polite');
    const gallery = el('div', 'gallery');
    panel.append(head, status, gallery);
    pane.appendChild(panel);

    let total = 0;           // 이 대화의 전체 사진 수 (서버가 처음에 알려 주고, 실시간으로 더하고 뺌)
    let nextBefore = null;
    let hasMore = false;
    let loading = false;
    let seq = 0;             // 다시 열어서 새로 불러오면 예전 응답은 버림

    const isOpen = () => !panel.classList.contains('hidden');

    function open() {
        if (onOpen) onOpen();
        panel.classList.remove('hidden');
        pane.classList.add('panel-open');
        button.setAttribute('aria-expanded', 'true');
        closeBtn.focus();
        load(false);   // 열 때마다 새로 (닫혀 있던 동안 바뀐 것까지)
    }

    function close() {
        if (!isOpen()) return;
        panel.classList.add('hidden');
        pane.classList.remove('panel-open');
        button.setAttribute('aria-expanded', 'false');
    }

    // ── 불러오기 (more: 더 오래된 사진 이어서) ──
    async function load(more) {
        const token = ++seq;
        if (!more) {
            gallery.replaceChildren();
            nextBefore = null;
            hasMore = false;
        }
        loading = true;
        updateStatus();
        try {
            const res = await fetch(mediaUrl + (more && nextBefore ? `?before=${nextBefore}` : ''));
            if (token !== seq) return;
            if (!res.ok) throw new Error(res.status);
            const page = await res.json();
            if (token !== seq) return;
            if (page.total != null) total = page.total;
            page.items.forEach(item => appendItem(item));
            hasMore = page.hasMore;
            nextBefore = page.nextBefore;
        } catch (e) {
            if (token === seq) hasMore = false;
        } finally {
            if (token === seq) {
                loading = false;
                updateStatus();
                if (hasMore && gallery.scrollHeight <= gallery.clientHeight) load(true);   // 스크롤할 곳이 없으면 이어서
            }
        }
    }

    function updateStatus() {
        count.textContent = total > 0 ? ` ${total}` : '';
        const n = gallery.querySelectorAll('.gallery-item').length;
        status.textContent = loading && n === 0 ? '불러오는 중...' : n === 0 ? '아직 주고받은 사진이 없어요' : '';
        status.classList.toggle('hidden', status.textContent === '');
    }

    // ── 사진 한 칸 (크게 보기가 보낸 사람·시각을 .msg-name, .msg-time에서 읽음) ──
    function tile(messageId, sender, date, time, photo) {
        const item = el('div', 'gallery-item');
        item.dataset.id = messageId;
        item.dataset.sender = sender;
        const name = el('span', 'msg-name sr-only', sender);
        const when = el('span', 'msg-time sr-only', dateLabel(date, time));
        const a = fileElement(photo);   // files.js — 작은 미리보기 (누르면 크게 보기)
        if (photo.photoId) a.dataset.photoId = photo.photoId;
        a.title = `${sender} · ${dateLabel(date, time)}`;
        item.append(name, when, a);
        return item;
    }

    // "2026-11-18" + "14:30" → "11월 18일 14:30"
    function dateLabel(date, time) {
        if (!date) return time || '';
        const [, m, d] = date.split('-').map(Number);
        return `${m}월 ${d}일 ${time || ''}`.trim();
    }

    // 월별 칸: "2026년 11월" 제목 + 3칸 격자 (없으면 만들어서 맨 위/맨 아래에)
    function monthGrid(date, atTop) {
        const key = (date || '').slice(0, 7);   // "2026-11"
        let section = gallery.querySelector(`.gallery-month[data-month="${key}"]`);
        if (!section) {
            section = el('section', 'gallery-month');
            section.dataset.month = key;
            const [y, m] = key.split('-').map(Number);
            section.append(el('h3', null, key ? `${y}년 ${m}월` : ''), el('div', 'gallery-grid'));
            if (atTop) gallery.prepend(section);
            else gallery.appendChild(section);
        }
        return section.querySelector('.gallery-grid');
    }

    function appendItem(item) {
        monthGrid(item.date, false).appendChild(tile(item.messageId, item.sender, item.date, item.time, item.photo));
    }

    // 사진이 빠져서 빈 달은 지움
    function removeEmptyMonths() {
        gallery.querySelectorAll('.gallery-month').forEach(s => {
            if (!s.querySelector('.gallery-item')) s.remove();
        });
    }

    function removeTiles(tiles) {
        if (tiles.length === 0) return;
        tiles.forEach(t => t.remove());
        total = Math.max(0, total - tiles.length);
        removeEmptyMonths();
        updateStatus();
    }

    // ── 이벤트 ──
    button.setAttribute('aria-expanded', 'false');
    button.addEventListener('click', () => (isOpen() ? close() : open()));
    closeBtn.addEventListener('click', close);
    panel.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && !lightboxOpen()) close();   // 크게 보기가 떠 있으면 그것부터 닫힘
    });
    gallery.addEventListener('scroll', () => {   // 아래 끝 근처 → 더 오래된 사진
        if (hasMore && !loading && gallery.scrollTop + gallery.clientHeight > gallery.scrollHeight - 120) load(true);
    });

    return {
        close,
        onMessage(m) {
            if (!isOpen() || !m.id) return;
            const photos = m.photos && m.photos.length ? m.photos : (m.file && m.file.image ? [m.file] : []);
            if (photos.length === 0) return;
            const grid = monthGrid(m.date, true);
            [...photos].reverse().forEach(p => grid.prepend(tile(m.id, m.sender, m.date, m.time, p)));   // 묶음 순서 그대로 맨 위에
            total += photos.length;
            updateStatus();
        },
        onDeleted(id) {
            removeTiles([...gallery.querySelectorAll(`.gallery-item[data-id="${id}"]`)]);
        },
        onPhotosChanged(id, photos) {
            const keep = new Set((photos || []).map(p => String(p.photoId)));
            removeTiles([...gallery.querySelectorAll(`.gallery-item[data-id="${id}"]`)]
                .filter(t => !keep.has(t.querySelector('.att-img')?.dataset.photoId)));
        },
        onRenamed(oldName, newName) {
            gallery.querySelectorAll('.gallery-item').forEach(t => {
                if (t.dataset.sender !== oldName) return;
                t.dataset.sender = newName;
                t.querySelector('.msg-name').textContent = newName;
                const a = t.querySelector('.att-img');
                a.title = `${newName} · ${t.querySelector('.msg-time').textContent}`;
            });
        }
    };
}