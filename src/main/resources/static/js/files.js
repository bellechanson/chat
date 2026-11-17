// ───────── 사진·파일 보내기 (채팅방, DM 공통) ─────────
// api.js(csrfHeaders) 다음에 불러와야 함
let uploadMaxMb = 50;                  // 파일 하나 최대(MB) — setupAttach가 서버 설정값(data-max-mb)으로 바꿈
const maxUploadBytes = () => uploadMaxMb * 1024 * 1024;
const MAX_FILES_AT_ONCE = 10;          // 한 번에 보낼 수 있는 개수 (끌어다 놓기, 여러 개 고르기)

// 파일 올리기 → 성공하면 메시지는 WebSocket으로 도착함
async function uploadFile(url, file) {
    if (file.size > maxUploadBytes()) {
        alert(`${uploadMaxMb}MB 이하 파일만 보낼 수 있어요.`);
        return false;
    }
    const form = new FormData();
    form.append('file', file);

    try {
        const res = await fetch(url, { method: 'POST', headers: csrfHeaders(), body: form });   // api.js
        if (res.ok) return true;
        const data = await res.json().catch(() => null);
        alert(data?.message || (res.status === 413 ? `${uploadMaxMb}MB 이하 파일만 보낼 수 있어요.` : '파일을 보내지 못했습니다.'));
    } catch (e) {
        alert('파일을 보내지 못했습니다. 연결 상태를 확인해 주세요.');
    }
    return false;
}

// 1234567 → "1.2MB"
function formatSize(bytes) {
    if (bytes < 1024) return bytes + 'B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB';
    return (bytes / 1024 / 1024).toFixed(1) + 'MB';
}

// "보고서.PDF" → "pdf"
function extensionOf(name) {
    const i = name.lastIndexOf('.');
    return i < 0 ? '' : name.slice(i + 1).toLowerCase();
}

// 끌고 있는 게 파일인지 (화면 안의 글자·링크·사진을 끌 때는 반응하지 않게)
function isFileDrag(e) {
    return !!e.dataTransfer && Array.from(e.dataTransfer.types || []).includes('Files');
}

// 말풍선 안에 넣을 사진/파일 요소
//   onImageLoad: 사진이 다 불러와진 뒤 실행 (높이가 늘어나니 스크롤 맞추기용)
function fileElement(file, onImageLoad) {
    const a = document.createElement('a');
    a.href = file.url;

    if (file.image) {
        a.className = 'att-img';
        a.target = '_blank';   // 클릭하면 새 창에서 원본
        const img = document.createElement('img');
        img.src = file.url;
        img.alt = file.name;
        if (onImageLoad) img.addEventListener('load', onImageLoad);
        a.appendChild(img);
        return a;
    }

    a.className = 'att-file';
    a.setAttribute('download', file.name);
    const icon = document.createElement('span');
    icon.className = 'icon';
    icon.textContent = (file.name.split('.').pop() || 'FILE').toUpperCase().slice(0, 4);   // PDF, XLSX …
    const meta = document.createElement('span');
    meta.className = 'att-meta';
    const name = document.createElement('span');
    name.className = 'name';
    name.textContent = file.name;
    const size = document.createElement('span');
    size.className = 'size';
    size.textContent = formatSize(file.size);
    meta.append(name, size);
    a.append(icon, meta);
    return a;
}

