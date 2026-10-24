package vrp.engine.resultado;

public sealed interface ResultadoFase2 extends ResultadoPlanificacion
        permits PlanGenerado, RutaImposible, ProveedorNoDisponible {
}
