package vrp.engine.resultado;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "resultado")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Geocodificado.class, name = "geocodificado"),
        @JsonSubTypes.Type(value = DireccionesInvalidas.class, name = "direcciones_invalidas"),
        @JsonSubTypes.Type(value = ProblemaInvalido.class, name = "problema_invalido"),
        @JsonSubTypes.Type(value = PlanGenerado.class, name = "plan_generado"),
        @JsonSubTypes.Type(value = RutaImposible.class, name = "ruta_imposible"),
        @JsonSubTypes.Type(value = ProveedorNoDisponible.class, name = "proveedor_no_disponible")
})
public sealed interface ResultadoPlanificacion permits ResultadoFase1, ResultadoFase2 {
}
