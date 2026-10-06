# Propiedades personalizadas y `/actuator/refresh`

> **Idea central**
>
> Cambiar un valor en Git y que `product-service` lo tome **sin reiniciarse**,
> llamando a `POST /actuator/refresh`.
>
> Todo lo demás de este documento es la maquinaria para que eso funcione.

Este documento explica los dos bloques agregados a `config-data/product-service.yml`:

```yaml
app:
  maintenance:
    message: "Operative system - Version 1.0"

Management:
  endpoints:
    web:
      exposure:
        include: "refresh, health"
```

La idea: **cambiar un valor en Git y que `product-service` lo tome sin reiniciarse.**

---

## 1. `app.maintenance.message`: una propiedad propia

`app.*` no es de Spring. Es un espacio de nombres **inventado por nosotros** para
configuración de negocio. Spring lo carga igual que cualquier otra propiedad, pero
**nadie lo usa hasta que el código lo lea**.

> Estado actual: ninguna clase de `product-service` lee `app.maintenance.message`.
> La propiedad existe en el entorno, pero todavía no produce ningún efecto.

Para que sirva, hay que inyectarla. Dos formas:

### Opción A: `@ConfigurationProperties` (recomendada)

```java
@ConfigurationProperties(prefix = "app.maintenance")
public record MaintenanceProperties(String message) {}
```

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class ProductServiceApplication { ... }
```

- Tipada, agrupa varias propiedades relacionadas.
- **Se re-enlaza sola** al hacer refresh (no necesita `@RefreshScope`).

### Opción B: `@Value` + `@RefreshScope`

```java
@RestController
@RefreshScope
public class MaintenanceController {

    @Value("${app.maintenance.message}")
    private String message;

    @GetMapping("/api/maintenance")
    public String message() {
        return message;
    }
}
```

- Rápida para un solo valor.
- **Sin `@RefreshScope`, el valor queda congelado** en el que tenía al arrancar.

---

## 2. `management.endpoints.web.exposure.include`: qué endpoints de Actuator se ven por HTTP

Actuator (`spring-boot-starter-actuator`, ya está en el `pom.xml`) trae muchos endpoints,
pero por defecto solo expone `health` por HTTP. Esta propiedad elige cuáles publicar:

| Endpoint | Método | Para qué |
|---|---|---|
| `/actuator/health` | `GET` | Saber si el servicio está vivo. |
| `/actuator/refresh` | `POST` | Volver a pedir la configuración al Config Server y aplicar los cambios. |

`refresh` no viene de Actuator sino de **Spring Cloud Context**, que llega con
`spring-cloud-starter-config`. Por eso solo existe en servicios que son clientes del Config Server.

> Nota: la clave está escrita como `Management` (con mayúscula). Spring la tolera por su
> *relaxed binding*, pero la forma canónica es en minúsculas: `management`. Conviene
> corregirla para que coincida con la documentación y el autocompletado del IDE.

---

## 3. El flujo completo

```
1. Editás product-service.yml en config-data  →  git commit + git push
2. POST http://localhost:8080/actuator/refresh
      product-service ──GET /product-service──▶ config-server :8888 ──git pull──▶ GitHub
