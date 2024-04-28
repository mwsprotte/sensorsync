// ***************************************************************************************************
// Constantes de uso no código

const HOST = "http://192.168.100.166";

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
            window.location.href ="./dashboard.html"
        }
    };
}

function verifyAndOpen() {

    if (localStorage.getItem('loadProject') == "true") {
        // window.alert("Você serrá direcionado para o projeto " + localStorage.getItem('projectID') + " (" + localStorage.getItem('projectName') + ")");
        window.location.href = "./dashboard.html"
    }

}