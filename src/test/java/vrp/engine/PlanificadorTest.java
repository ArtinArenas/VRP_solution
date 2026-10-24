package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vrp.engine.geocoding.GeocodingChain;
import vrp.engine.geocoding.MotivoGeo;
import vrp.engine.model.Coordenada;
import vrp.engine.model.DireccionPostal;
import vrp.engine.model.Entrega;
import vrp.engine.model.EstadoParada;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.VentanaHoraria;
import vrp.engine.plan.EstadoPlan;
import vrp.engine.plan.MotivoOmision;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.plan.RutaVehiculo;
import vrp.engine.resultado.DireccionesInvalidas;
import vrp.engine.resultado.Geocodificado;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ProblemaInvalido;
import vrp.engine.resultado.ProveedorNoDisponible;
import vrp.engine.resultado.ResultadoFase1;
import vrp.engine.resultado.ResultadoFase2;
import vrp.engine.resultado.RutaImposible;
import vrp.engine.routing.MatrixChain;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.routing.RoutingException;
import vrp.engine.routing.RoutingMatrixProvider;
import vrp.engine.service.Planificador;
import vrp.engine.solver.ParametrosSolver;
import vrp.engine.solver.SolucionVrp;
import vrp.engine.solver.VrpSolver;

class PlanificadorTest {

    static final Coordenada C1 = new Coordenada(-34.61, -58.39);
    static final Coordenada C2 = new Coordenada(-34.62, -58.40);
    static final Coordenada C3 = new Coordenada(-34.63, -58.41);

    static Planificador planificador(VrpSolver solver, RoutingMatrixProvider... matrices) {
        Fakes.GeoFalso georef = new Fakes.GeoFalso("georef", Map.of("Calle E1 100", C1));
        Fakes.GeoFalso nominatim = new Fakes.GeoFalso("nominatim", Map.of("Calle E2 100", C2));
        return new Planificador(new GeocodingChain(List.of(georef, nominatim)),
                new MatrixChain(List.of(matrices)), new Fakes.GeometriaFalsa(), solver, ParametrosSolver.porDefecto());
    }

    static Planificador planificador() {
        return planificador(new Fakes.SolverSecuencial(), new Fakes.MatrizFalsa("valhalla", (a, b) -> false));
    }

    @Test
    void fase1ResuelveConFallbackYCompletaCoordenadas() {
        ProblemaVRP p = Fakes.problema(10, Fakes.entrega("E1", 0, 0, 1), Fakes.entrega("E2", 0, 0, 1));
        ResultadoFase1 r = planificador().geocodificar(p);

        Geocodificado g = assertInstanceOf(Geocodificado.class, r);
        assertEquals(C1, g.problema().entregas().get(0).coordenada());
        assertEquals(C2, g.problema().entregas().get(1).coordenada());
        assertTrue(g.problema().geocodificado());
    }

    @Test
    void fase1DetieneConErroresEstructuradosYNoLlamaAlRuteo() {
        Fakes.MatrizFalsa matriz = new Fakes.MatrizFalsa("valhalla", (a, b) -> false);
        Planificador pl = planificador(new Fakes.SolverSecuencial(), matriz);
        ProblemaVRP p = Fakes.problema(10, Fakes.entrega("E1", 0, 0, 1), Fakes.entrega("E3", 0, 0, 1));

        DireccionesInvalidas r = assertInstanceOf(DireccionesInvalidas.class, pl.geocodificar(p));

        assertEquals(1, r.errores().size());
        assertEquals("E3", r.errores().get(0).entidadId());
        assertEquals(MotivoGeo.NO_ENCONTRADA, r.errores().get(0).motivo());
        assertEquals(List.of("georef", "nominatim"), r.errores().get(0).proveedoresIntentados());
        assertEquals(0, matriz.llamadas);
    }

    @Test
    void problemaConDatosFaltantesDevuelveProblemaInvalido() {
        Entrega sinProvincia = new Entrega("E9", "x", new DireccionPostal("Calle 1", "Merlo", ""), null, 1, 0, null, 1);
        ProblemaInvalido r = assertInstanceOf(ProblemaInvalido.class,
                planificador().geocodificar(Fakes.problema(10, sinProvincia)));
        assertEquals("E9", r.errores().get(0).referencia());
    }

    @Test
    void planificarRequiereFase1Completa() {
        ProblemaVRP p = Fakes.problema(10, Fakes.entrega("E1", 0, 0, 1));
        assertThrows(IllegalStateException.class, () -> planificador().planificar(p));
    }

