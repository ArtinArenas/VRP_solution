package vrp.engine.solver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vrp.engine.resultado.ErrorRuta;
import vrp.engine.routing.MatrizRuteo;

public final class ArcosRelevantes {

    private ArcosRelevantes() {
    }

    /** Un arco importa solo si algún vehículo puede recorrerlo: sale de un inicio o cliente y llega a un fin o cliente. */
    public static boolean relevante(ModeloRuteo m, int desde, int hasta) {
        if (desde == hasta) return false;
        return puedeSalir(m, desde) && puedeLlegar(m, hasta);
    }

    /** Lista los pares sin ruta que sí importan para el ruteo. */
    public static List<ErrorRuta> huecos(ModeloRuteo m, MatrizRuteo matriz) {
        List<ErrorRuta> errores = new ArrayList<>();
        for (int i = 0; i < m.nodos(); i++) {
            for (int j = 0; j < m.nodos(); j++) {
                if (relevante(m, i, j) && !matriz.hayRuta(i, j)) {
                    errores.add(new ErrorRuta(m.idsNodo().get(i), m.idsNodo().get(j)));
                }
            }
        }
        return errores;
    }

    /** Rellena con 0 los pares sin ruta que no importan, para que el solver los ignore. */
    public static MatrizRuteo sanear(ModeloRuteo m, MatrizRuteo matriz) {
        int n = matriz.tamano();
        double[][] d = new double[n][n];
        long[][] t = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                boolean usable = matriz.hayRuta(i, j);
                d[i][j] = usable ? matriz.distanciasM()[i][j] : 0;
                t[i][j] = usable ? matriz.tiemposSeg()[i][j] : 0;
            }
        }
        return new MatrizRuteo(d, t);
    }

    /** Un vehículo puede salir de un cliente o de un nodo de inicio. */
    private static boolean puedeSalir(ModeloRuteo m, int nodo) {
        return m.esEntrega(nodo) || contiene(m.inicios(), nodo);
    }

    /** Un vehículo puede llegar a un cliente o a un nodo de fin. */
    private static boolean puedeLlegar(ModeloRuteo m, int nodo) {
        return m.esEntrega(nodo) || contiene(m.fines(), nodo);
    }

    /** Verdadero si el nodo figura en el arreglo. */
    private static boolean contiene(int[] nodos, int nodo) {
        Set<Integer> set = new HashSet<>();
        for (int n : nodos) set.add(n);
        return set.contains(nodo);
    }
}
