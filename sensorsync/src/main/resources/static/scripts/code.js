// ***************************************************************************************************
// Tela de codigo embarcado: code.html?id=<projeto>&board=ESP32&protocol=MQTT[&device=<id>][&novo=1]

const TOKEN_PLACEHOLDER = 'COLE_AQUI_O_TOKEN_DO_DISPOSITIVO';
const projectId = queryParam('id');
let devices = [];

async function init() {
    await loadCurrentUser();
    if (!projectId) {
        document.getElementById('codeGenerated').innerHTML = "<div class='alert alert-warning'>Projeto não informado.</div>";
        return;
    }
    document.getElementById('dashboardLink').href = 'dashboard.html?id=' + encodeURIComponent(projectId);
    if (queryParam('board')) document.getElementById('board').value = queryParam('board');
    if (queryParam('protocol')) document.getElementById('protocol').value = queryParam('protocol');

    try {
        const project = await api('/project/' + projectId);
        document.getElementById('codeTitle').textContent = 'Código embarcado - ' + project.name;
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
        box.innerHTML = `<div class='alert alert-secondary small'>O código sai com o marcador <code>${TOKEN_PLACEHOLDER}</code>.
            Gere ou copie o token do dispositivo na tela <a href='dashboard.html?id=${encodeURIComponent(projectId)}'>Dispositivos</a> do dashboard.</div>`;
        return;
    }
    let rows = fresh.map(x => `<tr><td>${esc(x.d.name)}</td><td><code>${esc(x.token)}</code></td>
        <td><button class='btn btn-sm btn-outline-secondary' onclick='copyText(${JSON.stringify(x.token)})'>Copiar</button></td></tr>`).join('');
    box.innerHTML = `<div class='alert alert-warning'>
        <strong>Guarde estes tokens agora.</strong> Por segurança o servidor não guarda o token em si; depois de fechar esta aba
        só é possível gerar um novo (o antigo deixa de funcionar).
        <table class='table table-sm mt-2 mb-0'><tbody>${rows}</tbody></table></div>`;
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
            card.className = 'card mb-4';
            card.innerHTML = `<div class='card-header d-flex justify-content-between align-items-center'>
                <span class='fw-semibold'>${esc(f.title)} <span class='text-body-secondary small'>${esc(f.fileName)}</span></span>
                <span><button class='btn btn-sm btn-outline-secondary' data-act='copy'>Copiar</button>
                <button class='btn btn-sm btn-outline-primary' data-act='download'>Baixar .ino</button></span></div>
                <pre class='mb-0 p-3' style='max-height: 70vh; overflow: auto;'><code></code></pre>`;
            card.querySelector('code').textContent = code;
            card.querySelector("[data-act='copy']").onclick = () => copyText(code);
            card.querySelector("[data-act='download']").onclick = () => downloadText(f.fileName, code);
            target.appendChild(card);
        });
    } catch (e) {
        target.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

init();
