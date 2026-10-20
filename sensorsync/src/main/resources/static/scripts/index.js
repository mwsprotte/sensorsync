// ***************************************************************************************************
// Tela inicial: lista de projetos do usuario (admin ve todos)

let projects = [];

function statCard(icon, label, value) {
    return `<div class="col-6 col-md-3"><div class="ss-card p-3 d-flex align-items-center gap-3 h-100">
        <span class="ss-icon-badge"><i class="bi ${icon}"></i></span>
        <div><div class="fs-4 fw-semibold lh-1">${value}</div><div class="small ss-muted">${label}</div></div></div></div>`;
}

function renderStats() {
    const devices = projects.reduce((s, p) => s + Number(p.sensorNumber || 0), 0);
    const vars = projects.reduce((s, p) => s + Number(p.dataNumber || 0), 0);
    let html = statCard('bi-folder2-open', 'Projetos', projects.length)
        + statCard('bi-cpu', 'Dispositivos', devices)
        + statCard('bi-activity', 'Variáveis monitoradas', vars);
    if (currentUser.role === 'ADMIN') {
        const owners = new Set(projects.map(p => p.ownerLogin)).size;
        html += statCard('bi-people', 'Usuários com projetos', owners);
    }
    document.getElementById('stats').innerHTML = html;
}

function projectCard(p) {
    const owner = currentUser.role === 'ADMIN' && p.ownerLogin
        ? `<span class="badge rounded-pill ss-chip"><i class="bi bi-person me-1"></i>${esc(p.ownerLogin)}</span>` : '';
    return `<div class="col-sm-6 col-lg-4 col-xxl-3">
      <div class="ss-card ss-project h-100 p-3 position-relative d-flex flex-column">
        <div class="d-flex align-items-start gap-3">
          <span class="ss-icon-badge"><i class="bi bi-broadcast"></i></span>
          <div class="flex-grow-1 min-w-0">
            <h2 class="h6 fw-semibold mb-1 ss-clamp-2" title="${esc(p.name)}">${esc(p.name)}</h2>
            <div class="small ss-muted">Projeto #${p.id}</div>
          </div>
          <div class="dropdown">
            <button class="btn btn-sm btn-link text-body-secondary" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Mais ações">
              <i class="bi bi-three-dots-vertical"></i></button>
            <ul class="dropdown-menu dropdown-menu-end shadow-sm">
              <li><a class="dropdown-item" href="dashboard.html?id=${p.id}#codigo"><i class="bi bi-code-slash me-2"></i>Código embarcado</a></li>
              <li><a class="dropdown-item" href="dashboard.html?id=${p.id}#relatorio"><i class="bi bi-table me-2"></i>Relatório</a></li>
              ${currentUser.role === 'ADMIN' ? `<li><a class="dropdown-item" href="#" onclick="openTransfer(${p.id}); return false;"><i class="bi bi-person-gear me-2"></i>Transferir dono</a></li>` : ''}
              <li><hr class="dropdown-divider"></li>
              <li><a class="dropdown-item" href="#" onclick="clearData(${p.id}); return false;"><i class="bi bi-eraser me-2"></i>Apagar leituras</a></li>
              <li><a class="dropdown-item text-danger" href="#" onclick="deleteProject(${p.id}); return false;"><i class="bi bi-trash me-2"></i>Excluir projeto</a></li>
            </ul>
          </div>
        </div>
        <div class="d-flex flex-wrap gap-2 mt-3 mb-3">
          <span class="badge rounded-pill ss-chip"><i class="bi bi-cpu me-1"></i>${p.sensorNumber} dispositivo${p.sensorNumber == 1 ? '' : 's'}</span>
          <span class="badge rounded-pill ss-chip"><i class="bi bi-activity me-1"></i>${p.dataNumber} variáve${p.dataNumber == 1 ? 'l' : 'is'}</span>
          ${owner}
        </div>
        <a href="dashboard.html?id=${p.id}" class="stretched-link mt-auto small fw-semibold text-decoration-none">
          Abrir dashboard <i class="bi bi-arrow-right"></i></a>
      </div></div>`;
}

