# Experimentos (bench/)

Ferramentas para medir latência, perda e vazão do SensorSync, com e sem TLS, e o custo nas placas.

| Arquivo | O que faz |
|---|---|
| `loadgen.py` | Simula N dispositivos (HTTP/HTTPS ou MQTT/MQTTS), cada um com seu token, numa taxa fixa. Salva `client.csv`, baixa o log da API (`server.csv`) e grava `run.json`. |
| `analyze.py` | Cruza cliente e servidor por (dispositivo, `seq`): latência ponta a ponta, tempo no servidor, RTT, perda, duplicadas, fora de ordem e vazão. Acrescenta uma linha em `summary.csv` (tabela de todas as rodadas). |
| `serial_log.py` | Grava o CSV que o exemplo `Benchmark.ino` da biblioteca imprime na serial (custo na placa). |

Instalação (no PC que gera a carga): Python 3.13 ou mais novo (no Windows, versões antigas têm relógio com degraus de
~15 ms) e `pip install requests paho-mqtt pyserial` (e `matplotlib`, opcional, para o gráfico `latency.png`).

## Como funciona a medição

- Cada mensagem leva `"seq"` (1, 2, 3… por dispositivo) e `"ts"` (hora de envio, epoch ms). A API não grava esses
  campos nos dados; com `SENSORSYNC_METRICS=true` ela escreve uma linha por mensagem em `metrics/ingest-metrics.csv`
  (pasta de trabalho da API), com a hora de chegada e a hora depois do **commit no banco**.
- **Ponta a ponta** = commit no servidor − `ts` do cliente. Só vale com os relógios sincronizados: no mesmo PC é
  automático; com o Raspberry, use `chrony` (abaixo) e anote o offset.
- **No servidor** = chegada na API até o commit, com relógio monotônico (não depende de sincronia).
- **RTT no cliente** = tempo da chamada no cliente. No HTTP inclui o commit (a API só responde depois). No MQTT QoS 0
  é só a publicação; no QoS 1, até o PUBACK do broker (ainda antes da gravação).
- **Perda** = `seq` enviados que não aparecem gravados (`saved`) no servidor.
- O log da API é baixado e zerado por `GET`/`DELETE /admin/metrics.csv` (usuário administrador); o `loadgen.py` faz
  isso sozinho e cria/apaga projetos de teste (até 50 dispositivos por projeto).

## Preparar o servidor

Suba a API com `SENSORSYNC_METRICS=true` (no IntelliJ: *Environment variables*; no Raspberry: em
`/etc/sensorsync/sensorsync.env` e `sudo systemctl restart sensorsync`). Desligue depois dos experimentos.

Para comparar com e sem TLS na mesma instalação:
- HTTP: `SENSORSYNC_PLAIN_HTTP=allow` deixa a 8080 aceitando HTTP puro ao lado da 8443.
- MQTT: no Mosquitto, `listener 1883 0.0.0.0` (em vez de `127.0.0.1`) abre a 1883 sem TLS para a rede.

Volte ao padrão no fim (`redirect` e `127.0.0.1`).

**Relógio (Raspberry):** `sudo apt install chrony`; no `/etc/chrony/chrony.conf` do Raspberry acrescente
`allow 192.168.0.0/16` (a faixa da rede do laboratório) para ele servir hora ao PC e às placas. No Windows, aponte o
relógio para o Raspberry (`w32tm /config /manualpeerlist:IP_DO_RASPBERRY /syncfromflags:manual /update` como
administrador, depois `w32tm /resync`). Anote o offset de cada rodada (`chronyc tracking` no Raspberry,
`w32tm /stripchart /computer:IP_DO_RASPBERRY /samples:5` no Windows).

## Rodar

```powershell
cd C:\Development\sensorsync
# Aquecimento (a JVM fica mais rápida depois de alguns segundos): rode uma vez e descarte
python bench/loadgen.py --server 192.168.100.166 --admin admin:SENHA --devices 5 --duration 30 --out results/aquecimento

# E1 - HTTP sem TLS, 1 dispositivo, 1 msg/s, 10 min
python bench/loadgen.py --server 192.168.100.166 --admin admin:SENHA --protocol http --devices 1 --duration 600 --out results/E1_http_1
python bench/analyze.py results/E1_http_1
```

