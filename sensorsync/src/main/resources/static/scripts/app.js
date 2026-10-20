// ***************************************************************************************************
// Funcoes comuns a todas as telas (requer login)

const HOST = window.location.origin;

/** Chamada a API com JSON. Em 401 volta para o login. Lanca Error com a mensagem do servidor. */
async function api(path, options = {}) {
    const opts = Object.assign({ credentials: 'same-origin', headers: {} }, options);
    if (opts.body !== undefined && typeof opts.body !== 'string') {
        opts.body = JSON.stringify(opts.body);
    }
    if (opts.body !== undefined) {
        opts.headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(HOST + path, opts);
    if (response.status === 401) {
        window.location.href = '/login.html';
        throw new Error('Sessao expirada');
    }
    if (!response.ok) {
        let message = 'Erro ' + response.status;
        try {
            const err = await response.json();
            if (err.message) message = err.message;
        } catch (e) { /* corpo vazio */ }
        throw new Error(message);
    }
    if (response.status === 204) return null;
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

/** Escapa texto para inserir com innerHTML. */
function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function queryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
}

let currentUser = null;

function initials(u) {
    const parts = [u.firstName, u.lastName].filter(Boolean);
    const base = parts.length ? parts.map(p => p[0]).join('') : (u.login || '?')[0];
    return base.slice(0, 2).toUpperCase();
}

function currentTheme() {
    return document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'dark' : 'light';
}

function toggleTheme() {
    const next = currentTheme() === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem('theme', next); } catch (e) { }
    document.documentElement.setAttribute('data-bs-theme', next);
    const icon = document.getElementById('themeIcon');
    if (icon) icon.className = next === 'dark' ? 'bi bi-sun' : 'bi bi-moon-stars';
    if (typeof onThemeChange === 'function') onThemeChange(next);
}

/**
 * Monta a barra de navegacao padrao em <nav id="appNav"> e o rodape em <footer id="appFooter">.
 * active: 'home' | 'create' | 'users' | ''. extra: HTML opcional exibido a esquerda do menu do usuario.
 */
function renderNavbar(active, extra = '') {
    const nav = document.getElementById('appNav');
    if (!nav || !currentUser) return;
    const isAdmin = currentUser.role === 'ADMIN';
    const link = (key, href, icon, label) =>
        `<li class="nav-item"><a class="nav-link ${active === key ? 'active' : ''}" href="${href}"><i class="bi ${icon} me-1"></i>${label}</a></li>`;
    nav.className = 'navbar navbar-expand-lg ss-navbar sticky-top';
    nav.innerHTML = `
      <div class="container-xl">
        <a class="navbar-brand d-flex align-items-center gap-2" href="index.html">
          <img src="img/if_logo.png" alt="IFSC" class="ss-brand-logo">
          <span class="d-flex flex-column lh-sm">
            <span class="fw-semibold">SensorSync</span>
            <span class="ss-brand-sub d-none d-sm-inline">IFSC · Câmpus Jaraguá do Sul - Rau</span>
          </span>
        </a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#appNavMenu"
                aria-controls="appNavMenu" aria-expanded="false" aria-label="Abrir menu">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="appNavMenu">
          <ul class="navbar-nav me-auto">
            ${link('home', 'index.html', 'bi-grid', 'Projetos')}
            ${link('create', 'generator.html', 'bi-plus-circle', 'Novo projeto')}
            ${isAdmin ? link('users', 'users.html', 'bi-people', 'Usuários') : ''}
          </ul>
          <div class="d-flex align-items-center gap-2">
            ${extra}
            <button class="btn btn-link nav-link px-2" type="button" onclick="toggleTheme()" title="Alternar tema claro/escuro">
              <i id="themeIcon" class="bi ${currentTheme() === 'dark' ? 'bi-sun' : 'bi-moon-stars'}"></i>
            </button>
            <div class="dropdown">
              <a class="nav-link dropdown-toggle d-flex align-items-center gap-2" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                <span class="ss-avatar">${esc(initials(currentUser))}</span>
                <span class="d-lg-none d-xl-inline">${esc(currentUser.firstName || currentUser.login)}</span>
              </a>
              <ul class="dropdown-menu dropdown-menu-end shadow-sm">
                <li><h6 class="dropdown-header">${esc(currentUser.login)} · ${isAdmin ? 'Administrador' : 'Usuário'}</h6></li>
                <li><a class="dropdown-item" href="#" onclick="openPasswordModal(); return false;"><i class="bi bi-key me-2"></i>Alterar senha</a></li>
                <li><hr class="dropdown-divider"></li>
                <li><a class="dropdown-item" href="#" onclick="logout(); return false;"><i class="bi bi-box-arrow-right me-2"></i>Sair</a></li>
              </ul>
            </div>
          </div>
        </div>
      </div>`;
    const footer = document.getElementById('appFooter');
    if (footer) {
        footer.className = 'ss-footer py-4 mt-5 border-top';
        footer.innerHTML = `<div class="container-xl d-flex flex-wrap align-items-center justify-content-between gap-3">
            <span>
              <img src="img/jaragua_rau_horizontal_marca2015_CMYK.png" alt="IFSC Câmpus Jaraguá do Sul - Rau" class="ss-campus-logo light-only">
              <img src="img/index/jaragua_rau_horizontal_marca2015_mono_branco.png" alt="IFSC Câmpus Jaraguá do Sul - Rau" class="ss-campus-logo dark-only">
            </span>
            <span class="text-md-end">SensorSync · Plataforma IoT self-hosted do IFSC<br>
              Desenvolvido por <a href="https://github.com/mwsprotte" target="_blank" rel="noopener" class="fw-semibold">
              <i class="bi bi-github me-1"></i>Matheus Willian Sprotte</a></span></div>`;
    }
}

