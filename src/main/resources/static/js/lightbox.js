// ───────── 사진 크게 보기 (채팅방 · 1:1 대화 공통) ─────────
// messages.js 다음에 불러옴 (reactionChip, openReactPicker, closeReactPicker, reactPicker / sidebar.js의 closeOnOutside 사용)
//   메시지 속 사진을 누르면 화면 위에 크게: ←/→ 넘기기, 확대(클릭·휠), 내려받기, 보낸 사람·시각, 이모지 반응
//   Ctrl·Shift+클릭, 가운데 클릭은 예전처럼 새 탭에서 원본
// 실시간 (소켓 코드는 그대로, 메시지 목록이 바뀌는 것을 지켜보다가 다시 그림 — MutationObserver)
//   · 보던 사진이 삭제되면 닫고 "삭제되었어요" 안내
//   · 보는 중에 새 사진이 오면 넘기기 목록·개수(3 / 6)에 바로 추가
//   · 누가 반응을 누르거나 취소하면 크게 보기 화면의 반응도 바로 바뀜

let lbRoot = null;        // 크게 보기 화면 (처음 열 때 한 번 만듦)
let lbBox = null;         // 사진을 고른 메시지 목록 (#messages)
let lbCurrent = null;     // 지금 보는 사진의 메시지 줄 (.msg)
let lbCurrentId = null;   // 그 메시지 번호 (재연결로 목록을 다시 그려도 다시 찾기용)
let lbOpener = null;      // 닫은 뒤 포커스를 돌려줄 사진 링크
let lbObserver = null;
let lbScale = 1;          // 확대 배율 (1 ~ 5)
let lbPending = false;    // 다시 그리기 예약됨 (여러 변경을 한 번에)
const lb = {};            // 화면 요소들

const LB_MAX_SCALE = 5;
const LB_CLICK_SCALE = 2.5;

function lightboxOpen() {
    return !!lbRoot && !lbRoot.classList.contains('hidden');
}

// 목록에 있는 (삭제 안 된) 사진 메시지들, 화면 순서대로
function lightboxPhotos() {
    return [...lbBox.querySelectorAll('.msg:not(.deleted) .att-img')].map(a => a.closest('.msg'));
}

function buildLightbox() {
    lbRoot = document.createElement('div');
    lbRoot.className = 'lightbox hidden';
    lbRoot.setAttribute('role', 'dialog');
    lbRoot.setAttribute('aria-modal', 'true');
    lbRoot.setAttribute('aria-label', '사진 크게 보기');
    lbRoot.innerHTML = `
        <div class="lb-top">
            <span class="lb-who"><strong class="lb-name"></strong><span class="lb-time"></span></span>
            <span class="lb-count" aria-live="polite"></span>
            <a class="lb-btn lb-download" title="원본 내려받기">내려받기</a>
            <button type="button" class="lb-btn lb-close" title="닫기 (Esc)">닫기 ✕</button>
        </div>
        <div class="lb-stage">
            <button type="button" class="lb-nav lb-prev" aria-label="이전 사진" title="이전 사진 (←)">‹</button>
            <img class="lb-img" alt="" draggable="false">
            <button type="button" class="lb-nav lb-next" aria-label="다음 사진" title="다음 사진 (→)">›</button>
        </div>
        <div class="lb-bottom">
            <div class="lb-reactions"></div>
            <button type="button" class="lb-btn lb-react" title="반응 달기">반응</button>
            <span class="lb-hint">클릭·휠로 확대 · ← → 넘기기 · Esc 닫기</span>
        </div>`;
    document.body.appendChild(lbRoot);

    for (const key of ['name', 'time', 'count', 'download', 'close', 'stage', 'prev', 'img', 'next', 'reactions', 'react']) {
        lb[key] = lbRoot.querySelector('.lb-' + key);
    }

    lb.close.onclick = closeLightbox;
    lb.prev.onclick = () => moveLightbox(-1);
    lb.next.onclick = () => moveLightbox(1);
    // 반응 고르기 (messages.js의 고르는 창을 그대로 씀, 이 화면보다 위에 뜸)
    lb.react.onclick = () => {
        if (lbCurrent && lbCurrent._onReact) openReactPicker(lb.react, lbCurrent._onReact);
    };

    // 사진 바깥 어두운 곳을 누르면 닫기
    lb.stage.addEventListener('click', (e) => {
        if (e.target === lb.stage) closeLightbox();
    });
    // Esc로 닫기 (sidebar.js) — 반응 고르는 창을 누를 때는 닫지 않음
    closeOnOutside(lbRoot, closeLightbox, { ignore: t => !!reactPicker && reactPicker.contains(t) });

    // ←/→ 넘기기
    document.addEventListener('keydown', (e) => {
        if (!lightboxOpen()) return;
        if (e.key === 'ArrowLeft') {
            e.preventDefault();
            moveLightbox(-1);
        } else if (e.key === 'ArrowRight') {
            e.preventDefault();
            moveLightbox(1);
        }
    });

    // 확대: 사진을 누르면 2.5배 ↔ 원래대로, 휠로 조금씩 (1 ~ 5배)
    //   확대된 상태에서는 마우스를 움직이는 쪽을 따라 보여 줌
    lb.img.addEventListener('click', (e) => setLightboxScale(lbScale > 1 ? 1 : LB_CLICK_SCALE, e));
    lbRoot.addEventListener('wheel', (e) => {
        e.preventDefault();   // 뒤의 대화가 스크롤되지 않게
        if (e.target !== lb.img) return;
        setLightboxScale(lbScale * (e.deltaY < 0 ? 1.25 : 0.8), e);
    }, { passive: false });
    lb.img.addEventListener('mousemove', (e) => {
        if (lbScale > 1) setLightboxOrigin(e);
    });
}

