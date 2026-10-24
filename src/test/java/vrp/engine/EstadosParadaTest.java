package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import vrp.engine.model.EstadoParada;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.resultado.PlanGenerado;

class EstadosParadaTest {

    private static PlanDeRuteo plan() {
        ProblemaVRP p = Fakes.problema(2,
                Fakes.entrega("E1", PlanificadorTest.C1.lat(), PlanificadorTest.C1.lon(), 1),
                Fakes.entrega("E2", PlanificadorTest.C2.lat(), PlanificadorTest.C2.lon(), 1),
                Fakes.entrega("E3", PlanificadorTest.C3.lat(), PlanificadorTest.C3.lon(), 1));
        return assertInstanceOf(PlanGenerado.class, PlanificadorTest.planificador().planificar(p)).plan();
    }

    private static EstadoParada estado(PlanDeRuteo plan, String id) {
        return plan.parada(id).orElseThrow().estado();
    }

    @Test
    void cambiaElEstadoSinTocarElRestoDelPlan() {
        PlanDeRuteo base = plan();

        PlanDeRuteo nuevo = base.conEstadoDeParada("E1", EstadoParada.EN_CAMINO);

        assertEquals(EstadoParada.EN_CAMINO, estado(nuevo, "E1"));
        assertEquals(EstadoParada.PENDIENTE, estado(nuevo, "E2"));
        assertSame(base.rutas().get(1), nuevo.rutas().get(1));
        assertEquals(base.parada("E1").orElseThrow().eta(), nuevo.parada("E1").orElseThrow().eta());
        assertEquals(EstadoParada.PENDIENTE, estado(base, "E1"));
    }

    @Test
    void permiteLosEventosDelChoferEnCadena() {
        PlanDeRuteo p = plan().conEstadoDeParada("E1", EstadoParada.EN_CAMINO)
                .conEstadoDeParada("E1", EstadoParada.ENTREGADO)
                .conEstadoDeParada("E2", EstadoParada.CANCELADO);
        assertEquals(EstadoParada.ENTREGADO, estado(p, "E1"));
        assertEquals(EstadoParada.CANCELADO, estado(p, "E2"));
    }

    @Test
    void elMismoEstadoDevuelveElMismoPlan() {
        PlanDeRuteo base = plan();
        assertSame(base, base.conEstadoDeParada("E1", EstadoParada.PENDIENTE));
    }

    @Test
    void unEstadoCerradoNoSeReabre() {
        PlanDeRuteo cerrado = plan().conEstadoDeParada("E1", EstadoParada.ENTREGADO);
        assertThrows(IllegalArgumentException.class, () -> cerrado.conEstadoDeParada("E1", EstadoParada.PENDIENTE));
        assertThrows(IllegalArgumentException.class, () -> cerrado.conEstadoDeParada("E1", EstadoParada.CANCELADO));
    }

    @Test
    void reprogramarYDevolverSoloSeAplicanConElReoptimizador() {
        PlanDeRuteo base = plan();
        assertThrows(IllegalArgumentException.class, () -> base.conEstadoDeParada("E1", EstadoParada.REPROGRAMADO));
        assertThrows(IllegalArgumentException.class, () -> base.conEstadoDeParada("E1", EstadoParada.DEVOLVER_A_DEPOSITO));
    }

    @Test
    void unaEntregaInexistenteEsRechazada() {
        assertThrows(IllegalArgumentException.class, () -> plan().conEstadoDeParada("E99", EstadoParada.ENTREGADO));
    }
}
