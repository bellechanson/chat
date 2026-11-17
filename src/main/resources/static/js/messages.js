// ───────── 채팅방 · DM 대화방 공통 ─────────

// ───── 날짜 구분선 ─────
// "2026-11-17" → "2026년 11월 17일 화요일"
function dayLabel(day) {
    const [y, m, d] = day.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('ko-KR',
        { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
}

function dayDivider(day) {
    const div = document.createElement('div');
    div.className = 'day-divider';
    div.dataset.day = day;
    div.setAttribute('role', 'separator');
    const span = document.createElement('span');
    span.textContent = dayLabel(day);
    div.appendChild(span);
    return div;
}

// 메시지 목록: 처음 불러오기, 위로 스크롤하면 이전 대화 더보기, 맨 아래 따라가기, 날짜 구분선
//   box       : 메시지 영역 (#messages)
//   fetchPage : (beforeId) → { messages: [...], hasMore } (실패하면 null), 메시지마다 date("2026-11-17")
//   build     : 메시지 하나 → 화면 요소
//   divider   : 처음 불러온 대화 끝에 붙일 요소를 만드는 함수 (없으면 생략)
function createMessageList({ box, fetchPage, build, divider = null }) {
    let oldestId = null;        // 화면에 있는 가장 오래된 메시지 번호
    let hasMore = false;        // 더 이전 대화가 있는지
    let loadingMore = false;    // 불러오는 중인지 (중복 요청 방지)
    let stickToBottom = true;   // 맨 아래를 보고 있는지 (사진이 늦게 불러와져도 아래 유지)
    let topBar = null;          // 맨 위 "더보기" 자리
    let lastDay = null;         // 맨 아래 메시지의 날짜 (새 메시지가 다른 날이면 구분선)

    const isNearBottom = () => box.scrollHeight - box.scrollTop - box.clientHeight < 80;
    const scrollToBottom = () => { box.scrollTop = box.scrollHeight; };

    // 메시지 묶음 → 날짜가 바뀌는 곳마다 구분선을 넣은 조각 (첫 메시지 앞에도 넣음)
    function withDays(messages) {
        const frag = document.createDocumentFragment();
        let day = null;
        messages.forEach(m => {
            if (m.date && m.date !== day) {
                frag.appendChild(dayDivider(m.date));
                day = m.date;
            }
            frag.appendChild(build(m));
        });
        return frag;
    }

    // 맨 위 표시: 불러오는 중 / 더보기 버튼 / 처음입니다
    function updateTopBar() {
        topBar.innerHTML = '';
        if (loadingMore) {
            topBar.textContent = '불러오는 중...';
        } else if (hasMore) {
            const btn = document.createElement('button');
            btn.textContent = '▲ 이전 대화 더보기';
            btn.onclick = loadMore;
            topBar.appendChild(btn);
        } else if (oldestId !== null) {
            topBar.textContent = '대화의 처음입니다';
        }
    }

    // 처음(재연결 포함): 비우고 최근 50개
    async function loadFirst() {
        box.innerHTML = '';
        topBar = document.createElement('div');
        topBar.className = 'top-bar';
        box.appendChild(topBar);
        oldestId = null;
        hasMore = false;
        loadingMore = false;
        lastDay = null;

        try {
            const page = await fetchPage(null);
            if (!page) return;
            const messages = page.messages;
            box.appendChild(withDays(messages));
            if (messages.length > 0) {
                oldestId = messages[0].id;
                lastDay = messages[messages.length - 1].date || null;
                if (divider) box.appendChild(divider());
            }
            hasMore = page.hasMore;
            updateTopBar();
            scrollToBottom();
        } catch (e) {
            console.error('대화 불러오기 실패', e);
        }
    }

    // 더 오래된 50개를 맨 위에 붙임 (보던 위치 유지)
    async function loadMore() {
        if (!hasMore || loadingMore || oldestId === null) return;
        loadingMore = true;
        updateTopBar();

        const prevHeight = box.scrollHeight;
        const prevTop = box.scrollTop;
        try {
            const page = await fetchPage(oldestId);
            if (page && page.messages.length > 0) {
                const messages = page.messages;
                // 이어 붙이는 날짜가 지금 맨 위 날짜와 같으면, 맨 위 구분선은 빼고 하나만 남김
                const first = topBar.nextElementSibling;
                const olderLastDay = messages[messages.length - 1].date;
                if (first && first.classList.contains('day-divider') && first.dataset.day === olderLastDay) {
                    first.remove();
                }
                topBar.after(withDays(messages));
                oldestId = messages[0].id;
                box.scrollTop = box.scrollHeight - prevHeight + prevTop;
            }
            hasMore = page ? page.hasMore : false;
        } catch (e) {
            console.error('이전 대화 더 불러오기 실패', e);
        } finally {
            loadingMore = false;
            updateTopBar();
        }
    }

    box.addEventListener('scroll', () => {
        stickToBottom = isNearBottom();
        if (box.scrollTop < 40) loadMore();   // 맨 위 근처 → 이전 대화
    });

    return {
        loadFirst,
        // 새 메시지 붙이기: 날짜가 바뀌었으면 구분선 먼저
        // 내 메시지거나 맨 아래를 보고 있었으면 따라 내려감 (옛 대화 보는 중이면 그대로)
        append(element, mine, day) {
            const stick = mine || isNearBottom();
            if (day && day !== lastDay) {
                box.appendChild(dayDivider(day));
                lastDay = day;
            }
            box.appendChild(element);
            if (stick) scrollToBottom();
        },
        // 사진이 다 불러와진 뒤 높이가 늘어나도 맨 아래 유지
        keepBottom() {
            if (stickToBottom) scrollToBottom();
        },
        // 메시지 번호로 화면 요소 찾기 (삭제 표시용)
        find(id) {
            return box.querySelector(`.msg[data-id="${id}"]`);
        }
    };
}

// ───── 링크 자동 연결 ─────
// http://, https://, www. 로 시작하는 주소만 링크로 (그 외 글자는 그대로 — innerHTML을 쓰지 않아 안전)
const LINK_RE = /(?:https?:\/\/|www\.)[^\s<>"']+/gi;
const LINK_TRAIL_RE = /[.,!?;:'"\]}>…。、]+$/;   // 주소 끝에 붙은 문장부호는 링크에서 뺌

// 글 → [{text}, {url, href}, ...] 조각
function splitLinks(text) {
    const parts = [];
    let last = 0;
    for (const m of text.matchAll(LINK_RE)) {
        let url = m[0];
        // "https://naver.com." → 마침표 빼기, 닫는 괄호는 여는 괄호가 모자랄 때만 빼기
        //   "(https://a.com)" → 괄호 빼기 / "https://ko.wikipedia.org/wiki/A_(B)" → 그대로
        for (;;) {
            const t = url.match(LINK_TRAIL_RE);
            if (t) {
                url = url.slice(0, -t[0].length);
                continue;
            }
            if (url.endsWith(')') && (url.split('(').length < url.split(')').length)) {
                url = url.slice(0, -1);
                continue;
            }
            break;
        }
        const href = /^https?:\/\//i.test(url) ? url : 'https://' + url;
        let ok = false;
        try {
            const u = new URL(href);
            ok = (u.protocol === 'http:' || u.protocol === 'https:') && u.hostname !== '';   // localhost:8081도 링크
        } catch (e) { /* 주소 모양이 아니면 글자로 둠 */ }
        if (!ok) continue;

        if (m.index > last) parts.push({ text: text.slice(last, m.index) });
        parts.push({ url, href });
        last = m.index + url.length;
    }
    if (last < text.length) parts.push({ text: text.slice(last) });
    return parts;
}

// 요소 안을 글 + 링크로 채우기 (새 메시지, 수정된 메시지, 수정 취소 모두 이걸로 그림)
//   링크는 새 탭에서 열리고(noopener), 연 사이트가 채팅 창을 건드리지 못함
function fillWithLinks(el, text) {
    el.replaceChildren();
    if (!text) return;
    splitLinks(text).forEach(part => {
        if (!part.url) {
            el.append(part.text);
            return;
        }
        const a = document.createElement('a');
        a.className = 'msg-link';
        a.href = part.href;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        a.textContent = part.url;   // 적은 그대로 보여 줌 (www. 앞에 https://를 붙이지 않음)
        el.append(a);
    });
}

// ───── 메시지 삭제 ─────
function deleteButton(onClick) {
    const btn = document.createElement('button');
    btn.className = 'del-btn';
    btn.textContent = '삭제';
    btn.onclick = onClick;
    return btn;
}

// 삭제 표시 (수정하던 중이었으면 수정창도 같이 닫힘 — 말풍선 내용을 통째로 바꾸므로)
function markDeleted(div) {
    div.classList.add('deleted');
    div.classList.remove('editing');
    const bubble = div.querySelector('.bubble');
    bubble.classList.remove('has-img', 'has-file');
    bubble.textContent = '삭제된 메시지입니다';
    div.querySelectorAll('.del-btn, .edit-btn, .msg-edited').forEach(b => b.remove());
}

// ───── 메시지 수정 ─────
function editButton(onClick) {
    const btn = document.createElement('button');
    btn.className = 'edit-btn';
    btn.textContent = '수정';
    btn.onclick = onClick;
    return btn;
}

// 시각 옆 "(수정됨)" 표시 (마우스를 올리면 수정 시각)
function setEditedMark(div, editedAt) {
    const head = div.querySelector('.msg-head');
    let mark = head.querySelector('.msg-edited');
    if (!mark) {
        mark = document.createElement('span');
        mark.className = 'msg-edited';
        mark.textContent = '(수정됨)';
        head.querySelector('.msg-time').after(mark);
    }
    mark.title = editedAt ? `수정: ${editedAt}` : '수정됨';
}

// 수정 신호를 받았을 때 (나·다른 사람·내 다른 탭 모두): 내용 교체 + "(수정됨)"
//   그 메시지를 수정하던 중이었으면 수정창을 닫고 새 내용으로
function markEdited(div, content, editedAt) {
    if (div.classList.contains('deleted')) return;
    div.classList.remove('editing');
    fillWithLinks(div.querySelector('.bubble'), content);   // 새 내용도 링크로
    setEditedMark(div, editedAt);
}

// 말풍선 자리에서 바로 고치기: Enter 저장, Esc 취소
//   save(newText): 바뀐 내용이 있을 때만 불림 (서버로 보내기는 부르는 쪽에서)
function startEdit(div, save) {
    if (div.classList.contains('editing') || div.classList.contains('deleted')) return;
    const bubble = div.querySelector('.bubble');
    const original = bubble.textContent;   // 링크가 있어도 글자 그대로 (적은 주소 그대로 입력칸에)
    div.classList.add('editing');

    const input = document.createElement('input');
    input.className = 'edit-input';
    input.maxLength = 1000;
    input.value = original;
    input.setAttribute('aria-label', '메시지 수정');

    const hint = document.createElement('div');
    hint.className = 'edit-hint';
    const saveBtn = document.createElement('button');
    saveBtn.type = 'button';
    saveBtn.className = 'btn btn-primary btn-sm';
    saveBtn.textContent = '저장';
    const cancelBtn = document.createElement('button');
    cancelBtn.type = 'button';
    cancelBtn.className = 'btn btn-sm';
    cancelBtn.textContent = '취소';
    const keys = document.createElement('span');
    keys.textContent = 'Enter 저장 · Esc 취소';
    hint.append(saveBtn, cancelBtn, keys);

    bubble.replaceChildren(input, hint);
    input.focus();
    input.setSelectionRange(input.value.length, input.value.length);

    function close(text) {
        if (!div.classList.contains('editing')) return;   // 이미 삭제·수정 신호로 닫힘
        div.classList.remove('editing');
        fillWithLinks(bubble, text);
    }
    function done() {
        const text = input.value.trim();
        if (!text || text === original) return close(original);   // 비었거나 그대로면 취소
        close(text);                 // 화면엔 바로 반영, 서버의 수정 신호가 오면 "(수정됨)"이 붙음
        save(text);
    }

    input.addEventListener('keydown', (e) => {
        if (e.isComposing) return;   // 한글 조합 중 Enter는 무시
        if (e.key === 'Enter') {
            e.preventDefault();
            done();
        } else if (e.key === 'Escape') {
            e.preventDefault();
            close(original);
        }
    });
    saveBtn.onclick = done;
    cancelBtn.onclick = () => close(original);
}

// ───── 메시지 한 줄: 아바타 | 이름 · 시각 (수정됨) (· 1) [수정] [삭제] / 내용 ─────
//   editedAt   : 수정한 시각 문자열이면 "(수정됨)" 표시
//   unread     : true면 시각 옆에 "1" (DM에서 상대가 아직 안 읽음)
//   onEdit     : 있으면 [수정] 버튼, 저장할 때 onEdit(새 내용) (글 메시지에만, 권한은 부르는 쪽에서 판단)
//   onDelete   : 있으면 [삭제] 버튼 (권한은 부르는 쪽에서 판단)
//   onImageLoad: 사진이 다 불러와지면 호출 (맨 아래 유지용)
function messageLine({ id, name, mine, time, content, file, deleted, editedAt = null, unread = false,
    onDelete = null, onEdit = null, onImageLoad }) {
    const div = document.createElement('div');
    div.className = 'msg' + (mine ? ' me' : '');
    if (id) div.dataset.id = id;   // 삭제할 때 찾기용

    const avatar = document.createElement('span');
    avatar.className = 'avatar ' + (mine ? 'avatar-accent' : 'av-' + tintOf(name));
    avatar.textContent = initialOf(name);

    const main = document.createElement('div');
    main.className = 'msg-main';

    const head = document.createElement('div');
    head.className = 'msg-head';
    const nameEl = document.createElement('strong');
    nameEl.className = 'msg-name';
    nameEl.textContent = name;
    const timeEl = document.createElement('span');
    timeEl.className = 'msg-time';
    timeEl.textContent = time;
    head.append(nameEl, timeEl);
    if (unread) {
        const mark = document.createElement('span');
        mark.className = 'read-mark';
        mark.title = '상대가 아직 읽지 않음';
        mark.textContent = '1';
        head.appendChild(mark);
    }

    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    if (file) {   // 사진·파일 메시지 (files.js)
        bubble.classList.add(file.image ? 'has-img' : 'has-file');
        bubble.appendChild(fileElement(file, onImageLoad));
    } else {
        fillWithLinks(bubble, content);   // 주소는 누를 수 있는 링크로
    }

    main.append(head, bubble);
    div.append(avatar, main);

    if (deleted) {
        markDeleted(div);
        return div;
    }
    if (editedAt) setEditedMark(div, editedAt);
    // 글 메시지에만 [수정] (사진·파일은 고칠 내용이 없음)
    if (onEdit && !file) head.appendChild(editButton(() => startEdit(div, onEdit)));
    if (onDelete) head.appendChild(deleteButton(onDelete));
    return div;
}

// ───── 입력 중 신호 보내기 ─────
// 글자가 있으면 2초에 한 번 send(true), 입력칸을 다 지우면 send(false)
function watchTyping(input, send) {
    const INTERVAL = 2000;
    let last = 0;   // 마지막으로 "입력 중"을 보낸 시각 (0 = 안 보냄)

    input.addEventListener('input', () => {
        const hasText = input.value.trim() !== '';
        const now = Date.now();
        if (hasText && now - last > INTERVAL) {
            last = now;
            send(true);
        } else if (!hasText && last) {
            last = 0;
            send(false);
        }
    });

    // 메시지를 보낸 뒤 호출 → 다음에 입력을 시작하면 바로 다시 신호
    return { sent() { last = 0; } };
}