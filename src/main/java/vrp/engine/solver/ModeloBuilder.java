package vrp.engine.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Deposito;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.Vehiculo;

public final class ModeloBuilder {

    private ModeloBuilder() {
    }

    /** Traduce el problema a nodos y arreglos numéricos que entiende el solver. */
    public static ModeloRuteo construir(ProblemaVRP problema,
                                        Map<String, Coordenada> posiciones,
                                        Long ahoraSeg,
                                        ParametrosSolver parametros) {
        List<String> ids = new ArrayList<>();
        List<Coordenada> coordenadas = new ArrayList<>();
        Map<String, Integer> nodoDeposito = new HashMap<>();

        // Orden de nodos: depósitos, luego posiciones de choferes, luego clientes.
        for (Deposito d : problema.depositos()) {
            nodoDeposito.put(d.id(), ids.size());
            ids.add(d.id());
            coordenadas.add(d.coordenada());
        }

        int nv = problema.vehiculos().size();
        int[] inicios = new int[nv];
        int[] fines = new int[nv];
        for (int v = 0; v < nv; v++) {
            Vehiculo veh = problema.vehiculos().get(v);
            // Con posición del chofer (reoptimización) el vehículo parte de ese punto.
            Coordenada posicion = posiciones.get(veh.id());
            if (posicion != null) {
                inicios[v] = ids.size();
                ids.add("pos:" + veh.id());
                coordenadas.add(posicion);
            } else {
                inicios[v] = nodoDeposito.get(problema.depositoSalida(veh).id());
            }
            fines[v] = nodoDeposito.get(problema.depositoLlegada(veh).id());
        }

        int primerNodoEntrega = ids.size();
        for (Entrega e : problema.entregas()) {
            ids.add(e.id());
            coordenadas.add(e.coordenada());
        }

        int n = ids.size();
        long[] salida = new long[nv];
        long[] limite = new long[nv];
        long[] capacidad = new long[nv];
        for (int v = 0; v < nv; v++) {
            Vehiculo veh = problema.vehiculos().get(v);
            // En reoptimización el vehículo no puede salir antes del momento actual.
            long s = veh.horaSalida().toSecondOfDay();
            if (ahoraSeg != null) s = Math.max(s, ahoraSeg);
            salida[v] = s;
            limite[v] = veh.horaFin() == null ? -1 : veh.horaFin().toSecondOfDay();
            capacidad[v] = veh.capacidad();
        }

        long[] demanda = new long[n];
        long[] servicio = new long[n];
        long[] desde = new long[n];
        long[] hasta = new long[n];
        long[] penalizacion = new long[n];
        for (int k = 0; k < problema.entregas().size(); k++) {
            Entrega e = problema.entregas().get(k);
            int i = primerNodoEntrega + k;
            demanda[i] = e.demanda();
            servicio[i] = e.servicioMin() * 60L;
            desde[i] = e.ventana() == null ? 0 : e.ventana().desde().toSecondOfDay();
            hasta[i] = e.ventana() == null ? ModeloRuteo.HORIZONTE_SEG : e.ventana().hasta().toSecondOfDay();
            penalizacion[i] = parametros.penalizacionBase() * e.prioridad();
        }

        return new ModeloRuteo(List.copyOf(ids), List.copyOf(coordenadas), inicios, fines,
                problema.vehiculos(), problema.entregas(), primerNodoEntrega,
                salida, limite, capacidad, demanda, servicio, desde, hasta, penalizacion);
    }
}
