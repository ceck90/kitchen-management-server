# Deploy Docker

Questa guida descrive come compilare l'immagine Docker di Kitchen Management Server, configurarla senza includere segreti nell'immagine e collegarla agli altri servizi Music FestOn (es. PrinterServer, che consuma questo servizio come `KITCHEN_MGMT_SERVER_URL`).

## Prerequisiti

- Docker Engine e Docker Compose v2 installati e avviati.
- MongoDB e fornito localmente da `docker/docker-compose.yml`. PostgreSQL e invece una dipendenza esterna: questo servizio non lo avvia ne lo gestisce, si limita a connettercisi tramite le variabili `POSTGRES_*` in `.env` (deve essere gia in esecuzione e raggiungibile).
- Client `psql` installato in locale, necessario solo per eseguire `make migrate` contro il Postgres esterno.
- Per l'integrazione: rete Docker condivisa con gli altri servizi Music FestOn (es. PrinterServer) che devono raggiungere questo container.

## Configurazione esterna

Le variabili applicative risiedono nel file esterno `.env`, escluso dal repository (`.gitignore`) e dall'immagine (`.dockerignore`). Va creato a partire da `.env.sample`:

```bash
cp .env.sample .env
```

oppure con:

```bash
make check-env
```

Variabili principali definite in `.env.sample`:

```dotenv
# Network
BB_NETWORK=mfo-network

# MongoDB
MONGO_HOST_PORT=27017
MONGO_ROOT_USERNAME=root
MONGO_ROOT_PASSWORD=example

# PostgreSQL (dipendenza esterna: non e' avviato da questo stack, va gia'
# raggiungibile all'host/porta indicati)
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_USER=root
POSTGRES_PASSWORD=example
POSTGRES_DB=bear

# Kitchen Management Server
SERVER_HOST_PORT=8080
```

`POSTGRES_HOST`/`POSTGRES_PORT`/`POSTGRES_DB` puntano di default all'hostname `postgres` usato nella guida di PrinterServer/GSG: in un ambiente reale vanno impostati con l'host effettivo del Postgres esterno (es. `gsg-postgres`, se raggiungibile sulla stessa rete Docker, oppure un host/IP esterno).

Il container `km-server` (definito in `docker/docker-compose.yml`) compone da queste variabili le proprieta lette da `application.yaml` tramite placeholder `${VAR:default}`:

| Variabile           | Proprieta Spring                                          | Default                                 |
|-----------------------|-------------------------------------------------------------|-------------------------------------------|
| `SERVER_PORT`       | `server.port`                                             | `8080`                                  |
| `POSTGRES_URL`      | `spring.data.postgres.url`                                | `jdbc:pgsql://localhost:5432/bear`      |
| `POSTGRES_JDBC_URL` | `spring.data.postgres-ack.jdbc-url`                       | `jdbc:postgresql://localhost:5432/bear` |
| `POSTGRES_USER`     | `spring.data.postgres.user` / `postgres-ack.username`     | `root`                                  |
| `POSTGRES_PASSWORD` | `spring.data.postgres.password` / `postgres-ack.password` | `example`                               |
| `MONGO_DATABASE`    | `spring.data.mongodb.database`                            | `kitchenmgmt`                           |
| `MONGO_URI`         | `spring.data.mongodb.uri`                                 | `mongodb://root:example@mongo:27017/`   |

Il nome `mongo` e un nome di servizio Compose: funziona perche `km-server` e `mongo` condividono la stessa rete Docker (`mfo-network`, esterna e creata da `make up-infra`). `POSTGRES_HOST` invece deve essere l'hostname/IP effettivo del Postgres esterno, raggiungibile dalla rete `mfo-network` se e un container su quella rete, o dall'host Docker altrimenti. Non usare `127.0.0.1`/`localhost` per raggiungere un altro container: dall'interno di `km-server` indicherebbe il container stesso.

## Compilare l'immagine

Dalla root del progetto:

```bash
docker build -t musicfeston/kitchen-management-server:latest .
```

oppure, tramite il Makefile (usa `IMAGE_NAME`/`IMAGE_TAG` definiti in testa al file):

```bash
make build-image
```

Il build e multi-stage (`Dockerfile`): uno stage Maven (`maven:3.8.6-eclipse-temurin-11`) compila e impacchetta il jar, uno stage runtime (`eclipse-temurin:11-jre-jammy`) esegue l'applicazione con un utente non privilegiato. L'immagine include solo il jar applicativo: non contiene `.env`, credenziali o dati di Mongo (che restano in un volume esterno gestito da `docker/docker-compose.yml`) ne tantomeno dati di Postgres, che e interamente esterno a questo servizio.

## Trasferire l'immagine su un altro server

Per esportare un'immagine Docker usare `docker save`, non `docker import`. Il comando `save` conserva tag, `ENTRYPOINT`, `CMD` e tutti i metadati necessari al funzionamento dell'immagine. `import` crea invece una nuova immagine da un filesystem e perderebbe tali informazioni.

Assegnare un tag immutabile all'immagine appena compilata e creare un archivio compresso:

```bash
docker tag musicfeston/kitchen-management-server:latest musicfeston/kitchen-management-server:2.0.0
mkdir -p releases
docker save musicfeston/kitchen-management-server:2.0.0 | gzip > releases/kitchen-management-server_2.0.0.tar.gz
shasum -a 256 releases/kitchen-management-server_2.0.0.tar.gz > releases/kitchen-management-server_2.0.0.tar.gz.sha256
```

oppure, in un unico comando tramite il Makefile:

```bash
make release-image
```

`release-image` esegue `build-image`, poi tagga l'immagine con la versione letta da `application.yaml` (`application.version`, es. `2.0.0`) e genera `releases/kitchen-management-server_<versione>.tar.gz` + il relativo `.sha256`. La cartella `releases/` e esclusa dal repository (`.gitignore`): i file vanno trasferiti manualmente al server di destinazione.

Trasferire entrambi i file al server destinazione. Dopo aver verificato il checksum, caricare l'immagine:

```bash
shasum -a 256 -c kitchen-management-server_2.0.0.tar.gz.sha256
gzip -dc kitchen-management-server_2.0.0.tar.gz | docker load
docker image inspect musicfeston/kitchen-management-server:2.0.0
```

Nel Compose del server destinazione impostare il tag esportato, senza la sezione `build`:

```yaml
services:
  km-server:
    image: musicfeston/kitchen-management-server:2.0.0
```

## Avviare il servizio

Lo stack locale (app + MongoDB) e definito in `docker/docker-compose.yml` e orchestrato dal `Makefile`. PostgreSQL non fa parte di questo stack: deve essere gia raggiungibile all'host/porta indicati da `POSTGRES_HOST`/`POSTGRES_PORT` in `.env`.

```bash
make up-infra
docker compose --env-file .env -f docker/docker-compose.yml logs -f km-server
```

`up-infra` crea la rete esterna `mfo-network`, copia `.env.sample` in `.env` se assente e avvia `km-server` e `mongo` con `--build`, ricompilando l'immagine se il codice e cambiato. L'interfaccia HTTP sara disponibile su `http://localhost:8080` (o sulla porta indicata da `SERVER_HOST_PORT`).

Se il Postgres esterno richiede l'inizializzazione dello schema, eseguire le migrazioni con `psql` locale prima o dopo l'avvio dell'app:

```bash
make migrate
```

`migrate` legge `POSTGRES_HOST`/`POSTGRES_PORT`/`POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB` da `.env` ed esegue con `psql` (richiesto in locale) tutti gli script in `src/main/resources/postgres/*.sql` contro quell'istanza.

Per rieseguire solo la build dell'immagine dopo una modifica al codice:

```bash
docker compose --env-file .env -f docker/docker-compose.yml build km-server
docker compose --env-file .env -f docker/docker-compose.yml up -d km-server
```

Il container si arresta senza cancellare i dati di Mongo (PostgreSQL, essendo esterno, non e comunque toccato):

```bash
make down
```

`make down` esegue `docker compose down -v` (rimuove i volumi con dati creati dal compose) e rimuove la rete `mfo-network`. Se si vogliono preservare i volumi tra un riavvio e l'altro, usare direttamente `docker compose --env-file .env -f docker/docker-compose.yml down` (senza `-v`).

## Integrazione con altri servizi (es. PrinterServer)

Nel file Compose dell'ambiente completo, `km-server` condivide la rete `mfo-network` con gli altri servizi Music FestOn che lo consumano (es. PrinterServer, che legge `KITCHEN_MGMT_SERVER_URL`). Questo repository usa gia `mfo-network` come nome della rete esterna (`BB_NETWORK` in `.env.sample`), quindi nessun rename e necessario quando si aggrega lo stack finale.

Esempio minimo, presupponendo `km-server` e PrinterServer sulla stessa rete:

```yaml
services:
  km-server:
    image: musicfeston/kitchen-management-server:2.0.0
    networks:
      - mfo-network

  printer-server:
    image: musicfeston/printer-server:1.0.0-bun1.4.2
    environment:
      KITCHEN_MGMT_SERVER_URL: http://km-server:8080
    networks:
      - mfo-network

networks:
  mfo-network:
    external: true
```

Non pubblicare necessariamente la porta `8080` sull'host se l'accesso avviene solo da altri container sulla stessa rete Docker: in quel caso e sufficiente `EXPOSE 8080` (gia presente nel `Dockerfile`), senza mappare la porta con `ports:`.

## Comandi diagnostici

```bash
docker compose --env-file .env -f docker/docker-compose.yml ps
docker compose --env-file .env -f docker/docker-compose.yml logs --tail=200 km-server
docker exec -it km_server sh
docker network inspect mfo-network
```

Un avvio corretto mostra nei log il banner Spring Boot e `Tomcat started on port(s): 8080`. Un errore di connessione a MongoDB indica normalmente che il servizio non e ancora pronto (dipendenza di avvio, non di readiness) o che le credenziali in `.env` non corrispondono. Un errore di connessione a PostgreSQL (es. `Connection refused`) indica quasi sempre che `POSTGRES_HOST`/`POSTGRES_PORT` in `.env` non puntano a un'istanza effettivamente raggiungibile da `km-server` sulla rete `mfo-network` o dall'host Docker.
