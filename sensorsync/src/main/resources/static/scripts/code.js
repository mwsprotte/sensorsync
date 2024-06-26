// const HOST = "http://10.0.0.103";
//  const HOST = "http://10.0.0.104";
//const HOST = "http://localhost";
const HOST = "http://localhost";


function generateCode() {

    (localStorage.getItem('device') == null) ? device = 0 : device = localStorage.getItem('device');
    (localStorage.getItem('protocol') == null) ? protocol = 0 : protocol = localStorage.getItem('protocol');
    (localStorage.getItem('projectGenerated') == null) ? project = localStorage.getItem('projectID') : project = localStorage.getItem('projectGenerated');

    //var request = HOST + ":8080/data/report/" + localStorage.getItem('projectID') + "/device/" + device;
    var request = HOST + ":8080/project/generateCode/" + project + "/device/" + device + "/protocol/" + protocol;
    const xhr = new XMLHttpRequest();
    xhr.open('GET', request);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {

            code = JSON.parse(xhr.response);

            if (code.length > 1) {
                document.getElementById('codeGenerated').innerHTML = "<h3>Código Arduino</h3><br>";
                document.getElementById('codeGenerated').innerHTML += code[0];
                document.getElementById('codeGenerated').innerHTML += "<hr><br><h3>Código ESP</h3><br>"
                document.getElementById('codeGenerated').innerHTML += code[1];
            } else {
                document.getElementById('codeGenerated').innerHTML = code[0];
            }
        }
    }
}