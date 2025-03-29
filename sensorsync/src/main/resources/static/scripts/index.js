// ***************************************************************************************************
// Constantes de uso no código

// const HOST = "http://10.0.0.103";
// const HOST = "http://10.0.0.104";
// const HOST = "http://192.168.100.100";
const HOST = "http://10.0.0.103";

// ***************************************************************************************************

function generateProjectsTable() {
    var request = HOST + ":8080/project";

    const xhr = new XMLHttpRequest();
    xhr.open('GET', request);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {
            var allProjects = JSON.parse(xhr.response);
            let projectsList = "<div class='list-group'>";
            for (let index = 0; index < allProjects.length; index++) {
                const element = allProjects[index];
                projectsList = projectsList + "<a href='#' class='list-group-item list-group-item-action' onclick='return loadProjectData(" + element.id + ");'>" + element.id + " - " + element.name + "</a>";
            }
            projectsList = projectsList + "</div>";
            document.getElementById('projectList').innerHTML = projectsList;

        } else {
        }
    };

}

function loadProjectData(projectID) {
    var request = HOST + ":8080/project/" + projectID;
    const xhr = new XMLHttpRequest();
    xhr.open('GET', request);
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send();
    xhr.onreadystatechange = function () {
        if (xhr.readyState === 4 && xhr.status === 200) {


            var project = JSON.parse(xhr.response);

            console.log(project);

            localStorage.setItem('loadProject', "true");
            localStorage.setItem('projectName', project.name);
            localStorage.setItem('projectID', project.id);
            localStorage.setItem('dataNumber', project.dataNumber);
            localStorage.setItem('sensorNumber', project.sensorNumber);

            window.alert("Você serrá direcionado para o projeto " + projectID + " (" + project.name + ")");
            window.location.href = "./dashboard.html"
        }
    };
}

function verifyAndOpen() {

    if (localStorage.getItem('loadProject') == "true") {
        // window.alert("Você serrá direcionado para o projeto " + localStorage.getItem('projectID') + " (" + localStorage.getItem('projectName') + ")");
        window.location.href = "./dashboard.html"
    }


}

// ***************************************************************************************************

function generetaDeleteList() {


    if (document.querySelector("#login").value == "" || document.querySelector("#password").value == "") {

        alert("Insira usuário e senha!")
        window.location.reload();

    } else {

        const xhr = new XMLHttpRequest();

        var request = HOST + ":8080/user/check/login/" + document.querySelector("#login").value + "/password/" + document.querySelector("#password").value;
        // var request = HOST + ":8080/user/check/login/tese/password/senhawe";

        xhr.open('GET', request);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send();
        xhr.onreadystatechange = function () {
            if (xhr.readyState === 4 && xhr.status === 200) {
                var check = JSON.parse(xhr.response);

                if (check === true) {

                    var request = HOST + ":8080/project";

                    const xhr = new XMLHttpRequest();
                    xhr.open('GET', request);
                    xhr.setRequestHeader('Content-Type', 'application/json');
                    xhr.send();
                    xhr.onreadystatechange = function () {
                        if (xhr.readyState === 4 && xhr.status === 200) {
                            var allProjects = JSON.parse(xhr.response);
                            let projectsList = "<div class='list-group'>";
                            for (let index = 0; index < allProjects.length; index++) {
                                const element = allProjects[index];
                                projectsList = projectsList + "<a href='#' class='list-group-item list-group-item-action' onclick='return deleteProject(" + element.id + ");'> Projeto: " + element.id + " - " + element.name + "</a>";
                            }
                            projectsList = projectsList + "</div>";
                            document.getElementById('projectListToDelete').innerHTML = projectsList;

                        }
                    }
                } else {
                    alert("Senha incorreta!")
                    window.location.reload();
                }
            }
        }
    }


}

function deleteProject(projectID) {

    var resultado = confirm("Deseja excluir o projeto " + projectID + "?");

    if (resultado == true) {

        var request = HOST + ":8080/project/deleteAll/" + projectID +"/user/" + document.querySelector("#login").value + "/password/" + document.querySelector("#password").value;
        const xhr = new XMLHttpRequest();
        xhr.open('DELETE', request);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send();
        alert("Projeto excluído!");
        window.location.reload();

    } else {
        alert("O projeto não foi excluído");
    }
}