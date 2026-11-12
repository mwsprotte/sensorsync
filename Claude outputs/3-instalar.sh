#!/bin/bash
# SensorSync - passo 3: instala ou atualiza a versao nova, mantendo o banco e as placas antigas funcionando.
#
#   sudo bash deploy/lab/3-instalar.sh --jar /home/ubuntu/sensorsync.jar            (primeira vez / jar novo)
#   sudo bash deploy/lab/3-instalar.sh --modo final                                 (fim da transicao)
#   sudo bash deploy/lab/3-instalar.sh                                              (so reaplica a configuracao)
#
# Opcoes:
#   --jar ARQUIVO   .jar novo (gerado no PC com: cd sensorsync; .\mvnw.cmd package -DskipTests)
#   --modo transicao | final
#        transicao (padrao): placas antigas continuam funcionando sem token e sem TLS
#                            (HTTP 8080 so para envio, MQTT 1883 aberto na rede, formato antigo aceito);
#                            navegador e placas novas usam HTTPS 8443 / MQTT TLS 8883.
#        final:              so placas com token e TLS (8080 redireciona, 1883 so dentro do servidor).
#   --ip IP         endereco fixo do servidor (vai no certificado e no codigo das placas). Padrao: o 1o IP.
#   --sem-tls       sem HTTPS/MQTT TLS (tudo como antes: 8080 e 1883)
#   --chrony        instala/configura o chrony (servidor de hora para PC e placas; experimentos)
#   --sim           responde "sim" a todas as perguntas
#   --sem-systemd   (testes) nao usa systemctl; so prepara arquivos
#
# O que ele faz (cada passo explica na tela; e seguro rodar de novo):
#   1. Confere Java 21, MySQL, Mosquitto, OpenSSL e se existe backup recente (passo 2).
#   2. Cria o usuario de sistema "sensorsync" e as pastas /opt/sensorsync, /var/lib/sensorsync, /etc/sensorsync.
#   3. Copia o .jar novo e o script de certificados.
#   4. Cria no MySQL o usuario "sensorsync" (a versao antiga usava o root) com acesso so ao banco do sistema.
#   5. Grava /etc/sensorsync/sensorsync.env (configuracao; nao sobrescreve o que voce ja editou).
#   6. Ajusta o Mosquitto (comenta os listeners antigos e cria conf.d/sensorsync.conf).
#   7. Instala os servicos systemd (certificados -> Mosquitto -> API), que sobem sozinhos no boot.
#   8. Para a versao antiga e desativa o inicio automatico dela (com confirmacao).
#   9. Sobe tudo e testa.
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$DIR/../.." && pwd)
# shellcheck source=comum.sh
source "$DIR/comum.sh"

JAR=""; MODO=""; IP=""; TLS=true; CHRONY=false; SYSTEMD=true
while [ $# -gt 0 ]; do
    case "$1" in
        --jar) JAR=$2; shift 2;;
        --modo) MODO=$2; shift 2;;
        --ip) IP=$2; shift 2;;
        --sem-tls) TLS=false; shift;;
        --chrony) CHRONY=true; shift;;
        --sim) ASSUME_YES=true; shift;;
        --sem-systemd) SYSTEMD=false; shift;;
        -h|--help) sed -n '2,30p' "$0"; exit 0;;
        *) erro "opcao desconhecida: $1 (veja --help)";;
    esac
done
precisa_root
[ -z "$MODO" ] || [ "$MODO" = transicao ] || [ "$MODO" = final ] || erro "--modo deve ser transicao ou final"

sysctl_() { if $SYSTEMD; then systemctl "$@"; else info "(sem systemd) systemctl $*"; fi; }

# Le/grava chaves no sensorsync.env
env_get() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d= -f2- || true; }
env_set() {
    if grep -qE "^$1=" "$ENV_FILE"; then
        sed -i "s|^$1=.*|$1=$2|" "$ENV_FILE"
    elif grep -qE "^#\s*$1=" "$ENV_FILE"; then
        sed -i "0,/^#\s*$1=.*/s||$1=$2|" "$ENV_FILE"
    else
        echo "$1=$2" >> "$ENV_FILE"
    fi
}

