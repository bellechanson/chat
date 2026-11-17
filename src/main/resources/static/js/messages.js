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

// ───── 메시지 삭제 ─────
function deleteButton(onClick) {
    const btn = document.createElement('button');
    btn.className = 'del-btn';
    btn.textContent = '삭제';
    btn.onclick = onClick;
    return btn;
}

function markDeleted(div) {
    div.classList.add('deleted');
    const bubble = div.querySelector('.bubble');
    bubble.classList.remove('has-img', 'has-file');
    bubble.textContent = '삭제된 메시지입니다';
    div.querySelector('.del-btn')?.remove();
}

// ───── 메시지 한 줄: 아바타 | 이름 · 시각 (· 1) [삭제] / 내용 ─────
//   unread     : true면 시각 옆에 "1" (DM에서 상대가 아직 안 읽음)
//   onDelete   : 있으면 [삭제] 버튼 (권한은 부르는 쪽에서 판단)
//   onImageLoad: 사진이 다 불러와지면 호출 (맨 아래 유지용)
function messageLine({ id, name, mine, time, content, file, deleted, unread = false, onDelete = null, onImageLoad }) {
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
        bubble.textContent = content;
    }

    main.append(head, bubble);
    div.append(avatar, main);

    if (deleted) {
        markDeleted(div);
    } else if (onDelete) {
        head.appendChild(deleteButton(onDelete));
    }
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