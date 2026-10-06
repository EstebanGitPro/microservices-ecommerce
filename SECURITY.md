# Seguridad de dependencias

Registro de las vulnerabilidades (CVE) revisadas en las dependencias de los microservicios.
Sirve para no volver a investigar una alerta que ya fue analizada.

Última revisión: 2026-10-06 · Spring Boot 4.0.8 · Spring Cloud 2025.1.3

## Cómo leer este documento

- **Corregidas**: la versión vulnerable se reemplazó con un override en el `pom.xml`.
- **Mitigadas**: la versión sigue igual, pero una configuración evita el problema.
- **Aceptadas**: la versión sigue igual porque el código vulnerable no se usa y el fix exige un salto de versión menor que Spring todavía no adoptó.

Cada CVE aceptada tiene una **condición de revisión**: si esa condición se cumple, la CVE deja de ser inofensiva y hay que subir la versión.

## Política de versiones

1. Primero se sube la versión de Spring Boot o Spring Cloud, si existe una que traiga el fix.
2. Si no existe, se fija la versión con la propiedad del BOM de Boot (por ejemplo `<tomcat.version>`).
3. Si la librería la gestiona un BOM importado (`<scope>import</scope>`), se fija en `<dependencyManagement>`, porque sus propiedades no se pueden sobrescribir.
4. Nunca se agrega una dependencia directa para cambiar la versión de una dependencia transitiva.
5. Se prefieren saltos de **patch** (`11.0.25` → `11.0.26`). Un salto de versión **menor** solo se hace si la vulnerabilidad es explotable en este proyecto.
6. Se usa el último patch de la línea, no la versión mínima que corrige la CVE.

Todos los overrides tienen un comentario `remove once Spring Boot manages...`. Al subir Spring Boot, hay que revisarlos y quitar los que ya no hagan falta.

## Corregidas

| Librería | Versión anterior | Versión actual | Servicios | Mecanismo |
|---|---|---|---|---|
| Spring Framework (CVE-2026-59282) | 7.0.7 | 7.0.9 | todos | Spring Boot 4.0.6 → 4.0.8 |
| Tomcat | 11.0.24 | 11.0.26 | todos | `tomcat.version` |
| Jackson 3 (`tools.jackson`) | 3.1.5 | 3.1.7 | todos | `jackson-bom.version` |
| Jackson 2 | 2.21.5 | 2.21.7 | discovery-server | `jackson-2-bom.version` |
| httpcore5 | 5.3.6 | 5.4.3 | todos excepto notification-service | `httpcore5.version` |
| httpclient5 | 5.5.2 | 5.6.4 | todos excepto notification-service | `httpclient5.version` |
| Netty (CVE-2026-100655) | 4.2.17.Final | 4.2.19.Final | order-service, notification-service | `netty.version` |
| Bouncy Castle (CVE-2026-71891) | 1.81 / 1.85.2 | 1.86 | todos excepto notification-service | Spring Cloud 2025.1.3 + `<dependencyManagement>` |
| FreeMarker | 2.3.34 | 2.3.35 | discovery-server | `freemarker.version` |
| httpclient 4.x | 4.5.3 | 4.5.14 | discovery-server | `<dependencyManagement>` |

## Mitigadas

### CVE-2026-18710 — MongoDB Java Driver escribe credenciales del proxy en el log

- **Servicio**: product-service (driver 5.6.5, corregido en 5.9.2)
- **Problema**: al crear el cliente, el driver escribe en el log el usuario y la contraseña del proxy SOCKS5.
- **Por qué no aplica hoy**: `MongoConfig` no configura ningún proxy.
- **Mitigación**: `logging.level.org.mongodb.driver.client: WARN` en `config-data/product-service.yml`.
- **Revisar si**: se quita esa configuración de logging antes de subir el driver a 5.9.2 o superior.

## Aceptadas

### CVE-2026-88033 — MongoDB Java Driver, inyección de consultas en GridFS

- **Servicio**: product-service (driver 5.6.5, corregido en 5.11.1)
- **Por qué no aplica**: el proyecto no usa GridFS.
- **Por qué no se sube**: la línea 5.6.x no tiene backport, y Spring Data MongoDB 5.0.7 está probado con el driver 5.6.5.
- **Revisar si**: se empieza a usar `GridFSBucket` o `GridFsTemplate`.

### CVE-2026-19880 y CVE-2026-104721 — Logback, path traversal en SiftingAppender

- **Servicios**: todos (logback-classic 1.5.38, corregido en 1.6.5)
- **Por qué no aplica**: no hay ningún `logback.xml` ni `logback-spring.xml`, y ningún `SiftingAppender` ni discriminador basado en MDC.
- **Por qué no se sube**: la línea 1.5.x no tiene backport, y Spring Boot (incluido 4.1.1) sigue en Logback 1.5.
- **Revisar si**: se agrega una configuración de Logback con `SiftingAppender`.

### Apache MINA SSHD — consumo de memoria en el cliente SFTP

- **Servicio**: config-server (sshd-sftp 2.16.0, a través de JGit; corregido en 2.20.0)
- **Por qué no aplica**: el config-server clona el repositorio de configuración por HTTPS. El soporte SSH/SFTP de JGit nunca se usa.
- **Por qué no se sube**: JGit 7.4 está compilado contra SSHD 2.16.
- **Revisar si**: `spring.cloud.config.server.git.uri` pasa a usar SSH (`git@...` o `ssh://...`).

### RabbitMQ Java Client — `Class.forName` con datos no confiables en JsonRpcClient

- **Servicio**: notification-service (amqp-client 5.27.1, corregido en 5.33.0)
- **Por qué no aplica**: ni el código del proyecto ni Spring AMQP 4.0.5 usan `com.rabbitmq.tools.jsonrpc`.
- **Por qué no se sube**: la línea 5.27.x no tiene backport.
- **Revisar si**: se empieza a usar `JsonRpcClient` o `RpcServer` del cliente de RabbitMQ.

## Cómo verificar una alerta nueva

1. Ver qué versión resuelve cada servicio:

   ```bash
   ./mvnw -q dependency:tree -Dincludes=<groupId>:<artifactId> -DoutputFile=/dev/stdout
   ```

2. Revisar si la CVE ya está en este documento.
3. Si es nueva, aplicar la política de versiones y agregarla aquí.

Los escáneres pueden tardar días en registrar una CVE nueva, y pueden marcar un servicio como limpio sin estarlo. La fuente de verdad es `dependency:tree`.