    @Test
    void planGeneradoTieneOrdenEtaEstadosYPolilineas() {
        ProblemaVRP p = Fakes.problema(10,
                Fakes.entrega("E1", C1.lat(), C1.lon(), 1),
                Fakes.entrega("E2", C2.lat(), C2.lon(), 1),
                Fakes.entrega("E3", C3.lat(), C3.lon(), 1));

        PlanDeRuteo plan = assertInstanceOf(PlanGenerado.class, planificador().planificar(p)).plan();

        assertEquals(EstadoPlan.VALIDO, plan.estado());
        assertEquals(1, plan.version());
        RutaVehiculo v1 = plan.rutas().get(0);
        RutaVehiculo v2 = plan.rutas().get(1);
        assertEquals(3, v1.paradas().size());
        assertTrue(v2.paradas().isEmpty());
        assertEquals(List.of(1, 2, 3), v1.paradas().stream().map(x -> x.orden()).toList());
        assertTrue(v1.paradas().stream().allMatch(x -> x.estado() == EstadoParada.PENDIENTE));
        assertTrue(v1.paradas().stream().noneMatch(x -> x.entrega().id().equals("D1")));

        LocalDateTime salida = Fakes.FECHA.atTime(8, 0);
        assertEquals(salida, v1.salida());
        assertTrue(v1.paradas().get(0).eta().isAfter(salida));
        assertTrue(v1.paradas().get(1).eta().isAfter(v1.paradas().get(0).eta().plusMinutes(5)));
        assertTrue(v1.regreso().isAfter(v1.paradas().get(2).eta()));
        assertNotNull(v1.paradas().get(0).polilineaTramo());
        assertNotNull(v1.polilinea());
        assertNotNull(v1.polilineaRegreso());
        assertTrue(plan.distanciaTotalM() > 0);
        assertEquals(v1.distanciaTotalM() + v2.distanciaTotalM(), plan.distanciaTotalM(), 1e-6);
        assertTrue(plan.omisiones().isEmpty());
    }

    @Test
    void omisionesRequierenConfirmacionExplicita() {
        ProblemaVRP p = Fakes.problema(2,
                Fakes.entrega("E1", C1.lat(), C1.lon(), 2),
                Fakes.entrega("E2", C2.lat(), C2.lon(), 5),
                Fakes.entrega("E3", C3.lat(), C3.lon(), 2));
        Planificador pl = planificador();

        PlanDeRuteo plan = assertInstanceOf(PlanGenerado.class, pl.planificar(p)).plan();

        assertEquals(EstadoPlan.REQUIERE_CONFIRMACION, plan.estado());
        assertEquals(1, plan.omisiones().size());
        assertEquals("E2", plan.omisiones().get(0).entregaId());
        assertEquals(MotivoOmision.CAPACIDAD_INDIVIDUAL, plan.omisiones().get(0).motivo());
        assertEquals(List.of("E2"), plan.advertencias().get(0).entregaIds());

        assertThrows(IllegalArgumentException.class, () -> pl.confirmar(plan, Set.of()));
        assertEquals(EstadoPlan.CONFIRMADO, pl.confirmar(plan, Set.of("E2")).estado());
    }

    @Test
    void diagnosticaVentanaImposibleYRestriccionesCombinadas() {
        VentanaHoraria temprano = new VentanaHoraria(LocalTime.of(6, 0), LocalTime.of(7, 0));
        Entrega conVentana = new Entrega("E1", "x", new DireccionPostal("Calle E1 100", "CABA", "CABA"), C1, 1, 5, temprano, 1);
        Entrega normal = Fakes.entrega("E2", C2.lat(), C2.lon(), 1);

        VrpSolver omiteTodo = (m, mat, par) -> new SolucionVrp(
                SolucionVrp.sinSolucion(m).rutas(), SolucionVrp.sinSolucion(m).omitidos(), false);
        Planificador pl = planificador(omiteTodo, new Fakes.MatrizFalsa("valhalla", (a, b) -> false));

        PlanDeRuteo plan = assertInstanceOf(PlanGenerado.class, pl.planificar(Fakes.problema(10, conVentana, normal))).plan();

        assertEquals(MotivoOmision.VENTANA_HORARIA_IMPOSIBLE, plan.omisiones().get(0).motivo());
        assertEquals(MotivoOmision.RESTRICCIONES_COMBINADAS, plan.omisiones().get(1).motivo());
    }

    @Test
    void rutaImposibleSeIntentaConSecundarioYSiFallaNoAvanza() {
        ProblemaVRP p = Fakes.problema(10,
                Fakes.entrega("E1", C1.lat(), C1.lon(), 1), Fakes.entrega("E2", C2.lat(), C2.lon(), 1));
        java.util.function.BiPredicate<Coordenada, Coordenada> c1AC2 = (a, b) -> a.equals(C1) && b.equals(C2);

        Fakes.MatrizFalsa secundarioOk = new Fakes.MatrizFalsa("osrm", (a, b) -> false);
        Planificador conRescate = planificador(new Fakes.SolverSecuencial(),
                new Fakes.MatrizFalsa("valhalla", c1AC2), secundarioOk);
        assertInstanceOf(PlanGenerado.class, conRescate.planificar(p));
        assertEquals(1, secundarioOk.llamadas);

        Planificador sinRescate = planificador(new Fakes.SolverSecuencial(),
                new Fakes.MatrizFalsa("valhalla", c1AC2), new Fakes.MatrizFalsa("osrm", c1AC2));
        RutaImposible r = assertInstanceOf(RutaImposible.class, sinRescate.planificar(p));
        assertEquals(1, r.errores().size());
        assertEquals("E1", r.errores().get(0).desdeId());
        assertEquals("E2", r.errores().get(0).hastaId());
    }

    @Test
    void proveedoresCaidosDevuelvenResultadoEstructurado() {
        RoutingMatrixProvider caido = new RoutingMatrixProvider() {
            @Override
            public String nombre() {
                return "caido";
            }

            @Override
            public MatrizRuteo calcular(List<Coordenada> p) {
                throw new RoutingException("caido: sin conexion");
            }
        };
        ProblemaVRP p = Fakes.problema(10, Fakes.entrega("E1", C1.lat(), C1.lon(), 1));
        ResultadoFase2 r = planificador(new Fakes.SolverSecuencial(), caido).planificar(p);
        assertInstanceOf(ProveedorNoDisponible.class, r);
    }
}
