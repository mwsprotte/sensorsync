// ***************************************************************************************************
// Tela inicial

const SPINNER = "<div class='d-flex justify-content-center'><div class='spinner-border' role='status'></div></div>";

async function loadProjects(targetId, manage) {
    const target = document.getElementById(targetId);
    target.innerHTML = SPINNER;
    try {
        const projects = await api('/project');
        if (projects.length === 0) {
            target.innerHTML = "<p class='text-body-secondary'>Nenhum projeto ainda. Use <a href='generator.html'>Criar</a>.</p>";
            return;
        }
        const showOwner = currentUser && currentUser.role === 'ADMIN';
        let html = "<div class='list-group'>";
        for (const p of projects) {
            const owner = showOwner ? ` <span class='badge text-bg-light border'>${esc(p.ownerLogin)}</span>` : '';
            if (!manage) {
                html += `<a href='dashboard.html?id=${p.id}' class='list-group-item list-group-item-action'>${p.id} - ${esc(p.name)}${owner}</a>`;
            } else {
                html += `<div class='list-group-item d-flex justify-content-between align-items-center gap-2'>
                    <span>${p.id} - ${esc(p.name)}${owner}</span>
                    <span class='text-nowrap'>
                      <button class='btn btn-sm btn-outline-warning' onclick='clearData(${p.id})'>Apagar dados</button>
                      <button class='btn btn-sm btn-outline-danger' onclick='deleteProject(${p.id})'>Excluir projeto</button>
                    </span></div>`;
            }
        }
        target.innerHTML = html + "</div>";
    } catch (e) {
        target.innerHTML = `<div class='alert alert-danger'>${esc(e.message)}</div>`;
    }
}

async function deleteProject(id) {
    if (!confirm(`Excluir o projeto ${id}? Todos os dados, variáveis e dispositivos serão apagados.`)) return;
    try {
        await api('/project/' + id, { method: 'DELETE' });
        loadProjects('manageList', true);
    } catch (e) {
        alert(e.message);
    }
}

async function clearData(id) {
    if (!confirm(`Apagar todas as leituras gravadas do projeto ${id}? O projeto e os dispositivos continuam.`)) return;
    try {
        await api('/project/' + id + '/data', { method: 'DELETE' });
        alert('Dados apagados.');
    } catch (e) {
        alert(e.message);
    }
}

loadCurrentUser().then(u => {
    if (u.role === 'ADMIN') document.getElementById('usersCard').classList.remove('d-none');
});
