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

// ───── 이모지 반응 ─────
// 서버 ReactionService.EMOJIS와 같은 이름·순서 (DB에는 이름으로 저장)
const REACTIONS = [
    { key: 'like', emoji: '👍', label: '좋아요' },
    { key: 'heart', emoji: '❤️', label: '하트' },
    { key: 'laugh', emoji: '😂', label: '웃겨요' },
    { key: 'wow', emoji: '😮', label: '놀라워요' },
    { key: 'sad', emoji: '😢', label: '슬퍼요' },
    { key: 'pray', emoji: '🙏', label: '고마워요' }
];
const REACTION_INFO = Object.fromEntries(REACTIONS.map(r => [r.key, r]));

// 메시지 아래 반응 칩 그리기 (처음 그릴 때, 실시간으로 바뀔 때 모두)
//   reactions: [{ emoji: 'like', userIds: [1, 2], names: ['철수', '영희'] }, ...]
//   내가 누른 칩은 .mine (주황 테두리), 칩을 누르면 같은 반응이면 취소 / 다른 반응이면 바꿈
function setReactions(div, reactions) {
    const onReact = div._onReact;   // messageLine에서 넣어 둔 "반응 누르기" 함수 (없으면 보기만)
    const myId = div._myId;
    let bar = div.querySelector('.reactions');
    const list = (reactions || []).filter(r => REACTION_INFO[r.emoji] && r.userIds.length > 0);
    if (list.length === 0) {
        bar?.remove();
        return;
    }
    if (!bar) {
        bar = document.createElement('div');
        bar.className = 'reactions';
        div.querySelector('.msg-main').appendChild(bar);
    }
    bar.replaceChildren(...list.map(r => {
        const info = REACTION_INFO[r.emoji];
        const mine = r.userIds.some(id => String(id) === String(myId));
        const chip = document.createElement(onReact ? 'button' : 'span');
        chip.className = 'react-chip' + (mine ? ' mine' : '');
        // 누가 눌렀는지 (마우스를 올리면 보임, 실시간으로 함께 바뀜)
        chip.title = `${r.names.join(', ')}님이 ${info.emoji} 반응`
            + (onReact ? (mine ? ' · 누르면 취소' : ' · 누르면 나도 이 반응') : '');
        if (onReact) {
            chip.type = 'button';
            chip.setAttribute('aria-pressed', mine);
            chip.onclick = () => onReact(r.emoji);
        }
        const e = document.createElement('span');
        e.className = 'e';
        e.textContent = info.emoji;
        const n = document.createElement('span');
        n.className = 'n';
        n.textContent = r.userIds.length;
        chip.append(e, n);
        return chip;
    }));
}

// 이모지 고르는 작은 창 (하나를 만들어 두고 위치만 옮겨 씀)
let reactPicker = null;

function closeReactPicker() {
    if (!reactPicker) return;
    reactPicker.classList.add('hidden');
    reactPicker._owner = null;
}

function openReactPicker(button, onPick) {
    if (!reactPicker) {
        reactPicker = document.createElement('div');
        reactPicker.className = 'react-picker hidden';
        reactPicker.setAttribute('role', 'menu');
        reactPicker.setAttribute('aria-label', '반응 고르기');
        document.body.appendChild(reactPicker);
        // 창 밖을 누르거나 Esc, 스크롤, 창 크기 변경이면 닫기
        document.addEventListener('mousedown', (e) => {
            if (!reactPicker.contains(e.target) && !e.target.closest('.react-btn')) closeReactPicker();
        });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeReactPicker();
        });
        window.addEventListener('resize', closeReactPicker);
        document.addEventListener('scroll', closeReactPicker, true);
    }
    // 같은 버튼을 다시 누르면 닫기
    if (reactPicker._owner === button && !reactPicker.classList.contains('hidden')) {
        closeReactPicker();
        return;
    }

    reactPicker.replaceChildren(...REACTIONS.map(r => {
        const b = document.createElement('button');
        b.type = 'button';
        b.setAttribute('role', 'menuitem');
        b.setAttribute('aria-label', r.label);
        b.title = r.label;
        b.textContent = r.emoji;
        b.onclick = () => {
            closeReactPicker();
            onPick(r.key);
        };
        return b;
    }));
    reactPicker._owner = button;
    reactPicker.classList.remove('hidden');

    // 버튼 위쪽에 (공간이 없으면 아래쪽에), 화면 밖으로 나가지 않게
    const rect = button.getBoundingClientRect();
    const w = reactPicker.offsetWidth, h = reactPicker.offsetHeight;
    const left = Math.max(8, Math.min(rect.right - w, window.innerWidth - w - 8));
    let top = rect.top - h - 6;
    if (top < 8) top = rect.bottom + 6;
    reactPicker.style.left = left + 'px';
    reactPicker.style.top = top + 'px';
    reactPicker.querySelector('button').focus();
}

function reactButton(onClick) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'react-btn';
    btn.textContent = '반응';
    btn.title = '반응 달기';
    btn.onclick = () => onClick(btn);
    return btn;
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
//   반응 칩·반응 버튼도 없앰 (고르는 창이 이 메시지 것이면 닫음)
function markDeleted(div) {
    div.classList.add('deleted');
    div.classList.remove('editing');
    const bubble = div.querySelector('.bubble');
    bubble.classList.remove('has-img', 'has-file');
    bubble.textContent = '삭제된 메시지입니다';
    if (reactPicker && reactPicker._owner && div.contains(reactPicker._owner)) closeReactPicker();
    div._onReact = null;
    div.querySelectorAll('.del-btn, .edit-btn, .react-btn, .msg-edited, .reactions').forEach(b => b.remove());
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

// ───── 메시지 한 줄: 아바타 | 이름 · 시각 (수정됨) (· 1) [반응] [수정] [삭제] / 내용 / 반응 칩 ─────
//   editedAt   : 수정한 시각 문자열이면 "(수정됨)" 표시
//   reactions  : 반응 칩 목록 (setReactions), myId: 내 id (내가 누른 칩 표시용)
//   onReact    : 있으면 [반응] 버튼 + 칩 누르기, onReact(이모지 이름)
//   unread     : true면 시각 옆에 "1" (DM에서 상대가 아직 안 읽음)
//   onEdit     : 있으면 [수정] 버튼, 저장할 때 onEdit(새 내용) (글 메시지에만, 권한은 부르는 쪽에서 판단)
//   onDelete   : 있으면 [삭제] 버튼 (권한은 부르는 쪽에서 판단)
//   onImageLoad: 사진이 다 불러와지면 호출 (맨 아래 유지용)
function messageLine({ id, name, mine, time, content, file, deleted, editedAt = null, unread = false,
    reactions = [], myId = null, onReact = null, onDelete = null, onEdit = null, onImageLoad }) {
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
    // [반응]은 글·사진·파일 모두에
    div._myId = myId;
    div._onReact = onReact;
    if (onReact) head.appendChild(reactButton(btn => openReactPicker(btn, onReact)));
    // 글 메시지에만 [수정] (사진·파일은 고칠 내용이 없음)
    if (onEdit && !file) head.appendChild(editButton(() => startEdit(div, onEdit)));
    if (onDelete) head.appendChild(deleteButton(onDelete));
    setReactions(div, reactions);
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