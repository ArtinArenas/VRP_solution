package vrp.engine.resultado;

import java.util.List;

public record ProblemaInvalido(List<ErrorLectura> errores) implements ResultadoFase2 {

    public ProblemaInvalido {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }
}