function renderProjects() {
    const q = searchTyped ? document.getElementById('search').value.trim().toLowerCase() : '';
    const list = projects.filter(p => !q || p.name.toLowerCase().includes(q) || String(p.id) === q
        || (p.ownerLogin || '').toLowerCase().includes(q));
    const target = document.getElementById('projects');
    if (projects.length === 0) {
        target.innerHTML = `<div class="col-12"><div class="ss-card text-center p-5">
            <span class="ss-icon-badge mb-3" style="width:56px;height:56px;font-size:1.6rem"><i class="bi bi-plus-lg"></i></span>
            <h2 class="h5">Nenhum projeto ainda</h2>
            <p class="ss-muted">Crie o primeiro projeto: a plataforma monta o dashboard e o código do microcontrolador.</p>
            <a href="generator.html" class="btn btn-primary">Criar projeto</a></div></div>`;
        return;
    }
    target.innerHTML = list.length ? list.map(projectCard).join('')
        : `<div class='col-12'><div class='ss-card p-4 text-center'>
             <p class='mb-2'>Nenhum projeto encontrado para "<strong>${esc(q)}</strong>".</p>
             <button class='btn btn-sm btn-outline-secondary' onclick='clearSearch()'><i class='bi bi-x-lg me-1'></i>Limpar busca</button>
           </div></div>`;
}

async function loadProjects() {
    document.getElementById('projects').innerHTML = `<div class="col-12">${SPINNER}</div>`;
    try {
        projects = await api('/project');
        renderStats();
        renderProjects();
    } catch (e) {
        document.getElementById('projects').innerHTML = `<div class='col-12'><div class='alert alert-danger'>${esc(e.message)}</div></div>`;
    }
}

let transferId = null;

async function openTransfer(id) {
    transferId = id;
    const p = projects.find(x => x.id === id);
    document.getElementById('transferName').textContent = p ? p.name : id;
    const sel = document.getElementById('transferUser');
    sel.innerHTML = '<option>Carregando...</option>';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('transferModal')).show();
    try {
        const users = await api('/user');
        sel.innerHTML = users.filter(u => u.enabled).map(u =>
            `<option value="${esc(u.login)}" ${p && u.login === p.ownerLogin ? 'selected' : ''}>${esc([u.firstName, u.lastName].filter(Boolean).join(' ') || u.login)} (${esc(u.login)})</option>`).join('');
    } catch (e) {
        sel.innerHTML = '';
        toast(e.message, 'danger');
    }
}

async function confirmTransfer() {
    const login = document.getElementById('transferUser').value;
    try {
        await api(`/project/${transferId}/owner`, { method: 'PUT', body: { login: login } });
        bootstrap.Modal.getInstance(document.getElementById('transferModal')).hide();
        toast('Projeto transferido para ' + login + '.');
        loadProjects();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function deleteProject(id) {
    const p = projects.find(x => x.id === id);
    if (!confirm(`Excluir o projeto "${p ? p.name : id}"?\n\nTodos os dados, variáveis e dispositivos serão apagados.`)) return;
    try {
        await api('/project/' + id, { method: 'DELETE' });
        toast('Projeto excluído.');
        loadProjects();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

async function clearData(id) {
    const p = projects.find(x => x.id === id);
    if (!confirm(`Apagar todas as leituras gravadas do projeto "${p ? p.name : id}"?\n\nO projeto e os dispositivos continuam.`)) return;
    try {
        await api('/project/' + id + '/data', { method: 'DELETE' });
        toast('Leituras apagadas.');
    } catch (e) {
        toast(e.message, 'danger');
    }
}

function clearSearch() {
    const input = document.getElementById('search');
    input.value = '';
    renderProjects();
    input.focus();
}

// O campo comeca readonly (o gerenciador de senhas do navegador nao preenche campos readonly)
// e o filtro so reage ao que o usuario digita. Alguns navegadores preenchem campos de texto
// automaticamente (ex.: com o login salvo) e so liberam esse valor apos o primeiro clique na pagina,
// o que fazia todos os projetos sumirem.
let searchTyped = false;
const searchInput = document.getElementById('search');
searchInput.value = '';
searchInput.addEventListener('keydown', () => { searchTyped = true; });
searchInput.addEventListener('paste', () => { searchTyped = true; });
searchInput.addEventListener('input', () => {
    if (!searchTyped) {
        searchInput.value = '';
        return;
    }
    renderProjects();
});

loadCurrentUser('home').then(u => {
    const hour = new Date().getHours();
    const hello = hour < 12 ? 'Bom dia' : hour < 18 ? 'Boa tarde' : 'Boa noite';
    document.getElementById('greeting').textContent = `${hello}, ${u.firstName || u.login}`;
    loadProjects();
});
