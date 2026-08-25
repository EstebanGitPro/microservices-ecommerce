# Documentación de aprendizaje

Documentos escritos sobre el código real de este repositorio, no sobre ejemplos genéricos.

| Documento | De qué trata |
|---|---|
| [01 — Capas y flujo de datos](01-capas-y-flujo-de-datos.md) | Qué hace cada capa de `product-service`, por qué existe, y el viaje completo de un dato desde el JSON hasta Mongo y de vuelta |
| [02 — De código Java a imagen Docker](02-de-codigo-java-a-imagen-docker.md) | `.java` → `.class` → `.jar` → imagen → contenedor. Annotation processors, fat jar, multi-stage build, Compose |

---

## Cómo estudiar esto

El problema real no es que no sepas POO. Es que **la POO de un tutorial** (una clase `Perro`
que hereda de `Animal`) **no se parece a la POO de un proyecto**. En un proyecto real, POO
aparece como: interfaces que separan contratos de implementaciones, objetos que vos no creás
sino que te inyectan, y excepciones que suben por capas hasta un solo lugar que las traduce.

Por eso el orden que sigue no arranca por teoría. Arranca por **leer lo que ya escribiste**.

### Fase 1 — Entender lo que ya tenés (1 semana)

No escribas código nuevo todavía. Esto es lo que más te va a rendir.

1. Leé el documento 01 completo.
2. Hacé el ejercicio del debugger (sección 6 del doc 01). Breakpoint en el controller y en el
   service, mandá un POST, y andá paso a paso viendo cómo el dato cambia de tipo en cada frontera.
3. Leé el documento 02 y hacé sus ejercicios en orden. Especialmente abrir `ProductMapperImpl.java`
   generado: ver el código que "aparece solo" te desarma la sensación de magia.
4. **Prueba de fuego**: explicale a alguien (o escribilo) qué pasa entre que llega el JSON y
   se devuelve el 201. Si te trabás en un paso, ese paso es el que no entendiste.

### Fase 2 — Romper y arreglar (1 semana)

Entender de verdad es poder predecir. Rompé el código a propósito y **predecí el error antes de correrlo**:

| Rompelo así | Predecí qué pasa |
|---|---|
| Borrá `@Service` de `ProductServiceImpl` | ¿Compila? ¿Arranca? ¿Qué error exacto? |
| Cambiá el tipo del campo del controller de `ProductService` a `ProductServiceImpl` | ¿Funciona igual? ¿Qué perdiste? |
| Borrá `@Valid` del `createProduct` | Mandá `price: -5`. ¿Qué devuelve ahora? |
| Devolvé `Product` en vez de `ProductResponseDTO` | ¿Cambia el JSON? ¿Qué se filtró? |
| Comentá el handler de `ResourceNotFoundException` | Pedí un id que no existe. ¿Quién lo atrapa ahora? |
| Editá `application.yaml` y corré `docker compose up` **sin** `--build` | ¿El cambio se aplica? ¿Por qué no? |

Cada error que predecís bien es un concepto que entendiste. Cada uno que te sorprende es tu
próximo tema de estudio.

### Fase 3 — Construir con el patrón en la cabeza (2-3 semanas)

Ahora sí, código nuevo. Hacé `inventory-service` **vos solo**, sin copiar y pegar de
`product-service`, mirándolo solo cuando te trabes. Cambia la base (MySQL en vez de Mongo),
así que vas a tener que entender qué se mantiene igual y qué no:

- El repository pasa de `MongoRepository` a `JpaRepository`.
- El model pasa de `@Document` a `@Entity` + `@Table`.
- Aparecen conceptos nuevos: transacciones, `@Column`, esquema relacional.
- **El controller, el service, los DTO, el mapper y el advice quedan casi idénticos.**

Ese último punto es la lección entera: la arquitectura en capas **te aísla del cambio de base
de datos**. Cambiar de Mongo a MySQL toca dos capas de siete. Vas a *sentir* el valor, no leerlo.

### Fase 4 — Testing (donde se paga todo lo anterior)

Escribí un test de `ProductServiceImpl` con un `ProductRepository` mockeado, sin levantar Mongo.

Si te resulta fácil, tus capas están bien separadas. Si te resulta imposible sin levantar media
aplicación, hay acoplamiento escondido. **El testing es el detector de mala arquitectura más
honesto que existe** — no una tarea aparte que se hace al final.

---

## Conceptos por orden de prioridad

Estudiá en este orden. No saltes al siguiente sin poder explicar el anterior con tu propio código.

**Ahora**

1. Inversión de Control e Inyección de Dependencias — quién crea tus objetos y por qué no vos
2. Programar contra interfaces — por qué `private final ProductService`, no `ProductServiceImpl`
3. Propagación de excepciones — por qué no hay un solo `try/catch` en tu service
4. Por qué DTO ≠ entidad — la frontera entre tu API pública y tu base de datos

**Después**

5. Testing con mocks (Mockito, `@WebMvcTest`, `@DataMongoTest`)
6. Transacciones y consistencia
7. Comunicación entre servicios (REST síncrono vs. mensajería asíncrona)

**Más adelante**

8. Arquitectura Hexagonal / Puertos y Adaptadores — la evolución natural de lo que ya tenés
9. Domain-Driven Design — cuando el dominio se vuelve más complejo que un CRUD

---

## Una advertencia sobre la arquitectura hexagonal

La vas a ver en todos lados y da la sensación de que es "lo correcto" y que las capas son
"lo básico". No caigas en eso.

La hexagonal resuelve un problema concreto: **desacoplar el dominio de la infraestructura**
cuando el dominio tiene reglas complejas. En un CRUD como `product-service` te agrega tres
carpetas y cero valor.

Aprendé hexagonal **cuando sientas el dolor** que resuelve — cuando tengas reglas de negocio
que quieras testear y no puedas sin arrastrar Spring y Mongo con ellas. Adoptar un patrón
sin haber sentido el problema que resuelve es cargo cult: copiás la forma sin la sustancia.

Primero dominá las capas. Después el hexágono va a parecerte obvio, no místico.
