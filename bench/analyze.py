#!/usr/bin/env python3
"""
SensorSync - analise de uma rodada do gerador de carga (bench/loadgen.py).

    python bench/analyze.py results/E1_http_5 [--skip 5]

Le client.csv e server.csv da pasta e calcula, por mensagem identificada por (device_id, seq):
  - latencia ponta a ponta  = committed_ms (servidor) - ts (cliente, no envio)
                              -> exige relogios sincronizados (mesmo PC, ou chrony entre PC e Raspberry)
  - tempo no servidor       = server_us (chegada na API ate o commit, relogio monotonico)
  - tempo de resposta (RTT) = done - sent no cliente (HTTP: inclui o commit; MQTT QoS 0: so a publicacao)
  - perda                   = enviadas pelo cliente que nao aparecem gravadas no servidor
  - duplicadas, fora de ordem e vazao

Grava <pasta>/summary.json e acrescenta uma linha em <pasta>/../summary.csv (tabela de todas as rodadas).
Se o matplotlib estiver instalado, salva <pasta>/latency.png (CDF e serie temporal).
"""
import argparse
import csv
import json
import math
import os
import sys


def pct(values, p):
    """Percentil com interpolacao linear (igual ao numpy.percentile padrao)."""
    if not values:
        return float("nan")
    v = sorted(values)
    k = (len(v) - 1) * p / 100
    f, c = math.floor(k), math.ceil(k)
    return v[f] if f == c else v[f] + (v[c] - v[f]) * (k - f)


def stats(values):
    if not values:
        return {"n": 0}
    mean = sum(values) / len(values)
    sd = math.sqrt(sum((x - mean) ** 2 for x in values) / (len(values) - 1)) if len(values) > 1 else 0.0
    return {"n": len(values), "mean": mean, "sd": sd, "min": min(values), "p50": pct(values, 50),
            "p95": pct(values, 95), "p99": pct(values, 99), "max": max(values)}


