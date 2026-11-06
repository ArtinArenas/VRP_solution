package vrp.geocoding;

import java.util.Objects;

/**
 * Coordenada geográfica (latitud/longitud) en formato decimal WGS84.
 */
public record Coordenada(double lat, double lon) {

    public Coordenada {
        if (Double.isNaN(lat) || Double.isNaN(lon) || Double.isInfinite(lat) || Double.isInfinite(lon)) {
            throw new IllegalArgumentException("Coordenadas no pueden ser NaN o Infinity");
        }
        // Validación básica de rangos WGS84
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitud fuera de rango [-90, 90]: " + lat);
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitud fuera de rango [-180, 180]: " + lon);
        }
    }

    /**
     * Verifica si la coordenada está en Argentina (aproximado).
     * Bounding box aproximado de Argentina continental.
     */
    public boolean enArgentina() {
        return lat >= -55.0 && lat <= -21.0 && lon >= -74.0 && lon <= -53.0;
    }

    @Override
    public String toString() {
        return String.format("Coordenada[lat=%.6f, lon=%.6f]", lat, lon);
    }

    public static Coordenada de(double lat, double lon) {
        return new Coordenada(lat, lon);
    }
}