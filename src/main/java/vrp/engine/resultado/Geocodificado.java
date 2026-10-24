package vrp.engine.resultado;

import vrp.engine.model.ProblemaVRP;

public record Geocodificado(ProblemaVRP problema) implements ResultadoFase1 {
}
