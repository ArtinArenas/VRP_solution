package vrp.engine.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vrp.engine.config.ConfiguracionMotor;
import vrp.engine.io.ValidadorProblema;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Deposito;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.plan.EstadoPlan;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.resultado.ErrorLectura;
import vrp.engine.resultado.ErrorRuta;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ProblemaInvalido;
import vrp.engine.resultado.ProveedorNoDisponible;
import vrp.engine.resultado.ResultadoFase2;
import vrp.engine.resultado.RutaImposible;
import vrp.engine.resultado.TipoEntidad;
import vrp.engine.routing.GeometryChain;
import vrp.engine.routing.MatrixChain;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.routing.OsrmClient;
import vrp.engine.routing.RouteGeometryProvider;
import vrp.engine.routing.RoutingException;
import vrp.engine.routing.RoutingMatrixProvider;
import vrp.engine.routing.ValhallaClient;
import vrp.engine.solver.ArcosRelevantes;
import vrp.engine.solver.FiltradorNodosInalcanzables;
import vrp.engine.solver.ModeloBuilder;
import vrp.engine.solver.ModeloRuteo;
import vrp.engine.solver.OrToolsVrpSolver;
import vrp.engine.solver.ParametrosSolver;
import vrp.engine.solver.SolucionVrp;
import vrp.engine.solver.VrpSolver;

public final class Planificador {

    private static final String PREFIJO_DEPOSITO = "D:";
    private static final String PREFIJO_ENTREGA = "E:";

    private final RoutingMatrixProvider matriz;
    private final RouteGeometryProvider geometria;
    private final VrpSolver solver;
    private final ParametrosSolver parametros;

    public Planificador(RoutingMatrixProvider matriz,
                        RouteGeometryProvider geometria,
                        VrpSolver solver,
                        ParametrosSolver parametros) {
        this.matriz = matriz;
        this.geometria = geometria;
        this.solver = solver;
        this.parametros = parametros;
    }

    /** Arma el planificador con los proveedores definidos en la configuración. */
    public static Planificador porDefecto(ConfiguracionMotor cfg) {
        List<RoutingMatrixProvider> matrices = new ArrayList<>();
        List<RouteGeometryProvider> geometrias = new ArrayList<>();
        if (cfg.valhallaUrl() != null) {
            ValhallaClient v = new ValhallaClient(cfg.valhallaUrl(), cfg.valhallaCosting(), cfg.tamanoBloqueMatriz(), cfg.timeoutHttpSeg());
            matrices.add(v);
            geometrias.add(v);
        }
        if (cfg.osrmUrl() != null) {
            OsrmClient o = new OsrmClient(cfg.osrmUrl(), cfg.tamanoBloqueMatriz(), cfg.timeoutHttpSeg());
            matrices.add(o);
            geometrias.add(o);
        }
        if (matrices.isEmpty()) {
            throw new IllegalArgumentException("Debe configurarse Valhalla.url y/o OSRM.url");
        }

        return new Planificador(new MatrixChain(matrices),
                new GeometryChain(geometrias), new OrToolsVrpSolver(), cfg.parametrosSolver());
    }

    /** Fase 2: pide la matriz, resuelve el ruteo y arma el plan; usado también por la reoptimización. */
    public ResultadoFase2 planificar(ProblemaVRP problema,
                                     Map<String, Coordenada> posiciones,
                                     Long ahoraSeg,
                                     int version) {
        if (!problema.geocodificado()) {
            throw new IllegalStateException("El problema debe tener todas las coordenadas (geocodificado) antes de planificar");
        }

        // Validar estructura
        List<ErrorLectura> invalidos = ValidadorProblema.validar(problema);
        if (!invalidos.isEmpty()) return new ProblemaInvalido(invalidos);

        // Nodos: depósitos, posiciones de choferes y clientes.
        ModeloRuteo modelo = ModeloBuilder.construir(problema, posiciones, ahoraSeg, parametros);

        // Matriz entre todos los puntos, con fallback entre proveedores.
        MatrizRuteo matrizRuteo;
        try {
            matrizRuteo = matriz.calcular(modelo.coordenadas());
        } catch (RoutingException e) {
            return new ProveedorNoDisponible(e.getMessage());
        }

        // Filtrar nodos inalcanzables ANTES de validar arcos relevantes y pasar al solver
        boolean permitirPuntosDesconectados = parametros.permitirPuntosDesconectados();
        var resultadoFiltrado = FiltradorNodosInalcanzables.filtrar(modelo, matrizRuteo, permitirPuntosDesconectados);
        modelo = resultadoFiltrado.modeloFiltrado();
        List<vrp.engine.plan.Omision> omisionesSinRuta = resultadoFiltrado.omisionesSinRuta();

        // Si modo estricto y hay nodos inalcanzables -> falla
        if (!permitirPuntosDesconectados && !omisionesSinRuta.isEmpty()) {
            return new RutaImposible(omisionesSinRuta.stream()
                    .map(o -> new ErrorRuta(o.entregaId(), "depósito"))
                    .toList());
        }

        // Compuerta de la fase 2: un par sin ruta que importe detiene el proceso.
        List<ErrorRuta> huecos = ArcosRelevantes.huecos(modelo, matrizRuteo);
        if (!huecos.isEmpty()) return new RutaImposible(huecos);
        // Los pares sin ruta que no importan se rellenan con 0 para que el solver los ignore.
        matrizRuteo = ArcosRelevantes.sanear(modelo, matrizRuteo);

        // Sin entregas no hay nada que optimizar: el vehículo solo vuelve al depósito.
        SolucionVrp solucion = modelo.entregas().isEmpty()
                ? new SolucionVrp(List.of(), List.of(), false)
                : solver.resolver(modelo, matrizRuteo, parametros);

        // Construir el plan y agregar las omisiones por SIN_RUTA
        PlanDeRuteo plan = ConstructorPlan.construir(problema, modelo, matrizRuteo, solucion, geometria, version);
        if (!omisionesSinRuta.isEmpty()) {
            List<vrp.engine.plan.Omision> todasOmisiones = new ArrayList<>(plan.omisiones());
            todasOmisiones.addAll(omisionesSinRuta);
            plan = new PlanDeRuteo(plan.version(), plan.fecha(), plan.estado(), plan.depositos(),
                    plan.rutas(), List.copyOf(todasOmisiones), plan.advertencias(),
                    plan.distanciaTotalM(), plan.duracionTotalSeg());
        }

        return new PlanGenerado(plan);
    }

