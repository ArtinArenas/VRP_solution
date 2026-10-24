package vrp.engine.routing;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import vrp.engine.model.Coordenada;

public abstract class BloquesMatrixProvider implements RoutingMatrixProvider {

    private final int tamanoBloque;

    protected BloquesMatrixProvider(int tamanoBloque) {
        this.tamanoBloque = Math.max(1, tamanoBloque);
    }

    /** Pide al servicio la matriz entre un grupo de orígenes y un grupo de destinos. */
    protected abstract BloqueMatriz pedirBloque(List<Coordenada> origenes, List<Coordenada> destinos)
            throws IOException;

    /** Arma la matriz completa pidiendo bloques de tamaño acotado. */
    @Override
    public MatrizRuteo calcular(List<Coordenada> puntos) {
        int n = puntos.size();
        double[][] distancias = new double[n][n];
        long[][] tiempos = new long[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(distancias[i], MatrizRuteo.NO_RUTA);
            Arrays.fill(tiempos[i], MatrizRuteo.NO_RUTA);
        }

        try {
            // La matriz se pide por bloques para respetar los límites de los servidores.
            for (int i = 0; i < n; i += tamanoBloque) {
                int finI = Math.min(i + tamanoBloque, n);
                for (int j = 0; j < n; j += tamanoBloque) {
                    int finJ = Math.min(j + tamanoBloque, n);
                    BloqueMatriz b = pedirBloque(puntos.subList(i, finI), puntos.subList(j, finJ));
                    if (b.tiemposSeg().length != finI - i || b.distanciasM().length != finI - i) {
                        throw new IOException("Bloque de matriz con dimensiones inesperadas");
                    }
                    for (int a = 0; a < finI - i; a++) {
                        if (b.tiemposSeg()[a].length != finJ - j || b.distanciasM()[a].length != finJ - j) {
                            throw new IOException("Bloque de matriz con dimensiones inesperadas");
                        }
                        for (int c = 0; c < finJ - j; c++) {
                            distancias[i + a][j + c] = b.distanciasM()[a][c];
                            tiempos[i + a][j + c] = b.tiemposSeg()[a][c];
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new RoutingException(nombre() + ": " + e.getMessage(), e);
        }

        for (int i = 0; i < n; i++) {
            // La distancia de un punto a sí mismo es siempre 0.
            distancias[i][i] = 0;
            tiempos[i][i] = 0;
        }
        return new MatrizRuteo(distancias, tiempos);
    }
}
