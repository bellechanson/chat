// ───────── 채팅방 · DM 대화방 공통 ─────────
// sidebar.js(closeOnOutside, tintOf, initialOf) · files.js(fileElement) 와 함께 불러옴

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

// ───── 연속 메시지 묶기 ─────
// 같은 사람이 같은 분(시각 "14:30"이 같음)에 연달아 보낸 메시지 → 두 번째부터 .cont (아바타·이름·시각 생략)
//   바로 앞이 날짜 구분선·입장 안내면 묶지 않음 (메시지끼리 붙어 있을 때만)
function markContinued(div) {
    const prev = div.previousElementSibling;
    const cont = !!prev && prev.classList.contains('msg') && !!div.dataset.sender
        && prev.dataset.sender === div.dataset.sender && prev.dataset.time === div.dataset.time
        && !div.querySelector('.reply-quote');   // 답장은 이름이 보이게 묶지 않음
    div.classList.toggle('cont', cont);
}

// 목록 전체 다시 묶기 (처음 불러올 때, 이전 대화를 위에 붙일 때 — 경계의 메시지도 맞춰 줌)
function groupMessages(box) {
    box.querySelectorAll('.msg').forEach(markContinued);
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
            groupMessages(box);
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
                groupMessages(box);
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

    // 메시지 번호로 화면 요소 찾기 (삭제·수정·반응을 그 메시지에 반영할 때)
    const find = (id) => box.querySelector(`.msg[data-id="${id}"]`);

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
            if (element.classList.contains('msg')) markContinued(element);
            if (stick) scrollToBottom();
        },
        // 사진이 다 불러와진 뒤 높이가 늘어나도 맨 아래 유지
        keepBottom() {
            if (stickToBottom) scrollToBottom();
        },
        find,
        // 답장 인용을 누름 → 원래 메시지로 이동 (화면에 없으면 이전 대화를 더 불러오며 찾음, 최대 1000개)
        //   찾으면 가운데로 스크롤하고 잠깐 강조, 못 찾으면 missingText를 토스트로 띄우고 false
        async jumpTo(id, missingText = '메시지를 찾을 수 없어요.') {
            let target = find(id);
            for (let tries = 0;!target && hasMore && tries < 25;tries++) {
                if (loadingMore) await new Promise(r => setTimeout(r, 200));   // 스크롤로 불러오는 중이면 기다림
                else await loadMore();
                target = find(id);
            }
            if (!target) {
                toast(missingText);   // dialog.js
                return false;
            }
            target.scrollIntoView({ block: 'center', behavior: 'smooth' });
            target.classList.remove('flash');
            void target.offsetWidth;   // 같은 메시지를 다시 눌러도 강조가 다시 보이게
            target.classList.add('flash');
            setTimeout(() => target.classList.remove('flash'), 1600);
            return true;
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

// 요소 끝에 글 + 링크 붙이기
//   링크는 새 탭에서 열리고(noopener), 연 사이트가 채팅 창을 건드리지 못함
function appendLinks(el, text) {
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

// ───── @멘션 ─────
// 서버는 "@영희"를 "<@12>"로 저장하고, 메시지마다 mentions {12: '영희'}(지금 이름)를 같이 보냄
//   mentions에 있는 번호만 "@이름"으로 그림 (직접 "<@5>"라고 적은 글자는 그대로)
const MENTION_RE = /<@(\d+)>/g;

// 말풍선 내용 그리기: @멘션은 강조, 주소는 링크 (새 메시지, 수정된 메시지, 수정 취소 모두 이걸로)
function fillContent(el, text, mentions = {}) {
    el.replaceChildren();
    if (!text) return;
    let last = 0;
    for (const m of text.matchAll(MENTION_RE)) {
        const name = mentions[m[1]];
        if (name === undefined) continue;
        appendLinks(el, text.slice(last, m.index));
        const span = document.createElement('span');
        span.className = 'mention';
        span.dataset.userId = m[1];
        span.textContent = '@' + name;
        el.appendChild(span);
        last = m.index + m[0].length;
    }
    appendLinks(el, text.slice(last));
}

// "<@12> 안녕" → "@영희 안녕" (답장 미리보기용 글자)
function plainContent(text, mentions = {}) {
    return (text || '').replace(MENTION_RE, (token, id) => mentions[id] === undefined ? token : '@' + mentions[id]);
}

// 누가 닉네임을 바꾸거나 탈퇴함 → 화면의 @멘션 글자를 새 이름으로 (/topic/nickname)
function renameMentions(userId, nickname) {
    document.querySelectorAll(`.mention[data-user-id="${userId}"]`).forEach(span => {
        span.textContent = '@' + nickname;
    });
}

// ───── 답장 인용 ─────
const QUOTE_MAX = 80;   // 서버 ReplyDto와 같은 길이

// 인용에 넣을 글: 줄바꿈·여러 칸 띄어쓰기는 한 칸으로, 80자가 넘으면 "…"
function quotePreview(text) {
    const t = (text || '').replace(/\s+/g, ' ').trim();
    return t.length > QUOTE_MAX ? t.slice(0, QUOTE_MAX) + '…' : t;
}

// 메시지 위 작은 인용 "영희  강아지 귀엽네요" — 누르면 onJump(원래 메시지 번호)
//   reply: { id, sender, preview, deleted } (서버 ReplyDto)
function replyQuote(reply, onJump) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'reply-quote';
    btn.dataset.replyId = reply.id;
    btn.title = '원래 메시지로 이동';
    const who = document.createElement('strong');
    who.className = 'reply-who';
    who.textContent = reply.sender || '';
    const text = document.createElement('span');
    text.className = 'reply-text';
    btn.append(who, text);
    setQuoteText(btn, reply.deleted ? null : reply.preview);
    if (onJump) btn.onclick = () => onJump(reply.id);
    return btn;
}

// preview가 null이면 "삭제된 메시지입니다"
function setQuoteText(el, preview) {
    const deleted = preview == null;
    el.classList.toggle('deleted', deleted);
    el.querySelector('.reply-text').textContent = deleted ? '삭제된 메시지입니다' : preview;
}

// 원래 메시지가 수정·삭제됨 → 그 메시지를 인용한 답장들도 바로 바꿈 (preview가 null이면 삭제)
//   원래 메시지가 화면에 없어도(아직 안 불러온 예전 메시지) 인용은 바뀜
function updateQuotes(id, preview) {
    document.querySelectorAll(`.reply-quote[data-reply-id="${id}"]`).forEach(q => setQuoteText(q, preview));
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
    div._reactions = list;          // 사진 크게 보기 화면이 같은 반응을 그릴 때 씀 (lightbox.js)
    if (list.length === 0) {
        bar?.remove();
        return;
    }
    if (!bar) {
        bar = document.createElement('div');
        bar.className = 'reactions';
        div.querySelector('.msg-main').appendChild(bar);
    }
    bar.replaceChildren(...list.map(r => reactionChip(r, myId, onReact)));
}

// 반응 칩 하나: 👍 3 (메시지 아래, 사진 크게 보기 화면 모두 이걸로 그림)
//   onReact가 없으면 보기만 (눌러지지 않는 칩)
function reactionChip(r, myId, onReact) {
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
        // 창 밖 클릭, Esc, 스크롤·창 크기 변경·창 전환이면 닫기 (sidebar.js의 closeOnOutside)
        //   열어 준 [반응] 버튼은 빼고 — 그 버튼은 다시 누르면 닫히도록 아래에서 처리
        closeOnOutside(reactPicker, closeReactPicker,
            { ignore: t => t === reactPicker._owner, floating: true });
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

// ───── 메시지 오른쪽 버튼 ([반응] [수정] [삭제] …) ─────
// 모두 같은 모양(.msg-tool), 마우스를 올리면 보임 — 새 버튼도 이 함수로 만들면 CSS 추가가 필요 없음
//   onClick(btn): 누른 버튼을 넘겨 줌 (반응 고르는 창 위치 잡기용)
function toolButton(label, title, onClick, danger = false) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'msg-tool' + (danger ? ' danger' : '');
    btn.textContent = label;
    btn.title = title;
    btn.onclick = () => onClick(btn);
    return btn;
}

// ───── 사진 묶음 (여러 장을 메시지 하나로) ─────
const ALBUM_VISIBLE = 9;   // 격자에 보이는 장수 (넘으면 9번째 칸에 "+21", 나머지는 크게 보기에서 넘겨 봄)

// 사진 묶음 격자: 1장이면 지금처럼 한 장, 2장부터 격자 (2·4장은 2칸, 그 밖은 3칸)
//   onDeletePhoto(사진번호): 있으면 칸마다 ✕ (그 사진만 삭제, 권한은 부르는 쪽에서 판단)
//   각 사진 링크(.att-img)에 data-photo-id → 크게 보기 화면에서도 그 사진만 지울 수 있음
function albumElement(photos, onImageLoad, onDeletePhoto) {
    if (photos.length === 1) {
        const a = fileElement(photos[0], onImageLoad);
        a.dataset.photoId = photos[0].photoId;
        return a;   // 한 장 남으면 메시지의 [삭제]로 지움
    }
    const grid = document.createElement('div');
    grid.className = 'album';
    grid.dataset.count = Math.min(photos.length, ALBUM_VISIBLE);
    photos.forEach((p, i) => {
        const cell = document.createElement('div');
        cell.className = 'album-cell';
        cell.hidden = i >= ALBUM_VISIBLE;   // 숨긴 칸도 크게 보기에서는 넘겨 볼 수 있음
        const a = fileElement(p, onImageLoad);
        a.dataset.photoId = p.photoId;
        cell.appendChild(a);
        if (i === ALBUM_VISIBLE - 1 && photos.length > ALBUM_VISIBLE) {
            const more = document.createElement('span');
            more.className = 'album-more';
            more.textContent = '+' + (photos.length - ALBUM_VISIBLE);
            cell.appendChild(more);
        }
        if (onDeletePhoto) {
            const del = document.createElement('button');
            del.type = 'button';
            del.className = 'album-del';
            del.title = '이 사진만 삭제';
            del.setAttribute('aria-label', `${i + 1}번째 사진 삭제`);
            del.textContent = '✕';
            del.onclick = () => onDeletePhoto(p.photoId);
            cell.appendChild(del);
        }
        grid.appendChild(cell);
    });
    return grid;
}

// 묶음에서 사진이 지워짐 (나·다른 사람 모두) → 남은 사진으로 다시 그림
function setAlbum(div, photos) {
    if (div.classList.contains('deleted') || !photos || photos.length === 0) return;
    div.querySelector('.bubble').replaceChildren(albumElement(photos, div._onImageLoad, div._onDeletePhoto));
}

// ───── 메시지 삭제 ─────
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
    div._onDeletePhoto = null;
    div.querySelectorAll('.msg-tools, .msg-edited, .reactions, .reply-quote').forEach(el => el.remove());
    div.classList.remove('calls-me');
}

