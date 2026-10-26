# VRP Solution

Motor de enrutamiento de vehículos (VRP) pensado para repartos en Argentina. Toma una lista de clientes con direcciones, una flota de vehículos y uno o más depósitos, y devuelve las rutas optimizadas para cada vehículo con orden de paradas, horarios estimados (ETA), distancias y geometría de cada tramo.

Está diseñado como una librería Java modular y testeable, para ser consumida más adelante por una API web. JSON es el formato de intercambio.

## Contenido

1. [Qué hace](#qué-hace)
2. [Estado actual](#estado-actual)
3. [Requisitos](#requisitos)
4. [Configuración](#configuración)
5. [Formatos de entrada](#formatos-de-entrada)
6. [Flujo de ejecución en dos fases](#flujo-de-ejecución-en-dos-fases)
7. [Uso desde Java](#uso-desde-java)
8. [Formato de salida](#formato-de-salida)
9. [Reoptimización durante la jornada](#reoptimización-durante-la-jornada)
10. [Cómo decide el solver](#cómo-decide-el-solver)
11. [Estructura del proyecto](#estructura-del-proyecto)
12. [Tests](#tests)
13. [Limitaciones y pendientes](#limitaciones-y-pendientes)
14. [Código legacy](#código-legacy)

## Qué hace

- Lee clientes desde **CSV, Excel (.xls/.xlsx), XML o JSON**, con dirección, localidad y provincia en campos separados.
- **Geocodifica** las direcciones con la API Georef de Argentina (por lotes) y, si falla, con una instancia local de Nominatim.
- Calcula la **matriz de distancias y tiempos** con Valhalla (primario) y OSRM (secundario).
- Resuelve el ruteo con **OR-Tools** para `n` vehículos heterogéneos, con capacidad, ventanas horarias, tiempos de servicio y prioridades.
- Separa el **depósito** de los clientes: es el punto de salida y de llegada, no cuenta como entrega.
- Si no puede atender algún cliente, **no lo oculta**: lo informa como omisión y exige confirmación explícita del usuario.
- Genera un **plan en JSON** con estado por parada, y puede **reoptimizar** ese mismo JSON a mitad de jornada: recalcula solo la ruta del chofer afectado por una cancelación o una restricción nueva, partiendo de su posición actual.

## Estado actual

El motor está implementado y probado con proveedores simulados (52 tests). Todavía **no se probó contra servicios reales ni con OR-Tools nativo**:

| Componente | Estado |
|---|---|
| Modelos, validaciones, lectores, cadenas de proveedores, simulación de horarios, armado del plan, reoptimización | Implementado y cubierto por tests |
| Georef, Nominatim, Valhalla, OSRM (clientes HTTP) | Implementado, probado solo con respuestas simuladas |
| Solver OR-Tools (`OrToolsVrpSolver`) | Implementado, **sin ejecutar**. Hay un test escrito (`OrToolsVrpSolverTest`) para correr en un entorno con OR-Tools |
| API web, línea de comandos, persistencia | No existen todavía |

Ver [Limitaciones y pendientes](#limitaciones-y-pendientes).

## Requisitos

- Java 25 (según `pom.xml`) y Maven.
- Dependencias (ya declaradas en el `pom.xml`): Jackson, Apache POI, Commons CSV, OR-Tools Java 9.10.
- Servicios externos:
  - **Georef Argentina**: `https://apis.datos.gob.ar/georef/api/v2.0` (público, sin clave, con cuotas).
  - **Nominatim** local (opcional pero recomendado como fallback).
  - **Valhalla** y/o **OSRM** (al menos uno es obligatorio para el ruteo).

## Configuración

El motor lee un archivo `config.properties` (no versionado, está en `.gitignore`). Ejemplo:

```properties
# Geocodificación
Georef.url=https://apis.datos.gob.ar/georef/api/v2.0
Nominatim.url=http://192.168.0.10:8080
Nominatim.email=tu@correo.com
Nominatim.pausaMs=0

# Ruteo (al menos uno de los dos)
Valhalla.url=http://192.168.0.10:8002
OSRM.url=http://192.168.0.10:5000
Valhalla.costing=auto
Matriz.tamanoBloque=50

# Solver
Solver.objetivo=DISTANCIA
Solver.tiempoLimiteSeg=10
Solver.penalizacionBase=10000000
Solver.coeficienteBalanceo=0

# HTTP
Http.timeoutSeg=30
```

| Clave | Por defecto | Descripción |
|---|---|---|
| `Georef.url` | URL pública de Georef | Base de la API de Georef |
| `Nominatim.url` | sin definir | Si no se define, no se usa Nominatim |
| `Nominatim.email` | sin definir | Se envía como contacto en cada consulta |
| `Nominatim.pausaMs` | `0` | Pausa entre consultas (solo necesaria con instancias públicas) |
| `Valhalla.url` / `OSRM.url` | sin definir | Se usan en ese orden; Valhalla es el primario |
| `Valhalla.costing` | `auto` | Perfil de ruteo de Valhalla |
| `Matriz.tamanoBloque` | `50` | Cantidad de puntos por bloque al pedir la matriz |
| `Solver.objetivo` | `DISTANCIA` | `DISTANCIA` o `TIEMPO` |
| `Solver.tiempoLimiteSeg` | `10` | Tiempo máximo de búsqueda de OR-Tools |
| `Solver.penalizacionBase` | `10000000` | Costo de omitir una entrega de prioridad 1 |
| `Solver.coeficienteBalanceo` | `0` | Si es mayor que 0, penaliza rutas desparejas |
| `Http.timeoutSeg` | `30` | Tiempo máximo por consulta HTTP |

## Formatos de entrada

### Tabulares (CSV, Excel, XML)

Traen **solo los clientes**. La fecha, los depósitos y los vehículos se pasan por argumento (`ContextoOperativo`).

Columnas reconocidas (no distinguen mayúsculas, tildes, espacios ni guiones bajos, así que `servicio_min`, `Servicio Min` y `servicioMin` son equivalentes):

| Columna | Obligatoria | Descripción |
|---|---|---|
| `direccion` | Sí (si no hay lat/lon) | Calle y altura |
| `provincia` | Sí (si no hay lat/lon) | Nombre oficial, por ejemplo `Buenos Aires` |
| `localidad` | Recomendada | Mejora la precisión de Georef |
| `id` | No | Si falta, se genera `E1`, `E2`, ... según la fila |
| `nombre` | No | Nombre del cliente |
| `demanda` | No | Unidades que ocupa la entrega (por defecto 0) |
| `servicio_min` | No | Minutos de servicio en el cliente |
| `ventana_desde` / `ventana_hasta` | No | Ventana horaria `HH:mm` |
| `prioridad` | No | Entero, mayor es más importante (por defecto 1) |
| `lat` / `lon` | No | Si vienen, no se geocodifica ese cliente |

CSV: el separador (`;` o `,`) se detecta solo. Excel: se lee la primera hoja, con encabezados en la primera fila. XML: cada elemento con campos simples es un registro.

```csv
id;nombre;direccion;localidad;provincia;demanda;servicio_min;ventana_desde;ventana_hasta;prioridad
E1;Juan Perez;Av. Mitre 123;Merlo;Buenos Aires;2;10;09:00;12:00;3
E2;Ana Gomez;Calle 9 45;Moron;Buenos Aires;1;5;;;1
```

```xml
<entregas>
  <entrega>
    <nombre>Juan Perez</nombre>
    <direccion>Av. Mitre 123</direccion>
    <localidad>Merlo</localidad>
    <provincia>Buenos Aires</provincia>
    <demanda>2</demanda>
  </entrega>
</entregas>
```

### JSON (formato completo)

Incluye fecha, depósitos, vehículos y entregas. Si falta alguna de las tres primeras, se completa con el contexto operativo pasado por argumento.

```json
{
  "fecha": "2026-10-26",
  "depositos": [
    { "id": "D1", "nombre": "Central",
      "direccion": { "direccion": "Av. Rivadavia 17000", "localidad": "Haedo", "provincia": "Buenos Aires" } }
  ],
  "vehiculos": [
    { "id": "V1", "nombre": "Camion 1", "capacidad": 20,
      "horaSalida": "08:00", "horaFin": "18:00",
      "depositoSalidaId": "D1", "depositoLlegadaId": "D1" },
    { "id": "V2", "nombre": "Camioneta", "capacidad": 8, "horaSalida": "09:00", "depositoSalidaId": "D1" }
  ],
  "entregas": [
    { "id": "E1", "nombre": "Juan Perez",
      "direccion": { "direccion": "Av. Mitre 123", "localidad": "Merlo", "provincia": "Buenos Aires" },
      "demanda": 3, "servicioMin": 10,
      "ventana": { "desde": "09:00", "hasta": "12:30" }, "prioridad": 2 }
  ]
}
```

Reglas de los vehículos y depósitos:

- `horaSalida` es obligatoria y es desde donde se calculan los horarios y las ventanas.
- `horaFin` es opcional y funciona como límite de jornada.
- `depositoLlegadaId` es opcional: si falta, el vehículo vuelve al mismo depósito del que salió.
- `depositoSalidaId` es obligatorio solo si hay más de un depósito.
- Cualquier depósito o entrega puede traer `coordenada` (`{"lat": ..., "lon": ...}`) y en ese caso no se geocodifica.

## Flujo de ejecución en dos fases

El motor nunca lanza excepciones por datos inválidos: devuelve resultados estructurados, y cada fase actúa como una compuerta.

```
Archivo -> Lectura y validación -> FASE 1: geocodificación -> FASE 2: ruteo y asignación -> Confirmación
```

**Lectura y validación** (`ResultadoLectura`): errores por fila o por campo (dirección faltante, ids duplicados, ventana invertida, vehículo sin hora de salida, depósito inexistente, etc.).

**Fase 1: geocodificación** (`Planificador.geocodificar`). Intenta Georef por lotes de hasta 1000 direcciones. Lo que Georef no resuelve va a Nominatim. Se descartan coordenadas fuera de Argentina. Resultados posibles:

- `Geocodificado`: todo resuelto, trae el problema con coordenadas.
- `DireccionesInvalidas`: lista de errores por dirección (motivo, detalle y proveedores intentados). El proceso se detiene.
- `ProblemaInvalido`: datos estructurales incorrectos.

**Fase 2: ruteo y asignación** (`Planificador.planificar`). Solo se ejecuta con todas las coordenadas válidas. Pide la matriz a Valhalla y completa los pares sin ruta con OSRM. Resultados posibles:

- `RutaImposible`: quedan pares de puntos sin ruta incluso tras el fallback. No se avanza y se informa qué pares son.
- `ProveedorNoDisponible`: ningún servicio de ruteo respondió.
- `PlanGenerado`: el plan, con estado `valido` o `requiere_confirmacion`.

**Confirmación** (`Planificador.confirmar`). Si el solver omitió entregas (capacidad, ventanas, jornada o restricciones combinadas), el plan queda en `requiere_confirmacion`. Solo pasa a `confirmado` si se aceptan explícitamente todos los ids omitidos.

## Uso desde Java

Ejemplo completo, con datos propios:

```java
ConfiguracionMotor cfg = ConfiguracionMotor.desdeArchivo(Path.of("config.properties"));
Planificador planificador = Planificador.porDefecto(cfg,
        HttpTransporteJdk.porDefecto(cfg.timeoutHttpSeg(), cfg.nominatimEmail()));

Deposito deposito = new Deposito("D1", "Central",
        new DireccionPostal("Av. Rivadavia 17000", "Haedo", "Buenos Aires"), null);
Vehiculo v1 = new Vehiculo("V1", "Camion 1", 20, LocalTime.of(8, 0), LocalTime.of(18, 0), "D1", null);
Vehiculo v2 = new Vehiculo("V2", "Camioneta", 8, LocalTime.of(9, 0), null, "D1", null);
ContextoOperativo contexto = new ContextoOperativo(LocalDate.of(2026, 10, 26), List.of(deposito), List.of(v1, v2));

Path archivo = Path.of("clientes.csv");
ResultadoLectura lectura = ProblemReaderFactory.para(archivo).leer(archivo, contexto);
if (!lectura.errores().isEmpty()) {
    lectura.errores().forEach(System.out::println);
    return;
}

switch (planificador.geocodificar(lectura.problema())) {
    case ProblemaInvalido p -> p.errores().forEach(System.out::println);
    case DireccionesInvalidas d -> d.errores().forEach(System.out::println);
    case Geocodificado g -> {
        switch (planificador.planificar(g.problema())) {
            case RutaImposible r -> r.errores().forEach(System.out::println);
            case ProveedorNoDisponible p -> System.out.println(p.detalle());
            case PlanGenerado pg -> {
                PlanDeRuteo plan = pg.plan();
                if (plan.estado() == EstadoPlan.REQUIERE_CONFIRMACION) {
                    plan.omisiones().forEach(System.out::println);
                    // Solo después de que el usuario acepte las omisiones:
                    Set<String> aceptadas = plan.omisiones().stream()
                            .map(Omision::entregaId).collect(Collectors.toSet());
                    plan = planificador.confirmar(plan, aceptadas);
                }
                JsonSupport.mapper().writeValue(Path.of("plan.json").toFile(), plan);
            }
        }
    }
}
```

Para leer un JSON completo en lugar de un CSV, el código es el mismo: `ProblemReaderFactory.para(Path.of("problema.json"))` elige el lector por extensión, y el contexto puede ser `null`.

## Formato de salida

El plan se serializa con `JsonSupport.mapper()`. Los enums van en minúscula y los campos nulos se omiten. Ejemplo abreviado:

```json
{
  "version": 1,
  "fecha": "2026-10-26",
  "estado": "valido",
  "rutas": [
    {
      "vehiculo": { "id": "V1", "capacidad": 20, "horaSalida": "08:00" },
      "origen": { "lat": -34.64, "lon": -58.59 },
      "destino": { "lat": -34.64, "lon": -58.59 },
      "salida": "2026-10-26T08:00",
      "regreso": "2026-10-26T11:42",
      "paradas": [
        {
          "orden": 1,
          "entrega": { "id": "E1", "nombre": "Juan Perez", "demanda": 3, "servicioMin": 10 },
          "estado": "pendiente",
          "eta": "2026-10-26T09:00",
          "distanciaDesdeAnteriorM": 8420.5,
          "duracionDesdeAnteriorSeg": 910,
          "polilineaTramo": "..."
        }
      ],
      "distanciaRegresoM": 7100.0,
      "duracionRegresoSeg": 780,
      "polilinea": "...",
      "distanciaTotalM": 31250.0,
      "duracionTotalSeg": 13320
    }
  ],
  "omisiones": [],
  "advertencias": [],
  "distanciaTotalM": 31250.0,
  "duracionTotalSeg": 13320
}
```

- Las **polilíneas** están en formato polyline con precisión 6 (compatible con Leaflet, Mapbox, Google Maps con decodificador de precisión 1e-6).
- `eta` es el momento en que empieza el servicio en la parada (si el vehículo llega antes de la ventana, espera).
- Estados de parada: ver la tabla de la sección de reoptimización.
- Los depósitos no aparecen como paradas.

Los resultados de cada fase también se serializan con un campo `resultado` que indica el tipo, por ejemplo:

```json
{
  "resultado": "direcciones_invalidas",
  "errores": [
    {
      "entidadId": "E3",
      "tipo": "entrega",
      "direccion": { "direccion": "Calle Inexistente 1", "localidad": "Merlo", "provincia": "Buenos Aires" },
      "motivo": "no_encontrada",
      "detalle": "georef: Georef no encontro la direccion | nominatim: Nominatim no encontro resultados",
      "proveedoresIntentados": ["georef", "nominatim"]
    }
  ]
}
```

## Reoptimización durante la jornada

Se usa cuando los camiones **ya salieron**, es decir, ya llevan la mercadería cargada. Por eso:

- **No se pueden agregar clientes** a un plan en marcha: la mercadería no está en los camiones. Un cliente nuevo implica generar un plan nuevo (Fase 1 y 2).
- Solo se recalcula la ruta **del chofer que tiene al cliente afectado**. Las rutas de los demás choferes, con sus paradas, ETA y polilíneas, quedan idénticas.
- Un cliente no puede pasar de un camión a otro.
- **Solo se recalculan las paradas pendientes o postergadas.** Las demás salen de la ruta.

### Estados de parada

| Estado | Significado | Efecto al reoptimizar su ruta |
|---|---|---|
| `pendiente` | Por visitar | Se recalcula |
| `reprogramado` | Postergado | Se recalcula, y marcar una parada así dispara el recálculo de su ruta |
| `en_camino` | El chofer va hacia ese cliente | Se recalcula igual (puede convenir reordenar) |
| `entregado` | Entregado | Sale de la ruta |
| `cancelado` | Cancelado en puerta: el camión ya llegó al cliente | Sale de la ruta |
| `devolver_a_deposito` | Cancelado desde el depósito antes de llegar | Sale de la ruta y dispara el recálculo; la mercadería vuelve al depósito con el camión |

Al reoptimizar, las paradas pendientes recalculadas quedan como `pendiente`, salvo la primera si ya estaba `en_camino` en ese mismo vehículo.

Como las paradas cerradas salen de la ruta al reoptimizarla, el historial de entregas debe conservarse en el sistema de pedidos.

Hay dos formas de cambiar estados, según quién origina el evento (ver la sección siguiente).

### Qué dispara el recálculo de una ruta

1. Una parada con estado `devolver_a_deposito`.
2. Una parada con estado `reprogramado`.
3. Una `ModificacionEntrega` sobre alguno de sus clientes.

Las acciones `devolverADeposito` y `reprogramar` de `CambiosJornada` aplican el estado correspondiente y recalculan en una sola operación, así que no hace falta editar el JSON antes.

Si ninguna ruta cumple alguna de estas condiciones, el plan se devuelve sin cambios. Las rutas que no se recalculan conservan todo tal cual, incluso sus paradas ya entregadas.

### Modificaciones permitidas

`ModificacionEntrega` admite, por cliente (los campos en `null` no cambian):

- `ventana`: nueva ventana horaria, o `quitarVentana = true` para eliminarla.
- `prioridad` y `servicioMin`.
- `direccion`: se geocodifica solo esa dirección. Si no se resuelve, el resultado es `DireccionesInvalidas` y el plan no se modifica.

La demanda no se puede modificar, porque la mercadería ya está cargada. Solo se pueden modificar clientes en estado `pendiente`, `reprogramado` o `en_camino`.

### Acciones de la web y eventos del chofer

**Acciones de la web** (recalculan la ruta). Van dentro de `CambiosJornada`:

- `devolverADeposito`: lista de ids de entrega cancelados desde el depósito. La parada sale de la ruta y la mercadería vuelve al depósito con el camión.
- `reprogramar`: lista de `Reprogramacion(entregaId, ventana)`. La reprogramación es **siempre dentro de la misma jornada**, con una ventana horaria nueva opcional. Si el cliente pasa la entrega a otro día, la mercadería vuelve al depósito y se carga de nuevo ese día: se usa `devolverADeposito`.

El motor valida las acciones: la entrega debe existir y estar `pendiente`, `en_camino` o `reprogramado`; no puede repetirse ni estar a la vez en devolver y reprogramar, ni modificarse si se devuelve.

**Eventos del chofer** (no recalculan nada). Se aplican sobre el plan con `PlanDeRuteo.conEstadoDeParada(entregaId, estado)`, que devuelve un plan nuevo:

- Estados permitidos: `pendiente`, `en_camino`, `entregado` y `cancelado` (en puerta).
- Un estado cerrado (`entregado`, `cancelado`, `devolver_a_deposito`) no se puede reabrir.
- `reprogramado` y `devolver_a_deposito` no se aplican por este método, porque recalculan: se hacen con `CambiosJornada`.
- Si el cambio no es válido (entrega inexistente, estado cerrado, estado no permitido), lanza `IllegalArgumentException`.

El motor no guarda nada: recibe un plan y devuelve otro. Guardarlo le corresponde a quien lo consuma (la futura API).

### Ejemplo

```java
PlanDeRuteo plan = JsonSupport.mapper().readValue(Path.of("plan.json").toFile(), PlanDeRuteo.class);

// Evento del chofer: no recalcula.
plan = plan.conEstadoDeParada("E1", EstadoParada.ENTREGADO);

// Acciones de la web: recalculan solo la ruta afectada.
CambiosJornada cambios = new CambiosJornada(
        LocalDateTime.now(),
        List.of(new PosicionVehiculo("V1", new Coordenada(-34.66, -58.73), LocalDateTime.now())),
        List.of(new ModificacionEntrega("E5", null, false, 3, null, null)),               // prioridad nueva
        List.of("E3"),                                                                     // devolver al depósito
        List.of(new Reprogramacion("E4", new VentanaHoraria(LocalTime.of(15, 0), LocalTime.of(17, 0)))));

ResultadoPlanificacion resultado = new Reoptimizador(planificador).reoptimizar(plan, cambios);
```

### Reglas

- La ruta afectada parte de la **posición GPS** reportada del chofer. Si no hay, usa la última posición conocida del plan o, en su defecto, la de la última parada cerrada. Termina en su depósito de llegada, que por defecto es el de salida.
- La matriz solo incluye los puntos de esa ruta: depósito, posición del chofer y clientes pendientes.
- Como la mercadería ya está a bordo, la capacidad nunca es un motivo de omisión en la reoptimización. Sí pueden omitirse clientes por ventanas o fin de jornada: se omiten primero los de menor prioridad, y el plan queda en `requiere_confirmacion`.
- En una ruta reoptimizada, `distanciaTotalM` y `duracionTotalSeg` corresponden al tramo restante desde la posición del chofer.
- Si hubo cambios, el plan resultante tiene `version` incrementada.
- Si algo falla (cambio sobre una parada cerrada, id inexistente, dirección que no se resuelve, ruta imposible), no se modifica nada y se devuelve el error correspondiente.

## Cómo decide el solver

- **Objetivo**: minimiza distancia (por defecto) o tiempo total, según `Solver.objetivo`.
- **Capacidad**: cada vehículo tiene la suya y la suma de demandas de su ruta no puede superarla.
- **Ventanas horarias**: el vehículo puede esperar si llega antes; si llega tarde, no es válido.
- **Jornada**: `horaFin` es un límite duro para el regreso al depósito.
- **Omisiones**: cada entrega puede omitirse pagando una penalización de `Solver.penalizacionBase × prioridad`. El valor por defecto es alto para que omitir sea el último recurso. Una prioridad mayor hace más costoso omitir ese cliente.
- **Balanceo**: con `Solver.coeficienteBalanceo` mayor que 0 se penaliza la diferencia entre la ruta más larga y la más corta, lo que reparte mejor las entregas entre vehículos. Con 0, el solver puede concentrar todo en pocos vehículos si eso reduce la distancia total.
- **Causa de las omisiones**: el plan indica un motivo aproximado (`capacidad_individual`, `ventana_horaria_imposible`, `restricciones_combinadas`, `sin_solucion`).

## Estructura del proyecto

Hay muchos archivos porque Java exige un archivo por tipo público. La mayoría son registros (`record`) de pocas líneas.

```
src/main/java/vrp/
  legacy/            Código original (Main, OrTools.TSP_Solution, etc.), sin cambios salvo el paquete
  engine/
    model/           Datos de entrada: Deposito, Entrega, Vehiculo, ProblemaVRP, EstadoParada...
    io/              Lectores (JSON, CSV, Excel, XML), ValidadorProblema, JsonSupport
    geocoding/       GeorefProvider, NominatimLocalProvider, GeocodingChain
    routing/         ValhallaClient, OsrmClient, MatrixChain, GeometryChain, MatrizRuteo
    solver/          ModeloBuilder, OrToolsVrpSolver, Simulador, ArcosRelevantes
    plan/            Salida: PlanDeRuteo, RutaVehiculo, Parada, Omision, Advertencia
    resultado/       Resultados de cada fase y sus errores
    service/         Planificador, Reoptimizador, ConstructorPlan, CambiosJornada, ModificacionEntrega, Reprogramacion
    config/          ConfiguracionMotor
    http/            HttpTransporte (interfaz) y su implementación con el cliente HTTP de Java
    util/            Polyline6, Tiempos
```

Las interfaces permiten cambiar o simular los proveedores: `GeocodingProvider`, `RoutingMatrixProvider`, `RouteGeometryProvider`, `VrpSolver`, `ProblemReader` y `HttpTransporte`.

## Tests

```bash
mvn test
```

Cubren lectura de los cuatro formatos, geocodificación en cadena, matrices por bloques y fallback, las dos fases del planificador, omisiones y confirmación, y reoptimización por ruta afectada. Usan proveedores simulados: no necesitan red ni servicios. `OrToolsVrpSolverTest` usa el solver real y requiere las librerías nativas de OR-Tools.

## Limitaciones y pendientes

- No hay línea de comandos ni API web: hoy el motor se usa desde código Java.
- Los parámetros de Georef (`provincia`, `localidad`) siguen la documentación, pero conviene verificar cómo responde con tus localidades.
- No hay caché de geocodificación: cada ejecución vuelve a consultar. Georef tiene cuotas (10 consultas por segundo, 40 por minuto, 2000 por hora y 10000 por día para usuarios externos a la administración nacional).
- Un solo par de puntos sin ruta (tras el fallback) bloquea el plan completo, por diseño.
- La reoptimización no admite clientes nuevos (por diseño, porque la mercadería ya está cargada); eso requiere un plan nuevo.
- Las paradas cerradas desaparecen del plan al reoptimizar su ruta: el historial de entregas debe vivir en el sistema de pedidos.
- Los horarios se manejan dentro de un mismo día y sin zona horaria.
- No hay registro de eventos (logs).
- No están implementados: restricciones de compatibilidad vehículo-cliente, retiros y entregas combinados, ni múltiples viajes por vehículo.

## Código legacy

El flujo original (`vrp.legacy`) se conserva sin modificaciones: lee `nombre;direccion`, geocodifica con Nominatim, arma la matriz con OSRM y resuelve un único vehículo con `OrTools.TSP_Solution`. Se puede eliminar cuando el motor nuevo esté validado con datos reales.
