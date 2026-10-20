// ***************************************************************************************************
// Relatorio: export.html?id=<projeto>

const projectId = queryParam('id') || localStorage.getItem('projectID');
let project = null;
let report = [];

async function init() {
    await loadCurrentUser();
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
    document.getElementById('reportTitle').textContent = `Relatório - ${project.name} - ${deviceName}`;
    const div = document.getElementById('tableDataDiv');
    try {
        report = await api(`/data/report/${projectId}/device/${sel.value}`);
        let table = "<div class='table-responsive'><table class='table table-bordered table-sm'>";
        report.forEach((row, i) => {
            table += '<tr>' + row.map(cell => i === 0
                ? `<th class='text-center'>${esc(cell)}</th>`
                : `<td class='text-center'>${esc(String(cell).replace('.', ','))}</td>`).join('') + '</tr>';
        });
        div.innerHTML = table + '</table></div>' + (report.length <= 1 ? "<p class='text-body-secondary'>Sem leituras para este dispositivo.</p>" : '');
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
