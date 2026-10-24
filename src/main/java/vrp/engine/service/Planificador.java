package vrp.engine.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vrp.engine.config.ConfiguracionMotor;
import vrp.engine.geocoding.GeocodingChain;
import vrp.engine.geocoding.GeocodingProvider;
import vrp.engine.geocoding.GeorefProvider;
import vrp.engine.geocoding.NominatimLocalProvider;
import vrp.engine.geocoding.ResolucionGeo;
import vrp.engine.geocoding.SolicitudGeocodificacion;
import vrp.engine.http.HttpTransporte;
import vrp.engine.io.ValidadorProblema;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Deposito;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.plan.EstadoPlan;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.resultado.DireccionesInvalidas;
import vrp.engine.resultado.ErrorDireccion;
import vrp.engine.resultado.ErrorLectura;
import vrp.engine.resultado.ErrorRuta;
import vrp.engine.resultado.Geocodificado;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ProblemaInvalido;
import vrp.engine.resultado.ProveedorNoDisponible;
import vrp.engine.resultado.ResultadoFase1;
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
import vrp.engine.solver.ModeloBuilder;
import vrp.engine.solver.ModeloRuteo;
import vrp.engine.solver.OrToolsVrpSolver;
import vrp.engine.solver.ParametrosSolver;
import vrp.engine.solver.SolucionVrp;
import vrp.engine.solver.VrpSolver;

public final class Planificador {

    private static final String PREFIJO_DEPOSITO = "D:";
    private static final String PREFIJO_ENTREGA = "E:";

    private final GeocodingChain geocodificador;
    private final RoutingMatrixProvider matriz;
    private final RouteGeometryProvider geometria;
    private final VrpSolver solver;
    private final ParametrosSolver parametros;

    public Planificador(GeocodingChain geocodificador,
                        RoutingMatrixProvider matriz,
                        RouteGeometryProvider geometria,
                        VrpSolver solver,
                        ParametrosSolver parametros) {
        this.geocodificador = geocodificador;
        this.matriz = matriz;
        this.geometria = geometria;
        this.solver = solver;
        this.parametros = parametros;
    }

    /** Arma el planificador con los proveedores definidos en la configuración. */
    public static Planificador porDefecto(ConfiguracionMotor cfg, HttpTransporte http) {
        List<GeocodingProvider> geocoders = new ArrayList<>();
        geocoders.add(new GeorefProvider(http, cfg.georefUrl()));
        if (cfg.nominatimUrl() != null) {
            geocoders.add(new NominatimLocalProvider(http, cfg.nominatimUrl(), cfg.nominatimEmail(),
                    cfg.nominatimPausaMs()));
        }

        List<RoutingMatrixProvider> matrices = new ArrayList<>();
        List<RouteGeometryProvider> geometrias = new ArrayList<>();
        if (cfg.valhallaUrl() != null) {
            ValhallaClient v = new ValhallaClient(http, cfg.valhallaUrl(), cfg.valhallaCosting(), cfg.tamanoBloqueMatriz());
            matrices.add(v);
            geometrias.add(v);
        }
        if (cfg.osrmUrl() != null) {
            OsrmClient o = new OsrmClient(http, cfg.osrmUrl(), cfg.tamanoBloqueMatriz());
            matrices.add(o);
            geometrias.add(o);
        }
        if (matrices.isEmpty()) {
            throw new IllegalArgumentException("Debe configurarse Valhalla.url y/o OSRM.url");
        }

        return new Planificador(new GeocodingChain(geocoders), new MatrixChain(matrices),
                new GeometryChain(geometrias), new OrToolsVrpSolver(), cfg.parametrosSolver());
    }

