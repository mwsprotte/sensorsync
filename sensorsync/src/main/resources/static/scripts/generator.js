// ***************************************************************************************************
// Tela de criacao de projeto

function loadDataInput() {
    const dataNumber = Math.min(20, Math.max(1, parseInt(document.getElementById('dataNumber').value) || 1));
    // Preserva o que ja foi digitado ao mudar a quantidade
    const previous = [];
    for (let i = 0; i < 20; i++) {
        const d = document.getElementById('cardDesc' + i);
        if (!d) break;
        previous.push({ desc: d.value, card: document.getElementById('useCard' + i).checked, chart: document.getElementById('useChart' + i).checked });
    }
    if (previous.length === dataNumber) return;  // nada mudou: nao recria os campos
    let cards = "";
    for (let index = 0; index < dataNumber; index++) {
        const ref = index + 1;
        const prev = previous[index] || { desc: 'Dado ' + ref, card: true, chart: true };
        cards +=
            "<div class='col-md-12'><p class='text-center fw-semibold'>Dado " + ref + "</p></div>" +
            "<div class='col-md-6'><label class='form-label' for='cardDesc" + index + "'>Descrição</label><input type='text' class='form-control' maxlength='254' id='cardDesc" + index + "' value='" + esc(prev.desc) + "'></div>" +
            "<div class='col-md-6'><br>" +
            "<div class='form-check form-check-inline'><input class='form-check-input' type='checkbox' id='useCard" + index + "' " + (prev.card ? 'checked' : '') + "><label class='form-check-label' for='useCard" + index + "'>Card</label></div>" +
            "<div class='form-check form-check-inline'><input class='form-check-input' type='checkbox' id='useChart" + index + "' " + (prev.chart ? 'checked' : '') + "><label class='form-check-label' for='useChart" + index + "'>Gráfico</label></div>" +
            "</div><div><hr></div>";
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
        alert("Insira um nome para o projeto!");
        return;
    }
    const dataNumber = parseInt(document.getElementById('dataNumber').value);
    const variables = [];
    for (let i = 0; i < dataNumber; i++) {
        variables.push({
            dataDesc: document.getElementById('cardDesc' + i).value,
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
        window.location.href = './code.html?' + params.toString();
    } catch (e) {
        alert('Não foi possível criar o projeto: ' + e.message);
        btn.disabled = false;
    }
}

loadCurrentUser();