def read_csv(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def num(s):
    return int(s) if s not in ("", None) else None


def main():
    p = argparse.ArgumentParser()
    p.add_argument("run", help="pasta da rodada (com client.csv, server.csv e run.json)")
    p.add_argument("--skip", type=float, default=5, help="segundos iniciais descartados (aquecimento)")
    a = p.parse_args()

    run = json.load(open(os.path.join(a.run, "run.json")))
    client = read_csv(os.path.join(a.run, "client.csv"))
    server = read_csv(os.path.join(a.run, "server.csv"))
    if not client:
        sys.exit("client.csv vazio")

    t_start = min(int(r["sent_ms"]) for r in client) + a.skip * 1000
    client = [r for r in client if int(r["sent_ms"]) >= t_start]
    sent_keys = {(int(r["device_id"]), int(r["seq"])) for r in client}
    sent_ts = {(int(r["device_id"]), int(r["seq"])): int(r["sent_ms"]) for r in client}

    saved = {}
    dup = 0
    other_status = {}
    for r in server:
        if r["status"] != "saved":
            other_status[r["status"]] = other_status.get(r["status"], 0) + 1
            continue
        key = (num(r["device_id"]), num(r["seq"]))
        if key not in sent_keys:
            continue  # aquecimento ou outra rodada
        if key in saved:
            dup += 1
            continue
        saved[key] = r

    e2e = [float(r["committed_ms"]) - int(r["client_ts_ms"]) for r in saved.values() if r["client_ts_ms"]]
    srv = [int(r["server_us"]) / 1000 for r in saved.values()]
    rtt = [int(r["done_ms"]) - int(r["sent_ms"]) for r in client if r["status"] == "ok"]

    # Fora de ordem: por dispositivo, seq gravado menor que um seq gravado antes
    by_dev = {}
    for r in sorted(saved.values(), key=lambda r: (float(r["committed_ms"]), float(r["received_ms"]))):
        by_dev.setdefault(num(r["device_id"]), []).append(num(r["seq"]))
    out_of_order = 0
    for seqs in by_dev.values():
        top = -1
        for s in seqs:
            if s < top:
                out_of_order += 1
            top = max(top, s)

    lost = len(sent_keys) - len(saved)
    if saved:
        commits = [float(r["committed_ms"]) for r in saved.values()]
        span = max((max(commits) - min(commits)) / 1000, 1e-9)
        throughput = len(saved) / span
    else:
        throughput = 0.0
    client_errors = {}
    for r in client:
        if r["status"] != "ok":
            client_errors[r["status"]] = client_errors.get(r["status"], 0) + 1

    summary = {
        "run": os.path.basename(os.path.normpath(a.run)),
        "protocol": run["protocol"] + ("s" if run.get("tls") else ""),
        "qos": run.get("qos") if run["protocol"] == "mqtt" else "",
        "devices": run["devices"], "rate_per_device": run["rate"], "values": run["values"],
        "http_reuse": run.get("reuse") if run["protocol"] == "http" else "",
        "sent": len(sent_keys), "saved": len(saved), "lost": lost,
        "loss_pct": 100 * lost / len(sent_keys) if sent_keys else float("nan"),
        "duplicates": dup, "out_of_order": out_of_order,
        "throughput_msg_s": throughput,
        "e2e_ms": stats(e2e), "server_ms": stats(srv), "client_rtt_ms": stats(rtt),
        "client_errors": client_errors, "server_rejections": other_status,
    }
    with open(os.path.join(a.run, "summary.json"), "w") as f:
        json.dump(summary, f, indent=2)

    def line(name, s):
        if s.get("n", 0) == 0:
            return f"  {name:<22} sem dados"
        return (f"  {name:<22} n={s['n']:<6} media={s['mean']:8.2f}  p50={s['p50']:8.2f}  p95={s['p95']:8.2f}  "
                f"p99={s['p99']:8.2f}  max={s['max']:8.2f} ms")

    print(f"Rodada {summary['run']}: {summary['protocol']}{' QoS ' + str(summary['qos']) if summary['qos'] != '' else ''}, "
          f"{summary['devices']} disp. x {summary['rate_per_device']}/s, {summary['values']} valores")
    print(f"  enviadas={summary['sent']}  gravadas={summary['saved']}  perdidas={lost} ({summary['loss_pct']:.3f}%)  "
          f"duplicadas={dup}  fora de ordem={out_of_order}  vazao={throughput:.1f} msg/s")
    print(line("ponta a ponta", summary["e2e_ms"]))
    print(line("no servidor", summary["server_ms"]))
    print(line("RTT no cliente", summary["client_rtt_ms"]))
    if client_errors:
        print(f"  erros no cliente: {client_errors}")
    if other_status:
        print(f"  recusadas no servidor: {other_status}")
    if e2e and min(e2e) < 0:
        print(f"  ATENCAO: {sum(1 for x in e2e if x < 0)} latencias negativas (min {min(e2e):.2f} ms) -> relogios do "
              "cliente e do servidor fora de sincronia (chrony) ou cliente com relogio de baixa resolucao")

    # Tabela acumulada de rodadas (uma linha por pasta) para montar as tabelas do artigo
    table = os.path.join(os.path.dirname(os.path.normpath(a.run)) or ".", "summary.csv")
    flat = {k: v for k, v in summary.items() if not isinstance(v, dict)}
    for group in ("e2e_ms", "server_ms", "client_rtt_ms"):
        for k in ("n", "mean", "sd", "p50", "p95", "p99", "max"):
            flat[f"{group}_{k}"] = summary[group].get(k, "")
    rows = []
    if os.path.exists(table):
        rows = [r for r in read_csv(table) if r.get("run") != flat["run"]]
    rows.append({k: (f"{v:.3f}" if isinstance(v, float) else v) for k, v in flat.items()})
    with open(table, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(flat.keys()))
        w.writeheader()
        w.writerows(rows)
    print(f"  resumo: {os.path.join(a.run, 'summary.json')} | tabela: {table}")

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        return
    if not e2e:
        return
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 3.6))
    v = sorted(e2e)
    ax1.plot(v, [(i + 1) / len(v) for i in range(len(v))])
    ax1.set_xlabel("latencia ponta a ponta (ms)")
    ax1.set_ylabel("fracao das mensagens")
    ax1.grid(alpha=0.3)
    pts = sorted((float(r["committed_ms"]), float(r["committed_ms"]) - int(r["client_ts_ms"]))
                 for r in saved.values() if r["client_ts_ms"])
    t0 = pts[0][0]
    ax2.plot([(t - t0) / 1000 for t, _ in pts], [l for _, l in pts], ".", markersize=2)
    ax2.set_xlabel("tempo (s)")
    ax2.set_ylabel("latencia (ms)")
    ax2.grid(alpha=0.3)
    fig.suptitle(f"{summary['protocol']} - {summary['devices']} disp. x {summary['rate_per_device']}/s")
    fig.tight_layout()
    fig.savefig(os.path.join(a.run, "latency.png"), dpi=150)


if __name__ == "__main__":
    main()
