const HOST = "http://localhost";


function generateCode() {

    (localStorage.getItem('device') == null) ? device = 0 : device = localStorage.getItem('device');
    (localStorage.getItem('protocol') == null) ? protocol = 0 : protocol = localStorage.getItem('protocol');
    (localStorage.getItem('projectGenerated') == null) ? project = 0 : project = localStorage.getItem('projectGenerated');

    //var request = HOST + ":8080/data/report/" + localStorage.getItem('projectID') + "/device/" + device;
    var request = HOST + ":8080/project/generateCode/" + project + "/device/" + device + "/protocol/" + protocol;
    const xhr = new XMLHttpRequest();
    xhr.open('GET', request);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {

            code = JSON.parse(xhr.response);

            var codeESP8266 = code[0];

            document.getElementById('codeGenerated').innerHTML = codeESP8266;


        }
    }
}