package vrp.engine.model;

public record Deposito(String id, String nombre, DireccionPostal direccion, Coordenada coordenada) {

    /** Copia del depósito con las coordenadas indicadas. */
    public Deposito conCoordenada(Coordenada nueva) {
        return new Deposito(id, nombre, direccion, nueva);
    }
}
