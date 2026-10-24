package vrp.engine.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Entrega;
import vrp.engine.model.EstadoParada;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.plan.Advertencia;
import vrp.engine.plan.EstadoPlan;
import vrp.engine.plan.MotivoOmision;
import vrp.engine.plan.Omision;
import vrp.engine.plan.Parada;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.plan.RutaVehiculo;
import vrp.engine.plan.TipoAdvertencia;
import vrp.engine.routing.MatrizRuteo;
import vrp.engine.routing.RouteGeometryProvider;
import vrp.engine.routing.RoutingException;
import vrp.engine.solver.ModeloRuteo;
import vrp.engine.solver.RutaNodos;
import vrp.engine.solver.Simulador;
import vrp.engine.solver.SolucionVrp;
import vrp.engine.util.Polyline6;

final class ConstructorPlan {

    private ConstructorPlan() {
    }

    /** Arma el plan final: rutas con ETA y polilíneas, omisiones, advertencias y totales. */
    static PlanDeRuteo construir(ProblemaVRP problema,
                                 ModeloRuteo modelo,
                                 MatrizRuteo matriz,
                                 SolucionVrp solucion,
                                 RouteGeometryProvider geometria,
                                 int version) {
        LocalDate fecha = problema.fecha();
        List<RutaVehiculo> rutas = new ArrayList<>();
        List<Advertencia> advertencias = new ArrayList<>();
        String fallaGeometria = null;
        double distanciaTotal = 0;
        long duracionTotal = 0;

        for (int v = 0; v < modelo.vehiculos().size(); v++) {
            List<Integer> nodos = nodosDe(solucion, v);
            // Los horarios se calculan con la matriz, respetando la espera por ventanas horarias.
            Simulador.Resultado sim = Simulador.simular(modelo, matriz, v, nodos);

            Coordenada origen = modelo.coordenadas().get(modelo.inicios()[v]);
            Coordenada destino = modelo.coordenadas().get(modelo.fines()[v]);
            List<Coordenada> puntos = new ArrayList<>();
            puntos.add(origen);
            for (int nodo : nodos) puntos.add(modelo.coordenadas().get(nodo));
            puntos.add(destino);

            // Las polilíneas son opcionales: si el proveedor falla se informa con una advertencia.
            List<String> tramos = null;
            if (geometria != null && puntos.size() > 2 || geometria != null && !origen.equals(destino)) {
                try {
                    tramos = geometria.tramos(puntos);
                } catch (RoutingException e) {
                    fallaGeometria = e.getMessage();
                }
            }

            List<Parada> paradas = new ArrayList<>();
            for (int k = 0; k < sim.pasos().size(); k++) {
                Simulador.Paso paso = sim.pasos().get(k);
                paradas.add(new Parada(k + 1, modelo.entregaDeNodo(paso.nodo()), EstadoParada.PENDIENTE,
                        aFecha(fecha, paso.inicioServicioSeg()), paso.distanciaM(), paso.duracionSeg(),
                        tramos == null ? null : tramos.get(k)));
            }

            String polRegreso = tramos == null ? null : tramos.get(tramos.size() - 1);
            rutas.add(new RutaVehiculo(
                    modelo.vehiculos().get(v), origen, null, destino,
                    aFecha(fecha, sim.salidaSeg()), aFecha(fecha, sim.regresoSeg()),
                    paradas, sim.distanciaRegresoM(), sim.duracionRegresoSeg(), polRegreso,
                    tramos == null ? null : unir(tramos),
                    sim.distanciaTotalM(), sim.duracionTotalSeg()));
            distanciaTotal += sim.distanciaTotalM();
            duracionTotal += sim.duracionTotalSeg();
        }

        // Cada entrega que el solver dejó afuera se informa con su motivo probable.
        List<Omision> omisiones = new ArrayList<>();
        for (int nodo : solucion.omitidos()) {
            omisiones.add(diagnosticar(modelo, matriz, nodo, solucion.sinSolucion()));
        }

        if (fallaGeometria != null) {
            advertencias.add(new Advertencia(TipoAdvertencia.GEOMETRIA_NO_DISPONIBLE,
                    "No se pudieron obtener las polilineas: " + fallaGeometria, List.of()));
        }
        if (!omisiones.isEmpty()) {
            advertencias.add(new Advertencia(TipoAdvertencia.OMISION_DE_ENTREGAS,
                    omisiones.size() + " entrega(s) no pueden incluirse en las rutas; requiere confirmacion del usuario",
                    omisiones.stream().map(Omision::entregaId).toList()));
        }

        // Con omisiones el plan no es válido hasta que el usuario las acepte.
        EstadoPlan estado = omisiones.isEmpty() ? EstadoPlan.VALIDO : EstadoPlan.REQUIERE_CONFIRMACION;
        return new PlanDeRuteo(version, fecha, estado, problema.depositos(), rutas, omisiones, advertencias,
                distanciaTotal, duracionTotal);
    }

