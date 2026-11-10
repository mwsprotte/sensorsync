#!/usr/bin/env python3
"""
SensorSync - captura a saida CSV do exemplo Benchmark.ino (biblioteca SensorSync) pela serial.

    python bench/serial_log.py --port COM5 --out results/E4_esp32_https/board.csv

Mostra as linhas na tela, grava o CSV (cabecalho + uma linha por envio) e os comentarios "# ..." num
arquivo .log ao lado. Para quando a placa imprime "# fim" ou com Ctrl+C.
Feche o Monitor Serial da IDE Arduino antes (a porta so pode ser aberta por um programa).

Dependencia: pip install pyserial
"""
import argparse
import os
import sys

try:
    import serial
except ImportError:
    sys.exit("Falta a biblioteca pyserial: pip install pyserial")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--port", required=True, help="porta serial (ex.: COM5 no Windows, /dev/ttyUSB0 no Linux)")
    p.add_argument("--baud", type=int, default=115200)
    p.add_argument("--out", required=True, help="arquivo CSV de saida")
    a = p.parse_args()

    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    log_path = os.path.splitext(a.out)[0] + ".log"
    header_seen = False
    with serial.Serial(a.port, a.baud, timeout=1) as port, open(a.out, "w", newline="") as out, \
            open(log_path, "w") as log:
        print(f"Lendo {a.port} a {a.baud}. Aperte o botao EN/RST da placa para comecar (Ctrl+C para parar).")
        try:
            while True:
                raw = port.readline()
                if not raw:
                    continue
                line = raw.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                print(line)
                if line.startswith("#"):
                    log.write(line + "\n")
                    log.flush()
                    if line.startswith("# fim"):
                        break
                elif line.startswith("seq,"):
                    if not header_seen:
                        out.write(line + "\n")
                        header_seen = True
                elif header_seen and line[0].isdigit():
                    out.write(line + "\n")
                    out.flush()
                else:
                    log.write(line + "\n")
        except KeyboardInterrupt:
            pass
    print(f"CSV: {a.out} | comentarios: {log_path}")


if __name__ == "__main__":
    main()
