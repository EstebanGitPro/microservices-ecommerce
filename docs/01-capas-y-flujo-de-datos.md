# Capas y flujo de datos en `product-service`

Este documento explica, con el código real de este repositorio, qué hace cada capa,
por qué existe y cómo viaja un dato desde que entra por HTTP hasta que vuelve al cliente.

---

## 1. El problema que resuelven las capas

Sin capas, un endpoint termina así:

```java
@PostMapping
public Object create(@RequestBody Map<String,Object> body) {
    // valida, arma el documento, habla con Mongo, arma el JSON, maneja errores...
}
```

Ese método sabe de HTTP, de reglas de negocio y de la base de datos al mismo tiempo.
Tres razones distintas para cambiarlo: cambia la API, cambia una regla, cambia la base.
Cualquiera de las tres te obliga a tocar el mismo archivo, y no podés probar la regla
de negocio sin levantar un servidor HTTP y una base de datos.

Las capas son una respuesta a eso: **separar por razón de cambio**.

| Capa | Su única responsabilidad | Qué NO sabe |
|---|---|---|
| `controller` | Traducir HTTP ↔ objetos Java | Cómo se guarda un producto |
| `service` | Reglas y orquestación | Que existe HTTP o Mongo |
| `repository` | Persistencia | Reglas de negocio |
| `model` | Cómo se ve el dato en la base | HTTP, JSON |
| `dto` | Cómo se ve el dato en la API | Mongo, `@Document` |
| `mapper` | Traducir entre `dto` y `model` | Todo lo demás |
| `exception` | Convertir errores en respuestas HTTP | Reglas de negocio |

La regla mental: **cada capa habla solo con la de abajo, y las dependencias apuntan hacia adentro.**

---

## 2. Las capas de este proyecto, archivo por archivo

```
product-service/src/main/java/com/ecommerce/product_service/
├── controller/ProductController.java     ← borde HTTP
├── service/ProductService.java           ← contrato (interfaz)
├── service/impl/ProductServiceImpl.java  ← reglas
├── repository/ProductRepository.java     ← acceso a datos
├── model/Product.java                    ← entidad de Mongo
├── dto/ProductRequestDTO.java            ← entrada de la API
├── dto/ProductResponseDTO.java           ← salida de la API
├── mapper/ProductMapper.java             ← traductor DTO ↔ entidad
├── exception/                            ← errores → HTTP
└── config/MongoConfig.java               ← infraestructura
```

### 2.1 `controller` — el borde

```java
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponseDTO createProduct(@RequestBody @Valid ProductRequestDTO productRequestDTO) {
        return productService.createProduct(productRequestDTO);
    }
}
```

Fijate qué hace y qué no hace:

- **Hace**: mapea una URL a un método, deserializa el body, dispara validación (`@Valid`), fija el status code.
- **No hace**: ni un `if`, ni una consulta, ni un `try/catch`. Solo delega.

Un controller que crece es la primera señal de que la lógica se está escapando de su lugar.
Si ves un `if` de negocio dentro de un controller, va en el service.

**Punto clave de POO acá**: el campo es `private final ProductService productService`,
o sea el **tipo de la interfaz**, no de `ProductServiceImpl`. El controller depende del
**contrato**, no de la implementación. Eso es *programar contra interfaces*, y es lo que
te permite cambiar la implementación (o inyectar un mock en un test) sin tocar el controller.

### 2.2 `service` — interfaz e implementación

La interfaz es el contrato: qué se puede hacer con un producto.

```java
public interface ProductService {
    ProductResponseDTO createProduct(ProductRequestDTO requestDTO);
    List<ProductResponseDTO> getAllProducts();
    ProductResponseDTO getProductById(String id);
    ProductResponseDTO updateProduct(String id, ProductRequestDTO productRequest);
    void deleteProduct(String id);
}
```

