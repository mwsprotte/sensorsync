// ***************************************************************************************************
// Constantes de uso no código

const HOST = "http://192.168.0.100";

// ***************************************************************************************************

function generateTable() {


    (localStorage.getItem('deviceSelected') == null) ? device = 0 : device = localStorage.getItem('deviceSelected');

    var title = "Relatório de dados - Dispositivo " + device;

    document.getElementById('reportTitle').innerHTML = title;


    var request = HOST + ":8080/data/report/" + localStorage.getItem('projectID') + "/device/" + device;
    const xhr = new XMLHttpRequest();
    xhr.open('GET', request);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {
            var table = "<table  class=\"table table-bordered\">\n";
            dataTable = JSON.parse(xhr.response);
            for (let i = 0; i < dataTable.length; i++) {
                var dataToGenerate = dataTable[i];
                table += "  <tr>\n";
                for (j = 0; j < dataToGenerate.length; j++) {
                    if (i == 0) {
                        table += "    <th class=\"text-center\">\n" + dataToGenerate[j] + "</th>\n";
                    } else {
                        table += "    <td class=\"text-center\">\n" + dataToGenerate[j].replace(".", ",") + "</td>\n";
                    }
                }
                table += "  </tr>\n";
            }
            table += "</table>";
            document.getElementById('tableDataDiv').innerHTML = table;


        }
    };
}