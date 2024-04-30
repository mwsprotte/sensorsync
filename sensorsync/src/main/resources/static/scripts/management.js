// ***************************************************************************************************
// Constantes de uso no código

const HOST = "http://localhost";

// ***************************************************************************************************

function generetaDeleteList() {

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
            document.getElementById('projectList').innerHTML = projectsList;

        } else {
        }
    };

}

// todo: finalzar esse endpoint
function deleteProject(projectID) {
    var resultado = confirm("Deseja excluir o projeto " + projectID + "?");
    if (resultado == true) {

        var request = HOST + ":8080/project/deleteAll/" + projectID;
        const xhr = new XMLHttpRequest();
        xhr.open('DELETE', request);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send();
        alert("Projeto excluido!");
        window.location.href = "./index.html";

    } else {
        alert("O projeto não foi excluído");
    }
}