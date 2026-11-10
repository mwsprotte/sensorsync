# Atualizar o Raspberry do laboratório para a versão nova

Guia para trocar a versão antiga do SensorSync (0.x, a que roda hoje no laboratório) pela nova **sem perder o
banco e sem parar as placas que já estão gravadas**. Tudo foi ensaiado antes num Ubuntu com MySQL e Mosquitto de
verdade, montado como o laboratório (versão antiga rodando pelo crontab, `root` do MySQL com senha, dados criados pela
versão antiga). O resultado do ensaio está no fim deste guia.

> **Senhas e IPs** (Wi-Fi do laboratório, Raspberry, MySQL): no arquivo `ACESSO-LAB.md` na raiz do projeto, que
> fica só no seu PC (ignorado pelo Git, porque o repositório é público).

> **Tempo:** ~1 h no laboratório (a maior parte esperando). **Precisa de:** o PC com o projeto, acesso SSH ao
> Raspberry, a senha do usuário `ubuntu` do Raspberry. A senha do `root` do MySQL não precisa: o script lê da
> configuração da versão antiga.

## Sumário

1. [O que muda e o que continua igual](#1-o-que-muda-e-o-que-continua-igual)
2. [Antes de ir ao laboratório (no PC)](#2-antes-de-ir-ao-laboratório-no-pc)
3. [No laboratório, passo a passo](#3-no-laboratório-passo-a-passo)
4. [Conferir se deu certo](#4-conferir-se-deu-certo)
5. [Depois: período de transição e modo final](#5-depois-período-de-transição-e-modo-final)
6. [Se algo der errado: desfazer](#6-se-algo-der-errado-desfazer)
7. [Atualizar de novo no futuro](#7-atualizar-de-novo-no-futuro)
8. [Problemas comuns](#8-problemas-comuns)
9. [Referência: o que cada script faz](#9-referência-o-que-cada-script-faz)
10. [Resultado do ensaio](#10-resultado-do-ensaio)

---

## 1. O que muda e o que continua igual

| | Versão antiga | Versão nova (modo **transição**) |
|---|---|---|
| Banco | `sensor_sync_data`, acessado como `root` | O **mesmo banco**, migrado automaticamente; acessado por um usuário `sensorsync` só com acesso a ele |
| Usuários | Senha em texto puro | **Mesmo login e mesma senha**; a senha passa a ser guardada com BCrypt; todos viram administradores |
| Projetos e leituras | — | Tudo continua lá; cada projeto ganha um dono (o primeiro administrador) e dispositivos (sem token) |
| Navegador | `http://IP:8080` | `https://IP:8443` (o `http://IP:8080` redireciona sozinho). Aviso de certificado até instalar a CA (link na tela de login) |
| Placas antigas (HTTP) | `http://IP:8080/data/saveList` | **Continuam funcionando** (a 8080 aceita sem TLS só o envio das placas) |
| Placas antigas (MQTT) | porta 1883, tópico `sensorsync` | **Continuam funcionando** (1883 continua aberta na rede) |
| Placas novas | — | HTTPS 8443 ou MQTT com TLS 8883, com token (código gerado pela plataforma) |
| Como inicia | `java -jar` pelo crontab (`@reboot`) ou à mão | Serviços do systemd: certificados → Mosquitto → API, sozinhos no boot |
| Hora | Acertada à mão | Sincronizada pela rede (o Ubuntu já faz; `chrony` opcional para os experimentos) |

**O que você precisa saber antes:**

- A atualização é **reversível** (seção 6). O passo 2 faz backup do banco, do `.jar` antigo e das configurações.
- Depois que a versão nova roda, a versão antiga **ainda funciona** com o banco migrado (ela ignora as tabelas e
  colunas novas), **exceto o login** de quem já existia, porque a senha foi convertida para BCrypt. Por isso, para
  voltar de verdade, o `desfazer.sh` oferece restaurar o banco do backup.
- O IP do Raspberry vai dentro do certificado e do código das placas novas. Use o **IP fixo** do laboratório.

## 2. Antes de ir ao laboratório (no PC)

### 2.1 Gerar o `.jar` novo

No PowerShell:

```powershell
cd C:\Development\sensorsync\sensorsync
.\mvnw.cmd -DskipTests package
```

O arquivo sai em `C:\Development\sensorsync\sensorsync\target\sensorsync.jar` (~50 MB).
(No IntelliJ: painel *Maven* › `sensorsync` › *Lifecycle* › `package`, com *Skip Tests* ligado.)

### 2.2 Separar o que levar

Você vai copiar para o Raspberry só duas coisas: a pasta `deploy` (scripts e arquivos de configuração) e o `.jar`.
Leve num pendrive ou copie pela rede no laboratório (passo 3.2).

### 2.3 (Recomendado) Rodar o inventário antes

Se conseguir acessar o Raspberry antes (ou na primeira visita), rode só o passo 3.3 (inventário, não muda nada) e
guarde o arquivo que ele gera. Ele mostra a versão do Ubuntu, do Java, como a versão antiga é iniciada e o tamanho
do banco. Se algo fugir do esperado (ex.: Java antigo), dá para resolver antes do dia da atualização.

## 3. No laboratório, passo a passo

### 3.1 Entrar no Raspberry

Do PC, conectado à rede do laboratório (nome, senha e IP em `ACESSO-LAB.md`):

```powershell
ssh ubuntu@IP_DO_RASPBERRY
```

(Ou monitor e teclado direto no Raspberry.) Todos os comandos a seguir são no Raspberry.

### 3.2 Copiar os arquivos

Pela rede, **no PC** (outro PowerShell, não no SSH):

```powershell
ssh ubuntu@IP_DO_RASPBERRY "mkdir -p ~/sensorsync"
scp -r C:\Development\sensorsync\deploy ubuntu@IP_DO_RASPBERRY:~/sensorsync/
scp C:\Development\sensorsync\sensorsync\target\sensorsync.jar ubuntu@IP_DO_RASPBERRY:~/sensorsync/
```

Ou pelo pendrive, no Raspberry (o pendrive costuma aparecer como `/dev/sda1`; confira com `lsblk`):

```bash
sudo mkdir -p /mnt/usb && sudo mount /dev/sda1 /mnt/usb
mkdir -p ~/sensorsync && cp -r /mnt/usb/deploy /mnt/usb/sensorsync.jar ~/sensorsync/
sudo umount /mnt/usb
```

Depois, no Raspberry, **sempre** rode isto (arquivos vindos do Windows podem ter quebras de linha `\r` que o Linux
não aceita; o comando não estraga nada se já estiver certo):

```bash
cd ~/sensorsync
sed -i 's/\r$//' deploy/lab/*.sh deploy/tls/*.sh
```

### 3.3 Passo 1: inventário (não muda nada)

```bash
sudo bash deploy/lab/1-inventario.sh
```

O que conferir na saída:

- **Java:** precisa ser 21 ou mais novo. Se for mais antigo, veja [Problemas comuns › Java](#java-antigo-ou-ausente).
- **SensorSync antigo:** deve achar o `.jar` antigo, mostrar se está rodando e o que o inicia no boot (ex.: uma linha
  `@reboot` no crontab do usuário `ubuntu`).
- **Banco:** quantidade de usuários, projetos e leituras. **Anote** para comparar depois.
- **Portas:** 8080 (versão antiga), 1883 (Mosquitto), 3306 (MySQL).

O resultado também fica salvo em `~/inventario-sensorsync-*.txt`.

### 3.4 Passo 2: backup

```bash
sudo bash deploy/lab/2-backup.sh
```

Ele exporta o banco (confere se o arquivo está completo), copia o `.jar` antigo, a configuração do Mosquitto e os
crontabs para `/var/backups/sensorsync/DATA-HORA/`, e mostra quantas linhas tem cada tabela.

**Leve uma cópia do backup para o PC** antes de continuar (o próprio script mostra o comando). No Raspberry:

```bash
sudo chown -R ubuntu /var/backups/sensorsync
```

No PC:

```powershell
scp -r ubuntu@IP_DO_RASPBERRY:/var/backups/sensorsync C:\Development\backup-lab
```

### 3.5 Passo 3: instalar

```bash
sudo bash deploy/lab/3-instalar.sh --jar ~/sensorsync/sensorsync.jar --ip IP_DO_RASPBERRY
```

Ele explica cada etapa na tela. Ele **pergunta** antes de parar a versão antiga; responda `s`. No fim, espera a API
responder (no Pi 3 leva 1 a 3 minutos) e mostra o endereço e a impressão digital do certificado.

Opções úteis: `--chrony` (servidor de hora, para os experimentos), `--sem-tls` (sem HTTPS, tudo como antes). Todas
em `sudo bash deploy/lab/3-instalar.sh --help`.

## 4. Conferir se deu certo

1. **Navegador:** abra `http://IP_DO_RASPBERRY:8080`. Deve ir sozinho para `https://...:8443`. Aceite o aviso
   (ou instale a CA pelo link *"Não seguro" no navegador?* da tela de login).
2. **Login:** entre com um usuário que **já existia** na versão antiga, com a **senha de sempre**.
3. **Dados:** os projetos antigos aparecem com os gráficos e as leituras antigas.
4. **Placas antigas:** com uma placa antiga ligada, veja chegar leitura nova no dashboard em até 1 minuto.
5. **Contagem:** `sudo bash deploy/lab/1-inventario.sh` de novo: usuários, projetos e leituras ≥ o que você anotou.
6. **Reinício:** `sudo reboot`, espere ~3 minutos e repita 1 e 4. Prova que tudo sobe sozinho.

Log da API, se precisar: `sudo journalctl -u sensorsync -f` (Ctrl+C para sair).

## 5. Depois: período de transição e modo final

O modo **transição** deixa as placas antigas funcionando, mas sem segurança (sem token, sem TLS). Aos poucos:

1. Instale a CA nos PCs do laboratório (página *Certificado do servidor*, link na tela de login).
2. Para cada placa antiga: abra o projeto › aba *Código*, baixe o código novo (já com token e TLS), regrave a placa.
   Na aba *Dispositivos* aparece quando cada dispositivo enviou pela última vez.
3. Quando **todas** estiverem regravadas, feche a transição:

```bash
cd ~/sensorsync
sudo bash deploy/lab/3-instalar.sh --modo final
```

Isso desliga o formato antigo, faz a 8080 só redirecionar e deixa a 1883 só dentro do Raspberry. Placas antigas
param de ser aceitas. Para voltar à transição: `--modo transicao`.

## 6. Se algo der errado: desfazer

```bash
cd ~/sensorsync
sudo bash deploy/lab/desfazer.sh
```

Ele para a versão nova, restaura o Mosquitto e o crontab do backup mais recente e pergunta se quer restaurar o
banco (é preciso **digitar** `RESTAURAR`; Enter pula). Depois, `sudo reboot` e a versão antiga volta a subir
sozinha como antes.

- **Restaurar o banco** volta exatamente ao backup: o login antigo volta a funcionar, mas o que foi gravado depois do
  backup se perde (o script guarda uma cópia do banco atual antes, em `/var/backups/sensorsync/antes-de-desfazer-*`).
- **Não restaurar** mantém tudo o que foi gravado; a versão antiga funciona, mas os usuários antigos não conseguem
  entrar (senha convertida). Dá para criar um usuário novo pela versão antiga, se for só para consultar.

Para usar um backup específico: `sudo bash deploy/lab/desfazer.sh /var/backups/sensorsync/DATA-HORA`.

## 7. Atualizar de novo no futuro

Com a versão nova já instalada, para colocar um `.jar` mais novo:

```bash
sudo bash deploy/lab/2-backup.sh
sudo bash deploy/lab/3-instalar.sh --jar ~/sensorsync/sensorsync.jar
```

A configuração (`/etc/sensorsync/sensorsync.env`), os certificados e o modo são mantidos. A versão anterior fica em
`/opt/sensorsync/sensorsync.jar.anterior`.

## 8. Problemas comuns

### `$'\r': command not found` ao rodar um script

Os arquivos vieram do Windows com `\r`. Rode `sed -i 's/\r$//' deploy/lab/*.sh deploy/tls/*.sh` (passo 3.2).

### Java antigo ou ausente

`java -version` precisa mostrar 21 ou mais. No Ubuntu 23.10 (sem suporte), o `apt install` não funciona sem
ajustar as fontes; o jeito mais simples é o Java da Adoptium, baixado no PC
([adoptium.net](https://adoptium.net/temurin/releases/?os=linux&arch=aarch64&package=jre&version=21),
Linux, aarch64, JRE, `.tar.gz`) e copiado para o Raspberry:

```bash
sudo mkdir -p /opt/java && sudo tar xzf OpenJDK21U-jre_aarch64_linux_*.tar.gz -C /opt/java
sudo ln -sf /opt/java/jdk-21*-jre/bin/java /usr/local/bin/java
java -version
```

(Raspberry com sistema de 32 bits: `uname -m` mostra `armv7l`; baixe a versão `arm` em vez de `aarch64`.)

### `apt` dá erro no Ubuntu 23.10 (só se precisar instalar algo, ex.: `--chrony`)

O Ubuntu 23.10 saiu de suporte e os pacotes foram para outro servidor. Para voltar a instalar:

```bash
sudo sed -i 's|http://ports.ubuntu.com/ubuntu-ports|http://old-releases.ubuntu.com/ubuntu|g' \
    /etc/apt/sources.list /etc/apt/sources.list.d/*.sources 2>/dev/null
sudo apt update
```

Atualizar o Ubuntu para a 24.04 LTS resolve de vez, mas **não faça no mesmo dia** da atualização do SensorSync: uma
mudança grande de cada vez.

### A API não sobe

`sudo journalctl -u sensorsync -n 80 --no-pager`. Os mais comuns:

- `Access denied for user 'sensorsync'`: rode o `3-instalar.sh` de novo (ele refaz o usuário do MySQL).
- `Port 8080 already in use`: a versão antiga ainda está rodando: `sudo bash deploy/lab/1-inventario.sh` mostra o
  processo; pare com `sudo kill PID`.
- Certificado: `sudo systemctl status sensorsync-tls` e `ls -l /etc/sensorsync/tls`.

### O Mosquitto não sobe

`sudo journalctl -u mosquitto -n 30 --no-pager` e `sudo cat /var/log/mosquitto/mosquitto.log`. Se for "Address
already in use", outra configuração em `/etc/mosquitto/` ainda define a porta 1883; o instalador comenta essas
linhas (marcadas com `#[sensorsync]`), confira se sobrou alguma.

### Placa antiga parou de enviar

Confira o modo (`grep LEGACY /etc/sensorsync/sensorsync.env` deve dizer `true` na transição) e se a 1883 está aberta
na rede (`ss -ltn | grep 1883` deve mostrar `0.0.0.0:1883`).

## 9. Referência: o que cada script faz

| Script | Muda o sistema? | O que faz |
|---|---|---|
| `1-inventario.sh` | Não | Sistema, Java, MySQL, Mosquitto, versão antiga (jar, processo, início automático), portas, contagem do banco. Salva em `~/inventario-sensorsync-*.txt` |
| `2-backup.sh` | Não (só cria o backup) | `/var/backups/sensorsync/DATA-HORA/`: `banco.sql.gz` (só tabelas, importável em qualquer banco), `jar/`, `etc/` (Mosquitto, crontabs, `rc.local`, `/etc/sensorsync`), `info.txt`, `RESTAURAR.txt`, contagem de linhas |
| `3-instalar.sh` | Sim | Usuário `sensorsync`; `/opt/sensorsync` (programa), `/var/lib/sensorsync` (dados, métricas), `/etc/sensorsync` (configuração e certificados); usuário do MySQL; `sensorsync.env`; Mosquitto; serviços; para a versão antiga; sobe e testa. Pode rodar quantas vezes quiser |
| `desfazer.sh` | Sim | Para a versão nova, restaura Mosquitto/crontab e, se confirmado, o banco |
| `comum.sh` | — | Funções usadas pelos outros (não rode direto) |

Arquivos que o instalador cria ou altera:

| Arquivo | O que é |
|---|---|
| `/etc/sensorsync/sensorsync.env` | Configuração (senha do banco, IP, modo, TLS). Pode editar à mão e `sudo systemctl restart sensorsync` |
| `/etc/sensorsync/tls/` | CA e certificado do servidor (`ca.key` é a chave da CA: **não copie para lugar nenhum**) |
| `/etc/mosquitto/conf.d/sensorsync.conf` | Portas do Mosquitto (1883, 8883) |
| `/etc/mosquitto/mosquitto.conf` | Linhas antigas de porta comentadas com `#[sensorsync]` |
| `/etc/systemd/system/sensorsync*.service` | Serviços |
| crontab do usuário da versão antiga | Linha `@reboot` comentada com `#[sensorsync-desativado]` |

Comandos do dia a dia:

```bash
sudo systemctl status sensorsync          # está rodando?
sudo systemctl restart sensorsync         # reiniciar a API (ex.: depois de editar o .env)
sudo journalctl -u sensorsync -f          # log ao vivo
sudo bash deploy/lab/1-inventario.sh      # visão geral
```

## 10. Resultado do ensaio

Ambiente: Ubuntu 24.04, MySQL 8.0, Mosquitto 2.0, Java 21. Versão antiga = `main` (0.0.1-SNAPSHOT), iniciada pelo
crontab do usuário `ubuntu`, `root` do MySQL com a senha da configuração antiga. Dados criados pela versão antiga:
1 usuária (senha em texto puro), 1 projeto com 2 variáveis, 10 leituras (6 por HTTP, 4 por MQTT).

| Verificação | Resultado |
|---|---|
| Inventário acha jar, processo, linha do crontab e lê a senha do root do `.jar` antigo | ok |
| Backup: dump completo, contagem por tabela | ok |
| Instalação (2x seguidas, com arquivos em CRLF vindos do Windows) | ok, idempotente |
| Banco migrado: senha → BCrypt, usuária → ADMIN, projeto com dona, 2 dispositivos criados, 10 leituras e 2 variáveis intactas | ok |
| Usuária antiga entra com a senha antiga (HTTPS) | ok |
| Página pela 8080 redireciona para HTTPS | ok (301) |
| Placa antiga HTTP 8080 `/data/saveList` e MQTT 1883 (aspas simples), sem token | ok |
| Placa nova HTTPS 8443 e MQTT TLS 8883, com token | ok |
| Envio sem token pela 8443 | recusado (401) |
| Versão antiga rodando sobre o banco migrado | funciona, exceto login dos usuários migrados |
| `desfazer.sh` com restauração: contagens iguais ao backup, crontab e Mosquitto de volta, login antigo volta | ok |
| Reinstalar depois de desfazer; `--modo final`: 1883 só local, placa antiga recusada | ok |
| API sobe antes do Mosquitto e conecta sozinha quando ele volta | ok (tenta a cada 10 s) |

Não ensaiado (sem Raspberry): o tempo de subida no Pi 3, o `systemctl` de verdade (o ensaio iniciou os serviços na
mão, na mesma ordem) e o Ubuntu 23.10.