/** Carrega o usuario logado, monta a navegacao e o modal de senha. */
async function loadCurrentUser(active = '', extra = '') {
    currentUser = await api('/api/me');
    renderNavbar(active, extra);
    guardAutofill();
    return currentUser;
}

/**
 * Evita que o gerenciador de senhas do navegador preencha campos comuns (busca, nomes) com o login salvo:
 * desliga o autocomplete e deixa o campo somente leitura ate receber foco.
 * Vale para campos que ja existem e para os criados depois (tabelas, modais).
 */
function guardAutofill() {
    const guard = el => {
        if (el.dataset.ssGuard || el.closest('[data-autofill-ok]')) return;
        el.dataset.ssGuard = '1';
        el.setAttribute('autocomplete', 'off');
        el.setAttribute('data-lpignore', 'true');
        if (!el.readOnly && document.activeElement !== el) {
            el.readOnly = true;
            el.addEventListener('focus', () => { el.readOnly = false; }, { once: true });
            el.addEventListener('pointerdown', () => { el.readOnly = false; }, { once: true });
        }
    };
    const selector = "input[type=text]:not([hidden]), input[type=search], input[type=email], input:not([type])";
    document.querySelectorAll(selector).forEach(guard);
    new MutationObserver(muts => muts.forEach(m => m.addedNodes.forEach(n => {
        if (n.nodeType !== 1) return;
        if (n.matches && n.matches(selector)) guard(n);
        n.querySelectorAll && n.querySelectorAll(selector).forEach(guard);
    }))).observe(document.body, { childList: true, subtree: true });
}

/** Spinner centralizado para areas carregando. */
const SPINNER = "<div class='d-flex justify-content-center py-5'><div class='spinner-border text-success' role='status'><span class='visually-hidden'>Carregando...</span></div></div>";

