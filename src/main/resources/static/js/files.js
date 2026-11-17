// ───────── 사진·파일 보내기 (채팅방, DM 공통) ─────────
const MAX_UPLOAD = 10 * 1024 * 1024;   // 10MB (서버와 같은 값)

// 파일 올리기 → 성공하면 메시지는 WebSocket으로 도착함
async function uploadFile(url, file) {
    if (file.size > MAX_UPLOAD) {
        alert('10MB 이하 파일만 보낼 수 있어요.');
        return false;
    }
    const form = new FormData();
    form.append('file', file);

    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const headers = token && header ? { [header]: token } : {};

    try {
        const res = await fetch(url, { method: 'POST', headers, body: form });
        if (res.ok) return true;
        const data = await res.json().catch(() => null);
        alert(data?.message || (res.status === 413 ? '10MB 이하 파일만 보낼 수 있어요.' : '파일을 보내지 못했습니다.'));
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

// [첨부] 버튼 + 입력칸에 붙여넣기(Ctrl+V) 연결
function setupAttach({ button, input, textInput, uploadUrl }) {
    async function send(file) {
        button.disabled = true;
        try {
            await uploadFile(uploadUrl, file);
        } finally {
            button.disabled = false;
            textInput.focus();
        }
    }

    button.onclick = () => input.click();
    input.onchange = () => {
        const file = input.files[0];
        input.value = '';   // 같은 파일을 다시 골라도 동작하게
        if (file) send(file);
    };
    // 캡처한 이미지 등을 입력칸에 붙여넣으면 바로 전송
    textInput.addEventListener('paste', (e) => {
        const file = e.clipboardData && e.clipboardData.files[0];
        if (file) {
            e.preventDefault();
            send(file);
        }
    });
}