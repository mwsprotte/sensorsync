// ***************************************************************************************************
// Constantes de uso no código

 const HOST = "http://10.0.0.104";

// ***************************************************************************************************

function verifyAndOpenDashboard() {
    if (localStorage.getItem('loadProject') == "true") {
        document.getElementById('projectTitle').innerHTML = localStorage.getItem('projectName');
        constructDevicesSelector();
        prepareCharts();
    } else {
        console.log('Não foi encontrado projeto na memória!');
    }
}


function constructDevicesSelector() {

    var devicesMainScreen = "<select id='deviceSelected' class=form-select>"
    for (let index = 0; index < Number(localStorage.getItem('sensorNumber')); index++) {
        devicesMainScreen = devicesMainScreen + "<option value='" + index + "'>Dispositivo " + index + "</option>"
    }

    devicesMainScreen = devicesMainScreen + "</select>";
    document.getElementById('deviceSelector').innerHTML = devicesMainScreen;

    (localStorage.getItem('deviceSelected') == null) ? device = 0 : device = localStorage.getItem('deviceSelected');
    document.querySelector('#deviceSelected').value = device;

    (localStorage.getItem('dataLength') == null) ? dataLength = 5 : dataLength = localStorage.getItem('dataLength');
    document.querySelector("#length").value = dataLength;

    if (localStorage.getItem('autoUpdate') == null) {
        document.getElementById('setTimeOff').checked = true;
    } else if (localStorage.getItem('autoUpdate') == 'off') {
        document.getElementById('setTimeOff').checked = false;
    } else {
        document.getElementById('setTimeOff').checked = true;
    }
}

// verifyAndOpenDashboard();

function closeProject() {
    localStorage.removeItem('loadProject');

    localStorage.removeItem('projectName');
    localStorage.removeItem('projectID');
    localStorage.removeItem('sensorNumber');
    localStorage.removeItem('dataNumber');
    localStorage.removeItem('deviceSelected');

    window.location.href = HOST + ":8080/";
}

// Number(document.querySelector('#deviceSelected').value)

function generateCards() {
    // var request = HOST + ":8080/data/cards/" + localStorage.getItem('projectID');
    var request = HOST + ":8080/data/cards/" + localStorage.getItem('projectID') + "/device/" + Number(document.querySelector('#deviceSelected').value);
    const xhr = new XMLHttpRequest();
    xhr.open('GET', request);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {
            dataCards = "<div class='container-fluid text-center'><div class='row gap-5 align-items-center'>";
            dataPackage = JSON.parse(xhr.response);
            for (let i = 0; i < dataPackage.length; i++) {
                var dataToGenerate = dataPackage[i]

                // if (document.getElementById('device' + dataToGenerate.sensorIndex).checked == true) {
                // if (Number(document.querySelector('#deviceSelected').value) == dataToGenerate.sensorIndex) {
                dataCards += "<div class='col-sm-3 shadow-sm border rounded-4'><p class='fw-light'>Dispositivo " + dataToGenerate.sensorIndex + " - " + dataToGenerate.sensorDescription +
                    "</p>" + dataToGenerate.dataDesc +
                    "<p><strong>" + dataToGenerate.data + "</strong></p>" +
                    "<p class='fw-light'>" + dataToGenerate.readTime + "</p></div>";
                // }

            }
            dataCards += "</div></div>";
            document.getElementById('dataCards').innerHTML = dataCards;
        }
    };
}

function prepareCharts() {

    generateCards();

    // construindo o espaço para alocação dos gráficos
    var chartSpace = "<div style='position-relative'>"

    // for (let index = 0; index < localStorage.getItem('sensorNumber'); index++) {
    //     chartSpace = chartSpace + "<canvas id='dataChart" + index + "' height='500'></canvas>"
    // }

    chartSpace = chartSpace + "<canvas id='dataChart" + Number(document.querySelector('#deviceSelected').value) + "' height='500'></canvas>"
    chartSpace = chartSpace + "</div>";

    document.getElementById('dataCharts').innerHTML = chartSpace;

    generateCharts();

    updateCharts();

}


