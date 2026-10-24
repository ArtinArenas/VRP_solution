package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vrp.engine.model.Coordenada;
import vrp.engine.model.DireccionPostal;
import vrp.engine.model.EstadoParada;
import vrp.engine.model.PosicionVehiculo;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.VentanaHoraria;
import vrp.engine.plan.Parada;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.plan.RutaVehiculo;
import vrp.engine.resultado.DireccionesInvalidas;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ProblemaInvalido;
import vrp.engine.service.CambiosJornada;
import vrp.engine.service.ModificacionEntrega;
import vrp.engine.service.Planificador;
import vrp.engine.service.Reprogramacion;
import vrp.engine.service.Reoptimizador;

class ReoptimizadorTest {

    static final Coordenada C1 = PlanificadorTest.C1;
    static final Coordenada C2 = PlanificadorTest.C2;
    static final Coordenada C3 = PlanificadorTest.C3;
    static final Coordenada C4 = new Coordenada(-34.64, -58.42);
    static final Coordenada GPS_V1 = new Coordenada(-34.615, -58.395);
    static final Coordenada GPS_V2 = new Coordenada(-34.645, -58.425);
    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 24, 9, 0);

    private final Fakes.MatrizFalsa matriz = new Fakes.MatrizFalsa("valhalla", (a, b) -> false);
    private final Planificador planificador = PlanificadorTest.planificador(new Fakes.SolverSecuencial(), matriz);
    private final Reoptimizador reoptimizador = new Reoptimizador(planificador);

    private PlanDeRuteo planBase() {
        ProblemaVRP p = Fakes.problema(3,
                Fakes.entrega("E1", C1.lat(), C1.lon(), 1),
                Fakes.entrega("E2", C2.lat(), C2.lon(), 1),
                Fakes.entrega("E3", C3.lat(), C3.lon(), 1),
                Fakes.entrega("E4", C4.lat(), C4.lon(), 1));
        PlanDeRuteo plan = assertInstanceOf(PlanGenerado.class, planificador.planificar(p)).plan();
        assertEquals(List.of("E1", "E2", "E3"), ids(plan.rutas().get(0)));
        assertEquals(List.of("E4"), ids(plan.rutas().get(1)));
        matriz.llamadas = 0;
        return plan;
    }

    private static List<String> ids(RutaVehiculo r) {
        return r.paradas().stream().map(p -> p.entrega().id()).toList();
    }

    private static PlanDeRuteo conEstados(PlanDeRuteo plan, String vehiculoId, Map<String, EstadoParada> estados) {
        List<RutaVehiculo> rutas = new ArrayList<>();
        for (RutaVehiculo r : plan.rutas()) {
            if (!r.vehiculo().id().equals(vehiculoId)) {
                rutas.add(r);
                continue;
            }
            List<Parada> paradas = new ArrayList<>();
            for (Parada p : r.paradas()) {
                paradas.add(p.conOrdenYEstado(p.orden(), estados.getOrDefault(p.entrega().id(), p.estado())));
            }
            rutas.add(new RutaVehiculo(r.vehiculo(), r.origen(), r.posicionActual(), r.destino(), r.salida(),
                    r.regreso(), paradas, r.distanciaRegresoM(), r.duracionRegresoSeg(), r.polilineaRegreso(),
                    r.polilinea(), r.distanciaTotalM(), r.duracionTotalSeg()));
        }
        return new PlanDeRuteo(plan.version(), plan.fecha(), plan.estado(), plan.depositos(), rutas,
                plan.omisiones(), plan.advertencias(), plan.distanciaTotalM(), plan.duracionTotalSeg());
    }

    private static CambiosJornada cambios(List<PosicionVehiculo> posiciones, List<ModificacionEntrega> mods) {
        return new CambiosJornada(AHORA, posiciones, mods, List.of(), List.of());
    }

    private static CambiosJornada acciones(List<String> devolver, List<Reprogramacion> reprogramar) {
        return new CambiosJornada(AHORA, gps(), List.of(), devolver, reprogramar);
    }

    private static List<PosicionVehiculo> gps() {
        return List.of(new PosicionVehiculo("V1", GPS_V1, AHORA.minusMinutes(1)),
                new PosicionVehiculo("V2", GPS_V2, AHORA.minusMinutes(1)));
    }

    private static PlanDeRuteo plan(Object resultado) {
        return assertInstanceOf(PlanGenerado.class, resultado).plan();
    }

    @Test
    void devolverADepositoSacaLaParadaYSoloRecalculaLaRutaDelChoferAfectado() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of(
                "E1", EstadoParada.ENTREGADO, "E2", EstadoParada.EN_CAMINO, "E3", EstadoParada.DEVOLVER_A_DEPOSITO));
        base = conEstados(base, "V2", Map.of("E4", EstadoParada.ENTREGADO));

        PlanDeRuteo nuevo = plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of())));

        assertEquals(base.version() + 1, nuevo.version());
        assertSame(base.rutas().get(1), nuevo.rutas().get(1));

        RutaVehiculo v1 = nuevo.rutas().get(0);
        assertEquals(List.of("E2"), ids(v1));
        assertEquals(EstadoParada.EN_CAMINO, v1.paradas().get(0).estado());
        assertEquals(1, v1.paradas().get(0).orden());
        assertEquals(GPS_V1, v1.posicionActual());
        assertEquals(Fakes.metros(GPS_V1, C2), v1.paradas().get(0).distanciaDesdeAnteriorM(), 1e-6);
        assertEquals(Fakes.metros(GPS_V1, C2) + v1.distanciaRegresoM(), v1.distanciaTotalM(), 1e-6);
        assertEquals(3, matriz.ultimoTamano);
        assertEquals(1, matriz.llamadas);
        assertEquals(v1.distanciaTotalM() + nuevo.rutas().get(1).distanciaTotalM(), nuevo.distanciaTotalM(), 1e-6);
    }

    @Test
    void siSeDevuelveTodoElCamionVuelveAlDepositoDesdeSuPosicion() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of(
                "E1", EstadoParada.ENTREGADO, "E2", EstadoParada.DEVOLVER_A_DEPOSITO, "E3", EstadoParada.DEVOLVER_A_DEPOSITO));

        RutaVehiculo v1 = plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of()))).rutas().get(0);

        assertTrue(v1.paradas().isEmpty());
        double directo = Fakes.metros(GPS_V1, Fakes.deposito().coordenada());
        assertEquals(directo, v1.distanciaRegresoM(), 1e-6);
        assertEquals(directo, v1.distanciaTotalM(), 1e-6);
        assertEquals(2, matriz.ultimoTamano);
    }

    @Test
    void sinGpsUsaLaUltimaParadaCerrada() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of(
                "E1", EstadoParada.ENTREGADO, "E3", EstadoParada.DEVOLVER_A_DEPOSITO));

        RutaVehiculo v1 = plan(reoptimizador.reoptimizar(base, cambios(List.of(), List.of()))).rutas().get(0);

        assertEquals(C1, v1.posicionActual());
        assertEquals(List.of("E2"), ids(v1));
        assertEquals(Fakes.metros(C1, C2), v1.paradas().get(0).distanciaDesdeAnteriorM(), 1e-6);
        assertEquals(EstadoParada.PENDIENTE, v1.paradas().get(0).estado());
    }

    @Test
    void soloSeRecalculanPendientesYPostergadasYLasCanceladasSalen() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of(
                "E1", EstadoParada.CANCELADO, "E2", EstadoParada.REPROGRAMADO));

        RutaVehiculo v1 = plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of()))).rutas().get(0);

        assertEquals(List.of("E2", "E3"), ids(v1));
        assertTrue(v1.paradas().stream().allMatch(p -> p.estado() == EstadoParada.PENDIENTE));
        assertTrue(v1.paradas().stream().allMatch(p -> p.eta() != null));
        assertEquals(4, matriz.ultimoTamano);
    }

    @Test
    void reprogramarDisparaElRecalculoSoloDeSuRutaYNoSeRepite() {
        PlanDeRuteo base = conEstados(planBase(), "V2", Map.of("E4", EstadoParada.REPROGRAMADO));

        PlanDeRuteo primera = plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of())));

        assertSame(base.rutas().get(0), primera.rutas().get(0));
        assertEquals(base.version() + 1, primera.version());
        assertEquals(EstadoParada.PENDIENTE, primera.rutas().get(1).paradas().get(0).estado());
        assertEquals(Fakes.metros(GPS_V2, C4), primera.rutas().get(1).paradas().get(0).distanciaDesdeAnteriorM(), 1e-6);
        assertSame(primera, plan(reoptimizador.reoptimizar(primera, cambios(gps(), List.of()))));
    }

    @Test
    void restriccionesSobreUnClienteSoloAfectanSuRuta() {
        PlanDeRuteo base = planBase();
        VentanaHoraria ventana = new VentanaHoraria(LocalTime.of(10, 0), LocalTime.of(12, 0));
        ModificacionEntrega mod = new ModificacionEntrega("E4", ventana, false, 5, 20, null);

        PlanDeRuteo nuevo = plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of(mod))));

        assertSame(base.rutas().get(0), nuevo.rutas().get(0));
        var e4 = nuevo.rutas().get(1).paradas().get(0).entrega();
        assertEquals(ventana, e4.ventana());
        assertEquals(5, e4.prioridad());
        assertEquals(20, e4.servicioMin());
        assertEquals(1, e4.demanda());
        assertEquals(3, matriz.ultimoTamano);
    }

    @Test
    void cambiarLaDireccionGeocodificaSoloEsaDireccion() {
        ModificacionEntrega mod = new ModificacionEntrega("E3", null, false, null, null,
                new DireccionPostal("Calle E1 100", "CABA", "Ciudad Autonoma de Buenos Aires"));

        PlanDeRuteo nuevo = plan(reoptimizador.reoptimizar(planBase(), cambios(gps(), List.of(mod))));

        assertEquals(C1, nuevo.rutas().get(0).paradas().get(2).entrega().coordenada());
    }

    @Test
    void direccionNuevaNoResolubleDetieneSinModificarElPlan() {
        ModificacionEntrega mod = new ModificacionEntrega("E3", null, false, null, null,
                new DireccionPostal("Calle inexistente 9", "CABA", "Ciudad Autonoma de Buenos Aires"));

        DireccionesInvalidas r = assertInstanceOf(DireccionesInvalidas.class,
                reoptimizador.reoptimizar(planBase(), cambios(gps(), List.of(mod))));

        assertEquals("E3", r.errores().get(0).entidadId());
    }

    @Test
    void rechazaCambiosSobreParadasCerradasOInexistentes() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of("E1", EstadoParada.ENTREGADO));
        ModificacionEntrega sobreEntregada = new ModificacionEntrega("E1", null, false, 3, null, null);
        ModificacionEntrega inexistente = new ModificacionEntrega("E99", null, false, 3, null, null);

        ProblemaInvalido r = assertInstanceOf(ProblemaInvalido.class,
                reoptimizador.reoptimizar(base, cambios(gps(), List.of(sobreEntregada, inexistente))));

        assertEquals(List.of("E1", "E99"), r.errores().stream().map(e -> e.referencia()).toList());
    }

    @Test
    void sinCambiosDevuelveElMismoPlan() {
        PlanDeRuteo base = planBase();
        assertSame(base, plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of()))));
        assertEquals(0, matriz.llamadas);
    }

    @Test
    void unaRutaYaReoptimizadaNoVuelveARecalcularseSola() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of("E3", EstadoParada.DEVOLVER_A_DEPOSITO));
        PlanDeRuteo primera = plan(reoptimizador.reoptimizar(base, cambios(gps(), List.of())));

        PlanDeRuteo segunda = plan(reoptimizador.reoptimizar(primera, cambios(gps(), List.of())));

        assertSame(primera, segunda);
    }

    @Test
    void devolverADepositoPorAccionExplicitaSacaLaParadaSinEditarEstados() {
        PlanDeRuteo base = planBase();

        PlanDeRuteo nuevo = plan(reoptimizador.reoptimizar(base, acciones(List.of("E2"), List.of())));

        assertEquals(List.of("E1", "E3"), ids(nuevo.rutas().get(0)));
        assertSame(base.rutas().get(1), nuevo.rutas().get(1));
        assertEquals(base.version() + 1, nuevo.version());
        assertEquals(Fakes.metros(GPS_V1, C1), nuevo.rutas().get(0).paradas().get(0).distanciaDesdeAnteriorM(), 1e-6);
    }

    @Test
    void reprogramarPorAccionAplicaLaNuevaVentanaYRecalculaSoloEsaRuta() {
        PlanDeRuteo base = planBase();
        VentanaHoraria tarde = new VentanaHoraria(LocalTime.of(15, 0), LocalTime.of(17, 0));

        PlanDeRuteo nuevo = plan(reoptimizador.reoptimizar(base,
                acciones(List.of(), List.of(new Reprogramacion("E4", tarde)))));

        assertSame(base.rutas().get(0), nuevo.rutas().get(0));
        Parada e4 = nuevo.rutas().get(1).paradas().get(0);
        assertEquals(tarde, e4.entrega().ventana());
        assertEquals(EstadoParada.PENDIENTE, e4.estado());
        assertEquals(1, matriz.llamadas);
    }

    @Test
    void reprogramarSinVentanaTambienRecalculaSuRuta() {
        PlanDeRuteo base = planBase();

        PlanDeRuteo nuevo = plan(reoptimizador.reoptimizar(base,
                acciones(List.of(), List.of(new Reprogramacion("E1", null)))));

        assertEquals(base.version() + 1, nuevo.version());
        assertSame(base.rutas().get(1), nuevo.rutas().get(1));
        assertEquals(List.of("E1", "E2", "E3"), ids(nuevo.rutas().get(0)));
    }

    @Test
    void rechazaAccionesInvalidasSinModificarElPlan() {
        PlanDeRuteo base = conEstados(planBase(), "V1", Map.of("E1", EstadoParada.ENTREGADO));
        CambiosJornada cambios = new CambiosJornada(AHORA, gps(),
                List.of(new ModificacionEntrega("E3", null, false, 2, null, null)),
                List.of("E1", "E3", "E99", "E3"),
                List.of(new Reprogramacion("E3", null), new Reprogramacion("E4", null), new Reprogramacion("E4", null)));

        ProblemaInvalido r = assertInstanceOf(ProblemaInvalido.class, reoptimizador.reoptimizar(base, cambios));

        List<String> mensajes = r.errores().stream().map(e -> e.referencia() + ": " + e.mensaje()).toList();
        assertTrue(mensajes.stream().anyMatch(m -> m.startsWith("E1:") && m.contains("entregado")));
        assertTrue(mensajes.stream().anyMatch(m -> m.startsWith("E99:")));
        assertTrue(mensajes.stream().anyMatch(m -> m.startsWith("E3:") && m.contains("repetida en devolverADeposito")));
        assertTrue(mensajes.stream().anyMatch(m -> m.startsWith("E3:") && m.contains("devolver y reprogramar")));
        assertTrue(mensajes.stream().anyMatch(m -> m.startsWith("E4:") && m.contains("repetida en reprogramar")));
        assertTrue(mensajes.stream().anyMatch(m -> m.startsWith("E3:") && m.contains("se devuelve")));
        assertEquals(0, matriz.llamadas);
    }
}
