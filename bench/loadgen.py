#!/usr/bin/env python3
"""
SensorSync - gerador de carga para os experimentos (E1, E2, E3, E5, E6).

Simula N dispositivos enviando leituras por HTTP(S) ou MQTT(S), cada um com o proprio token,
numa taxa fixa. Cada mensagem leva "seq" e "ts" (hora de envio, epoch ms), que a API grava no
log de metricas (SENSORSYNC_METRICS=true). No fim baixa esse log e salva tudo numa pasta:

    <saida>/run.json     parametros da rodada
    <saida>/client.csv   uma linha por envio, visto pelo cliente
    <saida>/server.csv   log de metricas da API (GET /admin/metrics.csv)

Depois rode bench/analyze.py <saida> para latencia, perda e vazao.

Exemplos (no PC, com a API em 192.168.100.166):
    python bench/loadgen.py --server 192.168.100.166 --admin admin:SENHA --protocol http --devices 5 --duration 60 --out results/E1_http_5
    python bench/loadgen.py --server 192.168.100.166 --admin admin:SENHA --protocol mqtt --tls --ca config/tls/ca.crt --devices 20 --out results/E3_mqtts_20

Dependencias: pip install requests paho-mqtt
"""
import argparse
import csv
import json
import os
import random
import sys
import threading
import time
from datetime import datetime

try:
    import requests
except ImportError:
    sys.exit("Falta a biblioteca requests: pip install requests paho-mqtt")

MAX_DEVICES_PER_PROJECT = 50  # limite da API (ProjectService.MAX_DEVICES)


def now_ms():
    return time.time_ns() // 1_000_000


class Api:
    """Chamadas administrativas (criar/apagar projeto, metricas) com usuario e senha."""

    def __init__(self, base, user, password, verify):
        self.base = base.rstrip("/")
        self.s = requests.Session()
        self.s.auth = (user, password)
        # verify vai em cada chamada: REQUESTS_CA_BUNDLE no ambiente passaria por cima de Session.verify
        self.verify = verify

    @staticmethod
    def check_login(r):
        if r.status_code == 401:
            sys.exit("Login recusado (401): confira --admin usuario:senha (o seu login de administrador da plataforma)")
        if r.status_code == 403:
            sys.exit("Acesso negado (403): o usuario de --admin precisa ser administrador da plataforma")

    def create_project(self, name, devices, values):
        body = {"name": name, "devices": devices,
                "variables": [{"dataDesc": f"v{i}"} for i in range(values)]}
        r = self.s.post(f"{self.base}/project", json=body, timeout=30, verify=self.verify)
        self.check_login(r)
        r.raise_for_status()
        data = r.json()
        pid = data["project"]["id"]
        return pid, [(d["device"]["id"], d["token"], pid, d["device"]["deviceIndex"]) for d in data["devices"]]

    def delete_project(self, pid):
        self.s.delete(f"{self.base}/project/{pid}", timeout=60, verify=self.verify)

    def reset_metrics(self):
        r = self.s.delete(f"{self.base}/admin/metrics.csv", timeout=30, verify=self.verify)
        self.check_login(r)
        if r.status_code == 404:
            sys.exit("As metricas da API estao desligadas: suba a API com SENSORSYNC_METRICS=true")
        r.raise_for_status()

    def download_metrics(self, path):
        r = self.s.get(f"{self.base}/admin/metrics.csv", timeout=120, verify=self.verify)
        r.raise_for_status()
        with open(path, "wb") as f:
            f.write(r.content)


class Device(threading.Thread):
    """Um dispositivo simulado: envia `count` mensagens, uma a cada `period` segundos (laco fechado)."""

    def __init__(self, idx, device_id, token, args, start_at, period, rows, lock):
        super().__init__(daemon=True)
        self.idx, self.device_id, self.token = idx, device_id, token
        self.args, self.start_at, self.period = args, start_at, period
        self.rows, self.lock = rows, lock
        self.count = int(args.duration * args.rate)

    def payload(self, seq, ts, with_token):
        values = [round(random.uniform(0, 100), 2) for _ in range(self.args.values)]
        if self.args.legacy:
            # Formato da biblioteca 0.x: lista sem token (nao tem lugar para seq/ts)
            body = [{"projectID": self.project_id, "sensorIndex": self.device_index, "dataIndex": i, "data": v}
                    for i, v in enumerate(values)]
            return json.dumps(body, separators=(",", ":"))
        body = {"values": values, "seq": seq, "ts": ts}
        if with_token:
            body["token"] = self.token
        return json.dumps(body, separators=(",", ":"))

    def log(self, seq, sent, done, status, error=""):
        with self.lock:
            self.rows.append((self.idx, self.device_id, seq, sent, done, status, error))

    def wait_slot(self, k):
        delay = self.start_at + k * self.period - time.time()
        if delay > 0:
            time.sleep(delay)


