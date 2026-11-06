package vrp.geocoding;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de una geocodificación individual.
 * Puede ser exitoso (con coordenadas) o fallido (con motivo).
 */
public sealed interface ResultadoGeocodificacion
        permits ResultadoGeocodificacion.Exitoso, ResultadoGeocodificacion.Fallido {

    /**
     * Geocodificación exitosa con coordenadas.
     */
    record Exitoso(Coordenada coordenada, String proveedor, double confianza) implements ResultadoGeocodificacion {
        public Exitoso {
            Objects.requireNonNull(coordenada);
            Objects.requireNonNull(proveedor);
            confianza = Math.max(0.0, Math.min(1.0, confianza));
        }

        public static Exitoso de(Coordenada coordenada, String proveedor) {
            return new Exitoso(coordenada, proveedor, 1.0);
        }

        public static Exitoso de(Coordenada coordenada, String proveedor, double confianza) {
            return new Exitoso(coordenada, proveedor, confianza);
        }
    }

    /**
     * Geocodificación fallida con motivo y detalle.
     */
    record Fallido(MotivoFallo motivo, String detalle, String proveedor) implements ResultadoGeocodificacion {
        public Fallido {
            Objects.requireNonNull(motivo);
            Objects.requireNonNull(proveedor);
            detalle = detalle == null ? "" : detalle;
        }

        public static Fallido de(MotivoFallo motivo, String proveedor) {
            return new Fallido(motivo, "", proveedor);
        }

        public static Fallido de(MotivoFallo motivo, String detalle, String proveedor) {
            return new Fallido(motivo, detalle, proveedor);
        }
    }

    /**
     * Indica si la geocodificación fue exitosa.
     */
    default boolean esExitoso() {
        return this instanceof Exitoso;
    }

    /**
     * Obtiene la coordenada si fue exitosa, null si falló.
     */
    default Coordenada coordenada() {
        return this instanceof Exitoso e ? e.coordenada() : null;
    }

    /**
     * Obtiene el motivo de fallo si falló, null si fue exitosa.
     */
    default MotivoFallo motivoFallo() {
        return this instanceof Fallido f ? f.motivo() : null;
    }

    /**
     * Nombre del proveedor que intentó la geocodificación.
     */
    default String proveedor() {
        return switch (this) {
            case Exitoso e -> e.proveedor();
            case Fallido f -> f.proveedor();
        };
    }

    /**
     * Motivos posibles de fallo en geocodificación.
     */
    enum MotivoFallo {
        NO_ENCONTRADA("La dirección no fue encontrada"),
        FUERA_DE_AREA("La dirección está fuera del área de cobertura"),
        PROVEEDOR_NO_DISPONIBLE("El servicio de geocodificación no está disponible"),
        ERROR_COMUNICACION("Error de red o comunicación con el proveedor"),
        CUOTA_EXCEDIDA("Se excedió la cuota del proveedor"),
        DIRECCION_INVALIDA("La dirección proporcionada es inválida o incompleta"),
        ERROR_INTERNO("Error interno del proveedor"),
        SIN_COORDENADAS_VALIDAS("El proveedor no retornó coordenadas válidas");

        private final String descripcion;

        MotivoFallo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String descripcion() {
            return descripcion;
        }
    }
}