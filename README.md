# Microservices Ecommerce

Proyecto de ecommerce basado en microservicios con Spring Boot.

## Servicios

- `discovery-server`: servidor Eureka para descubrimiento de servicios.
- `product-service`: servicio de productos con MongoDB y Docker Compose local.
- `inventory-service`: servicio de inventario.
- `order-service`: servicio de ordenes.
- `notification-service`: servicio de notificaciones.

## Requisitos

- Java 21
- Maven Wrapper incluido en cada servicio
- Docker, para levantar las dependencias del `product-service`

## Ejecucion local

Cada servicio se ejecuta desde su propia carpeta:

```bash
cd discovery-server
./mvnw spring-boot:run
```

Para `product-service`, primero puedes levantar MongoDB con Docker Compose:

```bash
cd product-service
docker compose up -d mongodb
./mvnw spring-boot:run
```
