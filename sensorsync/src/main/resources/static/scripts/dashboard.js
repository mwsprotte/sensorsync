// ***************************************************************************************************
// Pagina do projeto: dashboard.html?id=<projeto>#visao|dispositivos|codigo|relatorio
// As abas trocam sem recarregar a pagina; cada uma carrega seus dados na primeira vez que e aberta.

const projectId = queryParam('id') || localStorage.getItem('projectID');
let devices = [];
let chart = null;
let activeTab = 'visao';
const TABS = ['visao', 'dispositivos', 'codigo', 'relatorio'];
const TOKEN_PLACEHOLDER = 'COLE_AQUI_O_TOKEN_DO_DISPOSITIVO';

function prefs() {
    return {
        device: localStorage.getItem('dash_device_' + projectId),
        length: Number(localStorage.getItem('dataLength')) || 20,
        auto: localStorage.getItem('autoUpdate') !== 'off'
    };
}

function selectedDevice() {
    return Number(document.getElementById('deviceSelected').value);
}

function chartLength() {
    const r = document.querySelector("input[name='len']:checked");
    return r ? Number(r.value) : 20;
}

async function init() {
    await loadCurrentUser('home');
    if (!projectId) {
        window.location.href = 'index.html';
        return;
    }
    let project;
    try {
        project = await api('/project/' + projectId);
    } catch (e) {
        document.getElementById('dataCards').innerHTML = `<div class='col-12'><div class='alert alert-danger'>${esc(e.message)}</div></div>`;
        return;
    }
    document.title = 'SensorSync - ' + project.name;
    document.getElementById('projectTitle').textContent = project.name;
    document.getElementById('projectTitle').title = project.name;
    if (queryParam('board')) document.getElementById('board').value = queryParam('board');
    if (queryParam('protocol')) document.getElementById('protocol').value = queryParam('protocol');
    document.getElementById('board').addEventListener('change', generateCode);
    document.getElementById('protocol').addEventListener('change', generateCode);

    const p = prefs();
    const lenRadio = document.querySelector(`input[name='len'][value='${p.length}']`) || document.getElementById('len20');
    lenRadio.checked = true;
    document.getElementById('setTimeOff').checked = p.auto;

    await refreshDeviceSelector(p.device);
    document.getElementById('deviceSelected').addEventListener('change', () => {
        localStorage.setItem('dash_device_' + projectId, selectedDevice());
        onDeviceChanged();
    });
    document.querySelectorAll("input[name='len']").forEach(r => r.addEventListener('change', updateViews));
    document.getElementById('setTimeOff').addEventListener('change', updateViews);

    createChart();
    document.querySelectorAll('#projectTabs [data-tab]').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));
    window.addEventListener('hashchange', () => showTab(tabFromHash(), false));
    showTab(tabFromHash(), false);
    setInterval(() => {
        if (activeTab === 'visao' && document.getElementById('setTimeOff').checked && !document.hidden) {
            generateCards();
            updateCharts();
        }
    }, 2000);
    setInterval(() => { if (!document.hidden) refreshDeviceStatus(); }, 15000);
}

async function refreshDeviceSelector(preferred) {
    devices = await api('/project/' + projectId + '/devices');
    const sel = document.getElementById('deviceSelected');
    const current = preferred ?? sel.value;
    sel.innerHTML = devices.map(d => `<option value='${d.deviceIndex}'>${esc(d.name)}</option>`).join('');
    if (current !== null && current !== '' && devices.some(d => String(d.deviceIndex) === String(current))) {
        sel.value = current;
    }
    renderDeviceStatus();
}

async function refreshDeviceStatus() {
    try {
        devices = await api('/project/' + projectId + '/devices');
        renderDeviceStatus();
    } catch (e) { /* silencioso */ }
}

function timeAgo(iso) {
    if (!iso) return null;
    const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 60) return 'agora há pouco';
    if (s < 3600) return `há ${Math.round(s / 60)} min`;
    if (s < 86400) return `há ${Math.round(s / 3600)} h`;
    return `há ${Math.round(s / 86400)} dia(s)`;
}

