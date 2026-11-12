package vrp.engine.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vrp.engine.config.ConfiguracionMotor;
import vrp.engine.io.JsonProblemReader;
import vrp.engine.io.ProblemReader;
import vrp.engine.io.ProblemReaderFactory;
import vrp.engine.io.ResultadoLectura;
import vrp.engine.model.ContextoOperativo;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Deposito;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.Vehiculo;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.model.EstadoParada;
import vrp.engine.resultado.ErrorLectura;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ProblemaInvalido;
import vrp.engine.resultado.ResultadoFase2;
import vrp.engine.resultado.ResultadoPlanificacion;
import vrp.engine.resultado.RutaImposible;
import vrp.engine.routing.GeometryChain;
import vrp.engine.routing.MatrixChain;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.routing.OsrmClient;
import vrp.engine.routing.RouteGeometryProvider;
import vrp.engine.routing.RoutingMatrixProvider;
import vrp.engine.routing.ValhallaClient;
import vrp.engine.solver.ArcosRelevantes;
import vrp.engine.solver.ModeloBuilder;
import vrp.engine.solver.ModeloRuteo;
import vrp.engine.solver.OrToolsVrpSolver;
import vrp.engine.solver.ParametrosSolver;
import vrp.engine.solver.SolucionVrp;
import vrp.engine.solver.VrpSolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Fachada principal del motor VRP para uso como librería.
 * Expone funciones para leer archivos, obtener matriz, calcular rutas, codificar/decodificar planes.
 * Diseñada para ser consumida por una API web.
 * Trabaja exclusivamente con coordenadas (latitud/longitud) - no realiza geocodificación.
 */
public final class VrpEngine {

    private final Planificador planificador;
    private final Reoptimizador reoptimizador;

    private VrpEngine(Planificador planificador, Reoptimizador reoptimizador) {
        this.planificador = planificador;
        this.reoptimizador = reoptimizador;
    }

    /** Crea un motor con la configuración por defecto. */
    public static VrpEngine crear(ConfiguracionMotor cfg) {
        Planificador planificador = Planificador.porDefecto(cfg);
        Reoptimizador reoptimizador = new Reoptimizador(planificador);
        return new VrpEngine(planificador, reoptimizador);
    }

    // =========================================================================
    // FUNCIONES DE LECTURA DE ARCHIVOS
    // =========================================================================

    /** Lee un archivo (JSON, CSV, Excel, XML) y devuelve el problema con errores si los hay. */
    public ResultadoLectura leerArchivo(Path archivo, ContextoOperativo contexto) throws Exception {
        ProblemReader lector = ProblemReaderFactory.para(archivo);
        return lector.leer(archivo, contexto);
    }

    /** Lee JSON desde texto y devuelve el problema. */
    public ResultadoLectura leerJson(String json, ContextoOperativo contexto) {
        return new JsonProblemReader().leerTexto(json, contexto);
    }

    // =========================================================================
    // FUNCIONES DE MATRIZ DE RUTEO
    // =========================================================================

    /**
     * Calcula la matriz de distancias y tiempos para una lista de coordenadas.
     * Usa Valhalla como primario y OSRM como fallback.
     * @return MatrizRuteo con distancias (metros) y tiempos (segundos)
     */
    public MatrizRuteo calcularMatriz(List<Coordenada> coordenadas) {
        return planificador.matriz().calcular(coordenadas);
    }

    /**
     * Obtiene la geometría (polilínea) de una ruta entre dos puntos.
     */
    public String obtenerGeometria(Coordenada origen, Coordenada destino) {
        List<String> tramos = planificador.geometria().tramos(List.of(origen, destino));
        return tramos.isEmpty() ? null : tramos.get(0);
    }

    // =========================================================================
    // FUNCIONES DE PLANIFICACIÓN (FASE 2)
    // =========================================================================

    /**
     * Planifica rutas para un problema ya geocodificado (con coordenadas).
     * @param permitirPuntosDesconectados Si true, omite entregas inalcanzables; si false, falla si hay inalcanzables
     * @return ResultadoFase2: PlanGenerado (éxito) o RutaImposible/ProveedorNoDisponible (error)
     */
    public ResultadoFase2 planificar(ProblemaVRP problema, boolean permitirPuntosDesconectados) {
        return planificar(problema, Map.of(), 0L, 1, permitirPuntosDesconectados);
    }

