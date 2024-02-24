# sensorsync - API REST para integração MQTT/REST de microcontroladores a Banco de Dados MySQL

Sumário:

[Subir a Stack para testes em ambiente Windows](#subir-a-stack-para-testes-em-ambiente-de-desenvolvimento-windows)

[Subir a Stack em Raspberry PI com Ubunbtu Server](#subir-a-stack-em-raspberry-pi-com-ubunbtu-server)



## Subir a Stack para testes em ambiente de desenvolvimento Windows

#### Mosquitto Broker

- Instalar moquitto broker conforme esta [documentação](https://cedalo.com/blog/how-to-install-mosquitto-mqtt-broker-on-windows/?utm_source=in_page&utm_medium=Cedalo&utm_campaign=publer). Não é necessário configurar autenticação para os testes, porém é necessário seguir os passos para configurar a variavel de ambiente do mosquitto.

- Abrir o prompt de comandos e iniciar o broker através do seguinte comando:

```
mosquitto -v
```

- Em uma nova janela do prompt de comando, fazer o subscribe em um tópico qualquer para testes:

```
mosquitto_sub -t NOME_DO_TOPICO
```

> :bulb: **Tip:** Fazendo dessa forma ele usará o localhost (IP local) e a porta 1883 como padrão. Para usar outras possibilidades, conulte esta [documentação](https://team-ethernet.github.io/guides/How%20to%20install%20and%20use%20Mosquitto%20for%20Windows.pdf).


- Para publicar um dado no tópico criado na etapa anterior, basta abrir uma nova janela no prompt e dar o seguinte comando:


```
mosquitto_pub -t NOME_DO_TOPICO -m "MENSAGEM" 
```
O segundo prompt aberto (subscribe) retornará a mensagem passada.

> :bulb: **Tip:** Se acontecer erro 'ao publicar a mensagem, é necessário permitir publicações anônima de acordo com esta [documentação](https://mosquitto.org/man/mosquitto-conf-5.html).


#### Banco de dados MSQL
Para subir o banco de dados é necessário primeiramente instalar o mysqlServer, conforme a seguinte documentação 


## Subir a Stack em Raspberry PI com Ubunbtu Server



- A desenvolver.