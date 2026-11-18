// ───────── 앱 안 확인창 · 알림 (브라우저 기본 confirm / alert 대신) ─────────
// 모든 화면에서 불러옴 (fragments/head.html의 common)
//   askConfirm(message, { ok, cancel, danger }) → Promise<boolean>
//       가운데 카드 + [취소] [확인]. danger면 확인 버튼이 빨강 (삭제·강퇴·탈퇴처럼 되돌릴 수 없는 일)
//       Enter = 확인, Esc · 바깥 누르기 = 취소
//   showAlert(message, { ok }) → Promise   가운데 카드 + [확인] (누른 뒤 다른 화면으로 갈 때)
//   toast(message, { duration })           화면 아래에 잠깐 떴다 사라지는 알림 (누르면 바로 닫힘)
// 폼에 data-confirm="물어볼 말" 을 달면 보내기 전에 askConfirm으로 물어봄
//   data-confirm-ok="삭제" (확인 버튼 글자), data-danger (빨간 확인 버튼). 글 속 \n 은 줄바꿈

let dialogChain = Promise.resolve();   // 창이 겹치면 앞의 창이 닫힌 뒤 차례로

function askConfirm(message, { ok = '확인', cancel = '취소', danger = false } = {}) {
    return queueDialog({ message, ok, cancel, danger });
}

function showAlert(message, { ok = '확인' } = {}) {
    return queueDialog({ message, ok, cancel: null, danger: false }).then(() => undefined);
}

function queueDialog(opts) {
    const shown = dialogChain.then(() => openDialog(opts));
    dialogChain = shown.catch(() => false);
    return shown;
}

function openDialog({ message, ok, cancel, danger }) {
    return new Promise((resolve) => {
        const before = document.activeElement;   // 닫으면 원래 자리로 포커스

        const backdrop = document.createElement('div');
        backdrop.className = 'dlg-backdrop';
        const box = document.createElement('div');
        box.className = 'dlg';
        box.setAttribute('role', cancel ? 'alertdialog' : 'dialog');
        box.setAttribute('aria-modal', 'true');
        const text = document.createElement('p');
        text.className = 'dlg-msg';
        text.id = 'dlgMsg';
        text.textContent = message;   // 줄바꿈은 CSS(pre-line)로
        box.setAttribute('aria-describedby', 'dlgMsg');

        const actions = document.createElement('div');
        actions.className = 'dlg-actions';
        const buttons = [];
        if (cancel) {
            const no = document.createElement('button');
            no.type = 'button';
            no.className = 'btn';
            no.textContent = cancel;
            no.addEventListener('click', () => done(false));
            buttons.push(no);
        }
        const yes = document.createElement('button');
        yes.type = 'button';
        yes.className = 'btn ' + (danger ? 'dlg-danger' : 'btn-primary');
        yes.textContent = ok;
        yes.addEventListener('click', () => done(true));
        buttons.push(yes);
        actions.append(...buttons);
        box.append(text, actions);
        backdrop.appendChild(box);
        document.body.appendChild(backdrop);
        yes.focus();

        // 창이 떠 있는 동안 키는 이 창만 받음 (뒤의 패널·크게 보기 Esc가 같이 움직이지 않게)
        function onKey(e) {
            if (e.key === 'Escape') {
                e.preventDefault();
                e.stopImmediatePropagation();
                done(false);
            } else if (e.key === 'Enter' && !e.isComposing) {
                e.preventDefault();
                e.stopImmediatePropagation();
                done(document.activeElement === buttons[0] && cancel ? false : true);   // [취소]에 포커스면 취소
            } else if (e.key === 'Tab') {   // 버튼 사이에서만 돌기
                e.preventDefault();
                const i = buttons.indexOf(document.activeElement);
                buttons[(i + (e.shiftKey ? -1 : 1) + buttons.length) % buttons.length].focus();
            }
        }
        window.addEventListener('keydown', onKey, true);
        backdrop.addEventListener('mousedown', (e) => {
            if (e.target === backdrop) done(false);   // 바깥 누르기 = 취소
        });

        let closed = false;
        function done(result) {
            if (closed) return;
            closed = true;
            window.removeEventListener('keydown', onKey, true);
            backdrop.remove();
            if (before && document.contains(before)) before.focus?.();
            resolve(result);
        }
    });
}

// ── 토스트: 화면 아래 가운데, 최대 3개까지 쌓임 ──
function toast(message, { duration } = {}) {
    let stack = document.querySelector('.toast-stack');
    if (!stack) {
        stack = document.createElement('div');
        stack.className = 'toast-stack';
        stack.setAttribute('role', 'status');
        stack.setAttribute('aria-live', 'polite');
        document.body.appendChild(stack);
    }
    const item = document.createElement('div');
    item.className = 'toast';
    item.textContent = message;
    stack.appendChild(item);
    while (stack.children.length > 3) stack.firstElementChild.remove();

    const ms = duration ?? Math.min(8000, 2500 + message.length * 50);   // 긴 글은 조금 더 오래
    const close = () => {
        item.classList.add('out');
        setTimeout(() => item.remove(), 200);
    };
    const timer = setTimeout(close, ms);
    item.addEventListener('click', () => {
        clearTimeout(timer);
        close();
    });
}

// ── data-confirm 폼: 보내기 전에 물어보고, [확인]이면 다시 보냄 (폼의 다른 submit 처리는 그때 실행) ──
document.addEventListener('submit', (e) => {
    const form = e.target;
    if (!(form instanceof HTMLFormElement) || !form.dataset.confirm) return;
    if (form._confirmed) {   // 확인을 받고 다시 보내는 중
        form._confirmed = false;
        return;
    }
    e.preventDefault();
    e.stopImmediatePropagation();   // 확인 전에는 폼의 다른 submit 처리도 멈춤
    const submitter = e.submitter;
    askConfirm(form.dataset.confirm.replace(/\\n/g, '\n'), {
        ok: form.dataset.confirmOk || '확인',
        danger: 'danger' in form.dataset
    }).then((yes) => {
        if (!yes) return;
        form._confirmed = true;
        if (form.requestSubmit) form.requestSubmit(submitter && submitter.form === form ? submitter : undefined);
        else form.submit();
    });
}, true);