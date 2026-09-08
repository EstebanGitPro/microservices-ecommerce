# De código Java a imagen Docker

Qué pasa exactamente entre que escribís `Product.java` y que un contenedor responde en el puerto 8080.

---

## 1. El panorama en una línea

```
.java  ──javac──►  .class  ──maven──►  .jar  ──docker build──►  imagen  ──docker run──►  contenedor
```

Cinco artefactos distintos. Confundirlos es la causa número uno de "pero si yo ya arreglé eso"
(volvemos sobre eso en la sección 7, porque ya te pasó en este proyecto).

---

## 2. Paso 1 — `.java` → `.class` (compilación)

`javac` toma tu código fuente y produce **bytecode**: instrucciones para la JVM, no para tu CPU.

```
Product.java  ──javac──►  Product.class
(texto)                   (bytecode, binario)
```

Por eso Java es *"write once, run anywhere"*: el `.class` es idéntico en Mac, Linux o Windows.
Lo que cambia es la JVM que lo ejecuta.

### La parte que te falta del modelo mental: los annotation processors

Antes de generar el bytecode, `javac` corre los **annotation processors** declarados en el `pom.xml`:

```xml
<annotationProcessorPaths>
    <path><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></path>
    <path><groupId>org.mapstruct</groupId><artifactId>mapstruct-processor</artifactId></path>
</annotationProcessorPaths>
```

Estos leen tus anotaciones y **generan código fuente nuevo**, que después se compila junto al tuyo:

- **Lombok** ve `@Data` en `Product` y le inyecta getters, setters, `equals`, `hashCode`, `toString`.
  Ve `@RequiredArgsConstructor` en `ProductServiceImpl` y le genera el constructor con los campos `final`.
  Ve `@Slf4j` y le agrega el campo `log`.
- **MapStruct** ve `@Mapper` en `ProductMapper` y **escribe un archivo real**:
  `target/generated-sources/annotations/com/ecommerce/product_service/mapper/ProductMapperImpl.java`

Andá a verlo después de compilar. Es la mejor forma de sacarle la "magia" a todo esto:

```bash
cd product-service
mvn clean compile
bat target/generated-sources/annotations/com/ecommerce/product_service/mapper/ProductMapperImpl.java
```

Vas a encontrar algo como esto, escrito por la máquina:

```java
@Component
public class ProductMapperImpl implements ProductMapper {
    @Override
    public Product toProduct(ProductRequestDTO requestDTO) {
        if (requestDTO == null) return null;
        Product.ProductBuilder product = Product.builder();
        product.name(requestDTO.name());
        product.description(requestDTO.description());
        product.price(requestDTO.price());
        return product.build();
    }
}
```

Ahí se te cae la ficha: **no hay magia, hay generación de código en tiempo de compilación.**
Ese archivo se compila igual que el tuyo y termina como un `.class` más adentro del jar.

> **Ojo con el orden de los processors**: Lombok y MapStruct tienen que declararse juntos
> en el mismo `annotationProcessorPaths`. Si Lombok no corre primero, MapStruct no encuentra
> los setters/builder que Lombok todavía no generó, y te tira "no accessor found".

---

## 3. Paso 2 — `.class` → `.jar` (empaquetado con Maven)

Maven corre un **ciclo de vida** de fases en orden. `mvn package` ejecuta todas las anteriores:

| Fase | Qué hace |
|---|---|
| `validate` | Chequea el `pom.xml` |
| `compile` | Corre los processors + `javac` → `target/classes/` |
| `test` | Corre los tests |
| `package` | Arma el `.jar` en `target/` |

`mvn clean package` = borrar `target/` primero y hacer todo de cero.
`-DskipTests` = saltear la fase `test`.

### Qué hay adentro del jar

El `spring-boot-maven-plugin` no arma un jar normal: arma un **fat jar** (o *uber jar*) ejecutable,
con tu código **y todas sus dependencias adentro**:

```
product-service-0.0.1-SNAPSHOT.jar
├── META-INF/MANIFEST.MF          ← dice cuál es la clase main
├── BOOT-INF/
│   ├── classes/                  ← TU código compilado
│   │   ├── com/ecommerce/product_service/...  (.class)
│   │   └── application.yaml      ← ¡los resources también van adentro!
│   └── lib/                      ← ~50 jars: spring, jackson, mongo-driver, tomcat...
└── org/springframework/boot/loader/  ← el launcher que sabe leer BOOT-INF/
```

Dos consecuencias prácticas:

1. **El jar es autocontenido.** No necesitás instalar Tomcat ni Spring en ningún lado. Solo una JVM.
2. **`application.yaml` está adentro del jar.** Si lo editás en `src/` y no recompilás, el jar
   sigue teniendo el viejo. Por eso la config importante va por variables de entorno, que sí se
   leen en cada arranque.

Verificalo vos mismo:

```bash
cd product-service
mvn clean package -DskipTests
unzip -l target/*.jar | head -30
```

Y probalo sin Docker:

```bash
java -jar target/product-service-0.0.1-SNAPSHOT.jar
```

Eso es *literalmente* lo que el contenedor va a ejecutar. El contenedor no agrega nada mágico:
solo aísla el entorno.

---

## 4. Paso 3 — `.jar` → imagen Docker

Este es el `Dockerfile` real del proyecto:

```dockerfile
# --- ETAPA 1: BUILD ---
FROM maven:3.9.15-eclipse-temurin-25 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# --- ETAPA 2: RUN TIME ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar product.jar
ENTRYPOINT ["java","-jar","product.jar"]
```

### Qué es una imagen, en concreto

Una imagen es una **pila de capas de solo lectura**. Cada instrucción del Dockerfile
(`FROM`, `COPY`, `RUN`) crea una capa nueva encima de la anterior. La imagen final es
la suma de todas, más un manifiesto que dice qué comando ejecutar.

### Multi-stage build: por qué hay dos `FROM`

Esta es la decisión de diseño clave del archivo, y vale entenderla bien:

**Etapa 1 (`builder`)** — necesita Maven, el JDK completo y todo tu código fuente para compilar.
La imagen `maven:3.9.15-eclipse-temurin-25` pesa ~800 MB.

**Etapa 2 (final)** — para *ejecutar* un jar solo hace falta una JRE. `eclipse-temurin:25-jre`
pesa ~200 MB.

`COPY --from=builder /app/target/*.jar product.jar` copia **únicamente el jar** de la etapa 1
a la etapa 2. Todo lo demás de la etapa 1 —Maven, el JDK, tu código fuente, el repositorio
`~/.m2` con las dependencias descargadas— **se descarta**.

```
┌─────────────── ETAPA 1: builder ───────────────┐
│  maven + JDK 25 + código fuente + ~/.m2        │
│  → mvn package → /app/target/product.jar       │
└────────────────────┬───────────────────────────┘
                     │  solo cruza el .jar
                     ▼
┌─────────────── ETAPA 2: final ─────────────────┐
│  JRE 25 + product.jar                          │
│  ENTRYPOINT java -jar product.jar              │
└────────────────────────────────────────────────┘
         ↑ ESTA es la imagen que se publica
```

Ganás dos cosas: **tamaño** (4x menos) y **seguridad** (tu código fuente y el compilador no
viajan a producción; menos software instalado = menos superficie de ataque).

### El `.dockerignore` que te falta

`COPY . .` copia **todo** el directorio, incluido `target/`, `.git` y `.idea` si existen.
Eso hace el build más lento e invalida la caché de capas sin motivo. Creá
`product-service/.dockerignore`:

```
target/
.git
.idea
*.iml
```

### Caché de capas: por qué a veces el build tarda 3 minutos y a veces 5 segundos

Docker cachea cada capa. Si nada cambió en las entradas de una instrucción, reusa la capa.
El problema del Dockerfile actual: `COPY . .` viene **antes** de `RUN mvn package`, así que
cambiar **una sola línea** de código invalida la capa del `COPY`, y con ella la del `RUN`.
Resultado: Maven vuelve a descargar todas las dependencias, cada vez.

La versión optimizada separa dependencias de código, porque el `pom.xml` cambia mucho menos seguido:

