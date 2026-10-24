package vrp.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vrp.engine.model.Coordenada;
import vrp.engine.model.DireccionPostal;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.VentanaHoraria;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.solver.ModeloBuilder;
import vrp.engine.solver.ModeloRuteo;
import vrp.engine.solver.OrToolsVrpSolver;
import vrp.engine.solver.ParametrosSolver;
import vrp.engine.solver.SolucionVrp;

class OrToolsVrpSolverTest {

    private static final ParametrosSolver PARAMETROS = new ParametrosSolver(
            vrp.engine.solver.Objetivo.DISTANCIA, 2, 10_000_000L, 0L);

    private static Entrega entrega(String id, double lat, double lon, int demanda, VentanaHoraria ventana) {
        return new Entrega(id, id, new DireccionPostal("x", "y", "z"), new Coordenada(lat, lon), demanda, 5, ventana, 1);
    }

    private static MatrizRuteo matriz(ModeloRuteo m) {
        return new Fakes.MatrizFalsa("m", (a, b) -> false).calcular(m.coordenadas());
    }

    @Test
    void repartePorCapacidadYOmiteLoQueNoEntra() {
        ProblemaVRP p = Fakes.problema(2,
                entrega("E1", -34.61, -58.39, 1, null), entrega("E2", -34.62, -58.40, 1, null),
                entrega("E3", -34.63, -58.41, 1, null), entrega("E4", -34.64, -58.42, 1, null),
                entrega("E5", -34.65, -58.43, 1, null));
        ModeloRuteo m = ModeloBuilder.construir(p, Map.of(), null, PARAMETROS);

        SolucionVrp s = new OrToolsVrpSolver().resolver(m, matriz(m), PARAMETROS);

        assertEquals(1, s.omitidos().size());
        assertTrue(s.rutas().stream().allMatch(r -> r.nodos().size() <= 2));
        assertEquals(4, s.rutas().stream().mapToInt(r -> r.nodos().size()).sum());
    }

    @Test
    void respetaVentanasHorarias() {
        VentanaHoraria imposible = new VentanaHoraria(LocalTime.of(6, 0), LocalTime.of(7, 0));
        VentanaHoraria tarde = new VentanaHoraria(LocalTime.of(14, 0), LocalTime.of(15, 0));
        ProblemaVRP p = Fakes.problema(10,
                entrega("E1", -34.61, -58.39, 1, imposible), entrega("E2", -34.62, -58.40, 1, tarde));
        ModeloRuteo m = ModeloBuilder.construir(p, Map.of(), null, PARAMETROS);

        SolucionVrp s = new OrToolsVrpSolver().resolver(m, matriz(m), PARAMETROS);

        assertEquals(List.of(m.primerNodoEntrega()), s.omitidos());
        assertEquals(1, s.rutas().stream().mapToInt(r -> r.nodos().size()).sum());
    }
}
