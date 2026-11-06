package vrp.engine.routing;

public record MatrizRuteo(double[][] distanciasM, long[][] tiemposSeg) {

    public static final long NO_RUTA = -1L;

    /** Cantidad de puntos de la matriz. */
    public int tamano() {
        return tiemposSeg.length;
    }

    /** Indica si existe ruta entre dos puntos (tiempo y distancia no negativos). */
    public boolean hayRuta(int desde, int hasta) {
        return tiemposSeg[desde][hasta] >= 0 && distanciasM[desde][hasta] >= 0;
    }

    /** Verdadero si todos los pares tienen ruta. */
    public boolean sinHuecos() {
        for (int i = 0; i < tamano(); i++) {
            for (int j = 0; j < tamano(); j++) {
                if (!hayRuta(i, j)) return false;
            }
        }
        return true;
    }

    /** Rellena los pares sin ruta con los valores de otra matriz. */
    public MatrizRuteo completarCon(MatrizRuteo otra) {
        int n = tamano();
        double[][] d = new double[n][n];
        long[][] t = new long[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                MatrizRuteo fuente = hayRuta(i, j) ? this : otra;
                d[i][j] = fuente.distanciasM[i][j];
                t[i][j] = fuente.tiemposSeg[i][j];
            }
        }
        return new MatrizRuteo(d, t);
    }
}
