// ***************************************************************************************************
// Tela de criacao de projeto

function loadDataInput() {
    const dataNumber = Math.min(20, Math.max(1, parseInt(document.getElementById('dataNumber').value) || 1));
    // Preserva o que ja foi digitado ao mudar a quantidade
    const previous = [];
    for (let i = 0; i < 20; i++) {
        const d = document.getElementById('cardDesc' + i);
        if (!d) break;
        previous.push({ desc: d.value, unit: document.getElementById('unit' + i).value, card: document.getElementById('useCard' + i).checked, chart: document.getElementById('useChart' + i).checked });
    }
    if (previous.length === dataNumber) return;  // nada mudou: nao recria os campos
    let cards = "";
    for (let index = 0; index < dataNumber; index++) {
        const prev = previous[index] || { desc: 'Dado ' + (index + 1), unit: '', card: true, chart: true };
        // Linha 1: indice + nome. Unidade e chaves ficam ao lado no desktop e quebram para baixo no celular.
        cards += `<div class="ss-var-row d-flex flex-wrap align-items-center gap-2 gap-md-3">
            <div class="d-flex align-items-center gap-2" style="flex: 1 1 220px; min-width: 0;">
              <span class="badge rounded-pill ss-chip" title="Índice no código">${index}</span>
              <input type="text" class="form-control" maxlength="254"
                     id="cardDesc${index}" value="${esc(prev.desc)}" aria-label="Nome da variável ${index + 1}">
            </div>
            <div class="d-flex align-items-center gap-2 gap-sm-3">
              <input type="text" class="form-control" style="width: 84px;" maxlength="32" list="unitList"
                     id="unit${index}" value="${esc(prev.unit)}" placeholder="Unidade" aria-label="Unidade da variável ${index + 1}">
              <div class="form-check form-switch mb-0">
                <input class="form-check-input" type="checkbox" role="switch" id="useCard${index}" ${prev.card ? 'checked' : ''}>
                <label class="form-check-label small" for="useCard${index}">Card</label></div>
              <div class="form-check form-switch mb-0">
                <input class="form-check-input" type="checkbox" role="switch" id="useChart${index}" ${prev.chart ? 'checked' : ''}>
                <label class="form-check-label small" for="useChart${index}">Gráfico</label></div>
            </div>
        </div>`;
    }
    document.getElementById('cardsSet').innerHTML = cards;
}

document.querySelector("#dataNumber").addEventListener("change", loadDataInput);

function selected(name) {
    return document.querySelector(`input[name='${name}']:checked`).value;
}

async function saveProject() {
    const name = document.getElementById('projetctName').value.trim();
    if (!name) {
        document.getElementById('projetctName').classList.add('is-invalid');
        document.getElementById('projetctName').focus();
        toast('Informe um nome para o projeto.', 'warning');
        return;
    }
    const dataNumber = parseInt(document.getElementById('dataNumber').value);
    const variables = [];
    for (let i = 0; i < dataNumber; i++) {
        variables.push({
            dataDesc: document.getElementById('cardDesc' + i).value,
            unit: document.getElementById('unit' + i).value,
            useCard: document.getElementById('useCard' + i).checked,
            useChart: document.getElementById('useChart' + i).checked
        });
    }
    const btn = document.getElementById('createBtn');
    btn.disabled = true;
    try {
        const created = await api('/project', {
            method: 'POST',
            body: { name: name, devices: parseInt(document.getElementById('sensorNumber').value), variables: variables }
        });
        // Os tokens so existem nesta resposta: guarda na aba para a tela de codigo
        for (const d of created.devices) {
            rememberToken(d.device.id, d.token);
        }
        const params = new URLSearchParams({
            id: created.project.id,
            board: selected('deviceSelector'),
            protocol: selected('protocolSelector'),
            novo: '1'
        });
        window.location.href = './dashboard.html?' + params.toString() + '#codigo';
    } catch (e) {
        toast('Não foi possível criar o projeto: ' + e.message, 'danger');
        btn.disabled = false;
    }
}

document.getElementById('projetctName').addEventListener('input', e => e.target.classList.remove('is-invalid'));
document.getElementById('dataNumber').addEventListener('input', loadDataInput);
loadDataInput();
loadCurrentUser('create');
