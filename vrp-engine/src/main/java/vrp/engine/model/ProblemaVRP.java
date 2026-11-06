package vrp.engine.model;

import java.time.LocalDate;
import java.util.List;

public record ProblemaVRP(
        LocalDate fecha,
        List<Deposito> depositos,
        List<Entrega> entregas,
        List<Vehiculo> vehiculos) {

    public ProblemaVRP {
        depositos = depositos == null ? List.of() : List.copyOf(depositos);
        entregas = entregas == null ? List.of() : List.copyOf(entregas);
        vehiculos = vehiculos == null ? List.of() : List.copyOf(vehiculos);
    }

    /** Busca un depósito por id; devuelve null si no existe. */
    public Deposito deposito(String id) {
        for (Deposito d : depositos) {
            if (d.id() != null && d.id().equals(id)) return d;
        }
        return null;
    }

    /** Depósito de salida del vehículo; si no declara uno, usa el primero. */
    public Deposito depositoSalida(Vehiculo v) {
        if (v.depositoSalidaId() == null) return depositos.isEmpty() ? null : depositos.get(0);
        return deposito(v.depositoSalidaId());
    }

    /** Depósito de llegada del vehículo; por defecto es el mismo de salida. */
    public Deposito depositoLlegada(Vehiculo v) {
        if (v.depositoLlegadaId() == null) return depositoSalida(v);
        return deposito(v.depositoLlegadaId());
    }

    /** Indica si todos los depósitos y entregas ya tienen coordenadas. */
    public boolean geocodificado() {
        return depositos.stream().allMatch(d -> d.coordenada() != null)
                && entregas.stream().allMatch(e -> e.coordenada() != null);
    }
}
