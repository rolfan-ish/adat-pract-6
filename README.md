# Practica 6 adat

## Configuración de exist
Se necesita la última imagen de existdb
```shell
sudo docker pull existdb/existdb:latest
sudo docker run -it -d -p 8080:8080 -p 8443:8443 --name exist existdb/existdb:latest
```
Tendras que cargar los archivos `.xml` encontrados `src/main/resources` a existdb en la base del repositorio (`/db/`)

## Ejecutar proyecto
Ejecuta el siguiente comando en el root del proyecto
```shell
mvn exec:java -Dexec.mainClass=io.gitlab.rolfan.Main
```