// ***************************************************************************************************
// Gerenciamento de usuarios (somente administradores)

let users = [];

async function loadUsers() {
    const div = document.getElementById('usersList');
    try {
        const [list, projects] = await Promise.all([api('/user'), api('/project')]);
        users = list;
        const owned = {};
        projects.forEach(p => { owned[p.ownerLogin] = (owned[p.ownerLogin] || 0) + 1; });
        let html = `<div class='table-responsive'><table class='table table-hover align-middle mb-0'><thead><tr>
            <th class='px-3'>Usuário</th><th class='d-none d-lg-table-cell'>E-mail</th><th class='d-none d-sm-table-cell'>Papel</th>
            <th class='d-none d-md-table-cell'>Projetos</th><th class='d-none d-md-table-cell'>Situação</th><th></th></tr></thead><tbody>`;
        for (const u of users) {
            const me = currentUser && u.id === currentUser.id;
            const name = [u.firstName, u.lastName].filter(Boolean).join(' ');
            html += `<tr><td class='px-3'><div class='d-flex align-items-center gap-2'>
                  <span class='ss-avatar'>${esc(initials(u))}</span>
                  <div><div class='fw-semibold'>${esc(name || u.login)}${me ? " <span class='badge rounded-pill ss-chip ms-1'>você</span>" : ''}</div>
                  <div class='small ss-muted'>${esc(u.login)}<span class='d-sm-none'> · ${u.role === 'ADMIN' ? 'Administrador' : 'Usuário'}</span>${u.enabled ? '' : " <span class='text-danger d-md-none'>· desativada</span>"}</div></div></div></td>
                <td class='small d-none d-lg-table-cell'>${esc(u.email) || "<span class='ss-muted'>—</span>"}</td>
                <td class='d-none d-sm-table-cell'>${u.role === 'ADMIN' ? "<span class='badge rounded-pill text-bg-primary'><i class='bi bi-shield-check me-1'></i>Administrador</span>" : "<span class='badge rounded-pill ss-chip'>Usuário</span>"}</td>
                <td class='d-none d-md-table-cell'>${owned[u.login] || 0}</td>
                <td class='d-none d-md-table-cell'>${u.enabled ? "<span class='small'><span class='ss-status-dot online me-1'></span>Ativa</span>" : "<span class='small text-danger'><i class='bi bi-slash-circle me-1'></i>Desativada</span>"}</td>
                <td class='text-end text-nowrap pe-3'>
                  <button class='btn btn-sm btn-outline-secondary' onclick='openUserModal(${u.id})' title='Editar'><i class='bi bi-pencil'></i></button>
                  ${me ? '' : `<button class='btn btn-sm btn-outline-danger' onclick='deleteUser(${u.id})' title='Excluir'><i class='bi bi-trash'></i></button>`}
                </td></tr>`;
        }
        div.innerHTML = html + '</tbody></table></div>';
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
        toast(id ? 'Usuário atualizado.' : 'Usuário criado.');
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
        toast('Usuário excluído.');
        loadUsers();
    } catch (e) {
        toast(e.message, 'danger');
    }
}

loadCurrentUser('users').then(loadUsers);