3. Spring compara el entorno viejo con el nuevo
4. Responde con las claves que cambiaron:  ["app.maintenance.message"]
5. Beans con @RefreshScope se recrean; @ConfigurationProperties se re-enlazan
6. GET /api/maintenance  →  devuelve el mensaje nuevo, sin reiniciar
```

Probarlo:

```bash
curl -X POST http://localhost:8080/actuator/refresh
curl http://localhost:8080/actuator/health
```

---

## 4. Qué se refresca y qué no

| Se refresca | NO se refresca (requiere reinicio) |
|---|---|
| `@ConfigurationProperties` | `server.port` |
| Beans con `@RefreshScope` | Conexión a MongoDB (`spring.data.mongodb.*`) |
| Nivel de logs (`logging.level.*`) | `@Value` en beans sin `@RefreshScope` |

Regla práctica: lo que se usa **al construir infraestructura** (servidor, pool de conexiones)
se lee una sola vez al arrancar.

---

## 5. ¿Qué refresca exactamente?

Refresh **solo recarga configuración**. No recarga código, no vacía cachés de datos, no
reconecta bases de datos. Lo que hace, paso a paso:

1. Vuelve a pedir las propiedades al Config Server y reconstruye el `Environment`.
2. Compara con el anterior y publica un `EnvironmentChangeEvent` con las claves que cambiaron.
3. Con ese evento:
   - Re-enlaza los beans `@ConfigurationProperties`.
   - Reaplica `logging.level.*`.
4. Destruye **todos** los beans `@RefreshScope`. Se recrean en el próximo uso, leyendo
   el `Environment` nuevo.

Detalles importantes:

- **Si nada cambió**, la respuesta es `[]`. Pero los beans `@RefreshScope` igual se
  recrean (el paso 4 corre siempre). Si un bean de ese tipo es caro de construir,
  lo vas a pagar en cada refresh.
- **Un bean `@RefreshScope` puede ser cualquier cosa**: un cliente HTTP con su URL
  y timeout, un conjunto de reglas de negocio, una feature flag. Si se construye a
  partir de configuración, refresh lo reconstruye con los valores nuevos.
- **No es un *hot reload* de código.** Si cambiaste una clase Java, eso es un
  nuevo build y un nuevo despliegue.

### Otras cosas que se pueden cambiar sin reiniciar (sin tocar Git)

| Qué | Cómo | Diferencia con refresh |
|---|---|---|
| Nivel de logs de un paquete | `POST /actuator/loggers/{paquete}` con `{"configuredLevel":"DEBUG"}` | Cambio inmediato y solo en memoria: se pierde al reiniciar y no queda registrado en Git. |
| Toda la aplicación | `POST /actuator/restart` (deshabilitado por defecto) | Reinicia el contexto de Spring dentro del mismo proceso. Es un reinicio, solo que más barato. |

Para usarlos hay que exponerlos en `management.endpoints.web.exposure.include`, igual
que se hizo con `refresh`.

---

## 6. Casos de uso: cuándo pensar en esto

Hacete una pregunta: **¿este valor podría cambiar mientras el servicio está corriendo,
y reiniciar sería caro o riesgoso?** Si la respuesta es sí, el valor va en `config-data`
y se lee de forma refrescable.

| Situación | Ejemplo en este proyecto |
|---|---|
| **Modo mantenimiento o avisos** | Mostrar `app.maintenance.message` o bloquear la creación de productos durante una migración. |
| **Feature flags** | `app.features.new-pricing: true` para activar una funcionalidad sin desplegar, y apagarla al instante si falla. |
| **Reglas de negocio que cambian seguido** | Stock mínimo, descuento máximo, límite de ítems por orden. |
| **Diagnóstico en producción** | Subir `logging.level.com.ecommerce` a `DEBUG` mientras investigás un bug y bajarlo después. |
| **Integraciones externas** | Timeout o URL de un servicio de terceros que se degradó. |

Cuándo **no** conviene:

- **Infraestructura** (puerto, conexión a Mongo, pools): no se refresca, necesita reinicio.
- **Secretos**: sí se refrescan, pero una contraseña en texto plano en Git es un problema
  aparte. Eso se resuelve con un vault, no con refresh.
- **Valores que nunca cambian**: dejalos en el `application.yaml` local; no suman nada acá.

---

## 7. Limitación: refresh es por instancia

`/actuator/refresh` actualiza **solo la instancia a la que le pegaste**. Con 3 réplicas,
son 3 llamadas. La solución a escala es **Spring Cloud Bus** (RabbitMQ/Kafka) con
`/actuator/busrefresh`: una llamada y el evento se propaga a todas.

---

## Checklist

- [ ] Cambiar `Management` → `management` en `product-service.yml`.
- [ ] Crear el consumidor de `app.maintenance.message` (opción A o B).
- [ ] `git push` del cambio en `config-data`.
- [ ] `POST /actuator/refresh` y verificar que la respuesta lista la clave modificada.
