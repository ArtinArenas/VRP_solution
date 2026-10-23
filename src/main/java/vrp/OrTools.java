package vrp;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;

import java.util.ArrayList;
import java.util.List;

public class OrTools {

    /**
     * Resuelve el TSP sobre la matriz de distancias y devuelve la ruta
     * en el orden de visita, lista para pedirla a Nominatim/OSRM.
     *
     * El primer elemento es el depósito (índice 0).
     * No se duplica el depósito al final: el retorno se asume implícito.
     */
    public static List<Cliente> TSP_Solution(MatrizOSRM matriz,
                                             List<Cliente> clientes) {

        Loader.loadNativeLibraries();

        final double[][] distancias = matriz.getDistancias();
        final int n = distancias.length;

        if (n != clientes.size()) {
            throw new IllegalArgumentException(
                    "La matriz (" + n + ") y la lista de clientes ("
                            + clientes.size() + ") tienen distinto tamaño");
        }
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Se necesitan al menos 2 puntos para resolver el TSP");
        }

        final int vehicleNumber = 1;
        final int depot = 0;

        RoutingIndexManager manager =
                new RoutingIndexManager(n, vehicleNumber, depot);
        RoutingModel routing = new RoutingModel(manager);

        final int transitCallbackIndex = routing.registerTransitCallback(
                (fromIndex, toIndex) -> {
                    int fromNode = manager.indexToNode(fromIndex);
                    int toNode   = manager.indexToNode(toIndex);
                    return Math.round(distancias[fromNode][toNode]);
                });

        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

        RoutingSearchParameters searchParameters =
                main.defaultRoutingSearchParameters()
                        .toBuilder()
                        .setFirstSolutionStrategy(
                                FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                        .build();

        Assignment solution = routing.solveWithParameters(searchParameters);
        if (solution == null) {
            throw new IllegalStateException(
                    "No se encontró solución para el TSP");
        }

        List<Cliente> ruta = new ArrayList<>();
        long index = routing.start(0);
        while (!routing.isEnd(index)) {
            int node = manager.indexToNode(index);
            ruta.add(clientes.get(node));
            index = solution.value(routing.nextVar(index));
        }

        return ruta;
    }
}