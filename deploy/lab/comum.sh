#!/bin/bash
# Funcoes comuns dos scripts de atualizacao do laboratorio (incluido pelos outros com "source").
# Nao rode este arquivo diretamente.

DB_NAME="${SENSORSYNC_DB_NAME:-sensor_sync_data}"
APP_DIR=/opt/sensorsync
DATA_DIR=/var/lib/sensorsync
ENV_DIR=/etc/sensorsync
ENV_FILE=$ENV_DIR/sensorsync.env
TLS_DIR=$ENV_DIR/tls
BACKUP_ROOT=/var/backups/sensorsync
NEW_JAR=$APP_DIR/sensorsync.jar
ASSUME_YES=${ASSUME_YES:-false}

if [ -t 1 ]; then
    B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; C=$'\e[36m'; N=$'\e[0m'
else
    B=; G=; Y=; R=; C=; N=
fi

titulo() { echo; echo "${B}${C}== $* ==${N}"; }
info()   { echo "   $*"; }
ok()     { echo "   ${G}OK${N} $*"; }
aviso()  { echo "   ${Y}ATENCAO${N} $*"; }
erro()   { echo "${R}ERRO:${N} $*" >&2; exit 1; }

precisa_root() {
    [ "$(id -u)" = 0 ] || erro "rode com sudo: sudo bash $0 $*"
}

# Pergunta sim/nao (padrao: nao). Com ASSUME_YES=true (opcao --sim) responde sim sozinho.
confirma() {
    if [ "$ASSUME_YES" = true ]; then
        info "$1 [s/N] s (automatico)"
        return 0
    fi
    local r
    read -r -p "   $1 [s/N] " r
    [[ "$r" =~ ^[sSyY] ]]
}

# ---- Sistema antigo --------------------------------------------------------------------------

# .jar da versao antiga (qualquer sensorsync*.jar fora de /opt/sensorsync), um por linha
jars_antigos() {
    find /home /root /opt /srv /usr/local -maxdepth 4 -type f -iname '*sensorsync*.jar' 2>/dev/null \
        | grep -v "^$APP_DIR/" || true
}

# Processos java da versao antiga (rodando fora do servico novo): "PID comando"
processos_antigos() {
    pgrep -af 'java' 2>/dev/null | grep -i 'sensorsync' | grep -v "$NEW_JAR" || true
}

# Linhas que iniciam a versao antiga no boot: "arquivo:linha"
autostart_antigo() {
    local f
    for f in /var/spool/cron/crontabs/* /etc/crontab /etc/rc.local; do
        [ -f "$f" ] || continue
        grep -n -i -E 'sensorsync|\.jar' "$f" 2>/dev/null | grep -v '^\s*[0-9]*:\s*#' | sed "s|^|$f:|" || true
    done
    systemctl list-unit-files 2>/dev/null | awk '{print $1}' | grep -i 'sensorsync' \
        | grep -v -E '^sensorsync(-tls)?\.service$' | sed 's|^|systemd:|' || true
}

# Le "chave: valor" do application.yml/properties de dentro do .jar antigo (ou ao lado dele).
# Uso: config_jar_antigo arquivo.jar password|username|url
config_jar_antigo() {
    python3 - "$1" "$2" <<'PY' 2>/dev/null
import sys, zipfile, os, re
jar, key = sys.argv[1], sys.argv[2]
texts = []
base = os.path.dirname(jar)
for p in (os.path.join(base, "application.yml"), os.path.join(base, "config", "application.yml"),
          os.path.join(base, "application.properties")):
    if os.path.isfile(p):
        texts.append(open(p, encoding="utf-8", errors="replace").read())
try:
    z = zipfile.ZipFile(jar)
    for n in ("BOOT-INF/classes/application.yml", "BOOT-INF/classes/application.properties"):
        if n in z.namelist():
            texts.append(z.read(n).decode("utf-8", "replace"))
except Exception:
    pass
for t in texts:
    # properties: spring.datasource.password=...
    m = re.search(r"spring\.datasource\." + key + r"\s*=\s*(.*)", t)
    if m:
        print(m.group(1).strip()); break
    # yaml: dentro de "datasource:" procura "  key: valor"
    m = re.search(r"datasource:\s*\n((?:[ \t]+.*\n?)+)", t)
    if m:
        k = re.search(r"^[ \t]+" + key + r":[ \t]*(.*)$", m.group(1), re.M)
        if k:
            print(k.group(1).strip().strip("'\"")); break
PY
}

# ---- MySQL como administrador ------------------------------------------------------------------
# Cria um arquivo temporario de credenciais (MYSQL_CNF) e testa. Ordem: socket do root (sudo),
# SENSORSYNC_MYSQL_ROOT_PASSWORD, senha do .jar antigo, pergunta.
MYSQL_CNF=""
mysql_admin_prepara() {
    MYSQL_CNF=$(mktemp)
    chmod 600 "$MYSQL_CNF"
    trap 'rm -f "$MYSQL_CNF"' EXIT
    printf '[client]\nuser=root\n' > "$MYSQL_CNF"
    if mysql --defaults-extra-file="$MYSQL_CNF" -e 'SELECT 1' >/dev/null 2>&1; then
        ok "MySQL: acesso de administrador pelo usuario root do sistema"
        return 0
    fi
    local pw="" jar user
    if [ -n "${SENSORSYNC_MYSQL_ROOT_PASSWORD:-}" ]; then
        pw=$SENSORSYNC_MYSQL_ROOT_PASSWORD
    else
        for jar in $(jars_antigos); do
            user=$(config_jar_antigo "$jar" username)
            if [ "$user" = root ]; then
                pw=$(config_jar_antigo "$jar" password)
                [ -n "$pw" ] && { info "senha do root do MySQL lida da configuracao de $jar"; break; }
            fi
        done
    fi
    if [ -n "$pw" ]; then
        printf '[client]\nuser=root\npassword="%s"\n' "$pw" > "$MYSQL_CNF"
        if mysql --defaults-extra-file="$MYSQL_CNF" -e 'SELECT 1' >/dev/null 2>&1; then
            ok "MySQL: acesso de administrador com a senha do root"
            return 0
        fi
        aviso "a senha encontrada nao funcionou"
    fi
    local tentativa
    for tentativa in 1 2 3; do
        read -r -s -p "   Senha do usuario root do MySQL: " pw; echo
        printf '[client]\nuser=root\npassword="%s"\n' "$pw" > "$MYSQL_CNF"
        if mysql --defaults-extra-file="$MYSQL_CNF" -e 'SELECT 1' >/dev/null 2>&1; then
            ok "MySQL: acesso de administrador"
            return 0
        fi
        aviso "senha recusada"
    done
    erro "nao foi possivel acessar o MySQL como administrador"
}

mysql_admin() { mysql --defaults-extra-file="$MYSQL_CNF" "$@"; }
