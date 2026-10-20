// ***************************************************************************************************
// Pagina do projeto: dashboard.html?id=<projeto>#visao|dispositivos|codigo|relatorio
// As abas trocam sem recarregar a pagina; cada uma carrega seus dados quando e aberta.
// Na visao geral da para comparar varios dispositivos ao mesmo tempo (chips de selecao);
// codigo e relatorio usam um dispositivo por vez.

const projectId = queryParam('id') || localStorage.getItem('projectID');
let devices = [];
let activeTab = 'visao';
const TABS = ['visao', 'dispositivos', 'codigo', 'relatorio'];
const TOKEN_PLACEHOLDER = 'COLE_AQUI_O_TOKEN_DO_DISPOSITIVO';
const PALETTE = ['#32a041', '#1c7ed6', '#f08c00', '#ae3ec9', '#c8191e', '#0ca678', '#5c7cfa', '#d6336c'];

let selection = [];      // indices dos dispositivos exibidos na visao geral
let single = null;       // indice do dispositivo usado em codigo e relatorio

function store(key, value) { try { localStorage.setItem(key, value); } catch (e) { } }
function load(key) { try { return localStorage.getItem(key); } catch (e) { return null; } }

/** Dispositivo atual de codigo/relatorio (mantido por compatibilidade com o restante do script). */
function selectedDevice() {
    return single;
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

    const len = Number(load('dataLength')) || 50;
    (document.querySelector(`input[name='len'][value='${len}']`) || document.getElementById('len50')).checked = true;
    document.getElementById('setTimeOff').checked = load('autoUpdate') !== 'off';
    document.querySelectorAll("input[name='len']").forEach(r => r.addEventListener('change', updateViews));
    document.getElementById('setTimeOff').addEventListener('change', updateViews);
    document.getElementById('modeCombined').addEventListener('change', () => setChartMode('combined'));
    document.getElementById('modeSplit').addEventListener('change', () => setChartMode('split'));

    await refreshDeviceSelector();
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

// ---- Selecao de dispositivos (chips) ----------------------------------------------------------

/** Recarrega a lista de dispositivos e ajusta a selecao salva (remove os que nao existem mais). */
async function refreshDeviceSelector() {
    devices = await api('/project/' + projectId + '/devices');
    const exists = i => devices.some(d => d.deviceIndex === i);
    let saved = [];
    try { saved = JSON.parse(load('dash_sel_' + projectId) || '[]'); } catch (e) { }
    selection = saved.filter(exists);
    if (selection.length === 0 && devices.length) selection = [devices[0].deviceIndex];
    const savedSingle = Number(load('dash_device_' + projectId));
    single = exists(savedSingle) && load('dash_device_' + projectId) !== null ? savedSingle : (selection[0] ?? null);
    renderChips();
    renderDeviceStatus();
}

function deviceColor(idx) {
    const pos = devices.findIndex(d => d.deviceIndex === idx);
    return PALETTE[(pos < 0 ? 0 : pos) % PALETTE.length];
}

function deviceName(idx) {
    const d = devices.find(x => x.deviceIndex === idx);
    return d ? d.name : 'Dispositivo ' + idx;
}

function statusOf(d) {
    if (!d.hasToken) return { cls: '', text: 'sem token' };
    if (!d.lastSeenAt) return { cls: '', text: 'aguardando o primeiro envio' };
    const age = (Date.now() - new Date(d.lastSeenAt).getTime()) / 1000;
    return { cls: age < 120 ? 'online' : age < 3600 ? 'idle' : 'offline', text: 'último envio ' + timeAgo(d.lastSeenAt) };
}

function renderChips() {
    const bar = document.getElementById('deviceChips');
    const multi = activeTab === 'visao';
    const active = idx => multi ? selection.includes(idx) : single === idx;
    let html = devices.map(d => {
        const st = statusOf(d);
        const on = active(d.deviceIndex);
        return `<button type="button" class="ss-dev-chip ${on ? 'active' : ''}" aria-pressed="${on}"
                    title="${esc(d.name)} · ${esc(st.text)}" onclick="toggleDevice(${d.deviceIndex})"
                    style="--chip-color: ${multi ? deviceColor(d.deviceIndex) : 'var(--ss-green)'}">
                  <span class="ss-status-dot ${st.cls}"></span>
                  <span class="text-truncate">${esc(d.name)}</span>
                  ${on ? "<i class='bi bi-check-lg'></i>" : ''}
                </button>`;
    }).join('');
    if (multi && devices.length > 1) {
        const all = selection.length === devices.length;
        html += `<button type="button" class="btn btn-sm btn-link text-decoration-none px-2" onclick="selectAll(${!all})">
                   ${all ? 'Só o primeiro' : 'Todos'}</button>`;
    }
    bar.innerHTML = html;
    document.getElementById('chipsHint').textContent = multi
        ? (devices.length > 1 ? 'Toque para incluir ou remover dispositivos da comparação' : '')
        : 'Escolha o dispositivo';
}

function toggleDevice(idx) {
    if (activeTab === 'visao') {
        if (selection.includes(idx)) {
            if (selection.length === 1) return;  // sempre ao menos um
            selection = selection.filter(i => i !== idx);
        } else {
            selection = devices.map(d => d.deviceIndex).filter(i => i === idx || selection.includes(i));
        }
        store('dash_sel_' + projectId, JSON.stringify(selection));
    } else {
        single = idx;
        store('dash_device_' + projectId, idx);
    }
    renderChips();
    onDeviceChanged();
}

function selectAll(all) {
    selection = all ? devices.map(d => d.deviceIndex) : [devices[0].deviceIndex];
    store('dash_sel_' + projectId, JSON.stringify(selection));
    renderChips();
    onDeviceChanged();
}

async function refreshDeviceStatus() {
    try {
        devices = await api('/project/' + projectId + '/devices');
        renderChips();
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

/** Linha de status abaixo do titulo. */
function renderDeviceStatus() {
    const el = document.getElementById('deviceStatus');
    const online = devices.filter(d => statusOf(d).cls === 'online').length;
    el.innerHTML = `<span class="ss-status-dot ${online ? 'online' : ''}"></span>
        <span>${devices.length} dispositivo${devices.length === 1 ? '' : 's'} · ${online} enviando agora</span>`;
}

// ---- Abas -------------------------------------------------------------------------------------

function tabFromHash() {
    const h = window.location.hash.replace('#', '');
    return TABS.includes(h) ? h : 'visao';
}

function showTab(tab, updateHash = true) {
    activeTab = tab;
    document.querySelectorAll('#projectTabs [data-tab]').forEach(b => {
        b.classList.toggle('active', b.dataset.tab === tab);
        b.setAttribute('aria-selected', b.dataset.tab === tab);
    });
    document.querySelectorAll('[data-panel]').forEach(p => p.classList.toggle('d-none', p.dataset.panel !== tab));
    document.getElementById('deviceChipsWrap').classList.toggle('d-none', tab === 'dispositivos');
    if (updateHash) history.replaceState(null, '', '#' + tab);
    renderChips();

    if (tab === 'visao') updateViews();
    if (tab === 'dispositivos') loadDevices();
    if (tab === 'codigo') { renderTokens(); generateCode(); }
    if (tab === 'relatorio') loadReport();
}

function onDeviceChanged() {
    if (activeTab === 'visao') loadSeries();
    if (activeTab === 'codigo') generateCode();
    if (activeTab === 'relatorio') loadReport();
}

/** Seleciona o dispositivo e abre a aba de codigo. */
function openCodeFor(deviceIndex) {
    single = deviceIndex;
    store('dash_device_' + projectId, deviceIndex);
    showTab('codigo');
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ---- Visao geral: cards e graficos -------------------------------------------------------------
// Uma unica chamada (/data/series/{projeto}?devices=...) alimenta cards e graficos de todos os
// dispositivos selecionados. Depois da carga, a cada 2 s so chegam os envios novos (?after=<id>).

let seriesVars = [];        // [{index, desc, unit, card, chart}]
let seriesByDev = {};       // indice do dispositivo -> [{id, t, time, v: [...]}]
let latestByDev = {};       // indice -> [{v, time} | null] (ultimo valor conhecido, fora da janela)
let lastPointId = 0;
let charts = [];
let polling = false;
let loadToken = 0;

function formatValue(v) {
    const n = Number(v);
    const digits = Math.abs(n) >= 100 ? 1 : Math.abs(n) >= 1 ? 2 : 4;
    return n.toLocaleString('pt-BR', { maximumFractionDigits: digits });
}

function multiDevice() {
    return selection.length > 1;
}

function chartMode() {
    return multiDevice() || load('chartMode') === 'split' ? 'split' : 'combined';
}

function setChartMode(mode) {
    store('chartMode', mode);
    buildCharts();
}

async function loadSeries() {
    const token = ++loadToken;
    const sel = selection.slice();
    try {
        const r = await api(`/data/series/${projectId}?devices=${sel.join(',')}&limit=${chartLength()}`);
        if (token !== loadToken) return;  // selecao mudou durante a busca
        seriesVars = r.variables;
        seriesByDev = {};
        latestByDev = r.latest || {};
        lastPointId = 0;
        sel.forEach(i => {
            seriesByDev[i] = r.devices[i] || [];
            seriesByDev[i].forEach(p => { if (p.id > lastPointId) lastPointId = p.id; });
        });
        renderCards();
        buildCharts();
    } catch (e) {
        document.getElementById('dataCards').innerHTML = `<div class='col-12'><div class='alert alert-danger'>${esc(e.message)}</div></div>`;
    }
}

async function pollSeries() {
    if (polling || selection.length === 0) return;
    polling = true;
    const token = loadToken;
    const sel = selection.slice();
    try {
        const r = await api(`/data/series/${projectId}?devices=${sel.join(',')}&limit=${chartLength()}&after=${lastPointId}`);
        if (token !== loadToken) return;
        let changed = false;
        sel.forEach(i => {
            const fresh = r.devices[i] || [];
            if (!fresh.length) return;
            changed = true;
            const arr = seriesByDev[i] || (seriesByDev[i] = []);
            arr.push(...fresh);
            const extra = arr.length - chartLength();
            if (extra > 0) arr.splice(0, extra);
            fresh.forEach(p => { if (p.id > lastPointId) lastPointId = p.id; });
        });
        if (changed) {
            renderCards();
            updateChartData();
        }
    } catch (e) {
        console.error(e);
    } finally {
        polling = false;
    }
}

// ---- Cards ------------------------------------------------------------------------------------

function sparkline(values, color, w = 120, h = 32) {
    const pts = values.map((v, i) => [i, v]).filter(p => p[1] !== null && p[1] !== undefined);
    if (pts.length < 2) return '';
    const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
    const minX = Math.min(...xs), maxX = Math.max(...xs), minY = Math.min(...ys), maxY = Math.max(...ys);
    const pad = 2;
    const sx = x => pad + (maxX === minX ? 0 : (x - minX) / (maxX - minX)) * (w - 2 * pad);
    const sy = y => h - pad - (maxY === minY ? 0.5 : (y - minY) / (maxY - minY)) * (h - 2 * pad);
    const d = pts.map((p, i) => (i ? 'L' : 'M') + sx(p[0]).toFixed(1) + ' ' + sy(p[1]).toFixed(1)).join(' ');
    return `<svg class="ss-spark" style="width:${w}px;height:${h}px" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" aria-hidden="true">
        <path d="${d}" fill="none" stroke="${color}" stroke-width="1.6" vector-effect="non-scaling-stroke"/></svg>`;
}

/** Ultimo valor de uma variavel para um dispositivo: da janela ou, se nao houver, o ultimo conhecido. */
function lastValue(idx, col) {
    const pts = seriesByDev[idx] || [];
    for (let k = pts.length - 1; k >= 0; k--) {
        if (pts[k].v[col] !== null) return { v: pts[k].v[col], time: pts[k].time, k };
    }
    const l = (latestByDev[idx] || [])[col];
    return l ? { v: l.v, time: l.time, k: -1 } : null;
}

function trendIcon(idx, col, last) {
    if (!last || last.k < 1) return '';
    const pts = seriesByDev[idx];
    for (let k = last.k - 1; k >= 0; k--) {
        if (pts[k].v[col] !== null) {
            const diff = last.v - pts[k].v[col];
            return diff > 0 ? "<i class='bi bi-arrow-up-right ss-muted' title='Subiu em relação ao envio anterior'></i>"
                : diff < 0 ? "<i class='bi bi-arrow-down-right ss-muted' title='Desceu em relação ao envio anterior'></i>"
                : "<i class='bi bi-arrow-right ss-muted' title='Estável'></i>";
        }
    }
    return '';
}

function renderCards() {
    const cardVars = seriesVars.map((v, i) => ({ ...v, col: i })).filter(v => v.card);
    const target = document.getElementById('dataCards');
    if (cardVars.length === 0) {
        target.innerHTML = "<div class='col-12'><p class='ss-muted'>Nenhuma variável marcada como card.</p></div>";
        return;
    }
    const unitHtml = v => v.unit ? `<span class="ss-kpi-unit">${esc(v.unit)}</span>` : '';
    let html = '';
    if (!multiDevice()) {
        const idx = selection[0];
        cardVars.forEach((v, k) => {
            const values = (seriesByDev[idx] || []).map(p => p.v[v.col]);
            const last = lastValue(idx, v.col);
            const nums = values.filter(x => x !== null);
            const color = PALETTE[Math.max(0, chartVars().findIndex(x => x.index === v.index)) % PALETTE.length];
            const range = nums.length > 1
                ? `<span title="Mínimo e máximo nos últimos ${nums.length} envios">mín ${esc(formatValue(Math.min(...nums)))} · máx ${esc(formatValue(Math.max(...nums)))}</span>` : '';
            html += `<div class="col-sm-6 col-lg-4 col-xl-3"><div class="ss-card p-3 h-100 d-flex flex-column">
                <div class="d-flex align-items-center justify-content-between mb-1 gap-2">
                  <span class="small fw-semibold ss-muted text-truncate" title="${esc(v.desc)}">${esc(v.desc)}</span>${trendIcon(idx, v.col, last)}</div>
                <div class="d-flex align-items-end justify-content-between gap-2">
                  <div class="ss-kpi-value">${last ? esc(formatValue(last.v)) : '—'}${unitHtml(v)}</div>
                  ${sparkline(values.slice(-60), color)}
                </div>
                <div class="small ss-muted mt-2 d-flex flex-wrap justify-content-between gap-1">
                  <span><i class="bi bi-clock me-1"></i>${esc(last ? last.time : 'sem leituras')}</span>${range}</div>
              </div></div>`;
        });
    } else {
        // Varios dispositivos: um card por variavel, uma linha por dispositivo (cor = dispositivo)
        cardVars.forEach(v => {
            const rows = selection.map(idx => {
                const last = lastValue(idx, v.col);
                const values = (seriesByDev[idx] || []).map(p => p.v[v.col]).slice(-40);
                return `<div class="d-flex align-items-center gap-2 py-1" title="${esc(last ? last.time : 'sem leituras')}">
                    <span class="ss-legend-dot flex-shrink-0" style="background:${deviceColor(idx)}"></span>
                    <span class="small text-truncate flex-grow-1 min-w-0">${esc(deviceName(idx))}</span>
                    ${sparkline(values, deviceColor(idx), 56, 20)}
                    <span class="fw-semibold font-monospace text-end" style="min-width: 64px">${last ? esc(formatValue(last.v)) : '—'}</span>
                  </div>`;
            }).join('');
            html += `<div class="col-sm-6 col-lg-4 col-xl-3"><div class="ss-card p-3 h-100">
                <div class="small fw-semibold ss-muted mb-1 text-truncate" title="${esc(v.desc)}">${esc(v.desc)}${v.unit ? ` <span class="fw-normal">(${esc(v.unit)})</span>` : ''}</div>
                ${rows}</div></div>`;
        });
    }
    target.innerHTML = html;
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

function allPoints() {
    return selection.flatMap(i => seriesByDev[i] || []);
}

/**
 * Pontos de uma variavel. Pausas longas no envio (mais de 5x o intervalo tipico, minimo 5 s)
 * viram uma quebra na linha, em vez de uma reta ligando dois momentos distantes.
 */
function dataFor(idx, col) {
    const pts = (seriesByDev[idx] || []).filter(p => p.v[col] !== null).map(p => ({ x: pointX(p), y: p.v[col] }));
    if (pts.length < 3) return pts;
    const dts = pts.slice(1).map((p, i) => p.x - pts[i].x).sort((a, b) => a - b);
    const gap = Math.max(5000, 5 * dts[Math.floor(dts.length / 2)]);
    const out = [pts[0]];
    for (let i = 1; i < pts.length; i++) {
        if (pts[i].x - pts[i - 1].x > gap) out.push({ x: (pts[i].x + pts[i - 1].x) / 2, y: null });
        out.push(pts[i]);
    }
    return out;
}

/**
 * Comparando dispositivos: o eixo x comeca no inicio da janela do dispositivo com o envio mais
 * recente, para que um dispositivo parado ha horas nao "esmague" o grafico dos demais.
 */
function compareXMin() {
    if (!multiDevice()) return undefined;
    let best = null;
    selection.forEach(i => {
        const pts = seriesByDev[i] || [];
        if (!pts.length) return;
        const last = pointX(pts[pts.length - 1]);
        if (!best || last > best.last) best = { last, first: pointX(pts[0]) };
    });
    return best ? best.first : undefined;
}

function makeDataset(label, color, data, fill) {
    return {
        label, data,
        borderColor: color,
        backgroundColor: color + '22',
        pointRadius: data.length > 120 ? 0 : 2,
        pointHoverRadius: 4,
        borderWidth: 2,
        tension: 0.25,
        fill
    };
}

/** Datasets de um grafico: por variavel (1 dispositivo) ou por dispositivo (varios). */
function datasetsFor(vars) {
    if (!multiDevice()) {
        const idx = selection[0];
        return vars.map((v, k) => makeDataset(v.desc + (v.unit ? ` (${v.unit})` : ''), PALETTE[k % PALETTE.length],
            dataFor(idx, v.col), chartMode() === 'split'));
    }
    const v = vars[0];
    return selection.map(idx => makeDataset(deviceName(idx), deviceColor(idx), dataFor(idx, v.col), false));
}

function timeTick(value) {
    const xs = allPoints().map(pointX);
    const min = compareXMin() ?? (xs.length ? Math.min(...xs) : 0);
    const span = xs.length ? Math.max(...xs) - min : 0;
    const d = new Date(value);
    if (isNaN(d)) return '';
    return span < 86400000
        ? d.toLocaleTimeString('pt-BR')
        : d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }) + ' ' + d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function chartOptions(showLegend, axes) {
    const scales = {
        x: { type: 'linear', bounds: 'data', min: compareXMin(), ticks: { maxTicksLimit: window.innerWidth < 576 ? 4 : 7, maxRotation: 0, callback: timeTick }, grid: { display: false } }
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
        spanGaps: false,
        interaction: { mode: 'nearest', axis: 'x', intersect: false },
        scales,
        plugins: {
            legend: { display: showLegend, position: 'bottom', labels: { usePointStyle: true, boxHeight: 6 } },
            decimation: { enabled: true, algorithm: 'lttb', samples: 400, threshold: 600 },
            tooltip: {
                callbacks: {
                    title: items => {
                        const x = items[0].parsed.x;
                        const p = allPoints().find(pt => pointX(pt) === x);
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
    const multi = multiDevice();
    const split = chartMode() === 'split';
    document.getElementById('modeCombined').checked = !split;
    document.getElementById('modeSplit').checked = split;
    // Com varios dispositivos o grafico e sempre um por variavel (uma linha por dispositivo)
    document.getElementById('modeGroup').classList.toggle('d-none', multi);
    document.getElementById('multiHint').classList.toggle('d-none', !multi);
    document.getElementById('noChart').classList.toggle('d-none', vars.length > 0);
    document.getElementById('noData').classList.toggle('d-none', vars.length === 0 || allPoints().length > 0);
    document.getElementById('chartCombinedWrap').classList.toggle('d-none', split || vars.length === 0);
    document.getElementById('chartPngBtn').classList.toggle('d-none', split || vars.length === 0);
    const splitWrap = document.getElementById('chartSplit');
    splitWrap.classList.toggle('d-none', !split || vars.length === 0);
    if (vars.length === 0) return;

    if (!split) {
        const units = [...new Set(vars.map(v => v.unit || ''))];
        const twoAxes = units.length === 2;
        const datasets = datasetsFor(vars).map((ds, k) => ({ ...ds, yAxisID: twoAxes && (vars[k].unit || '') === units[1] ? 'y1' : 'y' }));
        const axes = twoAxes ? [{ id: 'y', title: units[0] }, { id: 'y1', title: units[1] }] : [{ id: 'y', title: units.length === 1 ? units[0] : '' }];
        charts.push(new Chart(document.getElementById('dataChart'), { type: 'line', data: { datasets }, options: chartOptions(true, axes) }));
        return;
    }
    splitWrap.innerHTML = vars.map((v, k) => {
        const dot = multi ? '' : `<span class="ss-legend-dot" style="background:${PALETTE[k % PALETTE.length]}"></span>`;
        return `<div class="${vars.length === 1 ? 'col-12' : 'col-lg-6'}"><div class="border rounded-3 p-2 h-100">
            <div class="d-flex justify-content-between align-items-center px-1">
              <span class="small fw-semibold">${dot}${esc(v.desc)}${v.unit ? ` <span class="ss-muted fw-normal">(${esc(v.unit)})</span>` : ''}</span>
              <button class="btn btn-sm btn-link text-body-secondary p-0" title="Baixar imagem" onclick="downloadChart(${k})"><i class="bi bi-image"></i></button>
            </div>
            <div class="ss-chart-split"><canvas></canvas></div></div></div>`;
    }).join('');
    splitWrap.querySelectorAll('canvas').forEach((cv, k) => {
        const datasets = multi ? datasetsFor([vars[k]]) : [datasetsFor(vars)[k]];
        charts.push(new Chart(cv, { type: 'line', data: { datasets }, options: chartOptions(multi, [{ id: 'y', title: '' }]) }));
    });
}

/** Atualiza os dados dos graficos existentes sem recria-los. */
function updateChartData() {
    const vars = chartVars();
    if (charts.length === 0) {
        buildCharts();
        return;
    }
    document.getElementById('noData').classList.toggle('d-none', vars.length === 0 || allPoints().length > 0);
    if (chartMode() === 'combined') {
        const fresh = datasetsFor(vars);
        charts[0].data.datasets.forEach((ds, k) => { ds.data = fresh[k].data; ds.pointRadius = fresh[k].pointRadius; });
        charts[0].options.scales.x.min = compareXMin();
        charts[0].update('none');
    } else {
        charts.forEach((c, k) => {
            const fresh = multiDevice() ? datasetsFor([vars[k]]) : [datasetsFor(vars)[k]];
            c.data.datasets.forEach((ds, j) => { ds.data = fresh[j].data; ds.pointRadius = fresh[j].pointRadius; });
            c.options.scales.x.min = compareXMin();
            c.update('none');
        });
    }
}

function downloadChart(k = 0) {
    const c = charts[k];
    if (!c) return;
    const a = document.createElement('a');
    a.href = c.toBase64Image('image/png', 1);
    a.download = `sensorsync_projeto${projectId}_${chartVars()[k] ? chartVars()[k].desc.replace(/\W+/g, '_') : 'grafico'}.png`;
    a.click();
}

function updateViews() {
    store('autoUpdate', document.getElementById('setTimeOff').checked ? 'on' : 'off');
    store('dataLength', chartLength());
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
