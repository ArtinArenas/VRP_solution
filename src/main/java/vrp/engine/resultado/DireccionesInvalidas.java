package vrp.engine.resultado;

import java.util.List;

public record DireccionesInvalidas(List<ErrorDireccion> errores) implements ResultadoFase1 {

    public DireccionesInvalidas {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }
}