// [첨부] 버튼 + 입력칸에 붙여넣기(Ctrl+V) + 끌어다 놓기 연결
//   dropZone: 파일을 놓을 수 있는 영역 (생략하면 입력칸이 들어 있는 대화 화면 전체)
function setupAttach({ button, input, textInput, uploadUrl, dropZone }) {
    const zone = dropZone || textInput.closest('.pane') || document.body;
    uploadMaxMb = Number(input.dataset.maxMb) || uploadMaxMb;   // 서버 설정값 (application.yml chat.upload-max-mb)

    // 보낼 수 있는 확장자 = <input type="file" accept="..."> 목록 (서버 FileStorage 목록과 같게 유지)
    const allowed = new Set(
        (input.accept || '').split(',')
            .map(s => s.trim().replace(/^\./, '').toLowerCase())
            .filter(Boolean));

    let busy = false;

    // 여러 개를 받아서 형식·크기 확인 → 하나씩 차례대로 전송
    async function sendFiles(fileList) {
        const files = Array.from(fileList || []);
        if (!files.length) return;
        if (busy) {
            alert('앞의 파일을 보내는 중이에요. 끝난 뒤에 다시 보내 주세요.');
            return;
        }

        const rejected = [];
        let ok = files.filter(f => {
            if (allowed.size && !allowed.has(extensionOf(f.name))) {
                rejected.push(`${f.name} (보낼 수 없는 형식)`);
                return false;
            }
            if (f.size === 0) {
                rejected.push(`${f.name} (빈 파일이거나 폴더)`);
                return false;
            }
            if (f.size > maxUploadBytes()) {
                rejected.push(`${f.name} (${formatSize(f.size)}, ${uploadMaxMb}MB 초과)`);
                return false;
            }
            return true;
        });

        const notes = [];
        if (rejected.length) {
            notes.push(`다음 파일은 빼고 보낼게요.\n(사진·PDF·문서·한글·ZIP·TXT, 하나당 ${uploadMaxMb}MB까지)\n\n`
                + rejected.map(r => '· ' + r).join('\n'));
        }
        if (ok.length > MAX_FILES_AT_ONCE) {
            notes.push(`한 번에 ${MAX_FILES_AT_ONCE}개까지 보낼 수 있어서 앞의 ${MAX_FILES_AT_ONCE}개만 보낼게요.`);
            ok = ok.slice(0, MAX_FILES_AT_ONCE);
        }
        if (notes.length) alert(notes.join('\n\n'));
        if (!ok.length) return;

        busy = true;
        button.disabled = true;
        try {
            for (const f of ok) {
                const sent = await uploadFile(uploadUrl, f);
                if (!sent) break;   // 하나가 실패하면(연결 끊김 등) 나머지는 멈춤 — 안내는 uploadFile이 띄움
            }
        } finally {
            busy = false;
            button.disabled = false;
            textInput.focus();
        }
    }

    // ① [첨부] 버튼 (여러 개 고르기 가능)
    button.onclick = () => input.click();
    input.onchange = () => {
        const files = Array.from(input.files);
        input.value = '';   // 같은 파일을 다시 골라도 동작하게
        sendFiles(files);
    };

    // ② 캡처한 이미지 등을 입력칸에 붙여넣으면 바로 전송
    textInput.addEventListener('paste', (e) => {
        const files = e.clipboardData && e.clipboardData.files;
        if (files && files.length) {
            e.preventDefault();
            sendFiles(files);
        }
    });

    // ③ 끌어다 놓기: 대화 화면 위로 파일을 끌고 오면 안내 화면을 덮어 보여 줌
    zone.classList.add('drop-zone');
    const overlay = document.createElement('div');
    overlay.className = 'drop-overlay';
    overlay.setAttribute('aria-hidden', 'true');
    overlay.innerHTML = `
        <div class="drop-card">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                <path d="M17 8l-5-5-5 5"></path>
                <path d="M12 3v12"></path>
            </svg>
            <strong>여기에 놓으면 보내요</strong>
            <span>사진·파일 한 번에 ${MAX_FILES_AT_ONCE}개까지, 하나당 ${uploadMaxMb}MB</span>
        </div>`;
    zone.appendChild(overlay);

    // 안쪽 요소를 지날 때마다 dragenter/dragleave가 번갈아 생기므로 깊이를 세서 깜빡임을 막음
    let depth = 0;
    const show = (on) => {
        if (!on) depth = 0;
        zone.classList.toggle('dragging', on);
    };

    zone.addEventListener('dragenter', (e) => {
        if (!isFileDrag(e)) return;
        e.preventDefault();
        depth++;
        show(true);
    });
    zone.addEventListener('dragover', (e) => {
        if (!isFileDrag(e)) return;
        e.preventDefault();               // 이게 있어야 drop 이벤트가 생김
        e.dataTransfer.dropEffect = 'copy';
    });
    zone.addEventListener('dragleave', (e) => {
        if (!isFileDrag(e)) return;
        depth = Math.max(0, depth - 1);
        if (depth === 0) show(false);
    });
    zone.addEventListener('drop', (e) => {
        if (!isFileDrag(e)) return;
        e.preventDefault();
        show(false);
        sendFiles(e.dataTransfer.files);
    });

    // 영역 밖(사이드바 등)에 떨어뜨렸을 때 브라우저가 파일을 열어 버리지 않게
    window.addEventListener('dragover', (e) => {
        if (!isFileDrag(e)) return;
        e.preventDefault();
        if (!zone.contains(e.target)) e.dataTransfer.dropEffect = 'none';   // 커서도 '놓을 수 없음'으로
    });
    window.addEventListener('drop', (e) => {
        if (!isFileDrag(e)) return;
        e.preventDefault();
        show(false);
    });
    // 끌다가 창 밖으로 나가거나 Esc로 취소했을 때 안내 화면이 남지 않게
    window.addEventListener('blur', () => show(false));
}