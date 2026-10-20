// ***************************************************************************************************
// Gerenciamento de usuarios (somente administradores)

let users = [];

async function loadUsers() {
    const div = document.getElementById('usersList');
    try {
        users = await api('/user');
        let html = `<table class='table align-middle'><thead><tr><th>Login</th><th>Nome</th><th>E-mail</th><th>Papel</th><th>Situação</th><th></th></tr></thead><tbody>`;
        for (const u of users) {
            const me = currentUser && u.id === currentUser.id;
            html += `<tr><td>${esc(u.login)}${me ? " <span class='badge text-bg-info'>você</span>" : ''}</td>
                <td>${esc([u.firstName, u.lastName].filter(Boolean).join(' '))}</td>
                <td>${esc(u.email)}</td>
                <td>${u.role === 'ADMIN' ? 'Administrador' : 'Usuário'}</td>
                <td>${u.enabled ? 'Ativa' : "<span class='text-danger'>Desativada</span>"}</td>
                <td class='text-end text-nowrap'>
                  <button class='btn btn-sm btn-outline-primary' onclick='openUserModal(${u.id})'>Editar</button>
                  ${me ? '' : `<button class='btn btn-sm btn-outline-danger' onclick='deleteUser(${u.id})'>Excluir</button>`}
                </td></tr>`;
        }
        div.innerHTML = html + '</tbody></table>';
    } catch (e) {
        div.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

function openUserModal(id) {
    const u = users.find(x => x.id === id) || { login: '', firstName: '', lastName: '', email: '', role: 'USER', enabled: true };
    document.getElementById('uId').value = id || '';
    document.getElementById('uLogin').value = u.login;
    document.getElementById('uLogin').disabled = !!id;
    document.getElementById('uFirst').value = u.firstName || '';
    document.getElementById('uLast').value = u.lastName || '';
    document.getElementById('uEmail').value = u.email || '';
    document.getElementById('uPassword').value = '';
    document.getElementById('uPasswordLabel').textContent = id ? 'Nova senha (deixe em branco para manter)' : 'Senha (mínimo 8 caracteres)';
    document.getElementById('uRole').value = u.role;
    document.getElementById('uEnabled').checked = u.enabled;
    document.getElementById('uMsg').textContent = '';
    document.getElementById('userModalTitle').textContent = id ? 'Editar ' + u.login : 'Novo usuário';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('userModal')).show();
}

async function saveUser() {
    const id = document.getElementById('uId').value;
    const body = {
        login: document.getElementById('uLogin').value,
        firstName: document.getElementById('uFirst').value,
        lastName: document.getElementById('uLast').value,
        email: document.getElementById('uEmail').value,
        password: document.getElementById('uPassword').value,
        role: document.getElementById('uRole').value,
        enabled: document.getElementById('uEnabled').checked
    };
    try {
        if (id) {
            await api('/user/' + id, { method: 'PUT', body: body });
        } else {
            await api('/user', { method: 'POST', body: body });
        }
        bootstrap.Modal.getInstance(document.getElementById('userModal')).hide();
        loadUsers();
    } catch (e) {
        document.getElementById('uMsg').textContent = e.message;
    }
}

async function deleteUser(id) {
    const u = users.find(x => x.id === id);
    if (!confirm(`Excluir o usuário ${u.login}?`)) return;
    try {
        await api('/user/' + id, { method: 'DELETE' });
        loadUsers();
    } catch (e) {
        alert(e.message);
    }
}

loadCurrentUser().then(loadUsers);