// 확대 기준점 = 마우스 위치 (사진 안에서의 위치, 확대 전 크기 기준)
function setLightboxOrigin(e) {
    const r = lb.stage.getBoundingClientRect();
    const x = e.clientX - r.left - lb.img.offsetLeft;
    const y = e.clientY - r.top - lb.img.offsetTop;
    lb.img.style.transformOrigin = `${x}px ${y}px`;
}

function setLightboxScale(scale, e) {
    lbScale = Math.min(LB_MAX_SCALE, Math.max(1, scale));
    if (lbScale < 1.05) lbScale = 1;
    if (e) setLightboxOrigin(e);
    lb.img.style.transform = lbScale > 1 ? `scale(${lbScale})` : '';
    lb.img.classList.toggle('zoomed', lbScale > 1);
}

// 메시지 하나의 사진을 보여 줌
function showInLightbox(msg) {
    lbCurrent = msg;
    lbCurrentId = msg.dataset.id || null;
    const a = msg.querySelector('.att-img');
    const thumb = a.querySelector('img');
    const name = thumb ? thumb.alt : '';

    setLightboxScale(1);
    lb.img.style.transformOrigin = '';
    lb.img.src = a.href;
    lb.img.alt = name;
    lb.download.href = a.href;
    lb.download.setAttribute('download', name || '사진');
    lb.name.textContent = msg.querySelector('.msg-name')?.textContent || '';
    lb.time.textContent = msg.querySelector('.msg-time')?.textContent || '';
    renderLightbox();
}

// 개수·넘기기 버튼·반응 다시 그리기 (사진을 바꿀 때, 목록이 바뀔 때)
function renderLightbox() {
    const photos = lightboxPhotos();
    const i = photos.indexOf(lbCurrent);
    lb.count.textContent = `${i + 1} / ${photos.length}`;
    lb.prev.disabled = i <= 0;
    lb.next.disabled = i < 0 || i >= photos.length - 1;

    // 반응: 메시지 아래 칩과 같은 내용 (messages.js의 reactionChip), 반응할 수 없으면 보기만
    const onReact = lbCurrent._onReact || null;
    lb.react.hidden = !onReact;
    lb.reactions.replaceChildren(...(lbCurrent._reactions || []).map(r => reactionChip(r, lbCurrent._myId, onReact)));
}

function moveLightbox(step) {
    const photos = lightboxPhotos();
    const next = photos[photos.indexOf(lbCurrent) + step];
    if (next) {
        if (reactPicker && reactPicker._owner === lb.react) closeReactPicker();
        showInLightbox(next);
    }
}

function openLightbox(box, msg, opener) {
    if (!lbRoot) buildLightbox();
    lbBox = box;
    lbOpener = opener;
    showInLightbox(msg);
    lbRoot.classList.remove('hidden');
    lb.close.focus();

    // 실시간: 메시지 목록이 바뀌면 (새 메시지·삭제·반응·이전 대화 더보기) 다시 그림
    lbObserver = new MutationObserver(scheduleLightboxRefresh);
    lbObserver.observe(lbBox, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
}

function closeLightbox() {
    if (!lightboxOpen()) return;
    lbRoot.classList.add('hidden');
    lbObserver?.disconnect();
    lbObserver = null;
    if (reactPicker && reactPicker._owner === lb.react) closeReactPicker();
    lb.img.removeAttribute('src');
    lbCurrent = null;
    lbCurrentId = null;
    if (lbOpener && lbOpener.isConnected) lbOpener.focus({ preventScroll: true });
    lbOpener = null;
}

function scheduleLightboxRefresh() {
    if (lbPending) return;
    lbPending = true;
    requestAnimationFrame(refreshLightbox);
}

function refreshLightbox() {
    lbPending = false;
    if (!lightboxOpen()) return;

    // 재연결로 목록을 처음부터 다시 그렸으면 같은 메시지를 다시 찾음 (불러오는 중이면 기다림)
    if (!lbBox.contains(lbCurrent)) {
        const again = lbCurrentId && lbBox.querySelector(`.msg[data-id="${lbCurrentId}"]`);
        if (again) {
            lbCurrent = again;
            lbOpener = again.querySelector('.att-img');
        } else {
            if (lbBox.querySelector('.msg')) closeLightbox();   // 다 불러왔는데 없음
            return;
        }
    }

    // 보던 사진이 삭제됨 → 닫고 안내
    if (lbCurrent.classList.contains('deleted')) {
        closeLightbox();
        showLightboxToast('보던 사진이 삭제되었어요.');
        return;
    }
    renderLightbox();
}

// 화면 아래 잠깐 뜨는 안내
function showLightboxToast(text) {
    let t = document.querySelector('.lb-toast');
    if (!t) {
        t = document.createElement('div');
        t.className = 'lb-toast';
        t.setAttribute('role', 'status');
        document.body.appendChild(t);
    }
    t.textContent = text;
    t.classList.add('on');
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.classList.remove('on'), 2500);
}

// 메시지 속 사진 누르기 → 크게 보기 (새 사진도 따로 연결할 필요 없게 문서 전체에서 받음)
document.addEventListener('click', (e) => {
    const a = e.target.closest('.msg .att-img');
    if (!a) return;
    if (e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;   // 새 탭으로 열기는 그대로
    const msg = a.closest('.msg');
    const box = a.closest('.messages');
    if (!msg || !box || msg.classList.contains('deleted')) return;
    e.preventDefault();
    openLightbox(box, msg, a);
});