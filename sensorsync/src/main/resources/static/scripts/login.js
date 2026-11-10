// Mostra a mensagem de erro quando o Spring Security devolve /login.html?erro
if (new URLSearchParams(window.location.search).has('erro')) {
    document.getElementById('loginError').classList.remove('d-none');
}

// Com TLS ligado, mostra o link para instalar o certificado do servidor (some o aviso "Não seguro")
fetch('ca.json', {cache: 'no-store'})
    .then((r) => (r.ok ? r.json() : null))
    .then((info) => {
        if (info && info.enabled) {
            document.getElementById('caLink').classList.remove('d-none');
        }
    })
    .catch(() => {});