class HttpDevice(Device):
    def run(self):
        a = self.args
        url = f"{a.base}/api/ingest"
        s = requests.Session()
        s.headers.update({"Content-Type": "application/json"})
        if not a.legacy:
            s.headers["Authorization"] = f"Bearer {self.token}"
        for k in range(self.count):
            self.wait_slot(k)
            seq, sent = k + 1, now_ms()
            try:
                r = s.post(url, data=self.payload(seq, sent, False), timeout=a.timeout, verify=a.verify)
                if not a.reuse:
                    s.close()
                self.log(seq, sent, now_ms(), "ok" if r.status_code == 200 else f"http_{r.status_code}")
            except requests.RequestException as e:
                self.log(seq, sent, now_ms(), "error", type(e).__name__)


class MqttDevice(Device):
    def run(self):
        import paho.mqtt.client as mqtt
        a = self.args
        c = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"bench-{os.getpid()}-{self.idx}")
        if a.tls:
            c.tls_set(ca_certs=a.ca)
        connected = threading.Event()
        c.on_connect = lambda cl, ud, flags, rc, props=None: connected.set()
        port = a.mqtt_port or (8883 if a.tls else 1883)
        t0 = time.time()
        try:
            c.connect(a.server, port, keepalive=60)
        except Exception as e:  # broker fora do ar, TLS recusado
            for k in range(self.count):
                self.log(k + 1, now_ms(), now_ms(), "error", f"connect:{type(e).__name__}")
            return
        c.loop_start()
        if not connected.wait(15):
            print(f"  dispositivo {self.idx}: sem CONNACK do broker", file=sys.stderr)
        self.connect_ms = (time.time() - t0) * 1000
        for k in range(self.count):
            self.wait_slot(k)
            seq, sent = k + 1, now_ms()
            info = c.publish(a.topic, self.payload(seq, sent, not a.legacy), qos=a.qos)
            if a.qos > 0:
                try:
                    info.wait_for_publish(timeout=a.timeout)
                except Exception as e:
                    self.log(seq, sent, now_ms(), "error", type(e).__name__)
                    continue
            ok = info.rc == mqtt.MQTT_ERR_SUCCESS and (a.qos == 0 or info.is_published())
            self.log(seq, sent, now_ms(), "ok" if ok else f"mqtt_rc_{info.rc}")
        time.sleep(1)  # deixa a ultima mensagem sair antes de desconectar
        c.loop_stop()
        c.disconnect()