function renderDeviceStatus() {
    const d = devices.find(x => x.deviceIndex === selectedDevice());
    const el = document.getElementById('deviceStatus');
    if (!d) { el.innerHTML = '&nbsp;'; return; }
    const age = d.lastSeenAt ? (Date.now() - new Date(d.lastSeenAt).getTime()) / 1000 : Infinity;
    const cls = age < 120 ? 'online' : age < 3600 ? 'idle' : (d.lastSeenAt ? 'offline' : '');
    const text = !d.hasToken ? 'sem token: gere em Dispositivos'
        : d.lastSeenAt ? 'último envio ' + timeAgo(d.lastSeenAt) : 'aguardando o primeiro envio';
    el.innerHTML = `<span class="ss-status-dot ${cls}"></span><span>${esc(d.name)} · ${esc(text)}</span>`;
}

// ---- Abas -------------------------------------------------------------------------------------

function tabFromHash() {
    const h = window.location.hash.replace('#', '');
    return TABS.includes(h) ? h : 'visao';
}

const loadedTabs = new Set();

function showTab(tab, updateHash = true) {
    activeTab = tab;
    document.querySelectorAll('#projectTabs [data-tab]').forEach(b => {
        b.classList.toggle('active', b.dataset.tab === tab);
        b.setAttribute('aria-selected', b.dataset.tab === tab);
    });
    document.querySelectorAll('[data-panel]').forEach(p => p.classList.toggle('d-none', p.dataset.panel !== tab));
    // O seletor de dispositivo vale para visao geral, codigo e relatorio
    document.getElementById('deviceSelectorWrap').classList.toggle('invisible', tab === 'dispositivos');
    if (updateHash) history.replaceState(null, '', '#' + tab);

    if (tab === 'visao') updateViews();
    if (tab === 'dispositivos') loadDevices();
    if (tab === 'codigo') { renderTokens(); generateCode(); }
    if (tab === 'relatorio') loadReport();
    loadedTabs.add(tab);
}

function onDeviceChanged() {
    renderDeviceStatus();
    if (activeTab === 'visao') updateViews();
    if (activeTab === 'codigo') generateCode();
    if (activeTab === 'relatorio') loadReport();
}