// ───── 메시지 수정 ─────
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
//   mentions: 채팅방 메시지의 멘션 이름 (새로 부르거나 뺄 수 있으니 "나를 부름" 표시도 다시 계산)
function markEdited(div, content, editedAt, mentions = {}) {
    if (div.classList.contains('deleted')) return;
    div.classList.remove('editing');
    fillContent(div.querySelector('.bubble'), content, mentions);   // 새 내용도 멘션·링크로
    setEditedMark(div, editedAt);
    div.classList.toggle('calls-me', callsMe(div, mentions));
}

// 이 메시지가 나를 부르는지: 남의 메시지가 나를 @멘션했거나, 내 메시지에 단 답장
function callsMe(div, mentions) {
    if (div.classList.contains('me') || div._myId == null) return false;
    return div._repliesToMe || String(div._myId) in (mentions || {});
}

// 말풍선 자리에서 바로 고치기: Enter 저장, Esc 취소
//   save(newText): 바뀐 내용이 있을 때만 불림 (서버로 보내기는 부르는 쪽에서)
function startEdit(div, save) {
    if (div.classList.contains('editing') || div.classList.contains('deleted')) return;
    const bubble = div.querySelector('.bubble');
    const original = bubble.textContent;   // 링크·멘션이 있어도 글자 그대로 ("@영희", 적은 주소 그대로 입력칸에)
    const originalNodes = [...bubble.childNodes];   // 취소하면 멘션 강조까지 그대로 되돌림
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
        if (text === null) bubble.replaceChildren(...originalNodes);
        else fillContent(bubble, text);
    }
    function done() {
        const text = input.value.trim();
        if (!text || text === original) return close(null);   // 비었거나 그대로면 취소
        close(text);                 // 화면엔 바로 반영, 서버의 수정 신호가 오면 멘션 강조·"(수정됨)"이 붙음
        save(text);
    }

    input.addEventListener('keydown', (e) => {
        if (e.isComposing) return;   // 한글 조합 중 Enter는 무시
        if (e.key === 'Enter') {
            e.preventDefault();
            done();
        } else if (e.key === 'Escape') {
            e.preventDefault();
            close(null);
        }
    });
    saveBtn.onclick = done;
    cancelBtn.onclick = () => close(null);
}