La implementación es el cómo:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    @Override
    public ProductResponseDTO getProductById(String id) {
        Product product = repository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Producto", "id", id)
        );
        return mapper.toProductResponseDTO(product);
    }
}
```

Mirá que en todo `ProductServiceImpl` no aparece la palabra `HttpStatus`, ni `ResponseEntity`,
ni `@RequestMapping`. El service **no sabe que existe HTTP**. Si mañana este servicio se
consume por gRPC o por un consumidor de Kafka, esta clase no cambia.

Tampoco lanza un "404". Lanza una `ResourceNotFoundException`, que es un concepto de dominio.
Quién traduce eso a 404 es otra capa (sección 2.6).

### 2.3 `repository` — persistencia

```java
public interface ProductRepository extends MongoRepository<Product, String> {
}
```

Sí, está vacía. Y aun así tenés `save`, `findAll`, `findById`, `existsById`, `deleteById`.

**¿Por qué?** Porque Spring Data genera la implementación en tiempo de ejecución. Al arrancar,
Spring detecta la interfaz, lee los genéricos `<Product, String>` (entidad y tipo del id),
y crea un **proxy dinámico** que implementa esos métodos hablando con Mongo.

Esto es importante para tu modelo mental: **no todos los objetos que usás los escribiste vos**.
Spring inyecta un objeto que cumple el contrato `ProductRepository`, y a tu service le da igual
quién lo implementó. De nuevo: programar contra interfaces.

### 2.4 `model` vs `dto` — por qué son dos clases distintas

```java
@Document(value = "product")
public class Product {
    @Id private String id;
    private String name;
    private String description;
    private BigDecimal price;
}
```

```java
public record ProductRequestDTO(
        @NotBlank(message = "El nombre del producto no puede estar vacío")
        String name,
        String description,
        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio debe ser mayor a cero")
        BigDecimal price
) {}
```

Hoy tienen casi los mismos campos, y ahí viene la pregunta obvia: *¿para qué duplicar?*

Porque representan **cosas distintas que cambian por razones distintas**:

- `Product` es la forma del documento **en la base de datos**. Si mañana agregás `costoInterno`,
  `proveedorId` o `margenDeGanancia`, van acá.
- `ProductRequestDTO` es **el contrato público de entrada**. Fijate que no tiene `id`: el cliente
  no elige el id, lo genera Mongo. Ese "no tiene id" es una decisión de diseño, no un olvido.
- `ProductResponseDTO` es **el contrato público de salida**. Sí tiene `id`, porque el cliente lo
  necesita para el siguiente request.

Si expusieras `Product` directamente en la API, cada campo nuevo en la base se filtraría al JSON
público (`costoInterno` incluido), y cada cambio de API te obligaría a migrar la base. Los DTO son
la **frontera** que rompe ese acoplamiento.

Extra: son `record`, o sea inmutables. Un DTO que viaja entre capas y nadie puede mutar en el camino
elimina toda una familia de bugs.

### 2.5 `mapper` — el traductor

```java
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    Product toProduct(ProductRequestDTO requestDTO);

    ProductResponseDTO toProductResponseDTO(Product product);

    @Mapping(target = "id", ignore = true)
    void updateProductFromRequest(ProductRequestDTO productRequest, @MappingTarget Product product);
}
```

Otra interfaz sin implementación. Pero acá el mecanismo es **distinto** al del repository, y esta
diferencia vale oro entenderla:

| | `ProductRepository` | `ProductMapper` |
|---|---|---|
| Quién implementa | Spring Data | MapStruct |
| Cuándo | En **runtime**, al arrancar la app | En **compilación** (`mvn compile`) |
| Qué produce | Un proxy en memoria | Un archivo `.java` real |
| Dónde lo ves | En ningún lado | `target/generated-sources/annotations/.../ProductMapperImpl.java` |

MapStruct es un **annotation processor**: corre durante `javac`, lee tus anotaciones y **escribe
código Java** que después se compila junto al tuyo. Lombok (`@Data`, `@RequiredArgsConstructor`,
`@Slf4j`) hace lo mismo. Por eso en el `pom.xml` aparecen configurados como `annotationProcessorPaths`.

Andá y abrí ese archivo generado después de compilar. Vas a ver un `new Product()` con setters,
escrito a mano por la máquina. Ahí se te acomoda mucho el modelo mental de "magia" de Spring.

Los tres métodos, y por qué son tres:

- `toProduct` — DTO de entrada → entidad nueva. `id` ignorado porque lo pone Mongo.
- `toProductResponseDTO` — entidad → DTO de salida. Acá **sí** copia el `id`.
- `updateProductFromRequest` — recibe una entidad **existente** (`@MappingTarget`) y le pisa los
  campos. No crea una nueva. Por eso el update conserva el `id` original.

### 2.6 `exception` — errores de dominio → respuestas HTTP

El service lanza un concepto de negocio:

```java
throw new ResourceNotFoundException("Producto", "id", id);
```

Y una clase completamente separada decide cómo se ve eso en HTTP:

```java
@RestControllerAdvice
@Slf4j
public class GlobalControllerAdvice {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Recurso no encontrado ");
        // ...
        return problemDetail;
    }
}
```

`@RestControllerAdvice` es un interceptor global: cualquier excepción que se escape de cualquier
controller cae acá. Por eso **no hay un solo `try/catch` en todo el service ni en el controller**.

El orden importa: Spring elige el handler **más específico** que matchee. `ResourceNotFoundException`
va al de 404, `MethodArgumentNotValidException` (la que dispara `@Valid`) al de 400, y cualquier
otra cosa cae en `handleException(Exception.class)` → 500. Ese último es la red de seguridad:
`GET /api/v1/product/test-fail` existe justamente para probarla.

`ProblemDetail` es el estándar **RFC 7807**: un formato común para errores de API. No inventes tu
propio formato de error cuando ya hay uno estándar.

---

## 3. El viaje completo de un dato

### 3.1 `POST /api/v1/product` — camino feliz

```
CLIENTE
  │  POST /api/v1/product
  │  Content-Type: application/json
  │  { "name": "MacBook Pro", "description": "M3", "price": 2500.00 }
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 1. Tomcat recibe los bytes del socket                        │
│    Los convierte en un HttpServletRequest                    │
└──────────────────────────────────────────────────────────────┘
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 2. DispatcherServlet (el "front controller" de Spring)       │
│    Busca qué método matchea POST + /api/v1/product           │
│    → ProductController.createProduct                         │
└──────────────────────────────────────────────────────────────┘
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 3. Jackson deserializa el JSON                               │
│    texto ────────────────────► ProductRequestDTO             │
│    (acá deja de ser texto y pasa a ser un objeto Java)       │
└──────────────────────────────────────────────────────────────┘
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 4. @Valid dispara Bean Validation                            │
│    ¿name en blanco? ¿price null o negativo?                  │
│    Si falla → MethodArgumentNotValidException → salta al 6'  │
└──────────────────────────────────────────────────────────────┘
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 5. ProductController.createProduct(dto)                      │
│    Solo delega: productService.createProduct(dto)            │
└──────────────────────────────────────────────────────────────┘
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 6. ProductServiceImpl.createProduct                          │
│                                                              │
│    a) mapper.toProduct(dto)                                  │
│       ProductRequestDTO ──────► Product (id = null)          │
│                                                              │
│    b) repository.save(product)                               │
│                                                              │
│    c) log.info("Product {} guardado", ...)                   │
│                                                              │
│    d) mapper.toProductResponseDTO(savedProduct)              │
│       Product ──────► ProductResponseDTO (ahora CON id)      │
└──────────────────────────────────────────────────────────────┘
  ▼
