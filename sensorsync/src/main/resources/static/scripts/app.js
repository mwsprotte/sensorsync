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

/** Carrega o usuario logado e monta o menu do usuario no elemento #userMenu (se existir). */
async function loadCurrentUser() {
    currentUser = await api('/api/me');
    const menu = document.getElementById('userMenu');
    if (menu) {
        menu.innerHTML = `
        <div class="dropdown">
          <a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">
            👤 ${esc(currentUser.login)}${currentUser.role === 'ADMIN' ? ' <span class="badge text-bg-secondary">admin</span>' : ''}
          </a>
          <ul class="dropdown-menu dropdown-menu-end">
            ${currentUser.role === 'ADMIN' ? '<li><a class="dropdown-item" href="users.html">Gerenciar usuários</a></li>' : ''}
            <li><a class="dropdown-item" href="#" onclick="openPasswordModal(); return false;">Alterar senha</a></li>
            <li><hr class="dropdown-divider"></li>
            <li><a class="dropdown-item" href="#" onclick="logout(); return false;">Sair</a></li>
          </ul>
        </div>`;
    }
    ensurePasswordModal();
    return currentUser;
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
        <div class="modal-body">
          <div class="mb-3"><label class="form-label" for="pwCurrent">Senha atual</label>
            <input type="password" class="form-control" id="pwCurrent" autocomplete="current-password"></div>
          <div class="mb-3"><label class="form-label" for="pwNew">Nova senha (mínimo 8 caracteres)</label>
            <input type="password" class="form-control" id="pwNew" autocomplete="new-password"></div>
          <div class="mb-3"><label class="form-label" for="pwNew2">Repita a nova senha</label>
            <input type="password" class="form-control" id="pwNew2" autocomplete="new-password"></div>
          <div id="pwMsg" class="text-danger small"></div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>
          <button type="button" class="btn btn-primary" onclick="changePassword()">Salvar</button>
        </div>
      </div></div>
    </div>`;
    document.body.appendChild(div.firstElementChild);
}

function openPasswordModal() {
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
        alert('Senha alterada.');
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
