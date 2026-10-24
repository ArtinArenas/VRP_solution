package vrp.engine.geocoding;

import vrp.engine.model.Coordenada;

public record ResultadoGeo(Coordenada coordenada, String direccionNormalizada, MotivoGeo motivo, String detalle) {

    /** Resultado exitoso con coordenada. */
    public static ResultadoGeo ok(Coordenada coordenada, String direccionNormalizada) {
        return new ResultadoGeo(coordenada, direccionNormalizada, null, null);
    }

    /** Resultado fallido con su motivo. */
    public static ResultadoGeo fallo(MotivoGeo motivo, String detalle) {
        return new ResultadoGeo(null, null, motivo, detalle);
    }

    /** Verdadero si se obtuvo una coordenada. */
    public boolean exito() {
        return coordenada != null;
    }
}