┌──────────────────────────────────────────────────────────────┐
│ 7. MongoRepository (proxy) → driver de Mongo                 │
│    Product ──────► documento BSON                            │
│    db.product.insertOne({...})                               │
│    Mongo genera el _id y lo devuelve                         │
└──────────────────────────────────────────────────────────────┘
  ▼  (vuelta)
┌──────────────────────────────────────────────────────────────┐
│ 8. Jackson serializa la respuesta                            │
│    ProductResponseDTO ──────► JSON                           │
│    @ResponseStatus(CREATED) → 201                            │
└──────────────────────────────────────────────────────────────┘
  ▼
CLIENTE  201 Created
{ "id": "65f1a...", "name": "MacBook Pro", "description": "M3", "price": 2500.00 }
```

**Contá las transformaciones del dato**: `texto JSON` → `ProductRequestDTO` → `Product` →
`documento BSON` → `Product` → `ProductResponseDTO` → `texto JSON`. Seis formas para un mismo
concepto, y cada una existe porque cada capa necesita ver el dato de una manera distinta.

### 3.2 `GET /api/v1/product/{id}` que no existe — camino de error

```
CLIENTE  GET /api/v1/product/999
  ▼
ProductController.getProductById("999")
  ▼
ProductServiceImpl.getProductById("999")
  ▼
repository.findById("999") → Optional.empty()
  ▼
.orElseThrow(() -> new ResourceNotFoundException("Producto", "id", "999"))
  │
  │  ¡La excepción SUBE! El service no la atrapa.
  │  El controller tampoco la atrapa.
  ▼
GlobalControllerAdvice.handleResourceNotFound
  ▼
ProblemDetail(404, "Producto no encontrado con id: 999")
  ▼