```dockerfile
FROM maven:3.9.15-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline        # ← se cachea hasta que cambie el pom
COPY src ./src
RUN mvn clean package -DskipTests    # ← solo esto se rehace al cambiar código
```

**Regla general de Dockerfiles**: lo que cambia poco va arriba, lo que cambia mucho va abajo.

---

## 5. Paso 4 — imagen → contenedor

```
IMAGEN                          CONTENEDOR
(plantilla, inmutable)          (instancia en ejecución)
como una clase          ──►     como un objeto (new)
```

Esa analogía con POO es exacta y te sirve: de **una** imagen podés levantar **N** contenedores,
igual que de una clase instanciás N objetos. Cada contenedor tiene su propia capa de escritura
encima de las capas de solo lectura de la imagen.

Al hacer `docker run`, el contenedor arranca aislado por namespaces de Linux: su propio sistema
de archivos, su propia red, sus propios procesos. Adentro corre un solo proceso: `java -jar product.jar`.

### Dónde entra Docker Compose

Compose describe **varios contenedores y cómo se conectan**. En el `docker-compose.yml` del
proyecto, el `product-service` está comentado; así es como quedaría activo:

```yaml
product-service:
  build: product-service       # ← construir la imagen desde ese Dockerfile
  ports:
    - "8081:8080"              # ← puerto del host : puerto del contenedor
  environment:
    - MONGO_HOST=mongodb       # ← ¡NO localhost!
    - MONGO_PORT=27017
  depends_on:
    - mongodb
```

Tres conceptos que hay que tener clarísimos:

**`ports: "8081:8080"`** — El contenedor escucha en su puerto 8080 (el que dice `SERVER_PORT` en
`application.yaml`). Docker publica ese puerto en el 8081 de tu Mac. Vos entrás por
`localhost:8081`, el contenedor cree que está en el 8080. Los dos números son mundos distintos.

**`MONGO_HOST=mongodb`** — Compose crea una red virtual y le da a cada servicio un **nombre DNS
igual a su nombre de servicio**. Adentro de esa red, `mongodb` resuelve a la IP del contenedor
de Mongo. Si pusieras `localhost`, el contenedor de product-service se buscaría a sí mismo,
porque para él `localhost` es *su propio* contenedor, no tu Mac. Este es el error más común al
empezar con Compose.

**`depends_on`** — controla el **orden de arranque**, no que el otro servicio esté *listo*.
Mongo puede estar arrancado pero todavía inicializando. Por eso las apps necesitan reintentos
de conexión, no confiar en `depends_on`.

### Volúmenes: por qué los datos sobreviven

```yaml
volumes:
  - mongo_data:/data/db
```

La capa de escritura de un contenedor **muere con el contenedor**. Un volumen es
almacenamiento gestionado por Docker, fuera del ciclo de vida del contenedor: montado en
`/data/db` (donde Mongo guarda todo), los datos sobreviven a `docker compose down`.

---

## 6. El recorrido completo, de punta a punta

```
1.  Escribís           Product.java, ProductController.java...
         │
2.  javac + processors Lombok/MapStruct generan código
         │             → target/classes/*.class
         │
3.  mvn package        empaqueta clases + resources + ~50 dependencias
         │             → target/product-service-0.0.1-SNAPSHOT.jar
         │
4.  docker build       ETAPA 1: repite 2 y 3 adentro del contenedor
         │             ETAPA 2: copia SOLO el jar sobre una JRE
         │             → imagen product-service:latest
         │
5.  docker run         instancia la imagen, aísla el entorno,
         │             inyecta las variables de entorno,
         │             ejecuta java -jar product.jar
         │
6.  Spring Boot        lee application.yaml + variables de entorno,
         │             escanea @Component/@Service/@RestController,
         │             arma el ApplicationContext (inyección de dependencias),
         │             levanta Tomcat embebido en :8080,
         │             conecta a Mongo con las credenciales de MongoConfig
         │
7.  LISTO              curl localhost:8081/api/v1/product
```

Los pasos 1 a 3 pasan **en tu máquina o adentro del builder**. El 4 arma el artefacto que se
publica. El 5 y 6 pasan **cada vez que arranca el contenedor**. Esa frontera entre "tiempo de
build" y "tiempo de arranque" es la que hay que tener grabada.

