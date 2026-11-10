#!/bin/bash
# SensorSync - desfaz a atualizacao e volta ao estado de um backup do passo 2.
#
#   sudo bash deploy/lab/desfazer.sh                      (usa o backup mais recente)
#   sudo bash deploy/lab/desfazer.sh /var/backups/sensorsync/20261112-143000
#
# 1. Para e desativa os servicos novos (sensorsync, sensorsync-tls). Os arquivos novos ficam onde estao.
# 2. Restaura a configuracao do Mosquitto e os crontabs (inicio automatico da versao antiga).
# 3. Opcional, com confirmacao digitada: restaura o BANCO do backup. Isso APAGA o que foi gravado
#    depois do backup (leituras novas). Se a versao nova so rodou por pouco tempo, normalmente nao e
#    preciso: a versao antiga funciona com o banco migrado (ela ignora as tabelas e colunas novas).
#    Excecao: as senhas migradas para BCrypt nao funcionam no login da versao antiga.
# 4. Mostra como iniciar a versao antiga (ou reinicie o Raspberry).
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=comum.sh
source "$DIR/comum.sh"
SYSTEMD=true
[ "${1:-}" = "--sem-systemd" ] && { SYSTEMD=false; shift; }
precisa_root

SRC=${1:-$(cat "$BACKUP_ROOT/ULTIMO" 2>/dev/null || true)}
[ -n "$SRC" ] && [ -d "$SRC" ] || erro "backup nao encontrado (informe a pasta: ls $BACKUP_ROOT)"
titulo "Voltando ao backup $SRC"
cat "$SRC/info.txt" 2>/dev/null | sed -n '1,12p' | sed 's/^/   /'
confirma "Desfazer a atualizacao usando este backup?" || exit 1

titulo "1. Servicos novos"
if $SYSTEMD; then
    systemctl disable --now sensorsync sensorsync-tls 2>/dev/null || true
fi
ok "sensorsync e sensorsync-tls parados e desativados (arquivos mantidos em $APP_DIR e $ENV_DIR)"

titulo "2. Mosquitto e inicio automatico"
if [ -d "$SRC/etc/mosquitto" ]; then
    mv /etc/mosquitto "/etc/mosquitto.sensorsync-$(date +%Y%m%d-%H%M%S)"
    cp -a "$SRC/etc/mosquitto" /etc/
    ok "/etc/mosquitto restaurado (a configuracao nova ficou em /etc/mosquitto.sensorsync-*)"
fi
if [ -d "$SRC/etc/crontabs" ]; then
    cp -a "$SRC/etc/crontabs/." /var/spool/cron/crontabs/
    ok "crontabs restaurados"
fi
[ -f "$SRC/etc/rc.local" ] && cp -a "$SRC/etc/rc.local" /etc/rc.local && ok "/etc/rc.local restaurado"
for u in "$SRC"/etc/*.service; do
    [ -e "$u" ] || continue
    case "$(basename "$u")" in sensorsync.service|sensorsync-tls.service) continue;; esac
    cp -a "$u" /etc/systemd/system/ && $SYSTEMD && systemctl daemon-reload && systemctl enable "$(basename "$u")" || true
    ok "$(basename "$u") restaurado"
done
$SYSTEMD && systemctl restart mosquitto && ok "Mosquitto reiniciado" || true

titulo "3. Banco de dados"
if [ -f "$SRC/banco.sql.gz" ]; then
    info "Restaurar o banco APAGA tudo que foi gravado depois do backup ($(basename "$SRC"))."
    info "So e necessario se a versao antiga nao funcionar com o banco atual (ex.: login com senha antiga)."
    resp=""
    if [ "$ASSUME_YES" = true ]; then resp=RESTAURAR; else read -r -p "   Digite RESTAURAR para restaurar o banco (Enter pula): " resp; fi
    if [ "$resp" = RESTAURAR ]; then
        mysql_admin_prepara
        mysqldump --defaults-extra-file="$MYSQL_CNF" --single-transaction --no-tablespaces "$DB_NAME" 2>/dev/null \
            | gzip > "$BACKUP_ROOT/antes-de-desfazer-$(date +%Y%m%d-%H%M%S).sql.gz" || true
        info "copia do banco atual guardada em $BACKUP_ROOT/antes-de-desfazer-*.sql.gz"
        mysql_admin -e "DROP DATABASE IF EXISTS \`$DB_NAME\`; CREATE DATABASE \`$DB_NAME\`"
        zcat "$SRC/banco.sql.gz" | mysql_admin "$DB_NAME"
        if [ -f "$SRC/contagem-exata.txt" ]; then
            while read -r t n; do
                [ "$n" = "-" ] && continue
                m=$(mysql_admin -N -e "SELECT COUNT(*) FROM \`$DB_NAME\`.\`$t\`")
                [ "$m" = "$n" ] && ok "$t: $m linhas (igual ao backup)" || aviso "$t: $m linhas, backup tinha $n"
            done < "$SRC/contagem-exata.txt"
        fi
    else
        info "banco mantido como esta"
    fi
fi

titulo "4. Versao antiga"
info "Ela volta a subir sozinha no proximo boot se era iniciada pelo crontab/rc.local:"
grep -A3 'Inicio automatico' "$SRC/info.txt" 2>/dev/null | tail -n +2 | sed 's/^/      /' || true
info "Para iniciar agora sem reiniciar, use o mesmo comando de antes. Processos que rodavam no backup:"
grep -A3 'Processos' "$SRC/info.txt" 2>/dev/null | tail -n +2 | sed 's/^/      /' || true
info "Ou simplesmente: sudo reboot"
