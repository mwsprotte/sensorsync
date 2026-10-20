// ***************************************************************************************************
// Dashboard: dashboard.html?id=<projeto>

const projectId = queryParam('id') || localStorage.getItem('projectID');
let devices = [];
let chart = null;

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
    document.getElementById('exportLink').href = 'export.html?id=' + projectId;

    const p = prefs();
    const lenRadio = document.querySelector(`input[name='len'][value='${p.length}']`) || document.getElementById('len20');
    lenRadio.checked = true;
    document.getElementById('setTimeOff').checked = p.auto;

    await refreshDeviceSelector(p.device);
    document.getElementById('deviceSelected').addEventListener('change', () => {
        localStorage.setItem('dash_device_' + projectId, selectedDevice());
        updateViews();
    });
    document.querySelectorAll("input[name='len']").forEach(r => r.addEventListener('change', updateViews));
    document.getElementById('setTimeOff').addEventListener('change', updateViews);

    createChart();
    updateViews();
    setInterval(() => {
        if (document.getElementById('setTimeOff').checked && !document.hidden) {
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
    updateCodeLink();
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

function updateCodeLink() {
    const d = devices.find(x => x.deviceIndex === selectedDevice());
    document.getElementById('codeLink').href = 'code.html?id=' + projectId + (d ? '&device=' + d.id : '');
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
    updateCodeLink();
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
                  <a class='btn btn-sm btn-outline-secondary' href='code.html?id=${projectId}&device=${d.id}' title='Código'><i class='bi bi-code-slash'></i></a>
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
          <a class='btn btn-primary' href='code.html?id=${projectId}&device=${result.device.id}'><i class='bi bi-code-slash me-1'></i>Código com este token</a>
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
        updateViews();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

init();
