package vrp.engine.model;

import java.time.LocalDate;
import java.util.List;

public record ContextoOperativo(LocalDate fecha, List<Deposito> depositos, List<Vehiculo> vehiculos) {

    public ContextoOperativo {
        depositos = depositos == null ? List.of() : List.copyOf(depositos);
        vehiculos = vehiculos == null ? List.of() : List.copyOf(vehiculos);
    }
}
