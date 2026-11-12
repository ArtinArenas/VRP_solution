package vrp.engine.solver;

public record ParametrosSolver(
        Objetivo objetivo,
        int tiempoLimiteSeg,
        long penalizacionBase,
        long coeficienteBalanceo,
        boolean permitirPuntosDesconectados) {

    /** Parámetros por defecto: distancia, 10 segundos, penalización de 10 millones, permitir puntos desconectados true. */
    public static ParametrosSolver porDefecto() {
        return new ParametrosSolver(Objetivo.DISTANCIA, 10, 10_000_000L, 0L, true);
    }
}
