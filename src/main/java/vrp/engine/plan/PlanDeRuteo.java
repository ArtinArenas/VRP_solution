package vrp.engine.plan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import vrp.engine.model.Deposito;
import vrp.engine.model.EstadoParada;

public record PlanDeRuteo(
        int version,
        LocalDate fecha,
        EstadoPlan estado,
        List<Deposito> depositos,
        List<RutaVehiculo> rutas,
        List<Omision> omisiones,
        List<Advertencia> advertencias,
        double distanciaTotalM,
        long duracionTotalSeg) {

    public PlanDeRuteo {
        depositos = depositos == null ? List.of() : List.copyOf(depositos);
        rutas = rutas == null ? List.of() : List.copyOf(rutas);
        omisiones = omisiones == null ? List.of() : List.copyOf(omisiones);
        advertencias = advertencias == null ? List.of() : List.copyOf(advertencias);
    }

    /** Copia del plan con otro estado general. */
    public PlanDeRuteo conEstado(EstadoPlan nuevo) {
        return new PlanDeRuteo(version, fecha, nuevo, depositos, rutas, omisiones, advertencias,
                distanciaTotalM, duracionTotalSeg);
    }

    /** Busca la parada de una entrega en cualquier ruta del plan. */
    public Optional<Parada> parada(String entregaId) {
        for (RutaVehiculo r : rutas) {
            for (Parada p : r.paradas()) {
                if (p.entrega().id().equals(entregaId)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Plan nuevo donde se reemplaza la parada de una entrega; las demás rutas quedan idénticas. */
    public PlanDeRuteo conParadaCambiada(String entregaId, UnaryOperator<Parada> cambio) {
        List<RutaVehiculo> nuevas = new ArrayList<>();
        boolean encontrada = false;
        for (RutaVehiculo r : rutas) {
            int indice = -1;
            if (!encontrada) {
                for (int i = 0; i < r.paradas().size(); i++) {
                    if (r.paradas().get(i).entrega().id().equals(entregaId)) indice = i;
                }
            }
            if (indice < 0) {
                nuevas.add(r);
                continue;
            }
            List<Parada> paradas = new ArrayList<>(r.paradas());
            paradas.set(indice, cambio.apply(paradas.get(indice)));
            nuevas.add(r.conParadas(paradas));
            encontrada = true;
        }
        if (!encontrada) {
            throw new IllegalArgumentException("La entrega " + entregaId + " no pertenece a ninguna ruta del plan");
        }
        return new PlanDeRuteo(version, fecha, estado, depositos, nuevas, omisiones, advertencias,
                distanciaTotalM, duracionTotalSeg);
    }

    /** Cambia el estado de una parada por evento del chofer (en camino, entregado, cancelado); rechaza reabrir estados cerrados. */
    public PlanDeRuteo conEstadoDeParada(String entregaId, EstadoParada nuevo) {
        if (nuevo == EstadoParada.REPROGRAMADO || nuevo == EstadoParada.DEVOLVER_A_DEPOSITO) {
            throw new IllegalArgumentException("El estado " + nuevo.name().toLowerCase()
                    + " se aplica con Reoptimizador y CambiosJornada, porque recalcula la ruta");
        }
        Parada actual = parada(entregaId).orElseThrow(() -> new IllegalArgumentException(
                "La entrega " + entregaId + " no pertenece a ninguna ruta del plan"));
        if (actual.estado() == nuevo) return this;
        if (actual.estado().cerrado()) {
            throw new IllegalArgumentException("La entrega " + entregaId + " esta en estado "
                    + actual.estado().name().toLowerCase() + " y no puede reabrirse");
        }
        return conParadaCambiada(entregaId, p -> p.conOrdenYEstado(p.orden(), nuevo));
    }
}
