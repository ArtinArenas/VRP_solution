package vrp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cliente del VRP.
 *
 * Hereda de {@link Direccion}, por lo que la "dirección" del cliente
 * ES un objeto Direccion (name/lat/lon/display_name vienen del padre).
 * Agrega únicamente el nombre del cliente.
 *
 *  - nombre       : nombre del cliente, tal como llega del archivo.
 *  - name         : (heredado) texto de la dirección, tal como llega del archivo.
 *  - lat / lon    : (heredado) completados por Nominatim.
 *  - display_name : (heredado) completado por Nominatim.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Cliente extends Direccion {

    private String nombre;

    public Cliente() {}

    public Cliente(String nombre, String direccionTexto) {
        this.nombre = nombre;
        setName(direccionTexto);   // Direccion.name = texto de la dirección
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    /**
     * Devuelve la parte "dirección" del cliente como un objeto Direccion.
     * Como Cliente extiende Direccion, la instancia es la misma
     * (no se duplica información).
     */
    public Direccion getDireccion() {
        return this;
    }

    @Override
    public String toString() {
        return nombre + " - " + getName()
                + " (" + getLat() + ", " + getLon() + ")";
    }
}