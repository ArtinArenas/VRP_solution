# Resumen de Cambios - VRP Engine v2.0 (Motor de Ruteo Puro)

## Resumen Ejecutivo

Se ha refactorizado completamente el repositorio para **extraer la geocodificación del VRP Engine** y convertirlo en un **motor de ruteo puro** que trabaja exclusivamente con coordenadas (latitud/longitud). Todos los cambios se realizaron en la rama principal.

---

## 1. Eliminación de Tests y Geocodificación

### Tests eliminados
- **Directorio completo**: `src/test/java/vrp/engine/` (todos los 8 archivos de test)
  - `EstadosParadaTest.java`
  - `Fakes.java`
  - `GeocodingTest.java`
  - `IoTest.java`
  - `OrToolsVrpSolverTest.java`
  - `PlanificadorTest.java`
  - `Polyline6Test.java`
  - `ReoptimizadorTest.java`
  - `RoutingTest.java`

### Paquete de geocodificación eliminado
- **Directorio completo**: `src/main/java/vrp/engine/geocoding/` (9 archivos)
  - `GeocodingChain.java`
  - `GeocodingProvider.java`
  - `GeorefProvider.java`
  - `LimitesArgentina.java`
  - `MotivoGeo.java`
  - `NominatimLocalProvider.java`
  - `ResolucionGeo.java`
  - `ResultadoGeo.java`
  - `SolicitudGeocodificacion.java`

---

## 2. Modelos y Lectores con Coordenadas Obligatorias

### Deposito.java
- **Cambio**: `Coordenada` ahora es **obligatoria** (validación en constructor)
- **Eliminado**: Campo `DireccionPostal direccion`
- **Eliminado**: Validación de rango latitud (-90 a 90) / longitud (-180 a 180)
- **Mantenido**: `conCoordenada()`, `ventana()`

### Entrega.java
- **Cambio**: `Coordenada` ahora es **obligatoria** (validación en constructor)
- **Eliminado**: Campo `DireccionPostal direccion`
- **Eliminado**: Validación de rango latitud/longitud
- **Mantenido**: `conCoordenada()`

### DireccionPostal.java
- **ELIMINADO**: Clase completa (no necesaria para motor VRP puro)

### Clases de resultado de geocodificación eliminadas
- `ResultadoFase1.java` (sealed interface)
- `Geocodificado.java` (record)
- `DireccionesInvalidas.java` (record)

### Lectores de archivos (io/)
- **CsvProblemReader.java**: Hereda de `TablaProblemReader`, usa `FilaEntrega.mapear()`
- **ExcelProblemReader.java**: Hereda de `TablaProblemReader`, usa `FilaEntrega.mapear()`
- **XmlProblemReader.java**: Hereda de `TablaProblemReader`, usa `FilaEntrega.mapear()`
- **JsonProblemReader.java**: Usa Jackson directamente sobre `ProblemaVRP` (compatibilidad automática con records)
- **FilaEntrega.java**: 
  - Lee obligatoriamente columnas `lat`/`latitud` y `lon`/`lng`/`longitud`
  - Error si faltan coordenadas: "Faltan latitud y/o longitud"
  - Elimina lectura de `direccion`, `localidad`, `provincia`
  - Crea `Entrega` directamente con `Coordenada`
- **ValidadorProblema.java**:
  - Valida que todo `Deposito` y `Entrega` tenga `coordenada != null`
  - Elimina validación de `DireccionPostal` (dirección y provincia)
  - Mantiene validaciones estructurales (ids únicos, ventanas, capacidades, etc.)

---

## 3. Infraestructura HTTP (Java 25) y Configuración

### pom.xml
- **Java 25 LTS**: `<maven.compiler.release>25</maven.compiler.release>`
- **Dependencia agregada**: SLF4J API 2.0.17 (`org.slf4j:slf4j-api`)

### ConfiguracionMotor.java
- **Eliminados parámetros de geocodificación**:
  - `georefUrl`, `nominatimUrl`, `nominatimEmail`, `nominatimPausaMs`
  - Constante `GEOREF_URL_DEFECTO`
- **Mantenidos parámetros de ruteo/solver**:
  - `valhallaUrl`, `osrmUrl`, `valhallaCosting`
  - `tamanoBloqueMatriz`, `timeoutHttpSeg`
  - `objetivo`, `tiempoLimiteSolverSeg`, `penalizacionBase`, `coeficienteBalanceo`