---

## 7. El bug que ya te mordió (y que te va a volver a morder)

Ya te pasó en este proyecto: agregaste `GlobalControllerAdvice`, levantaste con `docker compose up`
y el endpoint seguía devolviendo el 404 genérico de Spring en vez de tu `ProblemDetail`.

**Causa raíz**: `docker compose up` sin `--build` **reutiliza la imagen ya construida**.
Esa imagen tenía adentro un jar compilado *antes* de que escribieras esa clase.
Tu código fuente estaba bien. El `.class` no estaba en el jar.

```bash
docker compose up -d --build     # ← reconstruye la imagen
```

Diagnóstico cuando sospechás de esto — mirá qué hay *realmente* adentro del jar del contenedor:

```bash
docker cp product-service:/app/product.jar /tmp/check.jar
unzip -l /tmp/check.jar | rg GlobalControllerAdvice
```

Si no aparece, el problema no está en tu código: está en el pipeline de build.

**La lección general**: cuando un cambio "no se aplica", preguntate en qué eslabón se quedó.
`.java` editado ≠ `.class` compilado ≠ jar empaquetado ≠ imagen construida ≠ contenedor corriendo.
Cinco artefactos, cinco lugares donde tu cambio puede haberse quedado atrás.

---

## 8. Cosas que hoy están mal en este repo

No son urgentes, pero conviene que las veas vos y las corrijas entendiendo por qué:

**`docker-compose.yml`**

- Los nombres de volúmenes no coinciden con los declarados. `inventory-db` monta `inventory_db`
  pero la sección `volumes:` declara `inventory_data_data`; `order-db` monta `order_db` pero se
  declara `order_data`. Solo `mongo_data` está bien. Los que no coinciden no quedan gestionados
  como esperás → los datos de MySQL y Postgres no están realmente persistidos.
- `MSQL_ROOT_PASSWORD` está mal escrito (es `MYSQL_ROOT_PASSWORD`), así que MySQL nunca
  recibe la contraseña de root.
- Las rutas `var/lib/msql` y `var/lib/postgresql/data` son **relativas** — les falta la barra
  inicial. Deben ser `/var/lib/mysql` y `/var/lib/postgresql/data`.

**`pom.xml` vs `Dockerfile`**

- El `pom.xml` declara `<java.version>21</java.version>` pero el `Dockerfile` compila y ejecuta
  con imágenes de Java 25. Funciona (compila apuntando a 21, corre sobre una JVM 25), pero es
  una inconsistencia: alineá los dos para que "lo que compilo" y "lo que ejecuto" digan lo mismo.

**`product-service/Dockerfile`**

- Falta `.dockerignore` (sección 4).
- El orden de las instrucciones desaprovecha la caché de capas (sección 4).
- El contenedor corre como `root`. En producción se agrega un usuario sin privilegios:
  ```dockerfile
  RUN useradd -r -u 1001 appuser
  USER appuser
  ```

---

## 9. Ejercicios para fijarlo

Hacelos en orden. Cada uno te muestra un eslabón distinto de la cadena.

1. `mvn clean compile` y abrí `ProductMapperImpl.java` en `target/generated-sources/`.
   → Se te acaba la "magia" de MapStruct.
2. `mvn clean package -DskipTests` y `unzip -l target/*.jar`.
   → Ves qué es realmente un fat jar.
3. `java -jar target/*.jar` sin Docker (con Mongo levantado en `localhost`).
   → Entendés que Docker no es necesario para correr la app, solo para aislarla.
4. `SERVER_PORT=9090 java -jar target/*.jar`.
   → Ves la config externa funcionando sin recompilar.
5. `docker build -t product-test .` y después `docker images`.
   → Compará el tamaño con el de `maven:3.9.15-eclipse-temurin-25`. Ahí ves el multi-stage.
6. Arreglá el `docker-compose.yml` de la sección 8 y descomentá `product-service`.
   → Levantás el stack completo.

---

## Ver también

- [01 — Capas y flujo de datos](01-capas-y-flujo-de-datos.md)
- [README de docs](README.md)