/** Mensagem temporaria no canto da tela (substitui alert() para avisos simples). */
function toast(message, variant = 'success') {
    let area = document.getElementById('toastArea');
    if (!area) {
        area = document.createElement('div');
        area.id = 'toastArea';
        area.className = 'toast-container position-fixed bottom-0 end-0 p-3';
        document.body.appendChild(area);
    }
    const el = document.createElement('div');
    el.className = `toast align-items-center text-bg-${variant} border-0`;
    el.setAttribute('role', 'status');
    el.innerHTML = `<div class="d-flex"><div class="toast-body">${esc(message)}</div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Fechar"></button></div>`;
    area.appendChild(el);
    const t = new bootstrap.Toast(el, { delay: 3500 });
    el.addEventListener('hidden.bs.toast', () => el.remove());
    t.show();
}

async function logout() {
    await fetch(HOST + '/logout', { method: 'POST', credentials: 'same-origin' });
    window.location.href = '/login.html';
}

function ensurePasswordModal() {
    if (document.getElementById('passwordModal')) return;
    const div = document.createElement('div');
    div.innerHTML = `
    <div class="modal fade" id="passwordModal" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog"><div class="modal-content">
        <div class="modal-header"><h1 class="modal-title fs-5">Alterar senha</h1>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Fechar"></button></div>
        <form class="modal-body" autocomplete="off" onsubmit="changePassword(); return false;">
          <input type="text" name="username" autocomplete="username" value="${esc(currentUser ? currentUser.login : '')}" hidden readonly>
          <div class="mb-3"><label class="form-label" for="pwCurrent">Senha atual</label>
            <input type="password" class="form-control" id="pwCurrent" autocomplete="current-password"></div>
          <div class="mb-3"><label class="form-label" for="pwNew">Nova senha (mínimo 8 caracteres)</label>
            <input type="password" class="form-control" id="pwNew" autocomplete="new-password"></div>
          <div class="mb-3"><label class="form-label" for="pwNew2">Repita a nova senha</label>
            <input type="password" class="form-control" id="pwNew2" autocomplete="new-password"></div>
          <div id="pwMsg" class="text-danger small"></div>
        </form>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>
          <button type="button" class="btn btn-primary" onclick="changePassword()">Salvar</button>
        </div>
      </div></div>
    </div>`;
    document.body.appendChild(div.firstElementChild);
}

function openPasswordModal() {
    // O modal (que tem campos de senha) so e criado quando aberto: um campo de senha presente na
    // pagina faz o navegador tratar outros campos de texto como "login" e preenche-los com o usuario salvo.
    ensurePasswordModal();
    ['pwCurrent', 'pwNew', 'pwNew2'].forEach(id => document.getElementById(id).value = '');
    document.getElementById('pwMsg').textContent = '';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('passwordModal')).show();
}

async function changePassword() {
    const msg = document.getElementById('pwMsg');
    const next = document.getElementById('pwNew').value;
    if (next !== document.getElementById('pwNew2').value) {
        msg.textContent = 'As senhas não conferem.';
        return;
    }
    try {
        await api('/api/me/password', { method: 'POST', body: { currentPassword: document.getElementById('pwCurrent').value, newPassword: next } });
        bootstrap.Modal.getInstance(document.getElementById('passwordModal')).hide();
        toast('Senha alterada.');
    } catch (e) {
        msg.textContent = e.message;
    }
}

/** Copia texto para a area de transferencia (funciona tambem em http sem HTTPS). */
async function copyText(text) {
    try {
        await navigator.clipboard.writeText(text);
    } catch (e) {
        const ta = document.createElement('textarea');
        ta.value = text;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        ta.remove();
    }
}

function downloadText(fileName, text, type = 'text/plain') {
    const blob = new Blob([text], { type: type + ';charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = fileName;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

// ---- Tokens recem-gerados ----------------------------------------------------------------------
// O servidor so guarda o hash do token. Logo apos criar/regenerar, o token fica guardado nesta aba
// (sessionStorage) para a tela de codigo inseri-lo no sketch. Some ao fechar a aba.

function rememberToken(deviceId, token) {
    try { sessionStorage.setItem('token_' + deviceId, token); } catch (e) { }
}

function recallToken(deviceId) {
    try { return sessionStorage.getItem('token_' + deviceId); } catch (e) { return null; }
}
