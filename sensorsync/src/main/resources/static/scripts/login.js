// Mostra a mensagem de erro quando o Spring Security devolve /login.html?erro
if (new URLSearchParams(window.location.search).has('erro')) {
    document.getElementById('loginError').classList.remove('d-none');
}
