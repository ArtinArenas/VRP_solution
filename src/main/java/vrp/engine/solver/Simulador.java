package vrp.engine.solver;

import java.util.ArrayList;
import java.util.List;
import vrp.engine.routing.MatrizRuteo;

public final class Simulador {

    public record Paso(int nodo, long inicioServicioSeg, double distanciaM, long duracionSeg) {
    }

    public record Resultado(
            List<Paso> pasos,
            long salidaSeg,
            long regresoSeg,
            double distanciaRegresoM,
            long duracionRegresoSeg) {

        /** Distancia de la ruta completa, incluido el regreso. */
        public double distanciaTotalM() {
            double total = distanciaRegresoM;
            for (Paso p : pasos) total += p.distanciaM();
            return total;
        }

        /** Segundos entre la salida y el regreso al depósito. */
        public long duracionTotalSeg() {
            return regresoSeg - salidaSeg;
        }
    }

    private Simulador() {
    }

    /** Recorre la ruta calculando llegada, espera, servicio y regreso de un vehículo. */
    public static Resultado simular(ModeloRuteo modelo, MatrizRuteo matriz, int vehiculo, List<Integer> nodos) {
        long t = modelo.salidaSeg()[vehiculo];
        int anterior = modelo.inicios()[vehiculo];
        List<Paso> pasos = new ArrayList<>();

        for (int nodo : nodos) {
            long viaje = matriz.tiemposSeg()[anterior][nodo];
            // Si llega antes de que abra la ventana, espera.
            long inicio = Math.max(t + viaje, modelo.desdeSeg()[nodo]);
            pasos.add(new Paso(nodo, inicio, matriz.distanciasM()[anterior][nodo], viaje));
            t = inicio + modelo.servicioSeg()[nodo];
            anterior = nodo;
        }

        int fin = modelo.fines()[vehiculo];
        long viajeRegreso = matriz.tiemposSeg()[anterior][fin];
        return new Resultado(pasos, modelo.salidaSeg()[vehiculo], t + viajeRegreso,
                matriz.distanciasM()[anterior][fin], viajeRegreso);
    }
}