    /**
     * Planifica rutas para un problema ya geocodificado, con posiciones iniciales de vehículos.
     * @param posiciones Mapa vehiculoId -> Coordenada (posición GPS actual)
     * @param ahoraSeg Segundos desde inicio de jornada (para ETA)
     * @param version Versión del plan (para reoptimización)
     * @param permitirPuntosDesconectados Si true, omite entregas inalcanzables; si false, falla si hay inalcanzables
     */
    public ResultadoFase2 planificar(ProblemaVRP problema, Map<String, Coordenada> posiciones,
                                     long ahoraSeg, int version, boolean permitirPuntosDesconectados) {
        // Crear parámetros temporales con el valor de permitirPuntosDesconectados
        ParametrosSolver parametrosBase = planificador.parametros();
        ParametrosSolver parametrosConOpcion = new ParametrosSolver(
                parametrosBase.objetivo(),
                parametrosBase.tiempoLimiteSeg(),
                parametrosBase.penalizacionBase(),
                parametrosBase.coeficienteBalanceo(),
                permitirPuntosDesconectados);
        return planificador.planificarConParametros(problema, posiciones, ahoraSeg, version, parametrosConOpcion);
    }

    /**
     * Confirma un plan aceptando explícitamente las omisiones.
     * @param omisionesAceptadas Set con IDs de entregas omitidas que el usuario acepta
     * @return Plan confirmado (estado CONFIRMADO) o lanza excepción si faltan omisiones
     */
    public PlanDeRuteo confirmar(PlanDeRuteo plan, Set<String> omisionesAceptadas) {
        return planificador.confirmar(plan, omisionesAceptadas);
    }

    // =========================================================================
    // FUNCIONES DE REOPTIMIZACIÓN
    // =========================================================================

    /**
     * Reoptimiza un plan a mitad de jornada aplicando cambios.
     * Recalcula solo las rutas afectadas, partiendo de la posición GPS actual del chofer.
     */
    public ResultadoPlanificacion reoptimizar(PlanDeRuteo plan, CambiosJornada cambios) {
        return reoptimizador.reoptimizar(plan, cambios);
    }

    // =========================================================================
    // FUNCIONES DE CODIFICACIÓN / DECODIFICACIÓN DE PLANES
    // =========================================================================

    /**
     * Codifica un plan a JSON para almacenamiento o transmisión a API.
     */
    public String codificarPlan(PlanDeRuteo plan) {
        try {
            return vrp.engine.io.JsonSupport.mapper().writeValueAsString(plan);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Error codificando plan a JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Decodifica un plan desde JSON.
     */
    public PlanDeRuteo decodificarPlan(String json) {
        try {
            return vrp.engine.io.JsonSupport.mapper().readValue(json, PlanDeRuteo.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON de plan inválido: " + e.getMessage(), e);
        }
    }

    /**
     * Decodifica un plan desde archivo JSON.
     */
    public PlanDeRuteo decodificarPlan(Path archivo) {
        try {
            return vrp.engine.io.JsonSupport.mapper().readValue(archivo.toFile(), PlanDeRuteo.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error leyendo plan desde archivo: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // FUNCIONES DE UTILIDAD PARA CREAR MODELOS
    // =========================================================================

    /** Crea un contexto operativo con fecha, depósitos y vehículos por defecto. */
    public static ContextoOperativo contextoDefecto(LocalDate fecha,
            List<Deposito> depositos, List<Vehiculo> vehiculos) {
        return new ContextoOperativo(fecha, depositos, vehiculos);
    }

    /** Crea una entrega con todos los campos opcionales (coordenada obligatoria). */
    public static Entrega entrega(String id, String nombre, Coordenada coordenada,
            int demanda, int servicioMin, vrp.engine.model.VentanaHoraria ventana, int prioridad) {
        return new Entrega(id, nombre, coordenada, demanda, servicioMin, ventana, prioridad);
    }

    /** Crea un vehículo con capacidad, horario y depósitos. */
    public static Vehiculo vehiculo(String id, String nombre, int capacidad,
            java.time.LocalTime horaSalida, java.time.LocalTime horaFin,
            String depositoSalidaId, String depositoLlegadaId, int maxViajes) {
        return new Vehiculo(id, nombre, capacidad, horaSalida, horaFin,
                depositoSalidaId, depositoLlegadaId, maxViajes);
    }

    /** Crea un depósito con ventana horaria y tiempo de estadía para carga/descarga (coordenada obligatoria). */
    public static Deposito deposito(String id, String nombre, Coordenada coordenada,
            java.time.LocalTime ventanaDesde, java.time.LocalTime ventanaHasta, int estadiaMin) {
        return new Deposito(id, nombre, coordenada, ventanaDesde, ventanaHasta, estadiaMin);
    }

    // =========================================================================
    // GETTERS PARA ACCESO A COMPONENTES INTERNOS (avanzado)
    // =========================================================================

    public Planificador planificador() {
        return planificador;
    }

    public Reoptimizador reoptimizador() {
        return reoptimizador;
    }
}