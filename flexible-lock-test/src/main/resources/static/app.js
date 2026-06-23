// Vanilla-JS UI for the Flexible Lock console.
// Polls /api/log once per second and updates the table.

const logBody = document.getElementById('log-body');
const lastUpdateEl = document.getElementById('last-update');
let lastSize = 0;
let isFirstLoad = true;

function fmtStatus(s) {
    return s || 'UNKNOWN';
}

function renderLog(entries) {
    if (!entries || entries.length === 0) {
        logBody.innerHTML = '<tr><td colspan="4" class="empty">日志为空。点上面的按钮试试。</td></tr>';
        return;
    }
    const html = entries.map(e => {
        const isNew = !isFirstLoad && entries.length > lastSize && e === entries[entries.length - 1];
        return `<tr class="status-${e.status} ${isNew ? 'new-row' : ''}">
            <td>${escapeHtml(e.key || '')}</td>
            <td class="status">${fmtStatus(e.status)}</td>
            <td>${e.durationMs}</td>
            <td>${escapeHtml(e.message || '')}</td>
        </tr>`;
    }).join('');
    logBody.innerHTML = html;
    lastSize = entries.length;
    isFirstLoad = false;
}

function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

async function refresh() {
    try {
        const r = await fetch('/api/log');
        const entries = await r.json();
        renderLog(entries);
        lastUpdateEl.textContent = '已刷新 @ ' + new Date().toLocaleTimeString();
    } catch (e) {
        lastUpdateEl.textContent = '刷新失败: ' + e.message;
    }
}

async function invoke(method, target) {
    const keyInput = document.querySelector(`input.${target === 'order' ? 'order-key-input' : 'key-input'}[data-method="${method}"]`);
    const key = keyInput ? keyInput.value : 'demo';
    const params = new URLSearchParams({ method, key, target });
    try {
        const r = await fetch('/api/invoke?' + params);
        const result = await r.json();
        flashResult(`${method} → ${result.status}` + (result.message ? ` (${result.message})` : ''));
    } catch (e) {
        flashResult(`${method} 调用失败: ${e.message}`);
    }
}

async function burst(method, target) {
    const keyInput = document.querySelector(`input.${target === 'order' ? 'order-key-input' : 'key-input'}[data-method="${method}"]`);
    const countInput = keyInput ? keyInput.parentElement.nextElementSibling.querySelector('.count-input') : null;
    const key = keyInput ? keyInput.value : 'burst';
    const count = countInput ? countInput.value : 5;
    const params = new URLSearchParams({ method, key, target, count });
    try {
        const r = await fetch('/api/burst?' + params);
        const result = await r.json();
        flashResult(`${method} 并发 x${count}: 成功 ${result.success}, 失败 ${result.failure}, 用时 ${result.elapsedMs}ms`);
    } catch (e) {
        flashResult(`${method} 并发失败: ${e.message}`);
    }
}

function flashResult(msg) {
    lastUpdateEl.textContent = msg;
    console.log('[flexible-lock]', msg);
}

async function clearLog() {
    await fetch('/api/clear', { method: 'POST' });
    lastSize = 0;
    isFirstLoad = true;
    await refresh();
}

document.querySelectorAll('.invoke-btn').forEach(btn => {
    const target = btn.classList.contains('order-btn') ? 'order' : 'demo';
    btn.addEventListener('click', () => invoke(btn.dataset.method, target));
});
document.querySelectorAll('.burst-btn').forEach(btn => {
    const target = btn.classList.contains('order-btn') ? 'order' : 'demo';
    btn.addEventListener('click', () => burst(btn.dataset.method, target));
});
document.getElementById('clear-log').addEventListener('click', clearLog);

// Poll the log every 1 second.
setInterval(refresh, 1000);
refresh();