function generateCharts() {


    var device = Number(document.querySelector('#deviceSelected').value);

    const ctx = document.getElementById('dataChart' + device).getContext("2d");


    globalThis.myBarChart = new Chart(ctx, {
        type: 'line',
        data: {
            // labels: [],
            // datasets: [{}],
        },
        options: {
            scales: {
                y: {
                    beginAtZero: true
                }
            },
            colors: {
                forceOverride: true
            }, plugins: {
                title: {
                    display: true,
                    text: 'Dispositivo ' + device,
                }
            },
            responsive: true,
            maintainAspectRatio: false,
            animation: true
        }

    });
}
//async function updateCharts() {
//    var chartRequest = `${HOST}:8080/data/charts/${localStorage.getItem('projectID')}/device/${Number(document.querySelector('#deviceSelected').value)}/length/${Number(document.querySelector("#length").value)}`;
//
//    try {
//        const response = await fetch(chartRequest, {
//            method: 'GET',
//            headers: {
//                'Content-Type': 'application/json'
//            }
//        });
//
//        if (response.ok) {
//            const chartData = await response.json();
//
//            var l = myBarChart.data.labels.length;
//            var response_l = chartData[1].length;
//
//            if (chartData[1][response_l - 1] != myBarChart.data.labels[l - 1]) {
//                for (let index = 0; index < l; index++) {
//                    myBarChart.data.labels.pop();
//                }
//
//                // limpando apenas os dados
//                // https://www.chartjs.org/docs/latest/developers/updates.html
//                var d = myBarChart.data.datasets.length;
//                for (let index = 0; index < d; index++) {
//                    myBarChart.data.datasets.pop();
//                }
//
//                chartLabels = chartData[0];
//
//                for (let index = 0; index < chartData[1].length; index++) {
//                    myBarChart.data.labels.push(chartData[1][index]);
//                }
//
//                for (let index = 2; index < chartData.length; index++) {
//                    const element = chartData[index];
//                    myBarChart.data.datasets.push({
//                        label: chartLabels[index - 2],
//                        data: element,
//                        fill: false,
//                        tension: 0.1
//                    });
//                }
//            }
//            myBarChart.update();
//        }
//    } catch (error) {
//        console.error('Error fetching chart data:', error);
//    }
//}

async function updateCharts() {
    var chartRequest = `${HOST}:8080/data/charts/${localStorage.getItem('projectID')}/device/${Number(document.querySelector('#deviceSelected').value)}/length/${Number(document.querySelector("#length").value)}`;

    try {
        const response = await fetch(chartRequest, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        });

        if (response.ok) {
            const chartData = await response.json();

            // Verificar se a quantidade de labels e dados mudou
            const newLabels = chartData[1];
            const currentLabels = myBarChart.data.labels;

            if (newLabels[newLabels.length - 1] !== currentLabels[currentLabels.length - 1]) {
                // Atualizar os rótulos
                myBarChart.data.labels = newLabels;

                // Atualizar os datasets
                for (let i = 2; i < chartData.length; i++) {
                    const newData = chartData[i];
                    const dataset = myBarChart.data.datasets[i - 2];

                    // Se o dataset já existe, atualize os dados
                    if (dataset) {
                        dataset.data = newData;
                    } else {
                        // Se o dataset não existir, crie um novo
                        myBarChart.data.datasets.push({
                            label: chartData[0][i - 2],
                            data: newData,
                            fill: false,
                            tension: 0.1
                        });
                    }
                }

                // Atualizar o gráfico com os novos dados
                myBarChart.update();
            }
        }
    } catch (error) {
        console.error('Error fetching chart data:', error);
    }
}


function updateViews() {
    if (document.getElementById('setTimeOff').checked == true) {
        localStorage.setItem('autoUpdate', 'on');
    } else {
        localStorage.setItem('autoUpdate', 'off');
    }

    localStorage.setItem('dataLength', document.querySelector("#length").value);
    generateCards();
    prepareCharts();
}

// ***************************************************************************************************
// Atualizando a guia de exportação dos dados

function openExportTab() {
    alert("Será aberta uma nova guia com o relatório dos dados");
    window.open("./export.html", '_blank').focus();
}

setInterval(function () {

    if (document.getElementById('setTimeOff').checked) {
        generateCards();
        updateCharts();
    }
}, 1000); //300000 is 5minutes in ms


function reGenerateCode() {
    alert("Será aberta uma nova guia com o código do sistema embarcado");
    window.open("./code.html", "_blank").focus()
}

// ***************************************************************************************************
// Configurando a vizualização para o dispositivo escolhido

deviceField = document.querySelector("#deviceSelector");

deviceField.addEventListener("change", (event) => {
    localStorage.setItem('deviceSelected', Number(document.querySelector('#deviceSelected').value));
    updateViews();
});


// ***************************************************************************************************
//Alualiza o estado da vizualização com enter
materialField = document.querySelector("#length");
materialField.addEventListener("keypress", (event) => {
        if (event.key === "Enter") {
            updateViews();
            $('#staticBackdrop').modal('toggle');
        }
    }
);