/** Seleciona o dispositivo no topo e abre a aba de codigo. */
function openCodeFor(deviceIndex) {
    document.getElementById('deviceSelected').value = deviceIndex;
    localStorage.setItem('dash_device_' + projectId, deviceIndex);
    renderDeviceStatus();
    showTab('codigo');
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ---- Cards ------------------------------------------------------------------------------------

function formatValue(v) {
    const n = Number(v);
    const digits = Math.abs(n) >= 100 ? 1 : Math.abs(n) >= 1 ? 2 : 4;
    return n.toLocaleString('pt-BR', { maximumFractionDigits: digits });
}

async function generateCards() {
    try {
        const cards = await api(`/data/cards/${projectId}/device/${selectedDevice()}`);
        let html = '';
        for (const c of cards) {
            const value = c.data === null ? '—' : formatValue(c.data);
            html += `<div class="col-sm-6 col-lg-4 col-xl-3"><div class="ss-card p-3 h-100">
                <div class="d-flex align-items-center justify-content-between mb-2">
                  <span class="small fw-semibold ss-muted text-truncate">${esc(c.dataDesc)}</span>
                  <i class="bi bi-activity text-success"></i></div>
                <div class="ss-kpi-value">${esc(value)}</div>
                <div class="small ss-muted mt-1"><i class="bi bi-clock me-1"></i>${esc(c.readTime || 'sem leituras')}</div>
              </div></div>`;
        }
        if (cards.length === 0) {
            html = "<div class='col-12'><p class='ss-muted'>Nenhuma variável marcada como card.</p></div>";
        }
        document.getElementById('dataCards').innerHTML = html;
    } catch (e) {
        console.error(e);
    }
}

// ---- Grafico ----------------------------------------------------------------------------------

const PALETTE = ['#32a041', '#1c7ed6', '#f08c00', '#ae3ec9', '#c8191e', '#0ca678', '#5c7cfa', '#d6336c'];

function applyChartTheme() {
    const dark = currentTheme() === 'dark';
    Chart.defaults.color = dark ? '#adb5bd' : '#495057';
    Chart.defaults.borderColor = dark ? 'rgba(255,255,255,.08)' : 'rgba(0,0,0,.06)';
}

function onThemeChange() {
    if (!chart) return;
    const data = chart.data;
    chart.destroy();
    createChart();
    chart.data = data;
    chart.update();
}

function createChart() {
    applyChartTheme();
    const ctx = document.getElementById('dataChart').getContext('2d');
    chart = new Chart(ctx, {
        type: 'line',
        data: { labels: [], datasets: [] },
        options: {
            // Rotulos "HH:mm:ss dd/MM/yyyy": o eixo mostra so a hora; a data aparece no tooltip
            scales: {
                x: { ticks: { maxTicksLimit: 8, maxRotation: 0, callback: function (v) { return String(this.getLabelForValue(v)).slice(0, 8); } } },
                y: { beginAtZero: false }
            },
            spanGaps: true,
            interaction: { mode: 'index', intersect: false },
            plugins: { legend: { position: 'bottom', labels: { usePointStyle: true } } },
            elements: { point: { radius: 2 }, line: { borderWidth: 2 } },
            responsive: true,
            maintainAspectRatio: false,
            animation: false
        }
    });
}

let lastChartKey = '';

async function updateCharts(force = false) {
    const length = chartLength();
    try {
        const data = await api(`/data/charts/${projectId}/device/${selectedDevice()}/length/${length}`);
        const names = data[0], labels = data[1], series = data.slice(2);
        const key = JSON.stringify([selectedDevice(), length, labels.length, labels[labels.length - 1], series.map(s => s[s.length - 1])]);
        if (!force && key === lastChartKey) return;
        lastChartKey = key;
        document.getElementById('noChart').classList.toggle('d-none', names.length > 0);
        chart.data.labels = labels;
        chart.data.datasets = series.map((s, i) => ({
            label: names[i],
            data: s.map(v => v === '' ? null : Number(v)),
            borderColor: PALETTE[i % PALETTE.length],
            backgroundColor: PALETTE[i % PALETTE.length],
            fill: false,
            tension: 0.25
        }));
        chart.update();
    } catch (e) {
        console.error(e);
    }
}

function updateViews() {
    localStorage.setItem('autoUpdate', document.getElementById('setTimeOff').checked ? 'on' : 'off');
    localStorage.setItem('dataLength', chartLength());
    renderDeviceStatus();
    generateCards();
    updateCharts(true);
}

// ---- Dispositivos -----------------------------------------------------------------------------

function fmtDate(s) {
    return s ? new Date(s).toLocaleString('pt-BR') : 'nunca';
}

async function loadDevices() {
    const list = document.getElementById('devicesList');
    try {
        await refreshDeviceSelector();
        let html = `<div class='table-responsive'><table class='table align-middle mb-0'><thead><tr><th>Índice</th><th>Nome</th><th>Token</th><th>Último envio</th><th></th></tr></thead><tbody>`;
        for (const d of devices) {
            const token = d.hasToken ? `<code>${esc(d.tokenPrefix)}…</code>` : "<span class='badge text-bg-warning'><i class='bi bi-exclamation-triangle me-1'></i>sem token</span>";
            html += `<tr><td>${d.deviceIndex}</td>
                <td><input class='form-control form-control-sm' value='${esc(d.name)}' onchange='renameDevice(${d.id}, this.value)'></td>
                <td>${token}</td><td class='small'>${esc(fmtDate(d.lastSeenAt))}</td>
                <td class='text-nowrap text-end'>
                  <button class='btn btn-sm ${d.hasToken ? 'btn-outline-primary' : 'btn-primary'}' onclick='newToken(${d.id})'><i class='bi bi-key me-1'></i>${d.hasToken ? 'Novo token' : 'Gerar token'}</button>
                  <button class='btn btn-sm btn-outline-secondary' onclick='openCodeFor(${d.deviceIndex})' title='Código'><i class='bi bi-code-slash'></i></button>
                  <button class='btn btn-sm btn-outline-danger' onclick='deleteDevice(${d.id})' title='Excluir'><i class='bi bi-trash'></i></button>
                </td></tr>`;
        }
        list.innerHTML = html + '</tbody></table></div>';
    } catch (e) {
        list.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

function showToken(result, title) {
    rememberToken(result.device.id, result.token);
    const box = document.getElementById('newTokenBox');
    box.innerHTML = `<div class='alert alert-warning'>
        <div class='fw-semibold mb-2'><i class='bi bi-shield-lock me-1'></i>${esc(title)}: copie agora, ele não será exibido de novo</div>
        <div class='input-group'>
          <input class='form-control font-monospace' readonly value='${esc(result.token)}'>
          <button class='btn btn-outline-secondary' id='copyTokenBtn'><i class='bi bi-clipboard me-1'></i>Copiar</button>
          <button class='btn btn-primary' onclick='openCodeFor(${result.device.deviceIndex})'><i class='bi bi-code-slash me-1'></i>Código com este token</button>
        </div></div>`;
    document.getElementById('copyTokenBtn').onclick = () => { copyText(result.token); toast('Token copiado.'); };
}

async function newToken(deviceId) {
    const d = devices.find(x => x.id === deviceId);
    if (d && d.hasToken && !confirm(`Gerar um novo token para "${d.name}"? O token atual deixa de funcionar e a placa precisará ser regravada.`)) return;
    try {
        const r = await api(`/project/${projectId}/devices/${deviceId}/token`, { method: 'POST' });
        showToken(r, 'Token de ' + r.device.name);
        loadDevices();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function addDevice() {
    const input = document.getElementById('newDeviceName');
    try {
        const r = await api(`/project/${projectId}/devices`, { method: 'POST', body: { name: input.value } });
        input.value = '';
        showToken(r, 'Dispositivo criado: ' + r.device.name);
        loadDevices();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function renameDevice(deviceId, name) {
    try {
        await api(`/project/${projectId}/devices/${deviceId}`, { method: 'PUT', body: { name: name } });
        await refreshDeviceSelector();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function deleteDevice(deviceId) {
    const d = devices.find(x => x.id === deviceId);
    if (!confirm(`Excluir o dispositivo "${d ? d.name : deviceId}"? O token dele deixa de funcionar.`)) return;
    const deleteData = confirm('Apagar também as leituras já gravadas por este dispositivo?\n\nOK = apagar, Cancelar = manter.');
    try {
        await api(`/project/${projectId}/devices/${deviceId}?deleteData=${deleteData}`, { method: 'DELETE' });
        document.getElementById('newTokenBox').innerHTML = '';
        await loadDevices();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

// ---- Codigo embarcado ---------------------------------------------------------------------------

function selectedDeviceObj() {
    return devices.find(d => d.deviceIndex === selectedDevice());
}

/** Tokens recem-gerados nesta aba do navegador (o servidor so guarda o hash). */
function renderTokens() {
    const fresh = devices.map(d => ({ d, token: recallToken(d.id) })).filter(x => x.token);
    const box = document.getElementById('tokensBox');
    if (fresh.length === 0) {
        box.innerHTML = `<div class='alert alert-secondary small d-flex gap-2 mb-4'><i class='bi bi-info-circle'></i><div>O código sai com o marcador <code>${TOKEN_PLACEHOLDER}</code>.
            Gere o token na aba <a href='#dispositivos'>Dispositivos</a> e use o botão "Código com este token".</div></div>`;
        return;
    }
    const rows = fresh.map(x => `<div class='input-group input-group-sm mt-2'>
        <span class='input-group-text text-truncate' style='min-width: 140px; max-width: 220px'>${esc(x.d.name)}</span>
        <input class='form-control font-monospace' readonly value='${esc(x.token)}'>
        <button class='btn btn-outline-secondary' onclick='copyText(${JSON.stringify(x.token)}); toast("Token copiado.")'><i class='bi bi-clipboard'></i></button></div>`).join('');
    box.innerHTML = `<div class='alert alert-warning mb-4'>
        <div class='fw-semibold'><i class='bi bi-shield-lock me-1'></i>Guarde estes tokens agora</div>
        <div class='small'>O servidor guarda só o hash do token. Depois de fechar esta aba do navegador, só é possível gerar um novo (o antigo deixa de funcionar).</div>
        ${rows}</div>`;
}

async function generateCode() {
    if (activeTab !== 'codigo') return;
    const d = selectedDeviceObj();
    const target = document.getElementById('codeGenerated');
    if (!d) {
        target.innerHTML = "<div class='alert alert-warning'>Nenhum dispositivo no projeto.</div>";
        return;
    }
    const params = new URLSearchParams({
        board: document.getElementById('board').value,
        protocol: document.getElementById('protocol').value,
        deviceId: d.id
    });
    target.innerHTML = SPINNER;
    try {
        const files = await api(`/project/${projectId}/code?` + params.toString());
        const token = recallToken(d.id);
        target.innerHTML = '';
        files.forEach(f => {
            const code = token ? f.code.replaceAll(TOKEN_PLACEHOLDER, token) : f.code;
            const card = document.createElement('div');
            card.className = 'ss-card mb-4';
            card.innerHTML = `<div class='d-flex flex-wrap justify-content-between align-items-center gap-2 px-3 py-2 border-bottom'>
                <span class='fw-semibold min-w-0 text-truncate'><i class='bi bi-file-earmark-code me-1'></i>${esc(f.title)} <span class='ss-muted small fw-normal'>${esc(f.fileName)}</span></span>
                <span class='d-flex gap-2'><button class='btn btn-sm btn-outline-secondary' data-act='copy'><i class='bi bi-clipboard me-1'></i>Copiar</button>
                <button class='btn btn-sm btn-primary' data-act='download'><i class='bi bi-download me-1'></i>Baixar .ino</button></span></div>
                <pre class='ss-code mb-0 p-3'><code></code></pre>`;
            card.querySelector('code').textContent = code;
            card.querySelector("[data-act='copy']").onclick = () => { copyText(code); toast('Código copiado.'); };
            card.querySelector("[data-act='download']").onclick = () => downloadText(f.fileName, code);
            target.appendChild(card);
        });
    } catch (e) {
        target.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

// ---- Relatorio ----------------------------------------------------------------------------------

let report = [];

async function loadReport() {
    const div = document.getElementById('tableDataDiv');
    const d = selectedDeviceObj();
    div.innerHTML = SPINNER;
    try {
        report = await api(`/data/report/${projectId}/device/${selectedDevice()}`);
        document.getElementById('reportInfo').textContent = `${d ? d.name : ''} · ${Math.max(0, report.length - 1)} leitura(s)`;
        if (report.length <= 1) {
            div.innerHTML = "<p class='ss-muted text-center py-5 mb-0'><i class='bi bi-inbox d-block fs-2 mb-2'></i>Sem leituras para este dispositivo.</p>";
            return;
        }
        let table = "<div class='table-responsive' style='max-height: 70vh;'><table class='table table-hover table-sm table-sticky mb-0 align-middle'>";
        report.forEach((row, i) => {
            table += (i === 0 ? '<thead>' : (i === 1 ? '<tbody>' : '')) + '<tr>' + row.map((cell, j) => i === 0
                ? `<th class='${j ? 'text-end' : ''} px-3 py-2'>${esc(cell)}</th>`
                : `<td class='${j ? 'text-end font-monospace' : 'ss-muted'} px-3'>${esc(String(cell).replace('.', ','))}</td>`).join('') + '</tr>' + (i === 0 ? '</thead>' : '');
        });
        div.innerHTML = table + '</tbody></table></div>';
    } catch (e) {
        div.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

/** CSV com ";" e virgula decimal (abre direto no Excel em portugues). */
function downloadCsv() {
    if (report.length <= 1) {
        toast('Sem leituras para exportar.', 'warning');
        return;
    }
    const csv = report.map((row, i) => row.map(cell => {
        let v = String(cell ?? '');
        if (i > 0) v = v.replace('.', ',');
        return /[;"\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v;
    }).join(';')).join('\r\n');
    downloadText(`sensorsync_projeto${projectId}_dispositivo${selectedDevice()}.csv`, '\ufeff' + csv, 'text/csv');
}

init();
