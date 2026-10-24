package vrp.engine.plan;

import java.util.List;

public record Advertencia(TipoAdvertencia tipo, String mensaje, List<String> entregaIds) {

    public Advertencia {
        entregaIds = entregaIds == null ? List.of() : List.copyOf(entregaIds);
    }
}