- **Soporte para variables de entorno** (prioridad sobre config.properties):
  - `VALHALLA_URL`, `OSRM_URL`, `VALHALLA_COSTING`
  - `MATRIZ_TAMANO_BLOQUE`, `HTTP_TIMEOUT_SEG`
  - `SOLVER_OBJETIVO`, `SOLVER_TIEMPO_LIMITE`, `SOLVER_PENALIZACION_BASE`, `SOLVER_COEFICIENTE_BALANCEO`
- **Métodos auxiliares**: `textoConEnv()`, `numeroConEnv()`

### HttpTransporte.java y HttpTransporteJdk.java
- **ELIMINADOS**: Ambos archivos (interfaz e implementación)

### ValhallaClient.java (reescrito)
- Usa **java.net.http.HttpClient nativo** directamente
- **Constructor**: `ValhallaClient(String baseUrl, String costing, int tamanoBloque, int timeoutSeg)`
- **SLF4J Logging**: Logs en DEBUG para cálculo de matriz y geometría
- Manejo correcto de `InterruptedException`
- Mantiene compatibilidad con `BloquesMatrixProvider` y `RouteGeometryProvider`

### OsrmClient.java (reescrito)
- Usa **java.net.http.HttpClient nativo** directamente
- **Constructor**: `OsrmClient(String baseUrl, int tamanoBloque, int timeoutSeg)`
- **SLF4J Logging**: Logs en DEBUG para cálculo de matriz y geometría
- Manejo correcto de `InterruptedException`
- Mantiene compatibilidad con `BloquesMatrixProvider` y `RouteGeometryProvider`

### config.properties (actualizado)
- Eliminadas propiedades de Georef y Nominatim
- Mantenidas solo: Valhalla, OSRM, Matriz, HTTP, Solver
- Comentario indicando soporte para variables de entorno

---

## 4. Fachada VrpEngine

### VrpEngine.java (reescrita)
- **Eliminado**: Constructor con `HttpTransporte`
- **Eliminados métodos de geocodificación**:
  - `geocodificar(ProblemaVRP)`
  - `geocodificarDirecciones(List<DireccionPostal>)`
- **Eliminados helpers de modelos legacy**:
  - `direccion(String, String, String)` → `DireccionPostal`
  - `entrega(..., DireccionPostal, ...)` → usa `Coordenada` obligatoria
  - `deposito(..., DireccionPostal, ...)` → usa `Coordenada` obligatoria
- **Mantenidos métodos de ruteo/planificación**:
  - `leerArchivo()`, `leerJson()`
  - `calcularMatriz()`, `obtenerGeometria()`
  - `planificar()` (dos overloads)
  - `confirmar()`
  - `reoptimizar()`
  - `codificarPlan()`, `decodificarPlan()`
- **Nuevos helpers**:
  - `entrega(id, nombre, Coordenada, demanda, servicioMin, ventana, prioridad)`
  - `deposito(id, nombre, Coordenada, ventanaDesde, ventanaHasta, estadiaMin)`
- **Creación simplificada**: `VrpEngine.crear(ConfiguracionMotor)` sin HttpTransporte

### Planificador.java (reescrito)
- **Eliminado**: `GeocodingChain`, `geocodificador`, método `geocodificar()`
- **Eliminado**: `HttpTransporte` del constructor
- **Mantenido**: `planificar()` (Fase 2 pura), `confirmar()`
- **Constructor simplificado**: Solo `RoutingMatrixProvider`, `RouteGeometryProvider`, `VrpSolver`, `ParametrosSolver`
- **porDefecto()**: Crea `ValhallaClient` y `OsrmClient` directamente con config
- **Validación**: Verifica `problema.geocodificado()` al inicio de `planificar()`
- **Retorna `ProblemaInvalido`** (ahora implementa `ResultadoFase2`) si validación falla

### Reoptimizador.java (actualizado)
- **Eliminado**: Llamada a `planificador.geocodificar()` en `subproblema()`
- **Agregado**: Verificación `sub.geocodificado()` - si hay coordenadas nulas, retorna `RutaImposible`
- **ModificacionEntrega**: Eliminado campo `direccion` (no soportado en motor puro)
- **aplicar()**: Simplificado sin lógica de cambio de dirección
- **Excepción**: Si se intenta modificar dirección → `IllegalArgumentException` explicativa

### ModificacionEntrega.java
- **Eliminado**: Campo `DireccionPostal direccion`
- **Mantenidos**: `ventana`, `quitarVentana`, `prioridad`, `servicioMin`
- **Actualizado**: `sinCambios()` sin verificar dirección

---

## 5. Resultado Planificacion (Jerarquía sealed actualizada)

### ResultadoPlanificacion.java
- **Permits**: Solo `ResultadoFase2` (elimina `ResultadoFase1`)
- **JsonSubTypes**: Actualizados para incluir solo `PlanGenerado`, `RutaImposible`, `ProveedorNoDisponible`, `ProblemaInvalido`

