// Pagina publica do certificado da CA: impressoes digitais e comando de instalacao no Windows.
// O comando (uma linha) ja leva o certificado dentro dele, entao funciona mesmo sem a porta HTTP 8080.
(function () {
    const show = (id) => document.getElementById(id).classList.remove('d-none');
    const text = (id, value) => { document.getElementById(id).textContent = value; };

    // Uma linha so: o certificado vai em base64 (DER), sem here-string, para colar e teclar Enter
    function psCommand(pem) {
        const der = pem.replace(/-----(BEGIN|END) CERTIFICATE-----/g, '').replace(/\s+/g, '');
        return "$f = Join-Path $env:TEMP 'sensorsync-ca.crt'; "
            + "[IO.File]::WriteAllBytes($f, [Convert]::FromBase64String('" + der + "')); "
            + 'Import-Certificate -FilePath $f -CertStoreLocation Cert:\\CurrentUser\\Root';
    }

    async function copy(value) {
        if (navigator.clipboard && window.isSecureContext) {
            try {
                await navigator.clipboard.writeText(value);
                return true;
            } catch (e) { /* cai no metodo antigo */ }
        }
        const area = document.createElement('textarea');
        area.value = value;
        area.style.position = 'fixed';
        area.style.opacity = '0';
        document.body.appendChild(area);
        area.select();
        const ok = document.execCommand('copy');
        area.remove();
        return ok;
    }

    async function load() {
        try {
            const info = await (await fetch('ca.json', {cache: 'no-store'})).json();
            if (!info.enabled) {
                show('tlsOff');
                return;
            }
            const pem = await (await fetch('ca.crt', {cache: 'no-store'})).text();
            text('caSubject', info.subject);
            text('caSha1', info.sha1);
            text('caThumb', info.sha1.replace(/:/g, ''));
            text('caSha256', info.sha256);
            text('caNotAfter', new Date(info.notAfter + 'T00:00:00').toLocaleDateString('pt-BR'));
            const command = psCommand(pem);
            text('psCommand', command);
            const button = document.getElementById('copyBtn');
            button.addEventListener('click', async () => {
                const label = button.querySelector('span');
                label.textContent = (await copy(command)) ? 'Copiado!' : 'Selecione e copie manualmente';
                setTimeout(() => { label.textContent = 'Copiar comando'; }, 2500);
            });
            show('tlsOn');
        } catch (e) {
            show('tlsError');
        }
    }

    load();
})();
