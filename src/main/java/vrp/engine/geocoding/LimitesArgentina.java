package vrp.engine.geocoding;

import vrp.engine.model.Coordenada;

public final class LimitesArgentina {

    private static final double LAT_MIN = -55.2;
    private static final double LAT_MAX = -21.7;
    private static final double LON_MIN = -73.7;
    private static final double LON_MAX = -53.5;

    private LimitesArgentina() {
    }

    /** Indica si la coordenada cae dentro de una caja aproximada de Argentina. */
    public static boolean contiene(Coordenada c) {
        return c.lat() >= LAT_MIN && c.lat() <= LAT_MAX && c.lon() >= LON_MIN && c.lon() <= LON_MAX;
    }
}