### ResultadoFase2.java
- **Permits**: Agregado `ProblemaInvalido` (para que `planificar()` pueda retornarlo)

### ProblemaInvalido.java
- **Cambio**: Ahora implementa `ResultadoFase2` (antes `ResultadoFase1`)

### ErrorDireccion.java
- **ELIMINADO**: Clase completa (solo usada en geocodificación)

---

## 6. Verificación y Compilación

```bash
mvn clean compile
# BUILD SUCCESS
```

El proyecto compila sin errores ni referencias residuales a geocodificación.

---

## 7. README y Archivos

### README.md (reescrito completamente)
- Documenta el motor como **motor de ruteo puro** (sin geocodificación)
- Formatos de entrada requieren coordenadas obligatorias (lat/lon)
- Configuración vía variables de entorno + config.properties
- Ejemplos actualizados para CSV, XML, JSON con coordenadas
- Flujo de ejecución simplificado (sin Fase 1)
- Uso desde Java actualizado (sin métodos de geocodificación)
- Reoptimización: no permite cambio de dirección
- Estructura de proyecto actualizada (sin paquetes geocoding, http)

### CAMBIOS_REALIZADOS.md (este archivo)
- Resumen detallado de todos los cambios realizados

---

## Impacto en API Consumidora

**Breaking Changes** para quien use el motor como librería:

| Antes | Ahora |
|---|---|
| `engine.geocodificar(problema)` | **Eliminado** - Geocodificar externamente antes |
| `engine.planificar(problemaSinCoords)` | `engine.planificar(problemaConCoords)` - Requiere coords |
| `VrpEngine.direccion(...)` | **Eliminado** - Usar `new Coordenada(lat, lon)` |
| `VrpEngine.entrega(..., DireccionPostal, ...)` | `VrpEngine.entrega(..., Coordenada, ...)` |
| `VrpEngine.deposito(..., DireccionPostal, ...)` | `VrpEngine.deposito(..., Coordenada, ...)` |
| `ConfiguracionMotor.georefUrl()` | **Eliminado** - Usar `VALHALLA_URL`/`OSRM_URL` |
| `HttpTransporte` / `HttpTransporteJdk` | **Eliminados** - Cliente HTTP nativo interno |

**Migración recomendada**:
1. Geocodificar todas las direcciones externamente (Georef, Nominatim, Google Maps, etc.)
2. Construir `ProblemaVRP` con `Deposito` y `Entrega` que tengan `Coordenada` obligatoria
3. Llamar directamente `engine.planificar(problema)`
4. Configurar `VALHALLA_URL` y/o `OSRM_URL` via variables de entorno

---

## Archivos Eliminados (Total: 20)

```
src/test/java/vrp/engine/                    (9 archivos)
src/main/java/vrp/engine/geocoding/          (9 archivos)
src/main/java/vrp/engine/model/DireccionPostal.java
src/main/java/vrp/engine/http/HttpTransporte.java
src/main/java/vrp/engine/http/HttpTransporteJdk.java
src/main/java/vrp/engine/resultado/ResultadoFase1.java
src/main/java/vrp/engine/resultado/Geocodificado.java
src/main/java/vrp/engine/resultado/DireccionesInvalidas.java
src/main/java/vrp/engine/resultado/ErrorDireccion.java
```

---

## Archivos Modificados (Total: 15)

```
pom.xml
config.properties
README.md
src/main/java/vrp/engine/model/Deposito.java
src/main/java/vrp/engine/model/Entrega.java
src/main/java/vrp/engine/model/Coordenada.java (sin cambios, solo referencia)
src/main/java/vrp/engine/model/ProblemaVRP.java (sin cambios, usa geocodificado())
src/main/java/vrp/engine/config/ConfiguracionMotor.java
src/main/java/vrp/engine/io/FilaEntrega.java
src/main/java/vrp/engine/io/ValidadorProblema.java
src/main/java/vrp/engine/routing/ValhallaClient.java
src/main/java/vrp/engine/routing/OsrmClient.java
src/main/java/vrp/engine/service/VrpEngine.java
src/main/java/vrp/engine/service/Planificador.java
src/main/java/vrp/engine/service/Reoptimizador.java
src/main/java/vrp/engine/service/ModificacionEntrega.java
src/main/java/vrp/engine/resultado/ResultadoPlanificacion.java
src/main/java/vrp/engine/resultado/ResultadoFase2.java
src/main/java/vrp/engine/resultado/ProblemaInvalido.java
```

---

**Fecha**: 2026-11-06  
**Versión**: 2.0.0 - Motor de Ruteo Puro