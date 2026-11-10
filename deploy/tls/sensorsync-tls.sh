#!/bin/sh
# SensorSync - certificados TLS gerados automaticamente (roda a cada boot, antes do Mosquitto e da API).
#
# 1. Na primeira execucao cria uma CA propria (ca.crt / ca.key). Ela nunca muda: e o certificado
#    que vai embutido no codigo gerado para as placas.
# 2. Emite o certificado do servidor (server.crt / server.key) com todos os nomes e IPs desta
#    maquina no SAN. Se o IP ou o nome mudar, so o certificado do servidor e reemitido: as placas
#    ja gravadas continuam confiando na mesma CA.
#
# Variaveis (lidas de /etc/sensorsync/sensorsync.env pela unit):
#   SENSORSYNC_TLS_DIR    pasta dos arquivos (padrao /etc/sensorsync/tls)
#   SENSORSYNC_TLS_KEY    ec (padrao, ECDSA P-256, mais leve nas placas) ou rsa (RSA-2048)
#   SENSORSYNC_PUBLIC_HOST IP fixo ou nome DNS usado pelas placas (entra no SAN)
#   SENSORSYNC_TLS_GROUP  grupo com leitura da chave do servidor (padrao sensorsync-tls)
set -eu

DIR="${SENSORSYNC_TLS_DIR:-/etc/sensorsync/tls}"
KEY_TYPE="${SENSORSYNC_TLS_KEY:-ec}"
GROUP="${SENSORSYNC_TLS_GROUP:-sensorsync-tls}"
DAYS=7300   # 20 anos: as placas nao tem relogio confiavel; ver README (secao TLS)

log() { echo "sensorsync-tls: $*"; }

new_key() {
    case "$KEY_TYPE" in
        ec)  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1" ;;
        rsa) openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$1" 2>/dev/null ;;
        *)   log "SENSORSYNC_TLS_KEY invalido: $KEY_TYPE (use ec ou rsa)"; exit 1 ;;
    esac
}

mkdir -p "$DIR"
chmod 755 "$DIR"
umask 077

# ---- Grupo que pode ler a chave do servidor (API e Mosquitto) ------------------------------
# Sem root (teste em maquina de desenvolvimento): arquivos ficam com o usuario atual.
if [ "$(id -u)" != 0 ]; then
    log "sem root: pulando grupo $GROUP e dono dos arquivos (so para testes)"
    ROOT=false
else
    ROOT=true
fi
if $ROOT && ! getent group "$GROUP" >/dev/null 2>&1; then
    groupadd --system "$GROUP" 2>/dev/null || addgroup --system "$GROUP"
fi
for u in sensorsync mosquitto; do
    if $ROOT && id "$u" >/dev/null 2>&1 && ! id -nG "$u" | tr ' ' '\n' | grep -qx "$GROUP"; then
        usermod -a -G "$GROUP" "$u"
        log "usuario $u adicionado ao grupo $GROUP"
    fi
done

# ---- CA (uma vez so) -----------------------------------------------------------------------
# Trocar o tipo de chave (ec/rsa) exige uma CA nova: guarda o tipo junto.
if [ -f "$DIR/ca.crt" ] && [ "$(cat "$DIR/ca.type" 2>/dev/null || echo ec)" != "$KEY_TYPE" ]; then
    log "tipo de chave mudou para $KEY_TYPE: movendo a CA antiga para ca-old/"
    mkdir -p "$DIR/ca-old"
    mv "$DIR"/ca.* "$DIR"/server.* "$DIR/ca-old/" 2>/dev/null || true
fi
if [ ! -f "$DIR/ca.crt" ] || [ ! -f "$DIR/ca.key" ]; then
    log "criando CA ($KEY_TYPE)"
    new_key "$DIR/ca.key"
    openssl req -x509 -new -key "$DIR/ca.key" -sha256 -days "$DAYS" \
        -subj "/O=SensorSync/CN=SensorSync CA $(hostname)" \
        -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
        -addext "keyUsage=critical,keyCertSign,cRLSign" \
        -out "$DIR/ca.crt"
    echo "$KEY_TYPE" > "$DIR/ca.type"
    rm -f "$DIR/server.crt" "$DIR/server.san"
