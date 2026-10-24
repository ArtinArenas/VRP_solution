package vrp.engine.resultado;

import java.util.List;

public record RutaImposible(List<ErrorRuta> errores) implements ResultadoFase2 {

    public RutaImposible {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }
}
