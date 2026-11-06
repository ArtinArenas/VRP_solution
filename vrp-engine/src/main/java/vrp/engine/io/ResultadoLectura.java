package vrp.engine.io;

import java.util.List;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.resultado.ErrorLectura;

public record ResultadoLectura(ProblemaVRP problema, List<ErrorLectura> errores) {

    public ResultadoLectura {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    /** Verdadero si hay problema y no hay errores. */
    public boolean valido() {
        return problema != null && errores.isEmpty();
    }
}