def main():
    p = argparse.ArgumentParser(description="Gerador de carga do SensorSync")
    p.add_argument("--server", required=True, help="IP ou nome do servidor (o mesmo do certificado, com --tls)")
    p.add_argument("--admin", required=True, help="usuario:senha de um administrador da plataforma")
    p.add_argument("--protocol", choices=["http", "mqtt"], default="http")
    p.add_argument("--tls", action="store_true", help="HTTPS (8443) / MQTT com TLS (8883)")
    p.add_argument("--ca", help="certificado da CA do servidor (ca.crt) para validar o TLS")
    p.add_argument("--devices", type=int, default=1)
    p.add_argument("--rate", type=float, default=1.0, help="mensagens por segundo por dispositivo")
    p.add_argument("--duration", type=float, default=60, help="segundos de envio")
    p.add_argument("--values", type=int, default=5, help="valores por mensagem (1 a 20)")
    p.add_argument("--qos", type=int, choices=[0, 1], default=0)
    p.add_argument("--topic", default="sensorsync")
    p.add_argument("--http-port", type=int, help="porta da API (padrao 8443 com --tls, 8080 sem)")
    p.add_argument("--api-port", type=int, help="porta da API para chamadas administrativas (padrao = --http-port)")
    p.add_argument("--mqtt-port", type=int)
    p.add_argument("--no-reuse", dest="reuse", action="store_false", help="HTTP: nova conexao a cada envio")
    p.add_argument("--legacy", action="store_true",
                   help="E2: formato antigo, sem token (API com SENSORSYNC_ALLOW_LEGACY_INGEST=true); "
                        "a analise compara so contagem e tempo no servidor")
    p.add_argument("--timeout", type=float, default=10)
    p.add_argument("--keep", action="store_true", help="nao apagar o projeto de teste no fim")
    p.add_argument("--out", required=True, help="pasta de saida da rodada")
    a = p.parse_args()

    if sys.platform == "win32" and sys.version_info < (3, 13):
        print("ATENCAO: no Windows, Python < 3.13 tem relogio de ~15 ms de resolucao; use 3.13+ para a latencia ponta a ponta")
    if not 1 <= a.values <= 20:
        sys.exit("--values deve ser de 1 a 20")
    if a.tls and not a.ca:
        sys.exit("Com --tls informe --ca (ex.: config/tls/ca.crt; baixe em http://SERVIDOR:8080/ca.crt)")
    a.verify = a.ca if a.tls else False
    scheme, port = ("https", a.http_port or 8443) if a.tls else ("http", a.http_port or 8080)
    a.base = f"{scheme}://{a.server}:{port}"
    api_base = f"{scheme}://{a.server}:{a.api_port or port}"
    user, _, password = a.admin.partition(":")
    api = Api(api_base, user, password, a.verify)

    os.makedirs(a.out, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")

    # Projetos de teste (no maximo 50 dispositivos por projeto)
    projects, creds = [], []
    remaining, n = a.devices, 0
    while remaining > 0:
        k = min(remaining, MAX_DEVICES_PER_PROJECT)
        pid, devs = api.create_project(f"bench-{stamp}-{n}", k, a.values)
        projects.append(pid)
        creds.extend(devs)
        remaining -= k
        n += 1
    print(f"Projetos de teste: {projects} ({a.devices} dispositivos)")

    api.reset_metrics()
    rows, lock = [], threading.Lock()
    period = 1.0 / a.rate
    start_at = time.time() + 2.0
    cls = HttpDevice if a.protocol == "http" else MqttDevice
    # Dispositivos defasados dentro do periodo, para nao enviarem todos no mesmo instante
    threads = [cls(i, c[0], c[1], a, start_at + period * i / a.devices, period, rows, lock)
               for i, c in enumerate(creds)]
    for t, c in zip(threads, creds):
        t.project_id, t.device_index = c[2], c[3]
    print(f"Enviando: {a.protocol}{' TLS' if a.tls else ''}, {a.devices} disp. x {a.rate}/s por {a.duration:.0f} s "
          f"({len(threads) * threads[0].count} mensagens)...")
    started = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - started
    time.sleep(3)  # tempo para a API gravar o que ainda estava em transito

    rows.sort(key=lambda r: (r[0], r[2]))
    with open(os.path.join(a.out, "client.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["device_idx", "device_id", "seq", "sent_ms", "done_ms", "status", "error"])
        w.writerows(rows)
    api.download_metrics(os.path.join(a.out, "server.csv"))

    run = {k: v for k, v in vars(a).items() if k not in ("admin", "verify")}
    run.update(started=stamp, elapsed_s=round(elapsed, 3), projects=projects, messages=len(rows),
               client_ok=sum(1 for r in rows if r[5] == "ok"))
    if a.protocol == "mqtt":
        run["mqtt_connect_ms"] = [round(getattr(t, "connect_ms", -1), 1) for t in threads]
    with open(os.path.join(a.out, "run.json"), "w") as f:
        json.dump(run, f, indent=2)

    if not a.keep:
        for pid in projects:
            api.delete_project(pid)
    print(f"Pronto em {elapsed:.1f} s: {run['client_ok']}/{len(rows)} enviados com sucesso. Resultados em {a.out}")
    if a.legacy and rows and all(r[5] == "http_401" for r in rows):
        print("ATENCAO: tudo recusado (401). Suba a API com SENSORSYNC_ALLOW_LEGACY_INGEST=true para o modo --legacy")
    print(f"Analise: python bench/analyze.py {a.out}")


if __name__ == "__main__":
    main()
