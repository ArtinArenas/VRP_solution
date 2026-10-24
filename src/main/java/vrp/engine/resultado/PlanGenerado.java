package vrp.engine.resultado;

import vrp.engine.plan.PlanDeRuteo;

public record PlanGenerado(PlanDeRuteo plan) implements ResultadoFase2 {
}
