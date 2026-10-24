package vrp.engine.model;

import java.time.LocalDateTime;

public record PosicionVehiculo(String vehiculoId, Coordenada coordenada, LocalDateTime reportadaEn) {
}
