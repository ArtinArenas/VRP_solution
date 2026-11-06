package vrp.engine.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Tiempos {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("H:mm[:ss]");

    private Tiempos() {
    }

    /** Convierte texto "H:mm" o "H:mm:ss" en hora; devuelve null si está vacío. */
    public static LocalTime parseHora(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return LocalTime.parse(texto.trim(), HORA);
    }

    /** Segundos transcurridos desde las 00:00 hasta la hora indicada. */
    public static long segundosDelDia(LocalTime hora) {
        return hora.toSecondOfDay();
    }
}