// ───── 메시지 한 줄: 아바타 | 이름 · 시각 (수정됨) (· 1) [반응] [수정] [삭제] / 내용 / 반응 칩 ─────
//   editedAt   : 수정한 시각 문자열이면 "(수정됨)" 표시
//   reactions  : 반응 칩 목록 (setReactions), myId: 내 id (내가 누른 칩 표시용)
//   onReact    : 있으면 [반응] 버튼 + 칩 누르기, onReact(이모지 이름)
//   unread     : true면 시각 옆에 "1" (DM에서 상대가 아직 안 읽음)
//   onEdit     : 있으면 [수정] 버튼, 저장할 때 onEdit(새 내용) (글 메시지에만, 권한은 부르는 쪽에서 판단)
//   onDelete   : 있으면 [삭제] 버튼 (권한은 부르는 쪽에서 판단)
//   photos     : 사진 묶음이면 사진 목록 (격자로 그림, 글 수정 불가)
//   onDeletePhoto: 있으면 묶음 칸마다 ✕, onDeletePhoto(사진번호)
//   onImageLoad: 사진이 다 불러와지면 호출 (맨 아래 유지용)
//   reply      : 답장이면 원래 메시지 인용 { id, sender, preview, deleted }, onJump(번호): 인용을 누르면
//   mentions   : 내용 속 "<@번호>"를 그릴 이름 {번호: 이름} (채팅방)
//   repliesToMe: 내 메시지에 단 답장이면 true → 말풍선에 "나를 부름" 표시 (채팅방)
//   onReply    : 있으면 [답장] 버튼
//   onPin      : 있으면 [고정] 버튼 → 이 메시지를 공지로 (권한은 부르는 쪽에서 판단, pin.js)
function messageLine({ id, name, mine, time, content, file, deleted, editedAt = null, unread = false,
    reactions = [], myId = null, onReact = null, onDelete = null, onEdit = null, photos = [],
    onDeletePhoto = null, onImageLoad, reply = null, onJump = null, mentions = {}, repliesToMe = false,
    onReply = null, onPin = null }) {
    const div = document.createElement('div');
    div.className = 'msg' + (mine ? ' me' : '');
    if (id) div.dataset.id = id;   // 삭제·수정·반응 신호가 오면 이 번호로 찾음

    div.classList.add(mine ? 'tint-me' : 'tint-' + tintOf(name));   // 말풍선 색 = 아바타 색
    div.dataset.sender = name;     // 연속 메시지 묶기 (markContinued)
    div.dataset.time = time;

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
    const album = !!(photos && photos.length);
    if (album) {   // 사진 묶음
        bubble.classList.add('has-img');
        bubble.appendChild(albumElement(photos, onImageLoad, onDeletePhoto));
    } else if (file) {   // 사진·파일 메시지 하나 (files.js)
        bubble.classList.add(file.image ? 'has-img' : 'has-file');
        bubble.appendChild(fileElement(file, onImageLoad));
    } else {
        fillContent(bubble, content, mentions);   // @멘션은 강조, 주소는 누를 수 있는 링크로
    }

    main.append(head);
    if (reply && !deleted) main.appendChild(replyQuote(reply, onJump));   // 말풍선 위 작은 인용
    main.appendChild(bubble);
    div.append(avatar, main);

    if (deleted) {
        markDeleted(div);
        return div;
    }
    if (editedAt) setEditedMark(div, editedAt);
    div._myId = myId;
    div._onReact = onReact;
    div._onImageLoad = onImageLoad;       // 묶음을 다시 그릴 때 (setAlbum)
    div._onDeletePhoto = onDeletePhoto;
    div._repliesToMe = repliesToMe;
    div.classList.toggle('calls-me', callsMe(div, mentions));

    // 오른쪽 버튼 묶음: [답장] [반응] [고정](글·사진·파일 모두) [수정](글 메시지만) [삭제]
    const tools = document.createElement('span');
    tools.className = 'msg-tools';
    if (onReply) tools.appendChild(toolButton('답장', '답장하기', () => onReply()));
    if (onReact) tools.appendChild(toolButton('반응', '반응 달기', btn => openReactPicker(btn, onReact)));
    if (onPin) tools.appendChild(toolButton('고정', '공지로 고정', () => onPin()));
    if (onEdit && !file && !album) tools.appendChild(toolButton('수정', '메시지 수정', () => startEdit(div, onEdit)));
    if (onDelete) tools.appendChild(toolButton('삭제', '메시지 삭제', () => onDelete(), true));
    if (tools.children.length > 0) head.appendChild(tools);

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


// ───── 서버로 보내는 메시지 동작 (채팅방, 1:1 공통) ─────
//   getClient : () => 지금 STOMP 연결 (재연결되면 바뀌므로 함수로 받음)
//   base      : '/app/chat/3' (채팅방) 또는 '/app/dm/3' (1:1)
//   audience  : 삭제 확인 문구에 넣을 말 ('모든 사람의 화면에서' / '상대 화면에서도')
// 연결이 끊겨 있으면 아무것도 안 함 (send는 보냈는지 true/false)
function messageActions({ getClient, base, audience }) {
    const connected = () => { const c = getClient(); return !!c && c.connected; };
    function publish(path, body) {
        if (!connected()) return false;
        getClient().publish({ destination: `${base}/${path}`, body: JSON.stringify(body) });
        return true;
    }
    return {
        send: (content, replyTo = null) => publish('send', { content, replyTo }),
        typing: (on) => publish('typing', { typing: on }),
        // 사진·파일 올리는 중 → 상대 화면에 "○○님이 사진 12장을 보내는 중" (다 올리면 0, 0)
        uploading: (photos, files) => publish('typing', { typing: photos + files > 0, photos, files }),
        react: (id, emoji) => publish('react', { id, emoji }),
        edit: (id, content) => publish('edit', { id, content }),
        async remove(id) {
            if (!connected()) return;
            if (!await askConfirm(`메시지를 삭제할까요?\n${audience} "삭제된 메시지입니다"로 바뀝니다.`,
                { ok: '삭제', danger: true })) return;   // dialog.js
            publish('delete', { id });
        },
        // 사진 묶음에서 한 장만 삭제 (마지막 한 장이면 메시지 삭제)
        async removePhoto(id, photoId) {
            if (!connected()) return;
            if (!await askConfirm(`이 사진만 삭제할까요?\n${audience} 이 사진이 빠집니다.`,
                { ok: '삭제', danger: true })) return;   // dialog.js
            publish('delete-photo', { id, photoId: Number(photoId) });
        }
    };
}

// ───── 입력칸: [보내기]·Enter로 보내기 + 입력 중 신호 + 답장 (채팅방, 1:1 공통) ─────
//   actions     : messageActions가 돌려준 값
//   loadMembers : 있으면 @를 칠 때 이름 고르기 (채팅방) — () => Promise<[{id, nickname}]>
// 돌려주는 값: replyTo({ id, sender, preview }) 답장 시작, cancelReplyIf(번호) 그 메시지가 지워지면 답장 닫기
function setupComposer({ input, sendButton, actions, loadMembers = null }) {
    const typing = watchTyping(input, (on) => actions.typing(on));   // 2초 간격으로 "입력 중"
    const picker = loadMembers ? setupMentionPicker(input, loadMembers) : null;

    // 입력칸 위 "영희님에게 답장  강아지 귀엽네요  ✕"
    let reply = null;
    const bar = document.createElement('div');
    bar.className = 'reply-bar hidden';
    const who = document.createElement('strong');
    who.className = 'reply-who';
    const text = document.createElement('span');
    text.className = 'reply-text';
    const close = document.createElement('button');
    close.type = 'button';
    close.textContent = '✕';
    close.title = '답장 취소 (Esc)';
    close.setAttribute('aria-label', '답장 취소');
    close.onclick = () => clearReply();
    bar.append(who, text, close);
    input.closest('.composer-box').before(bar);

    function clearReply() {
        reply = null;
        bar.classList.add('hidden');
    }

    function send() {
        const content = input.value.trim();
        if (!content || !actions.send(content, reply ? reply.id : null)) return;
        input.value = '';
        input.focus();
        typing.sent();
        clearReply();
    }

    sendButton.onclick = send;
    input.addEventListener('keydown', (e) => {
        if (picker && picker.handleKey(e)) return;   // 이름 고르는 중이면 ↑↓ Enter Esc는 거기서
        if (e.key === 'Escape' && reply) {
            e.preventDefault();
            clearReply();
        } else if (e.key === 'Enter' && !e.isComposing) {
            send();
        }
    });

    return {
        replyTo(info) {
            reply = info;
            who.textContent = `${info.sender}님에게 답장`;
            text.textContent = quotePreview(info.preview);
            bar.classList.remove('hidden');
            input.focus();
        },
        cancelReplyIf(id) {
            if (reply && String(reply.id) === String(id)) clearReply();
        }
    };
}

// ───── @를 치면 이름 고르기 (채팅방 입력칸) ─────
//   "@" 또는 "@영" 처럼 치면 이 방 사람 목록이 뜨고 ↑↓로 고르고 Enter·Tab(또는 클릭)으로 넣음, Esc 닫기
//   넣으면 "@영희 " (서버가 보낼 때 멘션으로 바꿈)
// 돌려주는 값: handleKey(e) — 목록이 떠 있을 때 키를 처리했으면 true
function setupMentionPicker(input, loadMembers) {
    const MAX_ITEMS = 8;
    const CACHE_MS = 30000;   // 사람 목록은 30초 동안 다시 안 불러옴
    const pop = document.createElement('ul');
    pop.className = 'mention-pop hidden';
    pop.setAttribute('role', 'listbox');
    input.closest('.composer-box').appendChild(pop);

    let members = null;
    let loadedAt = 0;
    let items = [];
    let active = 0;
    let start = -1;   // "@"의 위치

    async function loadCached() {
        if (!members || Date.now() - loadedAt > CACHE_MS) {
            try {
                members = await loadMembers();
            } catch (e) {
                members = members || [];
            }
            loadedAt = Date.now();
        }
        return members;
    }

    async function update() {
        const caret = input.selectionStart;
        const m = input.value.slice(0, caret).match(/(^|\s)@([^\s@]*)$/);   // 줄 처음이나 띄어쓰기 뒤의 @
        if (!m) return close();
        start = caret - m[2].length - 1;
        const q = m[2].toLowerCase();
        const list = await loadCached();
        items = list.filter(u => u.nickname.toLowerCase().includes(q)).slice(0, MAX_ITEMS);
        if (items.length === 0) return close();
        active = 0;
        render();
    }

    function render() {
        pop.replaceChildren(...items.map((u, i) => {
            const li = document.createElement('li');
            li.setAttribute('role', 'option');
            li.className = i === active ? 'active' : '';
            li.setAttribute('aria-selected', i === active);
            const av = document.createElement('span');
            av.className = 'avatar avatar-sm av-' + tintOf(u.nickname);   // sidebar.js
            av.textContent = initialOf(u.nickname);
            const name = document.createElement('span');
            name.textContent = u.nickname;
            li.append(av, name);
            li.addEventListener('mousedown', (e) => {   // 입력칸 포커스를 잃지 않게 mousedown에서
                e.preventDefault();
                pick(i);
            });
            return li;
        }));
        pop.classList.remove('hidden');
    }

    function close() {
        pop.classList.add('hidden');
        items = [];
    }

    function pick(i) {
        const u = items[i];
        if (!u) return close();
        const caret = input.selectionStart;
        const insert = '@' + u.nickname + ' ';
        input.value = input.value.slice(0, start) + insert + input.value.slice(caret);
        const pos = start + insert.length;
        input.setSelectionRange(pos, pos);
        input.focus();
        close();
    }

    input.addEventListener('input', update);
    input.addEventListener('blur', close);

    return {
        handleKey(e) {
            if (pop.classList.contains('hidden') || e.isComposing) return false;
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
                active = (active + (e.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length;
                render();
            } else if (e.key === 'Enter' || e.key === 'Tab') {
                pick(active);
            } else if (e.key === 'Escape') {
                close();
            } else {
                return false;
            }
            e.preventDefault();
            return true;
        }
    };
}