# Spring Cloud Config: BOM, starter y quién lleva qué

Este documento usa los `pom.xml` y `application.yaml` reales de este repositorio.

Diagrama interactivo: [diagrams/spring-cloud-config.html](diagrams/spring-cloud-config.html)
(fuente editable: [diagrams/spring-cloud-config.json](diagrams/spring-cloud-config.json)).

---

## 1. El panorama en una línea

La configuración vive en un repo de GitHub (`config-data`). `config-server` la clona y la
sirve por HTTP en `:8888`. Cada microservicio **le pide su archivo** al arrancar.

```
config-data (GitHub) ──git clone──▶ config-server :8888 ◀──GET /product-service── product-service
                                                         ◀──GET /order-service──── order-service
                                                         ◀──GET /inventory-service─ inventory-service
```

---

## 2. Dos piezas distintas en el `pom.xml`

### El BOM: `spring-cloud-dependencies`

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

- Es un **catálogo de versiones** que Spring ya probó juntas.
- **No agrega ninguna librería** a tu proyecto. Solo fija versiones.
- Gracias a él, declarás las dependencias de Spring Cloud **sin `<version>`**.
- La versión del catálogo sale de `<spring-cloud.version>` en `<properties>` (hoy `2025.1.3`).

### El starter: `spring-cloud-starter-config`

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

- Es la librería que **sí** entra al classpath.
- Es la que entiende esta línea del `application.yaml`:

```yaml
spring:
  config:
    import: "optional:configserver:http://localhost:8888"
```

- Sin el starter, esa línea no hace que el servicio le pida la config al servidor.

> **Cómo recordarlo:** el BOM es la **lista de precios del proveedor**; el starter es
> **el material que comprás**. La lista sola no te trae ladrillos, y comprar sin lista te
> arriesga a que las piezas no encajen.

---

## 3. Quién lleva qué

| Servicio | BOM | Dependencia de Spring Cloud | Rol |
|---|---|---|---|
| `config-server` | ✅ 2025.1.3 | `spring-cloud-config-server` | **Servidor**: sirve la config |
| `product-service` | ✅ 2025.1.3 | `spring-cloud-starter-config` | Cliente |
| `order-service` | ✅ 2025.1.3 | `spring-cloud-starter-config` | Cliente |
| `inventory-service` | ✅ 2025.1.3 | `spring-cloud-starter-config` | Cliente |
| `discovery-server` | ⚠️ 2025.1.1 | `spring-cloud-starter-netflix-eureka-server` | Eureka (no es cliente de config) |
| `notification-service` | ❌ | — | Todavía no lee del Config Server |

**Regla:**

- ¿El servicio usa **algo** de Spring Cloud? → lleva el **BOM**.
- ¿El servicio **lee** su config del Config Server? → lleva el **starter** + `spring.config.import`.
- `config-server` **no** lleva el starter: él es el servidor, no un cliente.

---

## 4. El nombre del archivo importa

El Config Server busca `{spring.application.name}.yml` en `config-data`.

| `spring.application.name` | Archivo que busca |
|---|---|
| `product-service` | `product-service.yml` |
| `order-service` | `order-service.yml` |

Si el archivo se llama distinto (por ejemplo `product-services.yml`, con **s**), el servidor
no lo encuentra. Y como el import es `optional:`, el servicio **arranca igual, sin esa config
y sin error**. Por eso es un error silencioso.

---

## 5. Variables de entorno: ¿quién las resuelve?

Si en `config-data` escribís:

```yaml
host: ${MONGO_HOST:localhost}
```

El Config Server **no** reemplaza el `${...}`: lo manda tal cual. Es **el microservicio** el que
lo resuelve con **sus propias** variables de entorno. Por eso las variables van en el run de
`product-service`, no en el de `config-server`.

Hoy los yml de `config-data` tienen valores fijos, sin placeholders, así que las variables
de entorno de los servicios no se usan.

---

## 6. Checklist para un microservicio nuevo

1. `<spring-cloud.version>` en `<properties>`, **la misma** que los demás servicios.
2. El BOM en `<dependencyManagement>`.
3. `spring-cloud-starter-config` en `<dependencies>`, sin `<version>`.
4. En `application.yaml`: `spring.application.name` + `spring.config.import`.
5. En `config-data`: un archivo llamado **exactamente** `{spring.application.name}.yml`.
6. Commit y push de `config-data`: el servidor lee de GitHub, no de tu disco.

---

## 7. Pendientes detectados

- `notification-service` no tiene ni BOM ni starter.
- `discovery-server` usa `spring-cloud.version` 2025.1.1; el resto usa 2025.1.3.
