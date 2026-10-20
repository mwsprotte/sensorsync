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

async function init() {
    await loadCurrentUser();
    if (!projectId) {
        window.location.href = 'index.html';
        return;
    }
    let project;
    try {
        project = await api('/project/' + projectId);
    } catch (e) {
        document.getElementById('dataCards').innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
        return;
    }
    document.title = 'SensorSync - ' + project.name;
    document.getElementById('projectTitle').textContent = project.name;
    document.getElementById('exportLink').href = 'export.html?id=' + projectId;

    const p = prefs();
    document.getElementById('length').value = p.length;
    document.getElementById('setTimeOff').checked = p.auto;

    await refreshDeviceSelector(p.device);
    document.getElementById('deviceSelected').addEventListener('change', () => {
        localStorage.setItem('dash_device_' + projectId, selectedDevice());
        updateViews();
    });
    document.getElementById('length').addEventListener('keypress', e => {
        if (e.key === 'Enter') {
            updateViews();
            bootstrap.Modal.getInstance(document.getElementById('optionsModal')).hide();
        }
    });

    createChart();
    updateViews();
    setInterval(() => {
        if (document.getElementById('setTimeOff').checked && !document.hidden) {
            generateCards();
            updateCharts();
        }
    }, 2000);
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
}

function updateCodeLink() {
    const d = devices.find(x => x.deviceIndex === selectedDevice());
    document.getElementById('codeLink').href = 'code.html?id=' + projectId + (d ? '&device=' + d.id : '');
}

// ---- Cards ------------------------------------------------------------------------------------

async function generateCards() {
    try {
        const cards = await api(`/data/cards/${projectId}/device/${selectedDevice()}`);
        let html = "<div class='container-fluid text-center'><div class='row gap-4 justify-content-center align-items-stretch'>";
        for (const c of cards) {
            const value = c.data === null ? '—' : c.data;
            html += `<div class='col-sm-3 shadow-sm border rounded-4 pt-2'>
                <p class='fw-light mb-1'>${esc(c.dataDesc)}</p>
                <p class='fs-3 mb-1'><strong>${esc(value)}</strong></p>
                <p class='fw-light small'>${esc(c.readTime || 'sem leituras')}</p></div>`;
        }
        if (cards.length === 0) {
            html += "<p class='text-body-secondary'>Nenhuma variável marcada como card.</p>";
        }
        document.getElementById('dataCards').innerHTML = html + "</div></div>";
    } catch (e) {
        console.error(e);
    }
}

// ---- Grafico ----------------------------------------------------------------------------------

function createChart() {
    const ctx = document.getElementById('dataChart').getContext('2d');
    chart = new Chart(ctx, {
        type: 'line',
        data: { labels: [], datasets: [] },
        options: {
            scales: { y: { beginAtZero: false } },
            spanGaps: true,
            plugins: { title: { display: true, text: '' } },
            responsive: true,
            maintainAspectRatio: false,
            animation: false
        }
    });
}

let lastChartKey = '';

async function updateCharts(force = false) {
    const length = Number(document.getElementById('length').value) || 20;
    try {
        const data = await api(`/data/charts/${projectId}/device/${selectedDevice()}/length/${length}`);
        const names = data[0], labels = data[1], series = data.slice(2);
        const key = JSON.stringify([selectedDevice(), length, labels.length, labels[labels.length - 1], series.map(s => s[s.length - 1])]);
        if (!force && key === lastChartKey) return;
        lastChartKey = key;
        const dev = devices.find(d => d.deviceIndex === selectedDevice());
        chart.options.plugins.title.text = dev ? dev.name : '';
        chart.data.labels = labels;
        chart.data.datasets = series.map((s, i) => ({
            label: names[i],
            data: s.map(v => v === '' ? null : Number(v)),
            fill: false,
            tension: 0.1
        }));
        chart.update();
    } catch (e) {
        console.error(e);
    }
}

function updateViews() {
    localStorage.setItem('autoUpdate', document.getElementById('setTimeOff').checked ? 'on' : 'off');
    localStorage.setItem('dataLength', document.getElementById('length').value);
    updateCodeLink();
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
        let html = `<table class='table align-middle'><thead><tr><th>Índice</th><th>Nome</th><th>Token</th><th>Último envio</th><th></th></tr></thead><tbody>`;
        for (const d of devices) {
            const token = d.hasToken ? `<code>${esc(d.tokenPrefix)}…</code>` : "<span class='badge text-bg-warning'>sem token</span>";
            html += `<tr><td>${d.deviceIndex}</td>
                <td><input class='form-control form-control-sm' value='${esc(d.name)}' onchange='renameDevice(${d.id}, this.value)'></td>
                <td>${token}</td><td class='small'>${esc(fmtDate(d.lastSeenAt))}</td>
                <td class='text-nowrap text-end'>
                  <button class='btn btn-sm btn-outline-primary' onclick='newToken(${d.id})'>${d.hasToken ? 'Novo token' : 'Gerar token'}</button>
                  <a class='btn btn-sm btn-outline-secondary' href='code.html?id=${projectId}&device=${d.id}'>Código</a>
                  <button class='btn btn-sm btn-outline-danger' onclick='deleteDevice(${d.id})'>Excluir</button>
                </td></tr>`;
        }
        list.innerHTML = html + '</tbody></table>';
    } catch (e) {
        list.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

function showToken(result, title) {
    rememberToken(result.device.id, result.token);
    const box = document.getElementById('newTokenBox');
    box.innerHTML = `<div class='alert alert-warning'>
        <strong>${esc(title)}</strong> — copie agora, ele não será exibido de novo:<br>
        <code class='fs-6'>${esc(result.token)}</code>
        <button class='btn btn-sm btn-outline-secondary ms-2' id='copyTokenBtn'>Copiar</button>
        <a class='btn btn-sm btn-outline-primary ms-1' href='code.html?id=${projectId}&device=${result.device.id}'>Ver código com este token</a></div>`;
    document.getElementById('copyTokenBtn').onclick = () => copyText(result.token);
}

async function newToken(deviceId) {
    const d = devices.find(x => x.id === deviceId);
    if (d && d.hasToken && !confirm(`Gerar um novo token para "${d.name}"? O token atual deixa de funcionar e a placa precisará ser regravada.`)) return;
    try {
        const r = await api(`/project/${projectId}/devices/${deviceId}/token`, { method: 'POST' });
        showToken(r, 'Token de ' + r.device.name);
        loadDevices();
    } catch (e) {
        alert(e.message);
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
        alert(e.message);
    }
}

async function renameDevice(deviceId, name) {
    try {
        await api(`/project/${projectId}/devices/${deviceId}`, { method: 'PUT', body: { name: name } });
        await refreshDeviceSelector();
    } catch (e) {
        alert(e.message);
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
        alert(e.message);
    }
}

init();
