// ***************************************************************************************************
// Tela de codigo embarcado: code.html?id=<projeto>&board=ESP32&protocol=MQTT[&device=<id>][&novo=1]

const TOKEN_PLACEHOLDER = 'COLE_AQUI_O_TOKEN_DO_DISPOSITIVO';
const projectId = queryParam('id');
let devices = [];

async function init() {
    await loadCurrentUser('home');
    if (!projectId) {
        document.getElementById('codeGenerated').innerHTML = "<div class='alert alert-warning'>Projeto não informado.</div>";
        return;
    }
    document.getElementById('dashboardLink').href = 'dashboard.html?id=' + encodeURIComponent(projectId);
    if (queryParam('board')) document.getElementById('board').value = queryParam('board');
    if (queryParam('protocol')) document.getElementById('protocol').value = queryParam('protocol');

    try {
        const project = await api('/project/' + projectId);
        document.getElementById('codeTitle').textContent = 'Código embarcado · ' + project.name;
        devices = await api('/project/' + projectId + '/devices');
    } catch (e) {
        document.getElementById('codeGenerated').innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
        return;
    }
    const sel = document.getElementById('device');
    sel.innerHTML = devices.map(d => `<option value='${d.id}'>${esc(d.name)} (índice ${d.deviceIndex})</option>`).join('');
    if (queryParam('device')) sel.value = queryParam('device');

    renderTokens();
    for (const id of ['board', 'protocol', 'device']) {
        document.getElementById(id).addEventListener('change', generateCode);
    }
    generateCode();
}

/** Mostra os tokens recem-gerados (existem so nesta aba). */
function renderTokens() {
    const fresh = devices.map(d => ({ d, token: recallToken(d.id) })).filter(x => x.token);
    const box = document.getElementById('tokensBox');
    if (fresh.length === 0) {
        box.innerHTML = `<div class='alert alert-secondary small d-flex gap-2'><i class='bi bi-info-circle'></i><div>O código sai com o marcador <code>${TOKEN_PLACEHOLDER}</code>.
            Gere o token do dispositivo em <a href='dashboard.html?id=${encodeURIComponent(projectId)}'>Dashboard › Dispositivos</a> e use o botão "Código com este token".</div></div>`;
        return;
    }
    let rows = fresh.map(x => `<div class='input-group input-group-sm mt-2'>
        <span class='input-group-text' style='min-width: 140px'>${esc(x.d.name)}</span>
        <input class='form-control font-monospace' readonly value='${esc(x.token)}'>
        <button class='btn btn-outline-secondary' onclick='copyText(${JSON.stringify(x.token)}); toast("Token copiado.")'><i class='bi bi-clipboard'></i></button></div>`).join('');
    box.innerHTML = `<div class='alert alert-warning mb-4'>
        <div class='fw-semibold'><i class='bi bi-shield-lock me-1'></i>Guarde estes tokens agora</div>
        <div class='small'>O servidor guarda só o hash do token. Depois de fechar esta aba, só é possível gerar um novo (o antigo deixa de funcionar).</div>
        ${rows}</div>`;
}

async function generateCode() {
    const deviceId = document.getElementById('device').value;
    const params = new URLSearchParams({
        board: document.getElementById('board').value,
        protocol: document.getElementById('protocol').value,
        deviceId: deviceId
    });
    const target = document.getElementById('codeGenerated');
    try {
        const files = await api(`/project/${projectId}/code?` + params.toString());
        const token = recallToken(deviceId);
        target.innerHTML = '';
        files.forEach((f, i) => {
            const code = token ? f.code.replaceAll(TOKEN_PLACEHOLDER, token) : f.code;
            const card = document.createElement('div');
            card.className = 'ss-card mb-4';
            card.innerHTML = `<div class='d-flex flex-wrap justify-content-between align-items-center gap-2 px-3 py-2 border-bottom'>
                <span class='fw-semibold'><i class='bi bi-file-earmark-code me-1'></i>${esc(f.title)} <span class='ss-muted small fw-normal'>${esc(f.fileName)}</span></span>
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

init();
