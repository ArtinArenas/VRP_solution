package vrp.engine.util;

import java.util.ArrayList;
import java.util.List;
import vrp.engine.model.Coordenada;

public final class Polyline6 {

    private static final double FACTOR = 1e6;

    private Polyline6() {
    }

    /** Codifica una lista de puntos como polilínea con precisión de 6 decimales. */
    public static String codificar(List<Coordenada> puntos) {
        StringBuilder sb = new StringBuilder();
        long latPrev = 0;
        long lonPrev = 0;
        for (Coordenada c : puntos) {
            long lat = Math.round(c.lat() * FACTOR);
            long lon = Math.round(c.lon() * FACTOR);
            valor(lat - latPrev, sb);
            valor(lon - lonPrev, sb);
            latPrev = lat;
            lonPrev = lon;
        }
        return sb.toString();
    }

    /** Decodifica una polilínea de precisión 6 en una lista de puntos. */
    public static List<Coordenada> decodificar(String texto) {
        List<Coordenada> out = new ArrayList<>();
        if (texto == null) return out;
        int i = 0;
        long lat = 0;
        long lon = 0;
        while (i < texto.length()) {
            long[] r = leer(texto, i);
            lat += r[0];
            i = (int) r[1];
            r = leer(texto, i);
            lon += r[0];
            i = (int) r[1];
            out.add(new Coordenada(lat / FACTOR, lon / FACTOR));
        }
        return out;
    }

    /** Escribe un número en el formato de polilínea (cambio relativo, zigzag y base 32). */
    private static void valor(long v, StringBuilder sb) {
        long x = v < 0 ? ~(v << 1) : v << 1;
        while (x >= 0x20) {
            sb.append((char) ((0x20 | (x & 0x1f)) + 63));
            x >>= 5;
        }
        sb.append((char) (x + 63));
    }

    /** Lee un número codificado desde una posición y devuelve su valor y la posición siguiente. */
    private static long[] leer(String s, int desde) {
        long resultado = 0;
        int shift = 0;
        int i = desde;
        int b;
        do {
            b = s.charAt(i++) - 63;
            resultado |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && i < s.length());
        long valor = (resultado & 1) != 0 ? ~(resultado >> 1) : resultado >> 1;
        return new long[]{valor, i};
    }
}