fi
chmod 600 "$DIR/ca.key"
chmod 644 "$DIR/ca.crt" "$DIR/ca.type"

# ---- Nomes e IPs do servidor ---------------------------------------------------------------
is_ip() { echo "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; }

NAMES="localhost $(hostname) $(hostname).local"
IPS="127.0.0.1 $(hostname -I 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' | tr '\n' ' ')"
if [ -n "${SENSORSYNC_PUBLIC_HOST:-}" ]; then
    if is_ip "$SENSORSYNC_PUBLIC_HOST"; then IPS="$IPS $SENSORSYNC_PUBLIC_HOST"; else NAMES="$NAMES $SENSORSYNC_PUBLIC_HOST"; fi
fi

SAN=""
for n in $(echo "$NAMES" | tr ' ' '\n' | sort -u); do SAN="$SAN,DNS:$n"; done
for ip in $(echo "$IPS" | tr ' ' '\n' | sort -u); do
    # IP tambem como DNS: o BearSSL (ESP8266) so compara o nome do servidor com entradas DNS
    SAN="$SAN,IP:$ip,DNS:$ip"
done
SAN="${SAN#,}"

# ---- Certificado do servidor (reemitido se o SAN mudou ou faltar arquivo) -------------------
if [ -f "$DIR/server.crt" ] && [ -f "$DIR/server.key" ] && [ "$(cat "$DIR/server.san" 2>/dev/null)" = "$SAN" ]; then
    log "certificado do servidor em dia ($SAN)"
else
    log "emitindo certificado do servidor: $SAN"
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT

    new_key "$TMP/server.key"
    openssl req -new -key "$TMP/server.key" -subj "/O=SensorSync/CN=$(hostname)" -out "$TMP/server.csr"

    if [ "$KEY_TYPE" = "rsa" ]; then KU="digitalSignature,keyEncipherment"; else KU="digitalSignature"; fi
    # Validade do servidor = validade da CA. O inicio igual ao da CA garante que placas sem
    # relogio (que usam a data da compilacao do firmware) aceitem um certificado reemitido depois.
    START="$(date -u -d "$(openssl x509 -in "$DIR/ca.crt" -noout -startdate | cut -d= -f2)" +%Y%m%d%H%M%SZ)"
    END="$(date -u -d "$(openssl x509 -in "$DIR/ca.crt" -noout -enddate | cut -d= -f2)" +%Y%m%d%H%M%SZ)"

    : > "$TMP/index.txt"
    openssl rand -hex 16 > "$TMP/serial"
    cat > "$TMP/ca.cnf" <<EOF
[ ca ]
default_ca = local
[ local ]
database = $TMP/index.txt
new_certs_dir = $TMP
serial = $TMP/serial
default_md = sha256
policy = anything
unique_subject = no
copy_extensions = none
[ anything ]
organizationName = optional
commonName = supplied
[ server ]
basicConstraints = critical,CA:FALSE
keyUsage = critical,$KU
extendedKeyUsage = serverAuth
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid
subjectAltName = $SAN
EOF
    openssl ca -batch -notext -config "$TMP/ca.cnf" -extensions server \
        -cert "$DIR/ca.crt" -keyfile "$DIR/ca.key" \
        -startdate "$START" -enddate "$END" \
        -in "$TMP/server.csr" -out "$TMP/server.crt" 2>"$TMP/ca.log" || { cat "$TMP/ca.log"; exit 1; }

    install -m 640 "$TMP/server.key" "$DIR/server.key"
    install -m 644 "$TMP/server.crt" "$DIR/server.crt"
    echo "$SAN" > "$DIR/server.san"
    chmod 644 "$DIR/server.san"
fi
if $ROOT; then
    chown root:"$GROUP" "$DIR/server.key"
    chown root:root "$DIR/server.crt" "$DIR/ca.crt" "$DIR/ca.key"
fi
chmod 640 "$DIR/server.key"

log "CA: $(openssl x509 -in "$DIR/ca.crt" -noout -fingerprint -sha256)"
