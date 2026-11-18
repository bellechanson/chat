// ───────── 오른쪽 패널 공통 (검색 search.js, 사진 모아보기 gallery.js) ─────────
// lightbox.js(lightboxOpen) 다음, search.js · gallery.js 앞에 불러옴
// 패널들은 대화 화면(main.pane) 오른쪽 같은 자리를 써서 한 번에 하나만 열림 (하나를 열면 다른 건 닫힘)
//   button  : 여닫는 머리글 버튼 (누르면 열고/닫음, aria-expanded를 맞춤)
//   pane    : 대화 화면 (main.pane) — 패널을 이 안 오른쪽에 붙임
//   label   : 화면 읽기용 패널 이름 ('메시지 검색')
//   onOpen  : 연 뒤 부름 (포커스, 불러오기)
//   onClose : 닫은 뒤 부름 (검색어 강조 지우기)
// 패널 안에서 Esc → 닫기 (크게 보기가 떠 있으면 그것부터 닫힘)
// 돌려주는 값: { panel(내용을 넣을 aside), isOpen(), open(), close(), closeButton(ariaLabel) }
function createPanel({ button, pane, label, onOpen = null, onClose = null }) {
    const panel = makeEl('aside', 'pane-panel hidden');
    panel.setAttribute('aria-label', label);
    pane.appendChild(panel);

    const isOpen = () => !panel.classList.contains('hidden');

    function open() {
        if (pane._openPanel && pane._openPanel !== api) pane._openPanel.close();   // 같은 자리의 다른 패널은 닫음
        pane._openPanel = api;
        panel.classList.remove('hidden');
        pane.classList.add('panel-open');
        button.setAttribute('aria-expanded', 'true');
        if (onOpen) onOpen();
    }

    function close() {
        if (!isOpen()) return;
        if (pane._openPanel === api) pane._openPanel = null;
        panel.classList.add('hidden');
        pane.classList.remove('panel-open');
        button.setAttribute('aria-expanded', 'false');
        if (onClose) onClose();
    }

    // 머리의 ✕ 버튼 (누르면 닫기)
    function closeButton(ariaLabel) {
        const btn = makeEl('button', 'icon-btn sm', '✕');
        btn.type = 'button';
        btn.title = '닫기 (Esc)';
        btn.setAttribute('aria-label', ariaLabel);
        btn.addEventListener('click', close);
        return btn;
    }

    button.setAttribute('aria-expanded', 'false');
    button.addEventListener('click', () => (isOpen() ? close() : open()));
    panel.addEventListener('keydown', (e) => {
        if (e.key !== 'Escape' || e.isComposing || lightboxOpen()) return;
        e.preventDefault();   // 검색창에서 Esc가 글자를 지우지 않게
        close();
    });

    const api = { panel, isOpen, open, close, closeButton };
    return api;
}

// 요소 만들기: makeEl('span', 'hit-time', '14:30')
function makeEl(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
}

// "2026-11-18" + "14:30" → "11월 18일 14:30" (withYear면 올해가 아닐 때 "2025년 11월 18일 14:30")
function dateTimeLabel(date, time, withYear = false) {
    if (!date) return time || '';
    const [y, m, d] = date.split('-').map(Number);
    const year = withYear && y !== new Date().getFullYear() ? `${y}년 ` : '';
    return `${year}${m}월 ${d}일 ${time || ''}`.trim();
}