    /** Nodos que visita un vehículo según la solución. */
    private static List<Integer> nodosDe(SolucionVrp solucion, int vehiculo) {
        for (RutaNodos r : solucion.rutas()) {
            if (r.vehiculo() == vehiculo) return r.nodos();
        }
        return List.of();
    }

    /** Convierte segundos desde las 00:00 en fecha y hora. */
    private static LocalDateTime aFecha(LocalDate fecha, long segundos) {
        return fecha.atStartOfDay().plusSeconds(segundos);
    }

    /** Une las polilíneas de los tramos en una sola, sin repetir puntos. */
    private static String unir(List<String> tramos) {
        List<Coordenada> todos = new ArrayList<>();
        for (String t : tramos) {
            for (Coordenada c : Polyline6.decodificar(t)) {
                if (todos.isEmpty() || !todos.get(todos.size() - 1).equals(c)) todos.add(c);
            }
        }
        return Polyline6.codificar(todos);
    }

    /** Explica de forma aproximada por qué una entrega quedó omitida. */
    private static Omision diagnosticar(ModeloRuteo m, MatrizRuteo mat, int nodo, boolean sinSolucion) {
        Entrega e = m.entregaDeNodo(nodo);
        if (sinSolucion) {
            return new Omision(e.id(), MotivoOmision.SIN_SOLUCION, "El solver no encontro una solucion factible");
        }

        long capacidadMaxima = Arrays.stream(m.capacidad()).max().orElse(0);
        if (e.demanda() > capacidadMaxima) {
            return new Omision(e.id(), MotivoOmision.CAPACIDAD_INDIVIDUAL,
                    "La demanda (" + e.demanda() + ") supera la capacidad del vehiculo mayor (" + capacidadMaxima + ")");
        }

        // Prueba si algún vehículo podría llegar a tiempo y volver dentro de su jornada.
        boolean alcanzable = false;
        for (int v = 0; v < m.vehiculos().size() && !alcanzable; v++) {
            if (e.demanda() > m.capacidad()[v]) continue;
            long llegada = m.salidaSeg()[v] + mat.tiemposSeg()[m.inicios()[v]][nodo];
            long inicio = Math.max(llegada, m.desdeSeg()[nodo]);
            if (inicio > m.hastaSeg()[nodo]) continue;
            long fin = inicio + m.servicioSeg()[nodo] + mat.tiemposSeg()[nodo][m.fines()[v]];
            if (m.limiteSeg()[v] >= 0 && fin > m.limiteSeg()[v]) continue;
            alcanzable = true;
        }
        if (!alcanzable) {
            return new Omision(e.id(), MotivoOmision.VENTANA_HORARIA_IMPOSIBLE,
                    "Ningun vehiculo llega dentro de la ventana horaria y de su jornada");
        }
        return new Omision(e.id(), MotivoOmision.RESTRICCIONES_COMBINADAS,
                "No entra en ninguna ruta sin violar capacidad, ventanas o jornada junto con el resto de las entregas");
    }
}
