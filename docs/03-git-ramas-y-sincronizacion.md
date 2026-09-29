# Ramas de git y cómo sincronizarlas

Este documento usa el historial real de este repositorio: el PR #1, que llevó
`develop` a `main` el 7 de septiembre de 2026.

---

## 1. El panorama en una línea

Una rama no es una carpeta ni una copia de tu código: **es un papelito con el número
de un commit**. Sincronizar ramas es mover papelitos.

---

## 2. Una rama es un papelito

Cuando hacés `git branch feature-x`, git no copia archivos. Crea un archivo de texto
con 41 bytes adentro. Comprobalo vos mismo:

```bash
cat .git/refs/heads/develop
```

Sale un hash. Eso es la rama entera. **Todo lo demás que creías que era una rama, no lo es.**

Por eso crear una rama en git es instantáneo aunque el proyecto pese 2 GB, y por eso en
otros sistemas de control de versiones ramificar era una operación cara que la gente evitaba.

Cambiar de rama (`git switch develop`) hace dos cosas: mueve el puntero `HEAD` para que
apunte a ese papelito, y acomoda los archivos de tu disco para que coincidan con ese commit.

---

## 3. Tenés cuatro papelitos, no dos

Este es el punto donde casi todo el mundo se pierde.

| Papelito | Dónde vive | Qué es |
|---|---|---|
| `main` | tu máquina | tu rama local |
| `develop` | tu máquina | tu rama local |
| `origin/main` | tu máquina | **tu foto** de la rama en GitHub |
| `origin/develop` | tu máquina | **tu foto** de la rama en GitHub |

Leelo de nuevo: `origin/main` **no es GitHub**. Vive en tu disco. Es lo que tu máquina
*recuerda* de GitHub la última vez que miró.

Si un compañero pushea algo ahora mismo, tu `origin/main` **no se entera**. Sigue mostrando
la foto vieja hasta que corras `git fetch`. No hay magia de fondo, no hay sincronización
automática. Git es deliberadamente offline: solo habla con el servidor cuando vos se lo pedís.

Verlos todos:

```bash
git branch -avv
```

---

## 4. Qué pasó realmente con el PR #1

Antes del merge, el historial de este repo era así:

```
main    ──── 6e0adff  (Initial commit)

develop ──── 8f52b62  (feat(order-service): add order module...)
```

Mergeaste el PR en GitHub. **GitHub creó un commit nuevo en el servidor**: `035c4d0`,
titulado `Merge pull request #1 from EstebanGitPro/develop`. Ese commit tiene dos padres
— el `main` viejo y la punta de `develop` — y eso es exactamente lo que significa "merge":
un commit con dos padres.

```
main    ──── 035c4d0   ← nació en el servidor, tu máquina no lo sabía
                 │
                 ├── padre 1: 6e0adff  (main viejo)
                 └── padre 2: 8f52b62  (develop)

develop ──── 8f52b62   ← tu papelito, quieto donde lo dejaste
```

Resultado en tu máquina:

- `main` local: **11 commits atrás**. Nunca lo actualizaste desde el primer día.
- `develop`: **1 commit atrás** de `main`.

Y acá está el detalle que hay que entender: a `develop` **no le faltaba código**. El
contenido de los archivos era byte por byte idéntico. Le faltaba el commit que *registra*
que el merge ocurrió.

Confirmarlo antes de tocar nada:

```bash
git diff --quiet origin/main origin/develop && echo "IDENTICO"
```

---

## 5. La rutina de sincronización

Cinco comandos. Corrélos en este orden después de cada PR mergeado.

```bash
git fetch origin                # 1. actualizá tus fotos
git branch -vv                  # 2. mirá quién está adelante y quién atrás
git fetch origin main:main      # 3. avanzá main local sin cambiarte de rama
git merge --ff-only main        # 4. avanzá develop
git push origin develop         # 5. publicá
```

### Paso 1 y 2 — mirar antes de tocar

`git fetch` actualiza tus fotos (`origin/*`) y **no toca tu código ni tus ramas locales**.
Es seguro siempre. No existe un escenario donde un `fetch` te rompa algo.

`git branch -vv` te muestra el diagnóstico:

```
* develop 8f52b62 [origin/develop] feat(order-service)...
  main    6e0adff [origin/main: detrás 11] Initial commit
```

Ese `[detrás 11]` es el que te dice qué hacer. **Nunca sincronices a ciegas.**

### Paso 3 — `git fetch origin main:main`

Este comando casi nadie lo conoce y es el que más te va a servir. Se lee:
*"traé `main` de origin y movele el papelito a mi `main` local"*.

La alternativa que usa todo el mundo:

```bash
git switch main && git pull && git switch develop
```

Funciona, pero te obliga a saltar de rama dos veces. Y si tenés cambios sin commitear,
o te los arrastra a la otra rama o git te bloquea el cambio.

`git fetch origin main:main` **no toca tu working tree**. Vos seguís parado en `develop`
todo el tiempo, con tus cambios intactos. Solo funciona si `main` no está checkouteada
(git no te deja mover el papelito de la rama en la que estás parado), que es justo el caso.

### Paso 4 — `git merge --ff-only main`

`ff` es *fast-forward*: **mover el papelito hacia adelante, sin crear nada**.

Como `develop` (`8f52b62`) es uno de los padres del merge commit (`035c4d0`), es un
**ancestro** de `main`. Git no necesita fusionar nada: desliza el puntero.

```
antes:   develop ──> 8f52b62
después: develop ──────────> 035c4d0
```

Cero commits nuevos. Cero conflictos posibles. Cero historial en diamante.