    /**
     * Planifica usando parámetros personalizados (para override de permitirPuntosDesconectados).
     */
    public ResultadoFase2 planificarConParametros(ProblemaVRP problema,
                                                   Map<String, Coordenada> posiciones,
                                                   Long ahoraSeg,
                                                   int version,
                                                   ParametrosSolver parametrosPersonalizados) {
        if (!problema.geocodificado()) {
            throw new IllegalStateException("El problema debe tener todas las coordenadas (geocodificado) antes de planificar");
        }

        // Validar estructura
        List<ErrorLectura> invalidos = ValidadorProblema.validar(problema);
        if (!invalidos.isEmpty()) return new ProblemaInvalido(invalidos);

        // Nodos: depósitos, posiciones de choferes y clientes.
        ModeloRuteo modelo = ModeloBuilder.construir(problema, posiciones, ahoraSeg, parametrosPersonalizados);

        // Matriz entre todos los puntos, con fallback entre proveedores.
        MatrizRuteo matrizRuteo;
        try {
            matrizRuteo = matriz.calcular(modelo.coordenadas());
        } catch (RoutingException e) {
            return new ProveedorNoDisponible(e.getMessage());
        }

        // Filtrar nodos inalcanzables ANTES de validar arcos relevantes y pasar al solver
        boolean permitirPuntosDesconectados = parametrosPersonalizados.permitirPuntosDesconectados();
        var resultadoFiltrado = FiltradorNodosInalcanzables.filtrar(modelo, matrizRuteo, permitirPuntosDesconectados);
        modelo = resultadoFiltrado.modeloFiltrado();
        List<vrp.engine.plan.Omision> omisionesSinRuta = resultadoFiltrado.omisionesSinRuta();

        // Si modo estricto y hay nodos inalcanzables -> falla
        if (!permitirPuntosDesconectados && !omisionesSinRuta.isEmpty()) {
            return new RutaImposible(omisionesSinRuta.stream()
                    .map(o -> new ErrorRuta(o.entregaId(), "depósito"))
                    .toList());
        }

        // Compuerta de la fase 2: un par sin ruta que importe detiene el proceso.
        List<ErrorRuta> huecos = ArcosRelevantes.huecos(modelo, matrizRuteo);
        if (!huecos.isEmpty()) return new RutaImposible(huecos);
        // Los pares sin ruta que no importan se rellenan con 0 para que el solver los ignore.
        matrizRuteo = ArcosRelevantes.sanear(modelo, matrizRuteo);

        // Sin entregas no hay nada que optimizar: el vehículo solo vuelve al depósito.
        SolucionVrp solucion = modelo.entregas().isEmpty()
                ? new SolucionVrp(List.of(), List.of(), false)
                : solver.resolver(modelo, matrizRuteo, parametrosPersonalizados);

        // Construir el plan y agregar las omisiones por SIN_RUTA
        PlanDeRuteo plan = ConstructorPlan.construir(problema, modelo, matrizRuteo, solucion, geometria, version);
        if (!omisionesSinRuta.isEmpty()) {
            List<vrp.engine.plan.Omision> todasOmisiones = new ArrayList<>(plan.omisiones());
            todasOmisiones.addAll(omisionesSinRuta);
            plan = new PlanDeRuteo(plan.version(), plan.fecha(), plan.estado(), plan.depositos(),
                    plan.rutas(), List.copyOf(todasOmisiones), plan.advertencias(),
                    plan.distanciaTotalM(), plan.duracionTotalSeg());
        }

        return new PlanGenerado(plan);
    }

    /** Marca el plan como confirmado solo si el usuario aceptó todas las omisiones. */
    public PlanDeRuteo confirmar(PlanDeRuteo plan, Set<String> omisionesAceptadas) {
        if (plan.estado() == EstadoPlan.VALIDO || plan.estado() == EstadoPlan.CONFIRMADO) return plan;
        // Cada omisión debe aceptarse de forma explícita.
        Set<String> faltantes = new HashSet<>();
        plan.omisiones().forEach(o -> faltantes.add(o.entregaId()));
        faltantes.removeAll(omisionesAceptadas);
        if (!faltantes.isEmpty()) {
            throw new IllegalArgumentException("Omisiones sin aceptar explicitamente: " + faltantes);
        }
        return plan.conEstado(EstadoPlan.CONFIRMADO);
    }

    // Getters para acceso desde VrpEngine facade
    public RoutingMatrixProvider matriz() { return matriz; }
    public RouteGeometryProvider geometria() { return geometria; }
    public ParametrosSolver parametros() { return parametros; }
}