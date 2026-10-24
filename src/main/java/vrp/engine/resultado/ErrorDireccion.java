package vrp.engine.resultado;

import java.util.List;
import vrp.engine.geocoding.MotivoGeo;
import vrp.engine.model.DireccionPostal;

public record ErrorDireccion(
        String entidadId,
        TipoEntidad tipo,
        DireccionPostal direccion,
        MotivoGeo motivo,
        String detalle,
        List<String> proveedoresIntentados) {

    public ErrorDireccion {
        proveedoresIntentados = proveedoresIntentados == null ? List.of() : List.copyOf(proveedoresIntentados);
    }
}
