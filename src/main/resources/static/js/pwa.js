// 서비스 워커 등록 (지원하는 브라우저에서만)
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js')
            .catch(err => console.warn('서비스 워커 등록 실패', err));
    });
}