Parâmetros principais: `--protocol http|mqtt`, `--tls --ca config/tls/ca.crt`, `--devices N`, `--rate msg/s por
dispositivo`, `--duration s`, `--values k`, `--qos 0|1`, `--no-reuse` (HTTP: conexão nova a cada envio, ou seja, um
handshake TLS por mensagem), `--legacy` (E2: formato antigo, sem token). Rode `python bench/loadgen.py -h` para todos.

**Aquecimento e ordem:** a JVM fica mais rápida nos primeiros minutos; rode uma rodada de aquecimento depois de
subir a API e, ao comparar duas configurações (com/sem token, com/sem TLS), alterne a ordem entre as repetições.

Varredura (PowerShell), por exemplo E1 em HTTP com 1, 5, 10, 20 e 50 dispositivos:

```powershell
foreach ($n in 1,5,10,20,50) {
  python bench/loadgen.py --server 192.168.100.166 --admin admin:SENHA --devices $n --duration 600 --out results/E1_http_$n
  python bench/analyze.py results/E1_http_$n
}
```

A tabela `results/summary.csv` junta todas as rodadas (uma linha por pasta), pronta para as tabelas do artigo.

## Matriz dos experimentos

| # | Pergunta | Como |
|---|---|---|
| E1 | Latência e perda reais | `--protocol http` e `mqtt --qos 0/1`, sem TLS, `--devices 1 5 10 20 50`, `--rate 1`, 10 min, 3 repetições |
| E2 | Custo do token | API com `SENSORSYNC_ALLOW_LEGACY_INGEST=true`; mesma carga com token e com `--legacy` (formato 0.x, sem token), em ordem alternada. Compare o **tempo no servidor** (sem `seq` no legado não há ponta a ponta; a perda é por contagem). Obs.: o caminho legado também busca o projeto no banco |
| E3 | Custo do TLS no servidor | Igual ao E1 com `--tls`; repetir com `SENSORSYNC_TLS_KEY=rsa`; vazão máxima subindo `--rate` até a latência disparar ou aparecer perda |
| E4 | Custo do TLS nas placas | `Benchmark.ino` (biblioteca) em ESP32 e ESP8266, HTTP/MQTT, com e sem TLS, `REUSAR_CONEXAO` true/false; `serial_log.py`. Flash e RAM estática: saída da compilação |
| E5 | Recuperação | `loadgen.py` rodando enquanto derruba o Mosquitto, reinicia a API ou o Raspberry; perda e tempo sem gravar aparecem no `analyze.py` e no gráfico |
| E6 | O método de 2025 | Repetir o `curl`/`mosquitto_pub` do artigo anterior contra a mesma carga e comparar com o `loadgen.py` |

Cuidados: Raspberry com dissipador e temperatura anotada (`vcgencmd measure_temp`, `vcgencmd get_throttled`);
banco limpo e API reiniciada entre rodadas; descartar os primeiros segundos (`analyze.py --skip`, padrão 5 s);
guardar a pasta `results/` inteira e o commit usado.

## Placas (E4)

1. Na biblioteca (IDE Arduino), abra *Exemplos › SensorSync › Benchmark*, preencha Wi-Fi, servidor e token
   (crie um projeto com 5 variáveis), escolha `USAR_MQTT`, `USAR_TLS` e `REUSAR_CONEXAO` e grave.
2. Feche o Monitor Serial e rode `python bench/serial_log.py --port COM5 --out results/E4_esp32_https/board.csv`.
   Aperte o botão EN/RST da placa; o script para sozinho no fim (`# fim`).
3. Com `SENSORSYNC_METRICS=true` no servidor, o log da API também registra cada envio da placa (mesmo `seq`), para
   latência ponta a ponta das placas.
