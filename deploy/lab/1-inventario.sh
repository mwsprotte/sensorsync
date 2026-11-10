#!/bin/bash
# SensorSync - passo 1: inventario do servidor (SOMENTE LEITURA, nao altera nada).
#
#   sudo bash deploy/lab/1-inventario.sh
#
# Mostra o que esta instalado e rodando (sistema, Java, MySQL, Mosquitto, versao antiga do SensorSync,
# o que a inicia no boot, portas abertas, tamanho do banco) e salva tudo em ~/inventario-sensorsync-*.txt.
# Mande esse arquivo para revisao antes de atualizar.
set -uo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=comum.sh
source "$DIR/comum.sh"

SAIDA="${SUDO_USER:+/home/$SUDO_USER}"
SAIDA="${SAIDA:-$HOME}/inventario-sensorsync-$(hostname)-$(date +%Y%m%d-%H%M).txt"

{
titulo "Sistema"
info "maquina:  $(tr -d '\0' < /proc/device-tree/model 2>/dev/null || echo '?')"
info "sistema:  $(. /etc/os-release 2>/dev/null; echo "${PRETTY_NAME:-?}")"
info "kernel:   $(uname -r) ($(uname -m))"
info "memoria:  $(free -m | awk '/^Mem:/ {print $2 " MB total, " $7 " MB disponivel"}')"
info "disco /:  $(df -h / | awk 'NR==2 {print $4 " livres de " $2}')"
info "IPs:      $(hostname -I 2>/dev/null)"
info "hora:     $(date '+%Y-%m-%d %H:%M:%S %Z')"
timedatectl 2>/dev/null | grep -E 'synchronized|NTP service' | sed 's/^ */   /'
. /etc/os-release 2>/dev/null
case "${VERSION_CODENAME:-}" in
    mantic|lunar|kinetic) aviso "Ubuntu ${VERSION_ID:-} esta sem suporte: 'apt install' nao funciona sem ajustar as fontes (ver guia)";;
esac

titulo "Java"
if command -v java >/dev/null; then
    java -version 2>&1 | grep -v JAVA_TOOL | sed 's/^/   /'
else
    aviso "java nao encontrado"
fi

titulo "MySQL"
if command -v mysql >/dev/null; then
    info "$(mysql --version)"
    systemctl is-active mysql >/dev/null 2>&1 && ok "servico mysql ativo" || aviso "servico mysql parado"
else
    aviso "mysql nao encontrado"
fi

titulo "Mosquitto"
if command -v mosquitto >/dev/null; then
    info "$(mosquitto -h 2>/dev/null | head -1)"
    systemctl is-active mosquitto >/dev/null 2>&1 && ok "servico mosquitto ativo" || aviso "servico mosquitto parado"
    for f in /etc/mosquitto/mosquitto.conf /etc/mosquitto/conf.d/*.conf; do
        [ -f "$f" ] || continue
        info "--- $f"
        grep -v -E '^\s*(#|$)' "$f" | sed 's/^/      /'
    done
else
    aviso "mosquitto nao encontrado"
fi

titulo "SensorSync antigo"
jars=$(jars_antigos)
if [ -n "$jars" ]; then
    for j in $jars; do
        info "jar: $j ($(du -h "$j" | cut -f1), $(date -r "$j" '+%Y-%m-%d'))"
        info "     banco: $(config_jar_antigo "$j" url)  usuario: $(config_jar_antigo "$j" username)"
    done
else
    aviso "nenhum sensorsync*.jar encontrado em /home, /root, /opt, /srv, /usr/local"
fi
p=$(processos_antigos)
[ -n "$p" ] && { info "rodando agora:"; echo "$p" | sed 's/^/      /'; } || info "nenhum processo da versao antiga rodando"
a=$(autostart_antigo)
[ -n "$a" ] && { info "iniciado no boot por:"; echo "$a" | sed 's/^/      /'; } || info "nada encontrado que inicie a versao antiga no boot (crontab, rc.local, systemd)"

titulo "SensorSync novo"
[ -f "$NEW_JAR" ] && info "instalado em $NEW_JAR ($(date -r "$NEW_JAR" '+%Y-%m-%d %H:%M'))" || info "nao instalado"
for s in sensorsync-tls sensorsync; do
    systemctl list-unit-files "$s.service" >/dev/null 2>&1 && info "$s: $(systemctl is-enabled $s 2>/dev/null), $(systemctl is-active $s 2>/dev/null)"
done
[ -f "$ENV_FILE" ] && { info "configuracao $ENV_FILE (sem senhas):"; grep -v -i -E 'PASSWORD' "$ENV_FILE" | grep -v '^\s*#' | grep . | sed 's/^/      /'; }
[ -d "$BACKUP_ROOT" ] && info "backups: $(ls "$BACKUP_ROOT" 2>/dev/null | grep -v ULTIMO | tr '\n' ' ')"

titulo "Portas abertas"
ss -ltnp 2>/dev/null | awk 'NR>1 {print "   " $4 "  " $6}' | grep -E ':(22|80|443|1883|3306|8080|8443|8883)\s' || info "(ss sem saida)"

titulo "Banco de dados ($DB_NAME)"
if [ "$(id -u)" = 0 ] && command -v mysql >/dev/null; then
    if mysql_admin_prepara; then
        mysql_admin -N -e "SELECT table_name, table_rows, ROUND((data_length+index_length)/1024/1024,1)
            FROM information_schema.tables WHERE table_schema='$DB_NAME' ORDER BY table_name" 2>/dev/null \
            | awk '{printf "   %-28s ~%s linhas  %s MB\n", $1, $2, $3}'
        mysql_admin -N -e "SELECT COUNT(*) FROM $DB_NAME.user_human" 2>/dev/null | sed 's/^/   usuarios: /'
        mysql_admin -N -e "SELECT COUNT(*) FROM $DB_NAME.project" 2>/dev/null | sed 's/^/   projetos: /'
        mysql_admin -N -e "SELECT COUNT(*) FROM $DB_NAME.data" 2>/dev/null | sed 's/^/   leituras: /'
    fi
else
    info "(rode com sudo para ver o banco)"
fi
echo
} 2>&1 | tee "$SAIDA"

echo "Inventario salvo em: $SAIDA"