# =============================================================================================
titulo "1. Verificacoes"
JAVA_MAJOR=$(java -version 2>&1 | grep -v JAVA_TOOL | head -1 | sed -E 's/.*version "([0-9]+).*/\1/' || echo 0)
[ "${JAVA_MAJOR:-0}" -ge 21 ] 2>/dev/null || erro "precisa de Java 21 ou mais novo (encontrado: '${JAVA_MAJOR:-nenhum}'). Veja o guia, secao Java."
ok "Java $JAVA_MAJOR"
command -v mysql >/dev/null || erro "MySQL nao encontrado"
command -v mosquitto >/dev/null || erro "Mosquitto nao encontrado (sudo apt install mosquitto)"
command -v openssl >/dev/null || erro "openssl nao encontrado"
ok "MySQL, Mosquitto e OpenSSL presentes"

if [ -n "$JAR" ]; then
    [ -f "$JAR" ] || erro "jar nao encontrado: $JAR"
    python3 -c "import zipfile,sys; z=zipfile.ZipFile(sys.argv[1]); assert 'BOOT-INF/classes/application-tls.yml' in z.namelist()" "$JAR" \
        2>/dev/null || erro "$JAR nao parece ser a versao nova do SensorSync (falta application-tls.yml)"
    ok "jar novo: $JAR ($(du -h "$JAR" | cut -f1))"
elif [ ! -f "$NEW_JAR" ]; then
    erro "primeira instalacao: informe o .jar novo com --jar"
fi

ULTIMO=$(cat "$BACKUP_ROOT/ULTIMO" 2>/dev/null || true)
if [ -n "$ULTIMO" ] && [ -f "$ULTIMO/banco.sql.gz" ] && [ -n "$(find "$ULTIMO" -maxdepth 0 -mmin -1440)" ]; then
    ok "backup de menos de 24 h: $ULTIMO"
else
    aviso "nao ha backup do banco das ultimas 24 h (passo 2: sudo bash deploy/lab/2-backup.sh)"
    confirma "Continuar SEM backup recente?" || erro "rode o backup antes"
fi

[ -z "$IP" ] && IP=$(env_get SENSORSYNC_PUBLIC_HOST)
[ -z "$IP" ] && IP=$(hostname -I | awk '{print $1}')
ok "endereco do servidor: $IP (confira se e o IP fixo da rede do laboratorio)"

[ -z "$MODO" ] && MODO=$( [ "$(env_get SENSORSYNC_ALLOW_LEGACY_INGEST)" = false ] && echo final || echo transicao )
info "modo: $MODO   TLS: $TLS"

# =============================================================================================
titulo "2. Usuario e pastas"
id sensorsync >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin sensorsync
getent group sensorsync-tls >/dev/null || groupadd --system sensorsync-tls
mkdir -p "$APP_DIR" "$DATA_DIR/metrics" "$ENV_DIR"
chown -R sensorsync:sensorsync "$DATA_DIR"
chmod 755 "$APP_DIR" "$ENV_DIR"   # $ENV_DIR legivel (o Mosquitto le tls/); o .env com senhas e 640
ok "usuario sensorsync; $APP_DIR (programa), $DATA_DIR (dados), $ENV_DIR (configuracao)"

# =============================================================================================
titulo "3. Programa"
if [ -n "$JAR" ]; then
    if [ -f "$NEW_JAR" ] && ! cmp -s "$JAR" "$NEW_JAR"; then
        cp -a "$NEW_JAR" "$APP_DIR/sensorsync.jar.anterior"
        info "versao instalada antes guardada em $APP_DIR/sensorsync.jar.anterior"
    fi
    install -m 644 "$JAR" "$NEW_JAR"
    ok "$NEW_JAR"
fi
install -m 755 "$REPO/deploy/tls/sensorsync-tls.sh" "$APP_DIR/sensorsync-tls.sh"
sed -i 's/\r$//' "$APP_DIR/sensorsync-tls.sh"
ok "$APP_DIR/sensorsync-tls.sh (certificados)"

