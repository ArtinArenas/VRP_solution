package vrp.engine.resultado;

public sealed interface ResultadoFase1 extends ResultadoPlanificacion
        permits Geocodificado, DireccionesInvalidas, ProblemaInvalido {
}
