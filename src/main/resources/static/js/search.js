// ───────── 메시지 검색: 오른쪽 검색 패널 (채팅방 chat.html, 1:1 dm-room.html) ─────────
// messages.js(fillContent, plainContent, createMessageList의 jumpTo·find) 다음에 불러옴
//   button    : 머리글 [검색] 버튼 (Ctrl+F로도 열림)
//   pane      : 대화 화면 (main.pane) — 패널을 이 안 오른쪽에 붙임
//   searchUrl : '/api/rooms/3/search' 또는 '/api/dm/3/search'
//   list      : createMessageList가 돌려준 값 (결과를 누르면 jumpTo로 이동)
// 돌려주는 값: 패널을 열어 둔 동안 실시간 반영
//   onMessage({id, sender, content, mentions, date, time}) 새 메시지가 검색어와 맞으면 맨 위에 추가
//   onEdited(id, content, mentions) 수정 → 내용 바꿈 (검색어가 빠지면 결과에서 빠짐, 새로 맞으면 추가)
//   onDeleted(id) 삭제 → 결과에서 빠짐
//   onRenamed(oldName, newName) 닉네임 바뀜 → 결과의 보낸 사람 이름도
function setupSearch({ button, pane, searchUrl, list }) {
    const DEBOUNCE_MS = 300;
    const el = (tag, cls, text) => {
        const e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text != null) e.textContent = text;
        return e;
    };

    // ── 패널 만들기: 머리(검색창 · 닫기) / 결과 개수 / 결과 목록 ──
    const panel = el('aside', 'search-panel hidden');
    panel.setAttribute('aria-label', '메시지 검색');
    const head = el('div', 'search-head');
    const input = el('input', 'search-input');
    input.type = 'search';
    input.placeholder = '이 대화에서 찾기';
    input.maxLength = 50;
    input.setAttribute('aria-label', '검색어');
    const closeBtn = el('button', 'icon-btn sm search-close', '✕');
    closeBtn.type = 'button';
    closeBtn.title = '닫기 (Esc)';
    closeBtn.setAttribute('aria-label', '검색 닫기');
    head.append(input, closeBtn);
    const status = el('div', 'search-status');
    status.setAttribute('aria-live', 'polite');
    const results = el('ul', 'search-results');
    panel.append(head, status, results);
    pane.appendChild(panel);

    let query = '';          // 지금 결과의 검색어
    let nextBefore = null;   // 이어서 찾을 때 기준 번호
    let hasMore = false;
    let loading = false;
    let seq = 0;             // 늦게 온 예전 검색 결과는 버림
    let timer = null;

    const isOpen = () => !panel.classList.contains('hidden');

    function open() {
        panel.classList.remove('hidden');
        pane.classList.add('searching');
        button.setAttribute('aria-expanded', 'true');
        input.focus();
        input.select();
    }

    function close() {
        panel.classList.add('hidden');
        pane.classList.remove('searching');
        button.setAttribute('aria-expanded', 'false');
        clearMarks(document.getElementById('messages'));   // 말풍선 속 강조도 지움
    }

    // ── 검색 ──
    async function search(q, more = false) {
        const token = ++seq;
        if (!more) {
            query = q;
            nextBefore = null;
            hasMore = false;
            results.replaceChildren();
            if (!q) return updateStatus();
        }
        loading = true;
        updateStatus();
        try {
            const url = `${searchUrl}?q=${encodeURIComponent(query)}` + (more && nextBefore ? `&before=${nextBefore}` : '');
            const res = await fetch(url);
            if (token !== seq) return;   // 그사이 다른 검색어로 바뀜
            if (!res.ok) throw new Error(res.status);
            const page = await res.json();
            if (token !== seq) return;
            page.hits.forEach(hit => results.appendChild(hitElement(hit)));
            hasMore = page.hasMore;
            nextBefore = page.nextBefore;
        } catch (e) {
            if (token === seq) hasMore = false;
        } finally {
            if (token === seq) {
                loading = false;
                updateStatus();
                // 걸러진 결과가 적어 목록이 짧으면 이어서 더 찾음 (스크롤할 곳이 없으니)
                if (hasMore && results.scrollHeight <= results.clientHeight) search(query, true);
            }
        }
    }

    function updateStatus() {
        const n = results.children.length;
        if (!query) status.textContent = '찾을 말을 입력하세요';
        else if (loading && n === 0) status.textContent = '찾는 중...';
        else if (n === 0) status.textContent = '찾는 메시지가 없어요';
        else status.textContent = `결과 ${n}개${hasMore ? '+' : ''}`;
    }

    // ── 결과 한 줄: 보낸 사람 · 날짜 시각 / 내용 (검색어 강조) ──
    function hitElement(hit) {
        const li = el('li', 'search-hit');
        li.dataset.id = hit.id;
        li._hit = hit;
        const btn = el('button');
        btn.type = 'button';
        const top = el('span', 'hit-head');
        const who = el('strong', 'hit-who', hit.sender);
        const when = el('span', 'hit-time', dateLabel(hit.date, hit.time));
        top.append(who, when);
        const text = el('span', 'hit-text');
        btn.append(top, text);
        li.appendChild(btn);
        renderText(li);
        btn.onclick = () => jump(hit.id);
        return li;
    }

    function renderText(li) {
        const text = li.querySelector('.hit-text');
        fillContent(text, li._hit.content, li._hit.mentions || {});   // @멘션 강조·링크 (messages.js)
        text.querySelectorAll('a').forEach(a => a.replaceWith(a.textContent));   // 결과 안에선 링크 대신 글자
        markText(text, query);
    }

    // "2026-11-18" + "14:30" → "11월 18일 14:30" (올해가 아니면 "2025년 11월 18일 14:30")
    function dateLabel(date, time) {
        if (!date) return time || '';
        const [y, m, d] = date.split('-').map(Number);
        const year = y !== new Date().getFullYear() ? `${y}년 ` : '';
        return `${year}${m}월 ${d}일 ${time || ''}`.trim();
    }

    // 화면에 보이는 글자에 검색어가 있는지 (대소문자 무시, 멘션은 "@이름"으로)
    function matches(content, mentions) {
        return !!query && plainContent(content, mentions).toLowerCase().includes(query.toLowerCase());
    }

    // 결과를 누름 → 그 메시지로 이동하고 말풍선 속 검색어 강조
    async function jump(id) {
        const box = document.getElementById('messages');
        if (!await list.jumpTo(id)) return alert('메시지를 찾을 수 없어요.');
        clearMarks(box);
        const bubble = list.find(id)?.querySelector('.bubble');
        if (bubble) markText(bubble, query);
        results.querySelectorAll('.search-hit.on').forEach(x => x.classList.remove('on'));
        results.querySelector(`.search-hit[data-id="${id}"]`)?.classList.add('on');
    }

    // ── 이벤트 ──
    button.setAttribute('aria-expanded', 'false');
    button.addEventListener('click', () => (isOpen() ? close() : open()));
    closeBtn.addEventListener('click', close);
    input.addEventListener('input', () => {
        clearTimeout(timer);
        timer = setTimeout(() => search(input.value.trim()), DEBOUNCE_MS);
    });
    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.isComposing) {
            clearTimeout(timer);
            search(input.value.trim());
        } else if (e.key === 'Escape') {
            e.preventDefault();
            close();
        }
    });
    results.addEventListener('scroll', () => {   // 아래 끝 근처 → 더 오래된 결과
        if (hasMore && !loading && results.scrollTop + results.clientHeight > results.scrollHeight - 80) {
            search(query, true);
        }
    });
    document.addEventListener('keydown', (e) => {   // Ctrl+F (Mac은 ⌘F) → 이 대화 검색
        if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === 'f') {
            e.preventDefault();
            open();
        }
    });
    updateStatus();

    // ── 실시간 반영 (패널을 열어 둔 동안, 검색어가 있을 때만) ──
    const findHit = (id) => results.querySelector(`.search-hit[data-id="${id}"]`);

    // 번호 순서(최신이 위)에 맞는 자리에 넣기 — 불러온 범위보다 오래된 건 넣지 않음 (이어서 찾을 때 나옴)
    function insertHit(hit) {
        const later = [...results.children].find(li => Number(li.dataset.id) < Number(hit.id));
        if (later) results.insertBefore(hitElement(hit), later);
        else if (!hasMore) results.appendChild(hitElement(hit));
        else return;
        updateStatus();
    }

    // 메시지의 날짜: 위쪽에서 가장 가까운 날짜 구분선 (messages.js의 dayDivider)
    function dayOf(msg) {
        for (let e = msg.previousElementSibling;e;e = e.previousElementSibling) {
            if (e.classList.contains('day-divider')) return e.dataset.day;
        }
        return null;
    }

    return {
        onMessage(m) {
            if (!isOpen() || !m.id || findHit(m.id) || !matches(m.content, m.mentions)) return;
            results.prepend(hitElement(m));
            updateStatus();
        },
        onEdited(id, content, mentions = {}) {
            if (!isOpen() || !query) return;
            const li = findHit(id);
            if (!matches(content, mentions)) {   // 검색어가 빠짐 → 결과에서 뺌
                if (li) {
                    li.remove();
                    updateStatus();
                }
                return;
            }
            if (li) {
                li._hit = { ...li._hit, content, mentions };
                renderText(li);
            } else {
                const msg = list.find(id);   // 화면에 있으면 보낸 사람·날짜·시각을 거기서
                if (!msg) return;
                insertHit({ id, content, mentions, sender: msg.dataset.sender, date: dayOf(msg), time: msg.dataset.time });
            }
        },
        onDeleted(id) {
            const li = findHit(id);
            if (!li) return;
            li.remove();
            updateStatus();
        },
        onRenamed(oldName, newName) {
            results.querySelectorAll('.search-hit').forEach(li => {
                if (li._hit.sender !== oldName) return;
                li._hit.sender = newName;
                li.querySelector('.hit-who').textContent = newName;
            });
        }
    };
}

// ───── 검색어 강조 (결과 목록, 이동한 말풍선) ─────
// el 안의 글자 중 q(대소문자 무시)를 <mark class="search-mark">로 감쌈 — 글자만 바꾸고 innerHTML은 안 씀
function markText(el, q) {
    if (!el || !q) return;
    const needle = q.toLowerCase();
    const walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(node => {
        const text = node.nodeValue;
        const lower = text.toLowerCase();
        let at = lower.indexOf(needle);
        if (at < 0) return;
        const frag = document.createDocumentFragment();
        let last = 0;
        while (at >= 0) {
            frag.append(text.slice(last, at));
            const mark = document.createElement('mark');
            mark.className = 'search-mark';
            mark.textContent = text.slice(at, at + needle.length);
            frag.append(mark);
            last = at + needle.length;
            at = lower.indexOf(needle, last);
        }
        frag.append(text.slice(last));
        node.replaceWith(frag);
    });
}

// 강조 지우기 (패널을 닫거나 다른 결과로 이동할 때)
function clearMarks(root) {
    if (!root) return;
    root.querySelectorAll('mark.search-mark').forEach(mark => {
        const parent = mark.parentNode;
        mark.replaceWith(mark.textContent);
        parent.normalize();
    });
}