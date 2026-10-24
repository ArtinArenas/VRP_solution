package vrp.engine.solver;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.LocalSearchMetaheuristic;
import com.google.ortools.constraintsolver.RoutingDimension;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import com.google.protobuf.Duration;
import java.util.ArrayList;
import java.util.List;
import vrp.engine.routing.MatrizRuteo;

public final class OrToolsVrpSolver implements VrpSolver {

    private static final String DIM_TIEMPO = "Tiempo";
    private static final String DIM_CAPACIDAD = "Capacidad";
    private static final String DIM_BALANCEO = "Balanceo";
    private static final long CAPACIDAD_BALANCEO = 1_000_000_000_000L;

    /** Resuelve el VRP con OR-Tools: capacidad, ventanas, jornada y omisión penalizada. */
    @Override
    public SolucionVrp resolver(ModeloRuteo modelo, MatrizRuteo matriz, ParametrosSolver parametros) {
        Loader.loadNativeLibraries();

        final int n = modelo.nodos();
        final int nv = modelo.vehiculos().size();
        validar(modelo, matriz);

        final long[][] costo = costos(matriz, parametros);
        final long[][] tiempo = matriz.tiemposSeg();

        // Cada vehículo tiene su nodo de inicio y de fin; pueden ser depósitos distintos.
        RoutingIndexManager manager = new RoutingIndexManager(n, nv, modelo.inicios(), modelo.fines());
        RoutingModel routing = new RoutingModel(manager);

        final int costoCb = routing.registerTransitCallback((long desde, long hasta) ->
                costo[manager.indexToNode(desde)][manager.indexToNode(hasta)]);
        routing.setArcCostEvaluatorOfAllVehicles(costoCb);

        // Tiempo de tránsito: viaje más el servicio en el nodo de origen.
        final int tiempoCb = routing.registerTransitCallback((long desde, long hasta) -> {
            int a = manager.indexToNode(desde);
            int b = manager.indexToNode(hasta);
            return tiempo[a][b] + modelo.servicioSeg()[a];
        });
        routing.addDimension(tiempoCb, ModeloRuteo.HORIZONTE_SEG, ModeloRuteo.HORIZONTE_SEG, false, DIM_TIEMPO);
        RoutingDimension dimTiempo = routing.getMutableDimension(DIM_TIEMPO);

        // Ventana horaria de cada cliente.
        for (int i = modelo.primerNodoEntrega(); i < n; i++) {
            long index = manager.nodeToIndex(i);
            dimTiempo.cumulVar(index).setRange(modelo.desdeSeg()[i], modelo.hastaSeg()[i]);
        }
        for (int v = 0; v < nv; v++) {
            // Salida fija; el regreso tiene un límite duro, salvo que el viaje mínimo ya lo supere.
            long salida = modelo.salidaSeg()[v];
            dimTiempo.cumulVar(routing.start(v)).setRange(salida, salida);
            if (modelo.limiteSeg()[v] >= 0) {
                long minimo = salida + tiempo[modelo.inicios()[v]][modelo.fines()[v]];
                dimTiempo.cumulVar(routing.end(v)).setMax(Math.max(modelo.limiteSeg()[v], minimo));
            }
        }

        // Capacidad por vehículo (la flota es heterogénea).
        final int demandaCb = routing.registerUnaryTransitCallback((long desde) ->
                modelo.demanda()[manager.indexToNode(desde)]);
        routing.addDimensionWithVehicleCapacity(demandaCb, 0, modelo.capacidad(), true, DIM_CAPACIDAD);

        // Balanceo opcional: penaliza la diferencia entre la ruta más larga y la más corta.
        if (parametros.coeficienteBalanceo() > 0) {
            routing.addDimension(costoCb, 0, CAPACIDAD_BALANCEO, true, DIM_BALANCEO);
            routing.getMutableDimension(DIM_BALANCEO).setGlobalSpanCostCoefficient(parametros.coeficienteBalanceo());
        }

        // Cada cliente puede omitirse pagando una penalización proporcional a su prioridad.
        for (int i = modelo.primerNodoEntrega(); i < n; i++) {
            routing.addDisjunction(new long[]{manager.nodeToIndex(i)}, modelo.penalizacion()[i]);
        }

        // Solución inicial rápida y mejora por búsqueda local guiada, acotada por tiempo.
        RoutingSearchParameters busqueda = main.defaultRoutingSearchParameters()
                .toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .setLocalSearchMetaheuristic(LocalSearchMetaheuristic.Value.GUIDED_LOCAL_SEARCH)
                .setTimeLimit(Duration.newBuilder().setSeconds(parametros.tiempoLimiteSeg()).build())
                .build();

        Assignment solucion = routing.solveWithParameters(busqueda);
        // Sin solución factible, todas las entregas se informan como omitidas.
        if (solucion == null) return SolucionVrp.sinSolucion(modelo);

        List<RutaNodos> rutas = new ArrayList<>();
        for (int v = 0; v < nv; v++) {
            List<Integer> nodos = new ArrayList<>();
            long index = solucion.value(routing.nextVar(routing.start(v)));
            while (!routing.isEnd(index)) {
                nodos.add(manager.indexToNode(index));
                index = solucion.value(routing.nextVar(index));
            }
            rutas.add(new RutaNodos(v, nodos));
        }

        // Un cliente omitido es el que queda enlazado consigo mismo.
        List<Integer> omitidos = new ArrayList<>();
        for (int i = modelo.primerNodoEntrega(); i < n; i++) {
            long index = manager.nodeToIndex(i);
            if (solucion.value(routing.nextVar(index)) == index) omitidos.add(i);
        }
        return new SolucionVrp(rutas, omitidos, false);
    }

    /** Matriz de costo del arco: distancia en metros o tiempo en segundos. */
    private static long[][] costos(MatrizRuteo matriz, ParametrosSolver parametros) {
        int n = matriz.tamano();
        long[][] costo = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                costo[i][j] = parametros.objetivo() == Objetivo.DISTANCIA
                        ? Math.round(matriz.distanciasM()[i][j])
                        : matriz.tiemposSeg()[i][j];
            }
        }
        return costo;
    }

    /** Verifica que la matriz coincida con el modelo y no tenga pares sin ruta. */
    private static void validar(ModeloRuteo modelo, MatrizRuteo matriz) {
        if (matriz.tamano() != modelo.nodos()) {
            throw new IllegalArgumentException("La matriz (" + matriz.tamano()
                    + ") no coincide con los nodos del modelo (" + modelo.nodos() + ")");
        }
        if (!matriz.sinHuecos()) {
            throw new IllegalArgumentException("La matriz contiene pares sin ruta");
        }
    }
}
