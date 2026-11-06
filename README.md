# VRP Engine

Motor de enrutamiento de vehículos (VRP) para logística. Librería Java modular, testeable y diseñada para ser consumida por una API web. JSON es el formato de intercambio.

**Novedad v2.0**: El motor ahora es un **motor de ruteo puro** que trabaja exclusivamente con coordenadas (latitud/longitud). La geocodificación se ha extraído completamente y debe realizarse externamente antes de usar el motor.

## Contenido

1. [Qué hace](#qué-hace)
2. [Estado actual](#estado-actual)
3. [Requisitos](#requisitos)
4. [Configuración](#configuración)
5. [Motor de Geocodificación (Nuevo)](#motor-de-geocodificación-nuevo)
6. [Formatos de entrada](#formatos-de-entrada)
7. [Flujo de ejecución](#flujo-de-ejecución)
8. [Uso desde Java (Fachada VrpEngine)](#uso-desde-java-fachada-vrpengine)
9. [Formato de salida](#formato-de-salida)
10. [Reoptimización durante la jornada](#reoptimización-durante-la-jornada)
11. [Cómo decide el solver](#cómo-decide-el-solver)
12. [Estructura del proyecto](#estructura-del-proyecto)
13. [Tests](#tests)
14. [Limitaciones y pendientes](#limitaciones-y-pendientes)

## Qué hace

- **Fachada `VrpEngine`**: expone funciones para leer archivos, calcular matriz de distancias/tiempos, planificar rutas, codificar/decodificar planes.
- Lee clientes desde **CSV, Excel (.xls/.xlsx), XML o JSON** con coordenadas obligatorias (lat/lon).
- Calcula **matriz de distancias/tiempos** con Valhalla (primario) y OSRM (fallback).
- Resuelve con **OR-Tools** para `n` vehículos heterogéneos: capacidad, ventanas, tiempos de servicio, prioridades, **balanceo opcional**. Modelo `maxViajes` existe en vehículo (solver pendiente).
- **Depósitos con ventana horaria y tiempo de estadía** (carga/descarga). Visitas a depósito en mitad de ruta permitidas.
- Paradas con tipo `ENTREGA` o `DEPOSITO`.
- Si no puede atender un cliente, lo informa como omisión y exige confirmación explícita.
- Plan en JSON con estado por parada; **reoptimización mid-jornada** desde posición GPS del chofer.
- **Logging SLF4J** para cálculo de matrices y ejecución del solver OR-Tools.
- **Configuración vía variables de entorno** con fallback a `config.properties`.

## Estado actual

| Componente | Estado |
|---|---|
| Modelos (coordenadas obligatorias), validaciones, lectores, matriz, solver, plan, reoptimización | ✅ Implementado |
| Valhalla, OSRM (clientes HTTP nativos Java 25) | ✅ Implementado |
| Solver OR-Tools (`OrToolsVrpSolver`) | ✅ Implementado |
| Fachada `VrpEngine` pura (sin geocodificación) | ✅ Implementado |
| Múltiples viajes por vehículo (`maxViajes`) | ⏳ Modelo listo, solver pendiente |
| API web, línea de comandos, persistencia | ❌ No existen (diseñado para API) |

## Requisitos

- **Java 25 LTS** y Maven 3.9+.
- Dependencias: Jackson 2.22, Apache POI 5.5, Commons CSV 1.14, OR-Tools Java 9.15, SLF4J 2.0, JUnit 5.11.
- Servicios externos (al menos uno obligatorio para ruteo):
  - **Valhalla** local (puerto 8002 por defecto)
  - **OSRM** local (puerto 5000 por defecto)

## Configuración

El motor lee un archivo `config.properties` (no versionado, está en `.gitignore`). También se puede configurar mediante **variables de entorno** (tienen prioridad sobre el archivo).

Variables de entorno soportadas:
- `VALHALLA_URL` - URL base de Valhalla (ej: `http://localhost:8002`)
- `OSRM_URL` - URL base de OSRM (ej: `http://localhost:5000`)
- `VALHALLA_COSTING` - Perfil de ruteo Valhalla (por defecto `auto`)
- `MATRIZ_TAMANO_BLOQUE` - Tamaño de bloque para matrices (por defecto 50)
- `HTTP_TIMEOUT_SEG` - Timeout HTTP en segundos (por defecto 30)
- `SOLVER_OBJETIVO` - `DISTANCIA` o `TIEMPO` (por defecto `DISTANCIA`)
- `SOLVER_TIEMPO_LIMITE` - Tiempo máximo solver en segundos (por defecto 10)
- `SOLVER_PENALIZACION_BASE` - Penalización base por omisión (por defecto 10000000)
- `SOLVER_COEFICIENTE_BALANCEO` - Coeficiente de balanceo (por defecto 0)

Ejemplo `config.properties`:

```properties
# Valhalla (motor de ruteo principal - requiere instancia local)
Valhalla.url=http://localhost:8002
Valhalla.costing=auto

# OSRM (motor de ruteo alternativo - requiere instancia local)
OSRM.url=http://localhost:5000

# Tamaño de bloques para matrices grandes (evita límites de URL)
Matriz.tamanoBloque=50

# Timeout HTTP en segundos
Http.timeoutSeg=30

# Solver OR-Tools
Solver.objetivo=DISTANCIA
Solver.tiempoLimiteSeg=10
Solver.penalizacionBase=10000000
Solver.coeficienteBalanceo=0
```

## Formatos de entrada

**Importante**: Todos los formatos requieren **coordenadas obligatorias** (`lat`/`lon` o `latitud`/`longitud`) para cada entrega y depósito. No hay geocodificación interna.

### Tabulares (CSV, Excel, XML)

Traen **solo los clientes**. La fecha, los depósitos y los vehículos se pasan por argumento (`ContextoOperativo`).

Columnas reconocidas (no distinguen mayúsculas, tildes, espacios ni guiones bajos):

| Columna | Obligatoria | Descripción |
|---|---|---|
| `id` | No | Si falta, se genera `E1`, `E2`, ... según la fila |
| `nombre` | No | Nombre del cliente |
| `lat` / `latitud` | **Sí** | Latitud (decimal) |
| `lon` / `lng` / `longitud` | **Sí** | Longitud (decimal) |
| `demanda` | No | Unidades que ocupa la entrega (por defecto 0) |
| `servicio_min` / `servicio` / `tiempo_de_servicio` | No | Minutos de servicio en el cliente |
| `ventana_desde` / `desde` | No | Ventana horaria inicio `HH:mm` |
| `ventana_hasta` / `hasta` | No | Ventana horaria fin `HH:mm` |
| `prioridad` | No | Entero, mayor es más importante (por defecto 1) |

CSV: el separador (`;` o `,`) se detecta solo. Excel: se lee la primera hoja, con encabezados en la primera fila. XML: cada elemento con campos simples es un registro.

```csv
id;nombre;lat;lon;demanda;servicio_min;ventana_desde;ventana_hasta;prioridad
E1;Juan Perez;-34.6412;-58.5923;2;10;09:00;12:00;3
E2;Ana Gomez;-34.6521;-58.5812;1;5;;;1
```

```xml
<entregas>
  <entrega>
    <id>E1</id>
    <nombre>Juan Perez</nombre>
    <lat>-34.6412</lat>
    <lon>-58.5923</lon>
    <demanda>2</demanda>
    <servicio_min>10</servicio_min>
  </entrega>
</entregas>
```

### JSON (formato completo)

Incluye fecha, depósitos, vehículos y entregas. Si falta alguna de las tres primeras, se completa con el contexto operativo pasado por argumento. **Todos los depósitos y entregas deben traer `coordenada`**.

```json
{
  "fecha": "2026-10-26",
  "depositos": [
    { "id": "D1", "nombre": "Central",
      "coordenada": { "lat": -34.6410, "lon": -58.5900 },
      "ventanaDesde": "06:00", "ventanaHasta": "22:00", "estadiaMin": 30 }
  ],
  "vehiculos": [
    { "id": "V1", "nombre": "Camión 1", "capacidad": 20,
      "horaSalida": "08:00", "horaFin": "18:00",
      "depositoSalidaId": "D1", "depositoLlegadaId": "D1", "maxViajes": 1 }
  ],
  "entregas": [
    { "id": "E1", "nombre": "Juan Perez",
      "coordenada": { "lat": -34.6412, "lon": -58.5923 },
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
- **Coordenada obligatoria** en depósitos y entregas.

## Motor de Geocodificación (Nuevo)

El motor de geocodificación es un componente **separado e independiente** del VRP Engine. Se encarga exclusivamente de recibir direcciones y devolver coordenadas. No procesa archivos, solo direcciones.

### Características principales

- **Cadena de proveedores configurable**: Georef (principal) → Nominatim (fallback), orden fácilmente modificable
- **Fallback inteligente**: El proveedor principal resuelve lo que puede; solo los fallos pasan al siguiente
- **Manejo de cuotas**: Georef respeta cuota diaria (1000); si se excede, el exceso va directo al fallback
- **Batching automático**: Agrupa en lotes según capacidad del proveedor (Georef: 100 por request)
- **Rate limiting**: Nominatim configurable con pausa entre requests
- **Logging SLF4J**: Trazabilidad completa de qué proveedor resolvió cada dirección
- **Configuración por variables de entorno** con fallback a `geocoding.properties`

### Proveedores incluidos

| Proveedor | Rol | Cuota | Batch | Rate Limit |
|---|---|---|---|---|
| **Georef** (datos.gob.ar) | Principal | 1000/día | 100 por request | No |
| **Nominatim** (local) | Fallback | Sin cuota dura | No (1 por request) | Configurable (pausaMs) |

### Uso desde Java

```java
// 1. Configurar (archivo + variables de entorno)
GeocodingConfig cfg = GeocodingConfig.desdeArchivo(Path.of("geocoding.properties"));
GeocodingEngine engine = GeocodingEngine.crear(cfg);

// 2. Geocodificar una dirección
Direccion dir = Direccion.de("Av. Rivadavia 17000", "Haedo", "Buenos Aires");
ResultadoGeocodificacion resultado = engine.geocodificar("CLIENTE-1", dir);

if (resultado.esExitoso()) {
    Coordenada coord = resultado.coordenada();  // lat/lon listas para VRP Engine
    System.out.println("Resuelto por: " + resultado.proveedor()); // "georef" o "nominatim"
} else {
    System.err.println("Fallo: " + resultado.motivoFallo().descripcion());
}

// 3. Geocodificar múltiples direcciones (una sola llamada)
List<Direccion> direcciones = List.of(dir1, dir2, dir3, ...);
Map<String, ResultadoGeocodificacion> resultados = engine.geocodificarDirecciones(direcciones);

// 4. Solo exitosas (lanza excepción si hay fallos)
Map<String, Coordenada> coords = engine.geocodificarExigiendoExito(solicitudes);
```

### Flujo interno de la cadena

```
Direcciones
    │
    ▼
┌─────────────────────┐
│   GEOREF (Principal)│  ◄── Cuota 1000/día, lotes de 100
│  - Resuelve válidas │
│  - Fallos → next    │
│  - Exceso cuota → next
└─────────────────────┘
    │
    ▼ (solo fallos + exceso cuota)
┌─────────────────────┐
│  NOMINATIM (Fallback)│ ◄── Sin cuota, rate-limited
│  - Resuelve resto   │
│  - Fallos finales   │
└─────────────────────┘
    │
    ▼
Resultados consolidados (mejor error por dirección)
```

### Configuración (geocoding.properties)

```properties
# Georef Argentina (principal, hasta 1000 consultas/día)
Georef.url=https://apis.datos.gob.ar/georef/api/v2.0

# Nominatim local (fallback)
Nominatim.url=http://localhost:8080
Nominatim.email=tu@email.com
Nominatim.pausaMs=1000

# Timeout HTTP en segundos
Http.timeoutSeg=30
```

### Variables de entorno (prioridad sobre archivo)

- `GEOREF_URL` - URL base de Georef
- `NOMINATIM_URL` - URL base de Nominatim
- `NOMINATIM_EMAIL` - Email de contacto para User-Agent
- `NOMINATIM_PAUSA_MS` - Pausa entre requests (ms)
- `HTTP_TIMEOUT_SEG` - Timeout HTTP

### Extensibilidad: Agregar proveedores

Para agregar un nuevo proveedor (ej. Google Maps, Mapbox, Azure Maps):

1. Implementar `GeocodingProvider`
2. Agregar al chain en `GeocodingConfig.crearChain()` en el orden deseado
3. El chain maneja automáticamente fallback, cuotas y batching

```java
// Ejemplo: cadena personalizada para tests
GeocodingChain chain = new GeocodingChain(List.of(
    new MiProveedorPrincipal(),
    new MiProveedorFallback()
));
GeocodingEngine engine = GeocodingEngine.crear(chain);
```

### Integración con VRP Engine

El flujo típico de uso combinado:

```java
// 1. Geocodificar direcciones externas
GeocodingEngine geoEngine = GeocodingEngine.crear(cfg);
Map<String, Coordenada> coords = geoEngine.geocodificarExigiendoExito(solicitudes);

// 2. Construir problema VRP con coordenadas
ProblemaVRP problema = new ProblemaVRP(fecha, depositos, entregasConCoords, vehiculos);

// 3. Planificar con VRP Engine puro
VrpEngine vrpEngine = VrpEngine.crear(vrpConfig);
ResultadoFase2 fase2 = vrpEngine.planificar(problema);
```

Esto mantiene la **separación de responsabilidades**: geocodificación externa, ruteo puro interno.

## Flujo de ejecución

El motor nunca lanza excepciones por datos inválidos: devuelve resultados estructurados.

```
Archivo -> Lectura y validación -> Cálculo de matriz -> Solver OR-Tools -> Confirmación
```

**Lectura y validación** (`ResultadoLectura`): errores por fila o por campo (coordenadas faltantes, ids duplicados, ventana invertida, vehículo sin hora de salida, depósito inexistente, etc.).

**Cálculo de matriz y planificación** (`Planificador.planificar` / `VrpEngine.planificar`). Solo se ejecuta con todas las coordenadas válidas. Pide la matriz a Valhalla y completa los pares sin ruta con OSRM. Resultados posibles:
- `RutaImposible`: quedan pares de puntos sin ruta incluso tras el fallback. Se informa qué pares son.
- `ProveedorNoDisponible`: ningún servicio de ruteo respondió.
- `ProblemaInvalido`: datos estructurales incorrectos.
- `PlanGenerado`: el plan, con estado `valido` o `requiere_confirmacion`.

**Confirmación** (`Planificador.confirmar` / `VrpEngine.confirmar`). Si el solver omitió entregas (capacidad, ventanas, jornada o restricciones combinadas), el plan queda en `requiere_confirmacion`. Solo pasa a `confirmado` si se aceptan explícitamente todos los ids omitidos.

## Uso desde Java (Fachada VrpEngine)

La forma recomendada es usar la fachada `VrpEngine` que expone funciones separadas para cada paso:

```java
ConfiguracionMotor cfg = ConfiguracionMotor.desdeArchivo(Path.of("config.properties"));
VrpEngine engine = VrpEngine.crear(cfg);  // Sin cliente HTTP explícito, usa java.net.http nativo

// 1. Leer archivo (CSV, Excel, XML, JSON)
ContextoOperativo contexto = VrpEngine.contextoDefecto(
        LocalDate.now(),
        List.of(VrpEngine.deposito("D1", "Central",
                new Coordenada(-34.6410, -58.5900),
                LocalTime.of(6, 0), LocalTime.of(22, 0), 30)),
        List.of(VrpEngine.vehiculo("V1", "Camión 1", 20,
                LocalTime.of(8, 0), LocalTime.of(18, 0), "D1", "D1", 1))
);

ResultadoLectura lectura = engine.leerArchivo(Path.of("clientes.csv"), contexto);
if (!lectura.errores().isEmpty()) { /* manejar errores */ }

// 2. Calcular matriz (opcional, para inspección)
MatrizRuteo matriz = engine.calcularMatriz(lectura.problema().coordenadasTodas());

// 3. Planificar (el problema YA debe tener coordenadas)
ResultadoFase2 fase2 = engine.planificar(lectura.problema());
if (fase2 instanceof RutaImposible r) { /* manejar errores */ }
if (fase2 instanceof ProveedorNoDisponible p) { /* manejar errores */ }
if (fase2 instanceof ProblemaInvalido p) { /* manejar errores */ }
PlanDeRuteo plan = ((PlanGenerado) fase2).plan();

// 4. Confirmar si hay omisiones
if (plan.estado() == EstadoPlan.REQUIERE_CONFIRMACION) {
    Set<String> aceptadas = plan.omisiones().stream()
            .map(Omision::entregaId).collect(Collectors.toSet());
    plan = engine.confirmar(plan, aceptadas);
}

// 5. Codificar/guardar plan
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
- `calcularMatriz`, `obtenerGeometria` — Matriz y geometría
- `planificar` — Planificación (con o sin posiciones GPS)
- `confirmar` — Aceptar omisiones
- `reoptimizar` — Reoptimización mid-jornada
- `codificarPlan`, `decodificarPlan` — JSON I/O
- Métodos helper: `contextoDefecto`, `entrega`, `vehiculo`, `deposito`

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

## Reoptimización durante la jornada

Se usa cuando los camiones **ya salieron**, es decir, ya llevan la mercadería cargada. Por eso:

- **No se pueden agregar clientes** a un plan en marcha: la mercadería no está en los camiones. Un cliente nuevo implica generar un plan nuevo.
- Solo se recalcula la ruta **del chofer que tiene al cliente afectado**. Las rutas de los demás choferes, con sus paradas, ETA y polilíneas, quedan idénticas.
- Un cliente no puede pasar de un camión a otro.
- **Solo se recalculan las paradas pendientes o postergadas.** Las demás salen de la ruta.
- **No se permite modificar direcciones** (el motor requiere coordenadas). Si se necesita cambiar una dirección, geocodificar externamente y pasar las nuevas coordenadas.

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

### Qué dispara el recálculo de una ruta

1. Una parada con estado `devolver_a_deposito`.
2. Una parada con estado `reprogramado`.
3. Una `ModificacionEntrega` sobre alguno de sus clientes (ventana, prioridad, servicioMin).

Las acciones `devolverADeposito` y `reprogramar` de `CambiosJornada` aplican el estado correspondiente y recalculan en una sola operación.

Si ninguna ruta cumple alguna de estas condiciones, el plan se devuelve sin cambios. Las rutas que no se recalculan conservan todo tal cual, incluso sus paradas ya entregadas.

### Modificaciones permitidas

`ModificacionEntrega` admite, por cliente (los campos en `null` no cambian):
- `ventana`: nueva ventana horaria, o `quitarVentana = true` para eliminarla.
- `prioridad` y `servicioMin`.

La demanda no se puede modificar, porque la mercadería ya está cargada. Solo se pueden modificar clientes en estado `pendiente`, `reprogramado` o `en_camino`.

### Ejemplo

```java
PlanDeRuteo plan = JsonSupport.mapper().readValue(Path.of("plan.json").toFile(), PlanDeRuteo.class);

// Evento del chofer: no recalcula.
plan = plan.conEstadoDeParada("E1", EstadoParada.ENTREGADO);

// Acciones de la web: recalculan solo la ruta afectada.
CambiosJornada cambios = new CambiosJornada(
        LocalDateTime.now(),
        List.of(new PosicionVehiculo("V1", new Coordenada(-34.66, -58.73), LocalDateTime.now())),
        List.of(new ModificacionEntrega("E5", null, false, 3, null)),               // prioridad nueva
        List.of("E3"),                                                                     // devolver al depósito
        List.of(new Reprogramacion("E4", new VentanaHoraria(LocalTime.of(15, 0), LocalTime.of(17, 0)))));

ResultadoPlanificacion resultado = new Reoptimizador(planificador).reoptimizar(plan, cambios);
```

## Cómo decide el solver

- **Objetivo**: minimiza distancia (por defecto) o tiempo total, según `Solver.objetivo`.
- **Capacidad**: cada vehículo tiene la suya y la suma de demandas de su ruta no puede superarla.
- **Ventanas horarias**: el vehículo puede esperar si llega antes; si llega tarde, no es válido.
- **Jornada**: `horaFin` es un límite duro para el regreso al depósito.
- **Omisiones**: cada entrega puede omitirse pagando una penalización de `Solver.penalizacionBase × prioridad`. El valor por defecto es alto para que omitir sea el último recurso. Una prioridad mayor hace más costoso omitir ese cliente.
- **Balanceo**: con `Solver.coeficienteBalanceo` mayor que 0 se penaliza la diferencia entre la ruta más larga y la más corta, lo que reparte mejor las entregas entre vehículos. Con 0, el solver puede concentrar todo en pocos vehículos si eso reduce la distancia total.
- **Causa de las omisiones**: el plan indica un motivo aproximado (`capacidad_individual`, `ventana_horaria_imposible`, `restricciones_combinadas`, `sin_solucion`).

## Estructura del proyecto

Proyecto Maven **multi-módulo**:

```
vrp-parent/                 # POM padre (dependency management)
├── pom.xml                 # Configuración común: Java 25, dependencias, plugins
├── vrp-engine/             # Módulo: Motor de ruteo puro (VRP)
│   ├── pom.xml
│   ├── config.properties   # Config VRP (Valhalla, OSRM, Solver)
│   └── src/main/java/vrp/engine/
│       ├── model/          # Deposito, Entrega, Vehiculo, ProblemaVRP, Coordenada...
│       ├── io/             # Lectores (JSON, CSV, Excel, XML), ProblemReaderFactory, JsonSupport
│       ├── routing/        # ValhallaClient, OsrmClient, MatrixChain, GeometryChain, MatrizRuteo
│       ├── solver/         # OrToolsVrpSolver, ModeloBuilder, Simulador, ArcosRelevantes
│       ├── plan/           # PlanDeRuteo, RutaVehiculo, Parada, Omision...
│       ├── resultado/      # ResultadoFase2, PlanGenerado, RutaImposible, ProveedorNoDisponible, ProblemaInvalido
│       ├── service/        # Planificador, Reoptimizador, ConstructorPlan, VrpEngine(fachada)...
│       ├── config/         # ConfiguracionMotor (record, .properties + env vars)
│       └── util/           # Polyline6, Tiempos
├── geocoding-engine/       # Módulo: Motor de geocodificación independiente
│   ├── pom.xml
│   ├── geocoding.properties # Config Geocoding (Georef, Nominatim)
│   └── src/main/java/vrp/geocoding/
│       ├── GeocodingEngine.java        # Fachada principal
│       ├── GeocodingChain.java         # Cadena de proveedores con fallback
│       ├── GeocodingProvider.java      # Interfaz de proveedor
│       ├── GeocodingConfig.java        # Configuración
│       ├── GeorefProvider.java         # Proveedor Georef (cuota 1000/día, batch)
│       ├── NominatimProvider.java      # Proveedor Nominatim (rate-limited)
│       ├── Direccion.java              # Dirección estructurada
│       ├── SolicitudGeocodificacion.java # Solicitud con ID
│       ├── ResultadoGeocodificacion.java # Resultado sellado (Exitoso/Fallido)
│       └── Coordenada.java             # Lat/Lon con validación
```

**Dependencias entre módulos**: Ninguna (son independientes).
- `vrp-engine` depende de: Jackson, SLF4J, OR-Tools, POI, Commons CSV
- `geocoding-engine` depende de: Jackson, SLF4J

Interfaces para testabilidad e inyección:
- VRP: `RoutingMatrixProvider`, `RouteGeometryProvider`, `VrpSolver`, `ProblemReader`
- Geocoding: `GeocodingProvider`

## Tests

```bash
# Tests de todos los módulos (desde raíz)
mvn test

# Tests solo VRP Engine
mvn test -pl vrp-engine

# Tests solo Geocoding Engine
mvn test -pl geocoding-engine
```

**VRP Engine (vrp-engine):** lectura 4 formatos, matrices bloques/fallback, planificación, omisiones/confirmación, reoptimización por ruta, polyline6, estados parada.

**Geocoding Engine (geocoding-engine):** Georef batch/cuota, Nominatim rate-limiting, cadena fallback, batching inteligente.

`OrToolsVrpSolverTest` usa solver real (requiere librerías nativas OR-Tools).

## Limitaciones y pendientes

- No hay API web ni CLI: el motor se usa desde código Java (fachada `VrpEngine` lista para Spring/Quarkus).
- Un par sin ruta (tras fallback) bloquea el plan (por diseño).
- Reoptimización no admite clientes nuevos (mercadería ya cargada); requiere plan nuevo.
- No se puede modificar dirección en reoptimización (requiere coordenadas).
- Paradas cerradas salen del plan al reoptimizar; historial en sistema de pedidos.
- Horarios en un día, sin zona horaria.
- Sin caché de geocodificación: geocodificar externamente antes de usar el motor.
- Pendientes: compatibilidad vehículo-cliente, pickups+deliveries, **múltiples viajes por vehículo** (modelo `maxViajes` existe, solver pendiente), API web de ejemplo.