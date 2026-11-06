# VRP Engine

Motor de enrutamiento de vehículos (VRP) para repartos en Argentina. Librería Java modular, testeable y diseñada para ser consumida por una API web. JSON es el formato de intercambio.

## Contenido

1. [Qué hace](#qué-hace)
2. [Estado actual](#estado-actual)
3. [Requisitos](#requisitos)
4. [Configuración](#configuración)
5. [Formatos de entrada](#formatos-de-entrada)
6. [Flujo de ejecución en dos fases](#flujo-de-ejecución-en-dos-fases)
7. [Uso desde Java (Fachada VrpEngine)](#uso-desde-java-fachada-vrpengine)
8. [Formato de salida](#formato-de-salida)
9. [Reoptimización durante la jornada](#reoptimización-durante-la-jornada)
10. [Cómo decide el solver](#cómo-decide-el-solver)
11. [Estructura del proyecto](#estructura-del-proyecto)
12. [Tests](#tests)
13. [Limitaciones y pendientes](#limitaciones-y-pendientes)

## Qué hace

- **Estructura multi-módulo Maven**: `vrp-engine` (librería) + `vrp-manual-tests` (pruebas con `Main`).
- **Fachada `VrpEngine`**: expone funciones separadas para geocodificar, obtener matriz, calcular rutas, codificar/decodificar planes.
- Lee clientes desde **CSV, Excel (.xls/.xlsx), XML o JSON**, con dirección, localidad y provincia en campos separados.
- **Geocodificación** con Georef Argentina (POST por lotes, hasta 1000/día) y Nominatim local como fallback. Google excluido.
- Calcula **matriz de distancias/tiempos** con Valhalla (primario) y OSRM (fallback).
- Resuelve con **OR-Tools** para `n` vehículos heterogéneos: capacidad, ventanas, tiempos de servicio, prioridades, **balanceo opcional**, **máx. viajes por vehículo**.
- **Depósitos con ventana horaria y tiempo de estadía** (carga/descarga). Visitas a depósito en mitad de ruta permitidas.
- Paradas con tipo `ENTREGA` o `DEPOSITO`.
- Si no puede atender un cliente, lo informa como omisión y exige confirmación explícita.
- Plan en JSON con estado por parada; **reoptimización mid-jornada** desde posición GPS del chofer.

## Estado actual

El motor está implementado y probado con proveedores simulados (54 tests).

| Componente | Estado |
|---|---|
| Modelos, validaciones, lectores, cadenas de proveedores, simulación, armado del plan, reoptimización | ✅ Implementado y cubierto por tests |
| Georef (lotes 1000), Nominatim, Valhalla, OSRM (clientes HTTP) | ✅ Implementado, probado con respuestas simuladas |
| Solver OR-Tools (`OrToolsVrpSolver`) | ✅ Implementado, test con OR-Tools nativo (`OrToolsVrpSolverTest`) |
| Fachada `VrpEngine` con funciones separadas | ✅ Implementado |
| API web, línea de comandos, persistencia | ❌ No existen todavía (diseñado para API) |

Ver [Limitaciones y pendientes](#limitaciones-y-pendientes).

## Requisitos

- **Java 17** y Maven 3.9+.
- Dependencias (en `pom.xml` padre): Jackson 2.22, Apache POI 5.5, Commons CSV 1.14, OR-Tools Java 9.10, JUnit 5.11.
- Servicios externos:
  - **Georef Argentina**: `https://apis.datos.gob.ar/georef/api/v2.0` (público, cuota 1000/día).
  - **Nominatim** local (fallback, opcional).
  - **Valhalla** y/o **OSRM** (al menos uno obligatorio para ruteo).

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
| `Georef.url` | URL pública de Georef | Base de la API de Georef (respeta cuota 1000/día) |
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

## Uso desde Java (Fachada VrpEngine)

La forma recomendada es usar la fachada `VrpEngine` que expone funciones separadas para cada paso:

```java
ConfiguracionMotor cfg = ConfiguracionMotor.desdeArchivo(Path.of("config.properties"));
VrpEngine engine = VrpEngine.crear(cfg);  // Usa HttpTransporteJdk por defecto

// 1. Leer archivo (CSV, Excel, XML, JSON)
ContextoOperativo contexto = VrpEngine.contextoDefecto(
        LocalDate.now(),
        List.of(VrpEngine.deposito("D1", "Central",
                VrpEngine.direccion("Av. Rivadavia 17000", "Haedo", "Buenos Aires"),
                LocalTime.of(6, 0), LocalTime.of(22, 0), 30)),
        List.of(VrpEngine.vehiculo("V1", "Camión 1", 20,
                LocalTime.of(8, 0), LocalTime.of(18, 0), "D1", "D1", 1))
);

ResultadoLectura lectura = engine.leerArchivo(Path.of("clientes.csv"), contexto);
if (!lectura.errores().isEmpty()) { /* manejar errores */ }

// 2. Geocodificar (Fase 1) - Georef hasta 1000, luego Nominatim
ResultadoFase1 fase1 = engine.geocodificar(lectura.problema());
if (fase1 instanceof DireccionesInvalidas d) { /* manejar errores */ }
if (fase1 instanceof ProblemaInvalido p) { /* manejar errores */ }
ProblemaVRP problema = ((Geocodificado) fase1).problema();

// 3. Calcular matriz (opcional, para inspección)
MatrizRuteo matriz = engine.calcularMatriz(problema.coordenadasTodas());  // requiere método helper

// 4. Planificar (Fase 2)
ResultadoFase2 fase2 = engine.planificar(problema);
if (fase2 instanceof RutaImposible r) { /* manejar errores */ }
if (fase2 instanceof ProveedorNoDisponible p) { /* manejar errores */ }
PlanDeRuteo plan = ((PlanGenerado) fase2).plan();

// 5. Confirmar si hay omisiones
if (plan.estado() == EstadoPlan.REQUIERE_CONFIRMACION) {
    Set<String> aceptadas = plan.omisiones().stream()
            .map(Omision::entregaId).collect(Collectors.toSet());
    plan = engine.confirmar(plan, aceptadas);
}

// 6. Codificar/guardar plan
String json = engine.codificarPlan(plan);
Files.writeString(Path.of("plan.json"), json);

// Para decodificar luego:
PlanDeRuteo planRecuperado = engine.decodificarPlan(json);

// Reoptimización mid-jornada
CambiosJornada cambios = new CambiosJornada(/* ... */);
ResultadoPlanificacion reopt = engine.reoptimizar(plan, cambios);
```

**Funciones separadas disponibles en `VrpEngine`:**
- `leerArchivo`, `leerJson` — Lectura de archivos
- `geocodificar`, `geocodificarDirecciones` — Fase 1
- `calcularMatriz`, `obtenerGeometria` — Matriz y geometría
- `planificar` — Fase 2 (con o sin posiciones GPS)
- `confirmar` — Aceptar omisiones
- `reoptimizar` — Reoptimización mid-jornada
- `codificarPlan`, `decodificarPlan` — JSON I/O
- Métodos helper: `contextoDefecto`, `direccion`, `entrega`, `vehiculo`, `deposito`

Para leer JSON completo: `engine.leerJson(jsonString, contexto)` o `engine.leerArchivo(Path.of("problema.json"), null)`.

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

Proyecto multi-módulo Maven:

```
vrp-parent/                 # POM padre
├── vrp-engine/             # Librería principal
│   └── src/main/java/vrp/engine/
│       ├── model/          # Deposito(ventana,estadia), Entrega, Vehiculo(maxViajes), ProblemaVRP...
│       ├── io/             # Lectores (JSON, CSV, Excel, XML), ProblemReaderFactory, JsonSupport
│       ├── geocoding/      # GeorefProvider(lotes 1000), NominatimLocalProvider, GeocodingChain(cuota)
│       ├── routing/        # ValhallaClient, OsrmClient, MatrixChain, GeometryChain, MatrizRuteo
│       ├── solver/         # OrToolsVrpSolver, ModeloBuilder, Simulador, ArcosRelevantes
│       ├── plan/           # PlanDeRuteo, RutaVehiculo, Parada(tipo ENTREGA/DEPOSITO), Omision...
│       ├── resultado/      # ResultadoFase1/2, Geocodificado, PlanGenerado, DireccionesInvalidas...
│       ├── service/        # Planificador, Reoptimizador, ConstructorPlan, VrpEngine(fachada)...
│       ├── config/         # ConfiguracionMotor (record, desde .properties)
│       ├── http/           # HttpTransporte, HttpTransporteJdk
│       └── util/           # Polyline6, Tiempos
└── vrp-manual-tests/       # Módulo de pruebas con Main
    └── src/main/java/vrp/manual/Main.java  # 12+ escenarios de prueba
```

Interfaces para testabilidad e inyección: `GeocodingProvider`, `RoutingMatrixProvider`, `RouteGeometryProvider`, `VrpSolver`, `ProblemReader`, `HttpTransporte`.

**Código legacy eliminado** (estaba en `vrp.legacy` en la versión original).

## Tests

```bash
# Tests unitarios e integración (vrp-engine)
mvn test

# Pruebas manuales con Main (vrp-manual-tests)
mvn exec:java -pl vrp-manual-tests -Dexec.mainClass=vrp.manual.Main
```

**Tests automatizados (54):** lectura 4 formatos, geocodificación en cadena (cuota 1000), matrices bloques/fallback, Fase 1 y 2, omisiones/confirmación, reoptimización por ruta, polyline6, estados parada.

**Main manual (vrp-manual-tests):** 12 escenarios que cubren modelos, JSON, geocodificación, matriz, planificación, codificación/decodificación, paradas depósito, reoptimización, multi-vehículo maxViajes, motivo capacidad_insuficiente, cuota Georef, fachada VrpEngine.

`OrToolsVrpSolverTest` usa solver real (requiere librerías nativas OR-Tools).

## Limitaciones y pendientes

- No hay API web ni CLI: el motor se usa desde código Java (fachada `VrpEngine` lista para Spring/Quarkus).
- Parámetros Georef (`provincia`, `localidad`) según doc; verificar con tus localidades.
- Sin caché de geocodificación: cada ejecución consulta de nuevo. Cuotas Georef: 10 qps, 40/min, 2000/h, 10000/día (externos).
- Un par sin ruta (tras fallback) bloquea el plan (por diseño).
- Reoptimización no admite clientes nuevos (mercadería ya cargada); requiere plan nuevo.
- Paradas cerradas salen del plan al reoptimizar; historial en sistema de pedidos.
- Horarios en un día, sin zona horaria.
- Sin logs/eventos.
- Pendientes: compatibilidad vehículo-cliente, pickups+deliveries, **múltiples viajes por vehículo** (modelo `maxViajes` existe, solver pendiente), caché de geocodificación, API web de ejemplo.