### Paso 5 — publicar

`git push origin develop` sube el papelito movido. Recién ahí GitHub ve `develop` al día.

---

## 6. Por qué `--ff-only` y no `merge` a secas

El `--only` es un **seguro**, y es la parte más importante del comando.

Le estás diciendo a git: *"si esto NO es un simple deslizamiento del puntero, abortá
y no hagas nada"*.

¿Por qué querés eso? Porque si el comando falla, te está avisando algo que necesitás saber:
**`main` tiene trabajo que `develop` nunca vio.** Típicamente un hotfix commiteado directo
a producción por una urgencia.

Sin el seguro, git haría un merge automático, quizás con conflictos, quizás resolviéndolos
de una forma que no querías, y te enterarías tres días después.

> Un comando que falla ruidosamente es tu amigo.
> Uno que "arregla" en silencio, no.

Si `--ff-only` falla, no lo fuerces. Parate y mirá qué tiene `main` que vos no:

```bash
git log --oneline develop..main
```

Ahí sí hacés un merge real, a conciencia.

---

## 7. Los cuatro verbos

La confusión con git casi siempre viene de no separar estas cuatro acciones:

| Verbo | Qué hace | ¿Toca tu código? | ¿Habla con el servidor? |
|---|---|---|---|
| `fetch` | actualiza tus fotos `origin/*` | no | sí |
| `merge` | mueve tu papelito local | sí | no |
| `push` | publica tu papelito | no | sí |
| `pull` | `fetch` + `merge` de una | sí | sí |

**`git pull` hace dos cosas y no te muestra ninguna.** Por eso confunde tanto: cuando algo
sale mal, no sabés si el problema fue al traer o al fusionar.

Mientras estés aprendiendo, usá `fetch` y `merge` por separado. Vas a ver exactamente qué
pasa en cada mitad. Cuando el modelo mental esté firme, `pull` te va a parecer cómodo en
vez de misterioso.

---

## 8. La trampa del índice fantasma

Esta te pasó de verdad en este repo, y vale documentarla.

Git guarda tus archivos en **tres lugares**, no en dos:

| Lugar | Qué es |
|---|---|
| working tree | los archivos en tu disco |
| índice (staging) | lo que entraría en el próximo commit |
| `HEAD` | el último commit de tu rama |

Cuando renombraste el paquete `com.ecomerce` → `com.ecommerce`, quedaron **10 archivos
viejos que existían solo en el índice**: ya no estaban en disco y nunca estuvieron en `HEAD`.

`git status` los mostraba con la marca `AD` (**A**dded en el índice, **D**eleted en disco):

```
AD inventory-service/src/main/java/com/ecomerce/inventory_service/model/Inventory.java
```

Y `git diff HEAD` **no mostraba nada** para ellos — porque compara disco contra `HEAD`,
y en ninguno de los dos estaban. Invisibles para el comando que uno usaría por instinto.

Si hubieras commiteado así, git los **resucitaba**, y terminabas con el paquete bueno y
el del typo conviviendo en el repo.

La limpieza:

```bash
git reset          # vacía el índice, NO toca tus archivos en disco
```

`git reset` sin argumentos asusta por el nombre, pero es de los comandos más inofensivos
que hay: desestagea todo y deja tu código exactamente igual.

### Cómo detectarlo siempre

Antes de cualquier commit importante:

```bash
git status --short          # buscá marcas raras: AD, AM, DU
git diff --cached --stat    # esto es LO QUE REALMENTE VA a entrar
```

`git diff --cached` compara **índice contra HEAD**. Es el único que te muestra la verdad
sobre tu próximo commit.

### Y el hermano de esa trampa

El archivo `DuplicateResourceException.java` estaba sin trackear (`??`), pero
`InventoryServiceImpl` lo importaba. Con `git commit -am` habría quedado afuera — porque
**`-a` solo agarra archivos ya trackeados, nunca los nuevos** — y el repo no compilaba
para nadie más.

La prueba definitiva de que no falta ningún archivo no es compilar tu carpeta, donde está
todo. Es clonar y compilar el clon:

```bash
git clone --local --branch develop . /tmp/verificacion
mvn -q compile -f /tmp/verificacion/order-service/pom.xml
rm -rf /tmp/verificacion
```

Eso es lo que va a recibir el que haga `git pull`.

---

## 9. Ejercicios para fijarlo

1. `cat .git/refs/heads/develop`
   → Ves que una rama son 41 bytes.

2. `git log --oneline --graph --all -15`
   → Ves el merge commit `035c4d0` con sus dos padres dibujado.

3. `git cat-file -p 035c4d0`
   → Ves los dos `parent` del merge escritos literalmente. Un merge no es magia.

4. Creá una rama, hacé un commit, volvé a `develop` y corré `git branch -vv`.
   → Ves los papelitos en posiciones distintas.

5. Editá un archivo, `git add`, y compará `git diff` con `git diff --cached`.
   → Ves la diferencia entre disco→índice e índice→HEAD. Los tres lugares, en vivo.

6. Con cambios sin commitear, probá `git switch main`.
   → Ves por qué `git fetch origin main:main` te evita el problema.

7. **Prueba de fuego**: sin mirar este documento, explicá por qué `develop` figuraba
   "detrás por 1" si el código era idéntico. Si te trabás, releé la sección 4.

---

## Ver también

- [01 — Capas y flujo de datos](01-capas-y-flujo-de-datos.md)
- [02 — De código Java a imagen Docker](02-de-codigo-java-a-imagen-docker.md)
- [README de docs](README.md)
