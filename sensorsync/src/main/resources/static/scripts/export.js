// ***************************************************************************************************
// Relatorio: export.html?id=<projeto>

const projectId = queryParam('id') || localStorage.getItem('projectID');
let project = null;
let report = [];

async function init() {
    await loadCurrentUser('home');
    document.getElementById('backLink').href = 'dashboard.html?id=' + projectId;
    project = await api('/project/' + projectId);
    const devices = await api('/project/' + projectId + '/devices');
    const sel = document.getElementById('deviceSelected');
    sel.innerHTML = devices.map(d => `<option value='${d.deviceIndex}'>${esc(d.name)}</option>`).join('');
    const saved = localStorage.getItem('dash_device_' + projectId);
    if (saved !== null && devices.some(d => String(d.deviceIndex) === saved)) sel.value = saved;
    sel.addEventListener('change', generateTable);
    generateTable();
}

async function generateTable() {
    const sel = document.getElementById('deviceSelected');
    const deviceName = sel.options[sel.selectedIndex] ? sel.options[sel.selectedIndex].text : '';
    document.getElementById('reportTitle').textContent = `Relatório · ${project.name}`;
    const div = document.getElementById('tableDataDiv');
    try {
        report = await api(`/data/report/${projectId}/device/${sel.value}`);
        document.getElementById('reportInfo').textContent = `${deviceName} · ${Math.max(0, report.length - 1)} leitura(s)`;
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
    const csv = report.map((row, i) => row.map(cell => {
        let v = String(cell ?? '');
        if (i > 0) v = v.replace('.', ',');
        return /[;"\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v;
    }).join(';')).join('\r\n');
    const sel = document.getElementById('deviceSelected');
    downloadText(`sensorsync_projeto${projectId}_dispositivo${sel.value}.csv`, '﻿' + csv, 'text/csv');
}

init().catch(e => {
    document.getElementById('tableDataDiv').innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
});
