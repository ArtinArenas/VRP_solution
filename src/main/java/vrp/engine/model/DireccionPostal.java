package vrp.engine.model;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public record DireccionPostal(String direccion, String localidad, String provincia) {

    /** Une dirección, localidad y provincia en un solo texto, omitiendo los vacíos. */
    public String texto() {
        return Stream.of(direccion, localidad, provincia)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }
}