CLIENTE  404 Not Found
{
  "type": "https://api.ecommerce.com/errors/not-found",
  "title": "Recurso no encontrado ",
  "status": 404,
  "detail": "Producto no encontrado con id: 999",
  "Timestamp": "...",
  "Resource": "Producto",
  "Field": "id",
  "Value": "999"
}
```

Esto es **propagación de excepciones**, y es el concepto de POO que más rinde entender acá:
una excepción sube por la pila de llamadas hasta que alguien la atrapa. Al no atraparla en el
camino, cada capa se queda limpia y **un solo lugar** decide cómo se le informa el error al cliente.

---

## 4. Inyección de dependencias: quién crea los objetos

Vos nunca escribís `new ProductServiceImpl(...)`. ¿Quién lo hace?

```java
@Service                    // ← "Spring, este objeto lo administrás vos"
@RequiredArgsConstructor    // ← Lombok genera el constructor con los campos final
public class ProductServiceImpl implements ProductService {
    private final ProductRepository repository;
    private final ProductMapper mapper;
}
```

`@RequiredArgsConstructor` genera, en compilación:

```java
public ProductServiceImpl(ProductRepository repository, ProductMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
}
```

Al arrancar la app, Spring:

1. Escanea el paquete buscando `@Component`, `@Service`, `@RestController`, `@Configuration`, `@Mapper(componentModel="spring")`.
2. Arma un grafo de dependencias: "para crear `ProductController` necesito un `ProductService`;
   para ese necesito un `ProductRepository` y un `ProductMapper`…".
3. Los crea en orden y los guarda en el **ApplicationContext** (el contenedor de beans).
4. Cada bean es **singleton** por defecto: una sola instancia compartida.

El nombre técnico es **Inversión de Control**: en vez de que tu clase decida qué implementación
usar (`new ProductServiceImpl()`), un tercero se la entrega ya construida. Por eso tus clases
dependen de interfaces y no saben ni les importa quién las implementa.

Y por eso `MongoConfig` es una `@Configuration` con un `@Bean`: le está diciendo a Spring
"cuando alguien pida un `MongoClient`, usá este que armo yo con estas credenciales", en lugar
del que Spring Boot crearía por defecto.

---

## 5. La configuración también viaja por capas

```yaml
# application.yaml
spring:
  data:
    mongodb:
      host: ${MONGO_HOST:localhost}   # variable de entorno : valor por defecto
```

```java
// MongoConfig.java
@Value("${spring.data.mongodb.host}")
private String host;
```

El recorrido: **variable de entorno del contenedor** → `application.yaml` (con default si no existe)
→ `Environment` de Spring → `@Value` inyecta el String en el campo.

Por eso el mismo `.jar` funciona en tu máquina (`localhost`) y en Docker (`mongodb`, el nombre
del servicio en la red de Compose) **sin recompilar nada**. Ese es el principio de
[12-Factor App](https://12factor.net/config): la config vive en el entorno, no en el código.

---

## 6. Cómo leer esto vos mismo la próxima vez

Cuando abras un servicio que no conocés, seguí este orden. No leas archivo por archivo alfabéticamente.

1. **Empezá por el controller.** Te dice qué puede hacer el servicio (los endpoints son su API pública).
2. **Elegí UN endpoint** y seguilo hacia abajo: controller → service → repository. Uno solo, completo.
3. **Mirá los DTO y el model juntos.** Las diferencias entre ellos te cuentan las decisiones de diseño.
4. **Buscá el `@RestControllerAdvice`.** Te dice cómo falla el sistema, que es la mitad de entenderlo.
5. **Leé el `application.yaml`.** Te dice de qué depende el servicio para arrancar.

Ejercicio concreto para fijar esto: poné un breakpoint en `ProductController.createProduct`
y otro en `ProductServiceImpl.createProduct`, mandá un POST y andá con F7/F8 paso a paso.
Mirá cómo el objeto cambia de tipo en cada frontera. Verlo pasar una vez en el debugger vale
más que leer este documento tres veces.

---

## 7. Lo que este servicio todavía NO tiene

Para que no confundas "está terminado" con "así se hace siempre":

- **Tests.** `ProductServiceApplicationTests` está vacío. Un service que depende de interfaces
  se testea con mocks sin levantar Mongo — ese es el pago real de haber separado en capas.
- **Paginación.** `getAllProducts()` trae todo. Con 100.000 productos eso se cae.
- **Transacciones.** Con un solo `save` no hace falta; cuando haya dos escrituras que deben
  ir juntas, sí.
- **Idempotencia y concurrencia.** Dos updates simultáneos al mismo producto: gana el último.

Ninguna de esas ausencias es un error hoy. Son el siguiente escalón.

---

## Ver también

- [02 — De código Java a imagen Docker](02-de-codigo-java-a-imagen-docker.md)
- [README de docs](README.md)