# =============================================================================================
titulo "4. MySQL"
mysql_admin_prepara
mysql_admin -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\`"
DB_PW=$(env_get SENSORSYNC_DB_PASSWORD)
if [ -z "$DB_PW" ]; then
    DB_PW=$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)
    NOVA_SENHA_DB=true
else
    NOVA_SENHA_DB=false
fi
mysql_admin -e "CREATE USER IF NOT EXISTS 'sensorsync'@'localhost' IDENTIFIED BY '$DB_PW';
                ALTER USER 'sensorsync'@'localhost' IDENTIFIED BY '$DB_PW';
                GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO 'sensorsync'@'localhost';
                FLUSH PRIVILEGES;"
mysql -u sensorsync -p"$DB_PW" -e "SELECT 1" "$DB_NAME" >/dev/null 2>&1 || erro "o usuario sensorsync nao conseguiu entrar no banco"
ok "usuario MySQL 'sensorsync' com acesso so a $DB_NAME$($NOVA_SENHA_DB && echo ' (senha nova, gravada no sensorsync.env)')"
info "o root do MySQL nao e mais usado pelo SensorSync (a senha dele continua a mesma)"

# =============================================================================================
titulo "5. Configuracao ($ENV_FILE)"
if [ ! -f "$ENV_FILE" ]; then
    cp "$REPO/deploy/sensorsync.env.example" "$ENV_FILE"
    info "criado a partir de deploy/sensorsync.env.example"
fi
# Arquivos copiados do Windows vem com CRLF; o \r no fim estragaria os valores (ex.: "ec\r")
sed -i 's/\r$//' "$ENV_FILE"
chown root:sensorsync "$ENV_FILE"; chmod 640 "$ENV_FILE"
env_set SENSORSYNC_DB_URL "jdbc:mysql://localhost:3306/$DB_NAME?serverTimezone=America/Sao_Paulo"
env_set SENSORSYNC_DB_USER sensorsync
env_set SENSORSYNC_DB_PASSWORD "$DB_PW"
env_set SENSORSYNC_PUBLIC_HOST "$IP"
env_set SENSORSYNC_TLS_DIR "$TLS_DIR"
env_set SENSORSYNC_METRICS_FILE "$DATA_DIR/metrics/ingest-metrics.csv"
grep -qE '^SENSORSYNC_METRICS=' "$ENV_FILE" || env_set SENSORSYNC_METRICS false
if $TLS; then
    env_set SPRING_PROFILES_ACTIVE tls
    grep -qE '^SENSORSYNC_TLS_KEY=' "$ENV_FILE" || env_set SENSORSYNC_TLS_KEY ec
else
    sed -i 's/^SPRING_PROFILES_ACTIVE=/#SPRING_PROFILES_ACTIVE=/' "$ENV_FILE"
fi
if [ "$MODO" = transicao ]; then
    env_set SENSORSYNC_ALLOW_LEGACY_INGEST true
    env_set SENSORSYNC_PLAIN_HTTP dispositivos
    env_set SENSORSYNC_MQTT_PUBLIC_PORT "$($TLS && echo 8883 || echo 1883)"
else
    env_set SENSORSYNC_ALLOW_LEGACY_INGEST false
    env_set SENSORSYNC_PLAIN_HTTP redirect
    env_set SENSORSYNC_MQTT_PUBLIC_PORT "$($TLS && echo 8883 || echo 1883)"
fi
if [ -z "$(env_get SENSORSYNC_ADMIN_PASSWORD)" ]; then
    env_set SENSORSYNC_ADMIN_PASSWORD "$(openssl rand -base64 12 | tr -dc 'A-Za-z0-9' | head -c 12)"
fi
ok "configuracao gravada (modo $MODO)"
info "SENSORSYNC_ADMIN_PASSWORD so e usada se o banco nao tiver nenhum usuario (instalacao nova)."
info "Usuarios da versao antiga continuam com o mesmo login e senha e passam a ser administradores."

# =============================================================================================
titulo "6. Mosquitto"
MCONF=/etc/mosquitto/conf.d/sensorsync.conf
mkdir -p /etc/mosquitto/conf.d
# Listeners definidos em outros arquivos conflitariam com os nossos: comenta (o backup tem o original)
for f in /etc/mosquitto/mosquitto.conf /etc/mosquitto/conf.d/*.conf; do
    [ -f "$f" ] && [ "$f" != "$MCONF" ] || continue
    if grep -qE '^\s*(listener|port|allow_anonymous|per_listener_settings|cafile|certfile|keyfile)\b' "$f"; then
        sed -i -E 's/^(\s*(listener|port|allow_anonymous|per_listener_settings|cafile|certfile|keyfile)\b.*)$/#[sensorsync] \1/' "$f"
        info "comentadas as linhas de porta/listener em $f (marcadas com #[sensorsync])"
    fi
done
grep -qE '^\s*include_dir\s+/etc/mosquitto/conf.d' /etc/mosquitto/mosquitto.conf \
    || echo "include_dir /etc/mosquitto/conf.d" >> /etc/mosquitto/mosquitto.conf
BIND=$( [ "$MODO" = transicao ] || ! $TLS && echo "" || echo " 127.0.0.1" )
{
    echo "# Gerado por deploy/lab/3-instalar.sh (modo $MODO). Para mudar, rode o script de novo."
    echo "per_listener_settings false"
    echo "# A autenticacao das placas e feita pela API (token no payload)."
    echo "allow_anonymous true"
    echo
    if [ -n "$BIND" ]; then
        echo "# 1883 sem TLS: so a API, dentro do servidor"
    else
        echo "# 1883 sem TLS aberta na rede: placas antigas (transicao) ou servidor sem TLS"
    fi
    echo "listener 1883$BIND"
    if $TLS; then
        echo
        echo "# 8883 com TLS: placas novas"
        echo "listener 8883"
        echo "cafile $TLS_DIR/ca.crt"
        echo "certfile $TLS_DIR/server.crt"
        echo "keyfile $TLS_DIR/server.key"
    fi
} > "$MCONF"
ok "$MCONF (1883$( [ -n "$BIND" ] && echo ' so local' || echo ' na rede')$($TLS && echo ', 8883 com TLS'))"

# =============================================================================================
titulo "7. Servicos (systemd)"
install -m 644 "$REPO/deploy/sensorsync-tls.service" /etc/systemd/system/sensorsync-tls.service
install -m 644 "$REPO/deploy/sensorsync.service" /etc/systemd/system/sensorsync.service
sed -i 's/\r$//' /etc/systemd/system/sensorsync-tls.service /etc/systemd/system/sensorsync.service
sed -i "s|^WorkingDirectory=.*|WorkingDirectory=$DATA_DIR|" /etc/systemd/system/sensorsync.service
# usa o mesmo java conferido acima (ex.: Temurin em /usr/local/bin/java), nao necessariamente o /usr/bin/java
JAVA_BIN=$(readlink -f "$(command -v java)")
sed -i "s|^ExecStart=[^ ]*java |ExecStart=$JAVA_BIN |" /etc/systemd/system/sensorsync.service
ok "API vai rodar com $JAVA_BIN"
sysctl_ daemon-reload
sysctl_ enable sensorsync-tls sensorsync >/dev/null 2>&1 || sysctl_ enable sensorsync-tls sensorsync
ok "sensorsync-tls.service (certificados) e sensorsync.service (API) habilitados no boot"
if $CHRONY; then
    if ! command -v chronyd >/dev/null; then
        info "instalando chrony..."
        apt-get install -y chrony >/dev/null || aviso "apt falhou (Ubuntu sem suporte? veja o guia); chrony nao instalado"
    fi
    if [ -f /etc/chrony/chrony.conf ]; then
        REDE=$(echo "$IP" | awk -F. '{print $1"."$2".0.0/16"}')
        grep -q "^allow $REDE" /etc/chrony/chrony.conf || echo "allow $REDE   # SensorSync: serve hora para PC e placas" >> /etc/chrony/chrony.conf
        sysctl_ restart chrony
        ok "chrony servindo hora para $REDE"
    fi
fi

# =============================================================================================
titulo "8. Versao antiga"
procs=$(processos_antigos)
auto=$(autostart_antigo)
if [ -z "$procs" ] && [ -z "$auto" ]; then
    ok "nada da versao antiga rodando nem no boot"
else
    [ -n "$procs" ] && { info "rodando:"; echo "$procs" | sed 's/^/      /'; }
    [ -n "$auto" ] && { info "iniciada no boot por:"; echo "$auto" | sed 's/^/      /'; }
    if confirma "Parar a versao antiga e desativar o inicio automatico dela?"; then
        echo "$auto" | while IFS= read -r linha; do
            [ -n "$linha" ] || continue
            case "$linha" in
                systemd:*) sysctl_ disable --now "${linha#systemd:}" || true;;
                *)  arq=${linha%%:*}; num=$(echo "$linha" | cut -d: -f2)
                    sed -i "${num}s/^/#[sensorsync-desativado] /" "$arq"
                    info "comentada a linha $num de $arq";;
            esac
        done
        for pid in $(echo "$procs" | awk '{print $1}'); do
            kill "$pid" 2>/dev/null || true
        done
        sleep 5
        for pid in $(echo "$procs" | awk '{print $1}'); do
            kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null || true
        done
        ok "versao antiga parada"
    else
        aviso "a versao antiga continua rodando: a nova nao vai conseguir usar a porta 8080"
    fi
fi

# =============================================================================================
titulo "9. Subindo"
if ! $SYSTEMD; then
    aviso "--sem-systemd: nada foi iniciado. Comandos equivalentes:"
    info "  sudo $APP_DIR/sensorsync-tls.sh   (com as variaveis de $ENV_FILE)"
    info "  sudo systemctl restart mosquitto"
    info "  cd $DATA_DIR && sudo -u sensorsync env \$(grep -v '^#' $ENV_FILE | xargs) java -Xmx384m -jar $NEW_JAR"
    exit 0
fi
systemctl restart sensorsync-tls
ok "certificados ($TLS_DIR)"
systemctl restart mosquitto
sleep 2
systemctl is-active --quiet mosquitto && ok "Mosquitto" || { journalctl -u mosquitto -n 20 --no-pager; erro "Mosquitto nao subiu (veja acima)"; }
systemctl restart sensorsync
info "aguardando a API (no Raspberry Pi 3 leva 1 a 3 minutos)..."
if $TLS; then URL="https://127.0.0.1:8443/login.html"; else URL="http://127.0.0.1:8080/login.html"; fi
for i in $(seq 1 120); do
    if curl -sk -o /dev/null -w '%{http_code}' "$URL" 2>/dev/null | grep -q 200; then
        ok "API respondendo em $(( i * 3 )) s"
        break
    fi
    if ! systemctl is-active --quiet sensorsync; then
        journalctl -u sensorsync -n 40 --no-pager
        erro "a API parou (log acima). Para voltar: sudo bash deploy/lab/desfazer.sh"
    fi
    sleep 3
    [ "$i" = 120 ] && { journalctl -u sensorsync -n 40 --no-pager; erro "a API nao respondeu em 6 min"; }
done

titulo "Pronto"
if $TLS; then
    info "Navegador:        https://$IP:8443   (aviso de certificado ate instalar a CA: link na tela de login)"
    info "Placas novas:     codigo gerado na aba Codigo (HTTPS 8443 / MQTT TLS 8883, CA embutida)"
    info "CA:               $(openssl x509 -in "$TLS_DIR/ca.crt" -noout -fingerprint -sha1 | cut -d= -f2) (SHA-1)"
else
    info "Navegador:        http://$IP:8080"
fi
if [ "$MODO" = transicao ]; then
    info "Placas antigas:   continuam enviando como antes (HTTP 8080 /data/saveList ou MQTT 1883, sem token)"
    info "Quando todas as placas forem regravadas: sudo bash deploy/lab/3-instalar.sh --modo final"
fi
info "Log da API:       sudo journalctl -u sensorsync -f"
info "Conferir tudo:    sudo bash deploy/lab/1-inventario.sh"
