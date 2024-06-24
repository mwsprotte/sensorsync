// ***************************************************************************************************
// Constantes de uso no código

// const HOST = "http://10.0.0.103";
// const HOST = "http://10.0.0.104";
// const HOST = "http://192.168.100.100";
const HOST = "http://localhost";


// ***************************************************************************************************
// Função para gerar a lista de cadastro de dados

function loadDataInput() {
    var dataNumber = parseInt(document.getElementById('dataNumber').value);
    var cards = "";
    for (let index = 0; index < dataNumber; index++) {
        var ref = index + 1;
        cards = cards +
            "<div class='col-md-12'><p class='text-center fw-semibold'>Dado " + ref + "</p></div>" +
            "<div class='col-md-6'><label class='form-label'>Descrição</label><input type='text' class='form-control' id='cardDesc" + index + "'  value='Unidade " + ref + "'></div>" +
            "<div class='col-md-6'><br>" +
            "<div class='form-check form-check-inline'><input class='form-check-input' type='checkbox' id='useCard" + index + "'value='' checked><label class='form-check-label' for='inlineCheckbox1'>Card</label></div>" +
            "<div class='form-check form-check-inline'><input class='form-check-input' type='checkbox' id='useChart" + index + "'value=''><label class='form-check-label' for='inlineCheckbox2'>Gráfico</label></div>" +
            "</div>" +
            "<div class='col-md-6'><br></div>" +
            "</div><div><hr></div>";

    }

    document.getElementById('cardsSet').innerHTML = cards
}

document.querySelector("#dataNumber").addEventListener("change", loadDataInput); //escutanto o campo de imputs de dados para disparar a função

function loadDataDesc(index) {
    try {
        var auxRead = document.getElementById("cardDesc" + index).value;
        if (auxRead == null) {
            return "";
        } else {
            return document.getElementById("cardDesc" + index).value;
        }
    } catch (error) {
        return "";
    }

}

function loadDataUseCard(index) {
    try {
        var auxRead = document.getElementById("useCard" + index).checked;
        if (auxRead == false) {
            return 0;
        } else {
            return 1;
        }
    } catch (error) {
        return 0;
    }

}

function loadDataUseChart(index) {
    try {
        var auxRead = document.getElementById("useChart" + index).checked;
        if (auxRead == false) {
            return 0;
        } else {
            return 1;
        }
    } catch (error) {
        return 0;
    }
}

// ***************************************************************************************************
// Método para criar a requisição de salvar projetos


function saveProject() {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", HOST + ":8080/project");
    xhr.setRequestHeader("Content-Type", "application/json; charset=UTF-8");

    if (document.getElementById('projetctName').value != "") {

        const body = JSON.stringify({
            name: document.getElementById('projetctName').value,
            sensorNumber: parseInt(document.getElementById('sensorNumber').value),
            dataNumber: parseInt(document.getElementById('dataNumber').value),
        });

        xhr.onload = () => {
            if (xhr.readyState == 4 && xhr.status == 200) {

                try {
                    var response = JSON.parse(xhr.responseText)
                    console.log(`Projeto: ` + response.id);
                    saveMetadata(response.id);
                } catch (e) {
                    alert("Esse projeto já existe!")
                    window.location.reload();
                }

            } else {
                console.log(`Error: ${xhr.status}`);
            }
        };
        xhr.send(body);
    } else {
        window.alert("Insira um nome para o projeto!")
    }
}

function saveMetadata(id) {

    const xhr = new XMLHttpRequest();
    xhr.open("POST", HOST + ":8080/metadata/list");
    xhr.setRequestHeader("Content-Type", "application/json; charset=UTF-8");

    var metadataAll = '[';
    for (let index = 0; index < parseInt(document.getElementById('dataNumber').value); index++) {
        metadataAll = metadataAll + '{"projectID": ' + id + ',"dataIndex": ' + index + ',"dataDesc": "' + document.getElementById("cardDesc" + index).value + '","useCard": ' + loadDataUseCard(index) + ',"useChart": ' + loadDataUseChart(index) + '}';
        if (index < parseInt(document.getElementById('dataNumber').value - 1)) {
            metadataAll = metadataAll + ",";
        }
    }
    metadataAll = metadataAll + "]";

    // console.log(metadataAll);
    const body = metadataAll;

    xhr.onload = () => {
        if (xhr.readyState == 4 && xhr.status == 200) {
            window.alert("Criado o projeto " + document.getElementById('projetctName').value);
            alert("Será aberta uma nova guia com o código do sistema embarcado");
            saveEmbeddedCodeInfos(id);
            window.open("./code.html", '_blank').focus();
            window.location.href = "./index.html";
        } else {
            window.alert(`Não foi possível criar o projeto! Erro ${xhr.status}`);

            // console.log(`Error: ${xhr.status}`);
        }
    };
    xhr.send(body);

}

function saveEmbeddedCodeInfos(projectID) {
    var device = 0;
    if (document.querySelector("#ESP32Device").checked == true) {
        device = 1;
    } else if (document.querySelector("#ArduinoDevice").checked == true) {
        device = 2;
    }
    localStorage.setItem("device", device);

    var protocol = 0;
    if (document.querySelector("#MQTTSelect").checked == true) {
        protocol = 1;
    }
    localStorage.setItem("protocol", protocol);

    localStorage.setItem("projectGenerated", projectID);
}

// ***************************************************************************************************