#!/bin/bash
# SensorSync - passo 2: backup completo antes de atualizar (nao altera nada do sistema).
#
#   sudo bash deploy/lab/2-backup.sh
#
# Cria /var/backups/sensorsync/AAAAMMDD-HHMMSS/ com:
#   banco.sql.gz        copia do banco sensor_sync_data (so as tabelas, sem o "CREATE DATABASE":
#                       pode ser importada em outro banco, por exemplo para o ensaio no PC)
#   jar/                o .jar da versao antiga
#   etc/                configuracoes do Mosquitto, crontabs, rc.local, /etc/sensorsync, units do systemd
#   info.txt            processos, o que inicia no boot, versoes, contagem de linhas
#   RESTAURAR.txt       como voltar ao estado anterior
# O caminho do backup mais recente fica em /var/backups/sensorsync/ULTIMO.
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=comum.sh
source "$DIR/comum.sh"
precisa_root "$@"

STAMP=$(date +%Y%m%d-%H%M%S)
DEST=$BACKUP_ROOT/$STAMP
mkdir -p "$DEST/jar" "$DEST/etc"
chmod 700 "$BACKUP_ROOT" "$DEST"

titulo "Backup em $DEST"

titulo "Banco de dados"
mysql_admin_prepara
if mysql_admin -N -e "SHOW DATABASES LIKE '$DB_NAME'" | grep -q "$DB_NAME"; then
    info "exportando $DB_NAME (pode levar alguns minutos se houver muitas leituras)..."
    mysql_admin -N -e "SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema='$DB_NAME'" \
        > "$DEST/contagem-aproximada.txt"
    GTID=""
    mysqldump --help 2>/dev/null | grep -q 'set-gtid-purged' && GTID="--set-gtid-purged=OFF"   # so no MySQL (nao no MariaDB)
    # shellcheck disable=SC2086
    mysqldump --defaults-extra-file="$MYSQL_CNF" --single-transaction --routines --triggers \
        --no-tablespaces $GTID "$DB_NAME" 2>"$DEST/mysqldump.log" | gzip > "$DEST/banco.sql.gz" \
        || { cat "$DEST/mysqldump.log"; erro "falha no mysqldump"; }
    gzip -t "$DEST/banco.sql.gz" || erro "arquivo do backup corrompido"
    tabelas=$(zcat "$DEST/banco.sql.gz" | grep -c '^CREATE TABLE' || true)
    zcat "$DEST/banco.sql.gz" | tail -1 | grep -q 'Dump completed' || erro "dump incompleto (sem 'Dump completed')"
    ok "banco.sql.gz: $(du -h "$DEST/banco.sql.gz" | cut -f1), $tabelas tabelas, dump completo"
    for t in user_human project data metadata device; do
        n=$(mysql_admin -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.\`$t\`" 2>/dev/null || echo "-")
        echo "$t $n" >> "$DEST/contagem-exata.txt"
        info "$t: $n linhas"
    done
else
    aviso "banco $DB_NAME nao existe (instalacao nova?): nada a exportar"
fi

titulo "Versao antiga e configuracoes"
for j in $(jars_antigos); do
    cp -a "$j" "$DEST/jar/"
    echo "$j" >> "$DEST/jar/ORIGEM.txt"
    for extra in "$(dirname "$j")/application.yml" "$(dirname "$j")/application.properties"; do
        [ -f "$extra" ] && cp -a "$extra" "$DEST/jar/"
    done
    ok "jar: $j"
done
[ -f "$NEW_JAR" ] && cp -a "$NEW_JAR" "$DEST/jar/sensorsync-novo-instalado.jar" && ok "jar novo ja instalado: $NEW_JAR"
[ -d /etc/mosquitto ] && cp -a /etc/mosquitto "$DEST/etc/" && ok "/etc/mosquitto"
[ -d /var/spool/cron/crontabs ] && cp -a /var/spool/cron/crontabs "$DEST/etc/" && ok "crontabs"
[ -f /etc/crontab ] && cp -a /etc/crontab "$DEST/etc/"
[ -f /etc/rc.local ] && cp -a /etc/rc.local "$DEST/etc/" && ok "/etc/rc.local"
[ -d "$ENV_DIR" ] && cp -a "$ENV_DIR" "$DEST/etc/" && ok "$ENV_DIR (inclui certificados e senhas: guarde o backup com cuidado)"
for u in /etc/systemd/system/*sensorsync*; do
    [ -e "$u" ] && cp -a "$u" "$DEST/etc/" && ok "$u"
done

{
    echo "Backup do SensorSync em $(hostname), $(date)"
    echo
    echo "== Processos da versao antiga"; processos_antigos
    echo; echo "== Inicio automatico da versao antiga"; autostart_antigo
    echo; echo "== Java"; java -version 2>&1 | grep -v JAVA_TOOL
    echo; echo "== MySQL"; mysql --version
    echo; echo "== Sistema"; . /etc/os-release; echo "$PRETTY_NAME"
} > "$DEST/info.txt" 2>&1

cat > "$DEST/RESTAURAR.txt" <<EOF
Como voltar ao estado deste backup ($STAMP)
============================================

Jeito facil (desfaz a atualizacao inteira):
    sudo bash deploy/lab/desfazer.sh $DEST

Manual, por partes:
1. Parar a versao nova:
       sudo systemctl disable --now sensorsync sensorsync-tls
2. Banco (APAGA o banco atual e volta ao do backup):
       sudo mysql -u root -p -e "DROP DATABASE $DB_NAME; CREATE DATABASE $DB_NAME"
       zcat $DEST/banco.sql.gz | sudo mysql -u root -p $DB_NAME
3. Mosquitto:
       sudo rm -rf /etc/mosquitto && sudo cp -a $DEST/etc/mosquitto /etc/ && sudo systemctl restart mosquitto
4. Inicio automatico da versao antiga (crontab):
       sudo cp -a $DEST/etc/crontabs/. /var/spool/cron/crontabs/
5. Iniciar a versao antiga como antes (veja info.txt, secao "Processos"), ou reinicie o Raspberry.
EOF
chmod -R go-rwx "$DEST"
echo "$DEST" > "$BACKUP_ROOT/ULTIMO"

titulo "Pronto"
ok "backup em $DEST ($(du -sh "$DEST" | cut -f1))"
info "Leve uma copia para fora do Raspberry (no PC, PowerShell):"
info "   scp -r ${SUDO_USER:-ubuntu}@$(hostname -I | awk '{print $1}'):$DEST ."
info "   (antes: sudo chown -R ${SUDO_USER:-ubuntu} $DEST, para o scp conseguir ler)"