    /** Fase 1: valida el problema y completa las coordenadas que falten. */
    public ResultadoFase1 geocodificar(ProblemaVRP problema) {
        // Primero la estructura: sin datos válidos no se consulta ningún servicio externo.
        List<ErrorLectura> invalidos = ValidadorProblema.validar(problema);
        if (!invalidos.isEmpty()) return new ProblemaInvalido(invalidos);

        // Solo se geocodifica lo que todavía no trae coordenadas.
        List<SolicitudGeocodificacion> solicitudes = new ArrayList<>();
        for (Deposito d : problema.depositos()) {
            if (d.coordenada() == null) solicitudes.add(new SolicitudGeocodificacion(PREFIJO_DEPOSITO + d.id(), d.direccion()));
        }
        for (Entrega e : problema.entregas()) {
            if (e.coordenada() == null) solicitudes.add(new SolicitudGeocodificacion(PREFIJO_ENTREGA + e.id(), e.direccion()));
        }
        if (solicitudes.isEmpty()) return new Geocodificado(problema);

        Map<String, ResolucionGeo> resoluciones = geocodificador.resolver(solicitudes);
        List<ErrorDireccion> errores = new ArrayList<>();
        List<Deposito> depositos = new ArrayList<>();
        List<Entrega> entregas = new ArrayList<>();

        for (Deposito d : problema.depositos()) {
            if (d.coordenada() != null) {
                depositos.add(d);
                continue;
            }
            ResolucionGeo r = resoluciones.get(PREFIJO_DEPOSITO + d.id());
            if (r.resultado().exito()) {
                depositos.add(d.conCoordenada(r.resultado().coordenada()));
            } else {
                errores.add(error(d.id(), TipoEntidad.DEPOSITO, d.direccion(), r));
            }
        }
        for (Entrega e : problema.entregas()) {
            if (e.coordenada() != null) {
                entregas.add(e);
                continue;
            }
            ResolucionGeo r = resoluciones.get(PREFIJO_ENTREGA + e.id());
            if (r.resultado().exito()) {
                entregas.add(e.conCoordenada(r.resultado().coordenada()));
            } else {
                errores.add(error(e.id(), TipoEntidad.ENTREGA, e.direccion(), r));
            }
        }

        // Compuerta de la fase 1: con una sola dirección inválida no se avanza al ruteo.
        if (!errores.isEmpty()) return new DireccionesInvalidas(errores);
        return new Geocodificado(new ProblemaVRP(problema.fecha(), depositos, entregas, problema.vehiculos()));
    }

    /** Fase 2 para un plan nuevo: sin posiciones de choferes y con la versión 1. */
    public ResultadoFase2 planificar(ProblemaVRP problema) {
        return planificar(problema, Map.of(), null, 1);
    }

    /** Fase 2: pide la matriz, resuelve el ruteo y arma el plan; usado también por la reoptimización. */
    public ResultadoFase2 planificar(ProblemaVRP problema,
                                     Map<String, Coordenada> posiciones,
                                     Long ahoraSeg,
                                     int version) {
        if (!problema.geocodificado()) {
            throw new IllegalStateException("La fase 1 (geocodificacion) debe completarse antes de planificar");
        }

        // Nodos: depósitos, posiciones de choferes y clientes.
        ModeloRuteo modelo = ModeloBuilder.construir(problema, posiciones, ahoraSeg, parametros);

        // Matriz entre todos los puntos, con fallback entre proveedores.
        MatrizRuteo matrizRuteo;
        try {
            matrizRuteo = matriz.calcular(modelo.coordenadas());
        } catch (RoutingException e) {
            return new ProveedorNoDisponible(e.getMessage());
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

        return new PlanGenerado(ConstructorPlan.construir(problema, modelo, matrizRuteo, solucion, geometria, version));
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

    /** Convierte una resolución fallida en un error de dirección. */
    private static ErrorDireccion error(String id, TipoEntidad tipo, vrp.engine.model.DireccionPostal direccion, ResolucionGeo r) {
        return new ErrorDireccion(id, tipo, direccion, r.resultado().motivo(), r.resultado().detalle(),
                r.proveedoresIntentados());
    }
}
