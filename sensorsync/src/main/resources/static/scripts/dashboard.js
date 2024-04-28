// ***************************************************************************************************
// Constantes de uso no código

const HOST = "http://192.168.100.166";

// ***************************************************************************************************

function verifyAndOpenDashboard() {
    if (localStorage.getItem('loadProject') == "true") {
        document.getElementById('projectTitle').innerHTML = localStorage.getItem('projectName');
        constructDevicesSelector();
        prepareCharts();
    } else {
        // window.alert("Não há um projeto selecionado!");
        // window.location.href = "./index.html";
        console.log('Não foi encontrado projeto na memória!');
    }
}


function constructDevicesSelector() {

    // SELEÇÃO MOVIDA PARA OUTRA TELA
    // var devices = ""
    // for (let index = 0; index < Number(localStorage.getItem('sensorNumber')); index++) {
    //     devices = devices + "<div class='form-check'><input class='form-check-input' type='checkbox' value='' id='device" + index + "' checked><label class='form-check-label' for='device" + index + "'>Dispositivo " + index + "</label></div>"
    // }
    // document.getElementById('devicesSelector').innerHTML = devices;


    var devicesMainScreen = "<select id='deviceSelected' class=form-select>"
    for (let index = 0; index < Number(localStorage.getItem('sensorNumber')); index++) {
        devicesMainScreen = devicesMainScreen + "<option value='" + index + "'>Dispositivo " + index + "</option>"
    }

    var devicesMainScreen = devicesMainScreen + "</select>";
    document.getElementById('deviceSelector').innerHTML = devicesMainScreen;


}

// verifyAndOpenDashboard();

function closeProject() {
    localStorage.removeItem('loadProject');

    localStorage.removeItem('projectName');
    localStorage.removeItem('projectID');
    localStorage.removeItem('sensorNumber');
    localStorage.removeItem('dataNumber');

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


function updateCharts() {
    var chartRequest = HOST + ":8080/data/charts/" + localStorage.getItem('projectID') + "/device/" + Number(document.querySelector('#deviceSelected').value) + "/length/" + Number(document.querySelector("#length").value) + "";

    const xhr = new XMLHttpRequest();
    xhr.open('GET', chartRequest);
    xhr.setRequestHeader('Content-Type', 'application/json');
    // xhr.setRequestHeader('Authorization', 'Basic ' + btoa(localStorage.getItem('user') + ':' + localStorage.getItem('password')));
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {
            var chartData = JSON.parse(xhr.response);

            var l = myBarChart.data.labels.length;
            var response_l = chartData[1].length;

            if (chartData[1][response_l - 1] != myBarChart.data.labels[l - 1]) {
                for (let index = 0; index < l; index++) {
                    myBarChart.data.labels.pop();
                }

                // limpando apenas os dados
                // https://www.chartjs.org/docs/latest/developers/updates.html
                var d = myBarChart.data.datasets.length;
                for (let index = 0; index < d; index++) {
                    myBarChart.data.datasets.pop();
                }


                chartLabels = chartData[0];

                for (let index = 0; index < chartData[1].length; index++) {
                    myBarChart.data.labels.push(chartData[1][index]);

                }

                for (let index = 2; index < chartData.length; index++) {
                    const element = chartData[index];
                    myBarChart.data.datasets.push({
                        label: chartLabels[index - 2],
                        data: element,
                        fill: false,
                        tension: 0.1
                    })
                }
            }
        };

        myBarChart.update();
        // myBarChart._doResize();


    }


}

function updateViews() {
    generateCards();
    prepareCharts();
}


// ***************************************************************************************************
// Atualizando os dados a cada segundo

function getTimeInterval() {

    var setTimeInterval = 1000;

    if (document.getElementById('setTime5').checked) {
        setTimeInterval = 5000;
    } else if (document.getElementById('setTime10').checked) {
        setTimeInterval = 10000;
    }

    return setTimeInterval;
}

setInterval(function () {

    if (!document.getElementById('setTimeOff').checked) {
        generateCards();
        updateCharts();
    }
}, getTimeInterval()); //300000 is 5minutes in ms


deviceField = document.querySelector("#deviceSelector");

deviceField.addEventListener("change", (event) => {

    // document.getElementById('dataCharts').innerHTML = '';
    updateViews();
});


// ***************************************************************************************************