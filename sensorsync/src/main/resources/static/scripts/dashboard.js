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
        length: Number(localStorage.getItem('dataLength')) || 50,
        auto: localStorage.getItem('autoUpdate') !== 'off'
    };
}

function selectedDevice() {
    return Number(document.getElementById('deviceSelected').value);
}

function chartLength() {
    const r = document.querySelector("input[name='len']:checked");
    return r ? Number(r.value) : 50;
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
    const lenRadio = document.querySelector(`input[name='len'][value='${p.length}']`) || document.getElementById('len50');
    lenRadio.checked = true;
    document.getElementById('setTimeOff').checked = p.auto;

    await refreshDeviceSelector(p.device);
    document.getElementById('deviceSelected').addEventListener('change', () => {
        localStorage.setItem('dash_device_' + projectId, selectedDevice());
        onDeviceChanged();
    });
    document.querySelectorAll("input[name='len']").forEach(r => r.addEventListener('change', updateViews));
    document.getElementById('setTimeOff').addEventListener('change', updateViews);

    document.getElementById('modeCombined').addEventListener('change', () => setChartMode('combined'));
    document.getElementById('modeSplit').addEventListener('change', () => setChartMode('split'));
    document.getElementById('modeCombined').checked = chartMode() === 'combined';
    document.getElementById('modeSplit').checked = chartMode() === 'split';
    document.querySelectorAll('#projectTabs [data-tab]').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));
    window.addEventListener('hashchange', () => showTab(tabFromHash(), false));
    showTab(tabFromHash(), false);
    setInterval(() => {
        if (activeTab === 'visao' && document.getElementById('setTimeOff').checked && !document.hidden) {
            pollSeries();
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
    document.getElementById('deviceSelectorWrap').classList.toggle('d-none', tab === 'dispositivos');
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

// ---- Visao geral: cards e graficos -------------------------------------------------------------
// Uma unica chamada (/data/series) alimenta cards e graficos. Depois da carga inicial, a cada 2 s
// so os envios novos sao buscados (?after=<ultimo id>) e acrescentados, sem redesenhar tudo.

const PALETTE = ['#32a041', '#1c7ed6', '#f08c00', '#ae3ec9', '#c8191e', '#0ca678', '#5c7cfa', '#d6336c'];

let seriesVars = [];        // [{index, desc, unit, card, chart}]
let seriesPoints = [];      // [{id, t, time, v: [...]}], do mais antigo ao mais recente
let lastPointId = 0;
let fallbackCards = {};     // ultimo valor conhecido fora da janela (via /data/cards)
let charts = [];            // instancias Chart.js ativas
let polling = false;

function formatValue(v) {
    const n = Number(v);
    const digits = Math.abs(n) >= 100 ? 1 : Math.abs(n) >= 1 ? 2 : 4;
    return n.toLocaleString('pt-BR', { maximumFractionDigits: digits });
}

function chartMode() {
    return localStorage.getItem('chartMode') === 'split' ? 'split' : 'combined';
}

function setChartMode(mode) {
    try { localStorage.setItem('chartMode', mode); } catch (e) { }
    document.getElementById('modeCombined').checked = mode === 'combined';
    document.getElementById('modeSplit').checked = mode === 'split';
    buildCharts();
}

/** Carga completa (ao abrir, trocar dispositivo ou quantidade de pontos). */
async function loadSeries() {
    const device = selectedDevice();
    try {
        const [series, cards] = await Promise.all([
            api(`/data/series/${projectId}/device/${device}?limit=${chartLength()}`),
            api(`/data/cards/${projectId}/device/${device}`)
        ]);
        if (device !== selectedDevice()) return;  // usuario trocou de dispositivo durante a busca
        seriesVars = series.variables;
        seriesPoints = series.points;
        lastPointId = seriesPoints.length ? seriesPoints[seriesPoints.length - 1].id : 0;
        fallbackCards = {};
        cards.forEach(c => { if (c.data !== null) fallbackCards[c.dataDesc] = { value: c.data, time: c.readTime }; });
        renderCards();
        buildCharts();
    } catch (e) {
        document.getElementById('dataCards').innerHTML = `<div class='col-12'><div class='alert alert-danger'>${esc(e.message)}</div></div>`;
    }
}

/** Atualizacao incremental: busca so o que chegou depois do ultimo ponto. */
async function pollSeries() {
    if (polling) return;
    polling = true;
    const device = selectedDevice();
    try {
        const series = await api(`/data/series/${projectId}/device/${device}?limit=${chartLength()}&after=${lastPointId}`);
        if (device !== selectedDevice() || series.points.length === 0) return;
        seriesPoints.push(...series.points);
        const extra = seriesPoints.length - chartLength();
        if (extra > 0) seriesPoints.splice(0, extra);
        lastPointId = seriesPoints[seriesPoints.length - 1].id;
        renderCards();
        updateChartData();
    } catch (e) {
        console.error(e);
    } finally {
        polling = false;
    }
}

// ---- Cards ------------------------------------------------------------------------------------

/** Mini grafico (SVG) da janela atual, sem custo de uma instancia Chart.js por card. */
function sparkline(values, color) {
    const pts = values.map((v, i) => [i, v]).filter(p => p[1] !== null);
    if (pts.length < 2) return '';
    const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
    const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
    const w = 120, h = 32, pad = 2;
    const sx = x => pad + (maxX === minX ? 0 : (x - minX) / (maxX - minX)) * (w - 2 * pad);
    const sy = y => h - pad - (maxY === minY ? 0.5 : (y - minY) / (maxY - minY)) * (h - 2 * pad);
    const d = pts.map((p, i) => (i ? 'L' : 'M') + sx(p[0]).toFixed(1) + ' ' + sy(p[1]).toFixed(1)).join(' ');
    return `<svg class="ss-spark" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-hidden="true">
        <path d="${d}" fill="none" stroke="${color}" stroke-width="1.6" vector-effect="non-scaling-stroke"/></svg>`;
}

function renderCards() {
    const cardVars = seriesVars.map((v, i) => ({ ...v, col: i })).filter(v => v.card);
    if (cardVars.length === 0) {
        document.getElementById('dataCards').innerHTML = "<div class='col-12'><p class='ss-muted'>Nenhuma variável marcada como card.</p></div>";
        return;
    }
    let html = '';
    cardVars.forEach((v, k) => {
        const values = seriesPoints.map(p => p.v[v.col]);
        let lastIdx = values.length - 1;
        while (lastIdx >= 0 && values[lastIdx] === null) lastIdx--;
        const fb = fallbackCards[v.desc];
        const value = lastIdx >= 0 ? values[lastIdx] : (fb ? fb.value : null);
        const time = lastIdx >= 0 ? seriesPoints[lastIdx].time : (fb ? fb.time : null);
        let prevIdx = lastIdx - 1;
        while (prevIdx >= 0 && values[prevIdx] === null) prevIdx--;
        const nums = values.filter(x => x !== null);
        let trend = '';
        if (prevIdx >= 0) {
            const diff = values[lastIdx] - values[prevIdx];
            trend = diff > 0 ? "<i class='bi bi-arrow-up-right ss-muted' title='Subiu em relação ao envio anterior'></i>"
                : diff < 0 ? "<i class='bi bi-arrow-down-right ss-muted' title='Desceu em relação ao envio anterior'></i>"
                : "<i class='bi bi-arrow-right ss-muted' title='Estável'></i>";
        }
        const unit = v.unit ? `<span class="ss-kpi-unit">${esc(v.unit)}</span>` : '';
        const color = PALETTE[seriesVars.filter(x => x.chart).findIndex(x => x.index === v.index) % PALETTE.length] || PALETTE[k % PALETTE.length];
        const range = nums.length > 1
            ? `<span title="Mínimo e máximo nos últimos ${nums.length} envios">mín ${esc(formatValue(Math.min(...nums)))} · máx ${esc(formatValue(Math.max(...nums)))}</span>` : '';
        html += `<div class="col-sm-6 col-lg-4 col-xl-3"><div class="ss-card p-3 h-100 d-flex flex-column">
            <div class="d-flex align-items-center justify-content-between mb-1 gap-2">
              <span class="small fw-semibold ss-muted text-truncate" title="${esc(v.desc)}">${esc(v.desc)}</span>${trend}</div>
            <div class="d-flex align-items-end justify-content-between gap-2">
              <div class="ss-kpi-value">${value === null ? '—' : esc(formatValue(value))}${unit}</div>
              ${sparkline(values.slice(-60), color)}
            </div>
            <div class="small ss-muted mt-2 d-flex flex-wrap justify-content-between gap-1">
              <span><i class="bi bi-clock me-1"></i>${esc(time || 'sem leituras')}</span>${range}</div>
          </div></div>`;
    });
    document.getElementById('dataCards').innerHTML = html;
}

// ---- Graficos ---------------------------------------------------------------------------------

function applyChartTheme() {
    const dark = currentTheme() === 'dark';
    Chart.defaults.color = dark ? '#adb5bd' : '#495057';
    Chart.defaults.borderColor = dark ? 'rgba(255,255,255,.08)' : 'rgba(0,0,0,.06)';
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
}

function onThemeChange() {
    buildCharts();
}

function chartVars() {
    return seriesVars.map((v, i) => ({ ...v, col: i })).filter(v => v.chart);
}

function pointX(p) {
    return p.t ?? p.id;
}

function datasetFor(v, k) {
    const color = PALETTE[k % PALETTE.length];
    return {
        label: v.desc + (v.unit ? ` (${v.unit})` : ''),
        data: seriesPoints.filter(p => p.v[v.col] !== null).map(p => ({ x: pointX(p), y: p.v[v.col] })),
        borderColor: color,
        backgroundColor: color + '22',
        pointRadius: seriesPoints.length > 120 ? 0 : 2,
        pointHoverRadius: 4,
        borderWidth: 2,
        tension: 0.25,
        fill: chartMode() === 'split'
    };
}

/** Rotulo do eixo x: so a hora se a janela for de um dia; senao, data e hora. */
function timeTick(value) {
    const first = seriesPoints.length ? pointX(seriesPoints[0]) : value;
    const last = seriesPoints.length ? pointX(seriesPoints[seriesPoints.length - 1]) : value;
    const d = new Date(value);
    if (isNaN(d)) return '';
    return (last - first) < 86400000
        ? d.toLocaleTimeString('pt-BR')
        : d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function chartOptions(yTitle, axes) {
    const scales = {
        x: { type: 'linear', bounds: 'data', ticks: { maxTicksLimit: window.innerWidth < 576 ? 4 : 7, maxRotation: 0, callback: timeTick }, grid: { display: false } }
    };
    axes.forEach((a, i) => {
        scales[a.id] = {
            position: i === 0 ? 'left' : 'right',
            title: { display: !!a.title, text: a.title },
            grid: { drawOnChartArea: i === 0 },
            ticks: { callback: v => formatValue(v) }
        };
    });
    return {
        parsing: false,
        normalized: true,
        animation: false,
        responsive: true,
        maintainAspectRatio: false,
        spanGaps: true,
        interaction: { mode: 'nearest', axis: 'x', intersect: false },
        scales: scales,
        plugins: {
            legend: { display: !yTitle, position: 'bottom', labels: { usePointStyle: true, boxHeight: 6 } },
            // Muitos pontos: reduz para ~400 preservando a forma da curva (algoritmo LTTB)
            decimation: { enabled: true, algorithm: 'lttb', samples: 400, threshold: 600 },
            tooltip: {
                callbacks: {
                    title: items => {
                        const x = items[0].parsed.x;
                        const p = seriesPoints.find(pt => pointX(pt) === x);
                        return p ? p.time : '';
                    },
                    label: item => ` ${item.dataset.label}: ${formatValue(item.parsed.y)}`
                }
            }
        }
    };
}

function destroyCharts() {
    charts.forEach(c => c.destroy());
    charts = [];
}

function buildCharts() {
    destroyCharts();
    applyChartTheme();
    const vars = chartVars();
    const combinedWrap = document.getElementById('chartCombinedWrap');
    const splitWrap = document.getElementById('chartSplit');
    document.getElementById('noChart').classList.toggle('d-none', vars.length > 0);
    document.getElementById('noData').classList.toggle('d-none', vars.length === 0 || seriesPoints.length > 0);
    const split = chartMode() === 'split';
    combinedWrap.classList.toggle('d-none', split || vars.length === 0);
    document.getElementById('chartPngBtn').classList.toggle('d-none', split || vars.length === 0);
    splitWrap.classList.toggle('d-none', !split || vars.length === 0);
    if (vars.length === 0) return;

    if (!split) {
        // Ate duas unidades diferentes: um eixo y para cada (esquerda/direita)
        const units = [...new Set(vars.map(v => v.unit || ''))];
        const twoAxes = units.length === 2;
        const datasets = vars.map((v, k) => ({ ...datasetFor(v, k), yAxisID: twoAxes && (v.unit || '') === units[1] ? 'y1' : 'y' }));
        const axes = twoAxes ? [{ id: 'y', title: units[0] }, { id: 'y1', title: units[1] }] : [{ id: 'y', title: units.length === 1 ? units[0] : '' }];
        const opts = chartOptions(null, axes);
        charts.push(new Chart(document.getElementById('dataChart'), { type: 'line', data: { datasets }, options: opts }));
    } else {
        splitWrap.innerHTML = vars.map((v, k) => `<div class="col-lg-6"><div class="border rounded-3 p-2 h-100">
            <div class="d-flex justify-content-between align-items-center px-1">
              <span class="small fw-semibold"><span class="ss-legend-dot" style="background:${PALETTE[k % PALETTE.length]}"></span>${esc(v.desc)}${v.unit ? ` <span class="ss-muted fw-normal">(${esc(v.unit)})</span>` : ''}</span>
              <button class="btn btn-sm btn-link text-body-secondary p-0" title="Baixar imagem" onclick="downloadChart(${k})"><i class="bi bi-image"></i></button>
            </div>
            <div style="position: relative; height: 220px;"><canvas></canvas></div></div></div>`).join('');
        splitWrap.querySelectorAll('canvas').forEach((cv, k) => {
            const v = vars[k];
            charts.push(new Chart(cv, { type: 'line', data: { datasets: [datasetFor(v, k)] }, options: chartOptions(v.desc, [{ id: 'y', title: '' }]) }));
        });
    }
}

/** Atualiza os dados dos graficos existentes sem recria-los. */
function updateChartData() {
    const vars = chartVars();
    if (charts.length === 0) {
        buildCharts();
        return;
    }
    document.getElementById('noData').classList.toggle('d-none', vars.length === 0 || seriesPoints.length > 0);
    if (chartMode() === 'combined') {
        const c = charts[0];
        vars.forEach((v, k) => {
            const fresh = datasetFor(v, k);
            c.data.datasets[k].data = fresh.data;
            c.data.datasets[k].pointRadius = fresh.pointRadius;
        });
        c.update('none');
    } else {
        charts.forEach((c, k) => {
            const fresh = datasetFor(vars[k], k);
            c.data.datasets[0].data = fresh.data;
            c.data.datasets[0].pointRadius = fresh.pointRadius;
            c.update('none');
        });
    }
}

function downloadChart(k = 0) {
    const c = charts[k];
    if (!c) return;
    const a = document.createElement('a');
    a.href = c.toBase64Image('image/png', 1);
    a.download = `sensorsync_projeto${projectId}_dispositivo${selectedDevice()}${chartMode() === 'split' ? '_var' + k : ''}.png`;
    a.click();
}

function updateViews() {
    localStorage.setItem('autoUpdate', document.getElementById('setTimeOff').checked ? 'on' : 'off');
    localStorage.setItem('dataLength', chartLength());
    renderDeviceStatus();
    loadSeries();
}

// ---- Dispositivos -----------------------------------------------------------------------------

function fmtDate(s) {
    return s ? new Date(s).toLocaleString('pt-BR') : 'nunca';
}

async function loadDevices() {
    const list = document.getElementById('devicesList');
    try {
        await refreshDeviceSelector();
        // Lista em linhas flexiveis: no celular cada dispositivo vira um bloco empilhado
        let html = "<div class='d-flex flex-column gap-2'>";
        for (const d of devices) {
            const token = d.hasToken ? `<code>${esc(d.tokenPrefix)}…</code>` : "<span class='badge text-bg-warning'><i class='bi bi-exclamation-triangle me-1'></i>sem token</span>";
            html += `<div class='ss-var-row d-flex flex-wrap align-items-center gap-2 gap-md-3'>
                <span class='badge rounded-pill ss-chip' title='Índice do dispositivo'>${d.deviceIndex}</span>
                <input class='form-control form-control-sm flex-grow-1' style='min-width: 160px; width: auto; max-width: 320px;' value='${esc(d.name)}'
                       aria-label='Nome do dispositivo ${d.deviceIndex}' onchange='renameDevice(${d.id}, this.value)'>
                <span class='small'><span class='ss-muted'>Token</span> ${token}</span>
                <span class='small'><span class='ss-muted'>Último envio</span> ${esc(fmtDate(d.lastSeenAt))}</span>
                <span class='ms-md-auto d-flex gap-2'>
                  <button class='btn btn-sm ${d.hasToken ? 'btn-outline-primary' : 'btn-primary'}' onclick='newToken(${d.id})'><i class='bi bi-key me-1'></i>${d.hasToken ? 'Novo token' : 'Gerar token'}</button>
                  <button class='btn btn-sm btn-outline-secondary' onclick='openCodeFor(${d.deviceIndex})' title='Código'><i class='bi bi-code-slash'></i></button>
                  <button class='btn btn-sm btn-outline-danger' onclick='deleteDevice(${d.id})' title='Excluir'><i class='bi bi-trash'></i></button>
                </span></div>`;
        }
        list.innerHTML = html + '</div>';
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
                ? `<th class='${j ? 'text-end' : ''} px-2 px-sm-3 py-2'>${esc(cell)}</th>`
                : (j ? `<td class='text-end font-monospace px-2 px-sm-3' title='${esc(String(cell).replace('.', ','))}'>${cell === '' ? '' : esc(formatValue(cell))}</td>`
                     : `<td class='ss-muted px-2 px-sm-3 small text-nowrap' title='${esc(cell)}'><span class='d-none d-sm-inline'>${esc(cell)}</span><span class='d-sm-none'>${esc(String(cell).slice(0, 8))}</span></td>`)).join('') + '</tr>' + (i === 0 ? '</thead>' : '');
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
