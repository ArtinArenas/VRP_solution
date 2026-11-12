package vrp.engine.solver;

import java.util.ArrayList;
import java.util.List;
import vrp.engine.plan.MotivoOmision;
import vrp.engine.plan.Omision;
import vrp.engine.routing.MatrizRuteo;

/**
 * Componente especializado (SRP) para detectar y filtrar entregas inalcanzables antes de invocar el solver.
 * Una entrega es inalcanzable si no tiene conectividad (tiempo/distancia infinita) desde/hacia los depósitos
 * de salida/llegada de todos los vehículos.
 */
public final class FiltradorNodosInalcanzables {

    private FiltradorNodosInalcanzables() {
    }

    /**
     * Resultado del filtrado: modelo con nodos accesibles y lista de omisiones por falta de ruta.
     */
    public record ResultadoFiltrado(
            ModeloRuteo modeloFiltrado,
            List<Omision> omisionesSinRuta) {
    }

    /**
     * Filtra las entregas que no tienen ruta desde/hacia los depósitos de ningún vehículo.
     *
     * @param modelo        modelo original con todas las entregas
     * @param matriz        matriz de ruteo
     * @param permitirPuntosDesconectados si true, omite entregas inalcanzables; si false, lanza excepción
     * @return modelo filtrado y lista de omisiones con motivo SIN_RUTA
     */
    public static ResultadoFiltrado filtrar(ModeloRuteo modelo,
                                             MatrizRuteo matriz,
                                             boolean permitirPuntosDesconectados) {
        List<Integer> nodosAccesibles = new ArrayList<>();
        List<Omision> omisiones = new ArrayList<>();

        // Primero, siempre mantenemos los depósitos y posiciones de choferes (índices 0 a primerNodoEntrega - 1)
        for (int i = 0; i < modelo.primerNodoEntrega(); i++) {
            nodosAccesibles.add(i);
        }

        // Verificamos cada entrega
        for (int i = modelo.primerNodoEntrega(); i < modelo.nodos(); i++) {
            if (esAccesible(modelo, matriz, i)) {
                nodosAccesibles.add(i);
            } else {
                // Entrega inalcanzable
                var entrega = modelo.entregaDeNodo(i);
                omisiones.add(new Omision(entrega.id(), MotivoOmision.SIN_RUTA,
                        "Sin ruta válida desde/hacia los depósitos para la entrega " + entrega.id()));
            }
        }

        if (!permitirPuntosDesconectados && !omisiones.isEmpty()) {
            // En modo estricto, no filtramos - el solver fallará en la validación
            // Retornamos el modelo original y las omisiones para que el caller decida
            return new ResultadoFiltrado(modelo, omisiones);
        }

        // Reconstruimos el modelo solo con nodos accesibles
        ModeloRuteo modeloFiltrado = reconstruirModelo(modelo, nodosAccesibles);
        return new ResultadoFiltrado(modeloFiltrado, omisiones);
    }

    /**
     * Determina si un nodo de entrega es accesible desde al menos un depósito de salida
     * y puede llegar a al menos un depósito de llegada de algún vehículo.
     */
    private static boolean esAccesible(ModeloRuteo modelo, MatrizRuteo matriz, int nodoEntrega) {
        // Verificar si hay algún vehículo que pueda salir de su depósito y llegar a esta entrega
        boolean haySalida = false;
        for (int inicio : modelo.inicios()) {
            if (matriz.hayRuta(inicio, nodoEntrega)) {
                haySalida = true;
                break;
            }
        }
        if (!haySalida) return false;

        // Verificar si desde esta entrega se puede llegar a algún depósito de llegada
        boolean hayLlegada = false;
        for (int fin : modelo.fines()) {
            if (matriz.hayRuta(nodoEntrega, fin)) {
                hayLlegada = true;
                break;
            }
        }
        return hayLlegada;
    }

    /**
     * Reconstruye el modelo manteniendo solo los nodos accesibles.
     * Re-mapea los índicios de inicios, fines, primerNodoEntrega y todos los arrays.
     */
    private static ModeloRuteo reconstruirModelo(ModeloRuteo original, List<Integer> nodosAccesibles) {
        int nNuevos = nodosAccesibles.size();
        int[] mapeoViejoANuevo = new int[original.nodos()];
        for (int i = 0; i < nNuevos; i++) {
            mapeoViejoANuevo[nodosAccesibles.get(i)] = i;
        }

        // IDs y coordenadas filtrados
        List<String> nuevosIds = new ArrayList<>(nNuevos);
        List<vrp.engine.model.Coordenada> nuevasCoords = new ArrayList<>(nNuevos);
        for (int viejo : nodosAccesibles) {
            nuevosIds.add(original.idsNodo().get(viejo));
            nuevasCoords.add(original.coordenadas().get(viejo));
        }

        // Inicios y fines remapeados
        int[] nuevosInicios = new int[original.inicios().length];
        int[] nuevosFines = new int[original.fines().length];
        for (int v = 0; v < original.inicios().length; v++) {
            nuevosInicios[v] = mapeoViejoANuevo[original.inicios()[v]];
            nuevosFines[v] = mapeoViejoANuevo[original.fines()[v]];
        }

        // Arrays por vehículo (sin cambios en longitud)
        long[] nuevaSalida = original.salidaSeg().clone();
        long[] nuevoLimite = original.limiteSeg().clone();
        long[] nuevaCapacidad = original.capacidad().clone();

        // Arrays por nodo (filtrados)
        long[] nuevaDemanda = new long[nNuevos];
        long[] nuevoServicio = new long[nNuevos];
        long[] nuevoDesde = new long[nNuevos];
        long[] nuevoHasta = new long[nNuevos];
        long[] nuevaPenalizacion = new long[nNuevos];

        for (int i = 0; i < nNuevos; i++) {
            int viejo = nodosAccesibles.get(i);
            nuevaDemanda[i] = original.demanda()[viejo];
            nuevoServicio[i] = original.servicioSeg()[viejo];
            nuevoDesde[i] = original.desdeSeg()[viejo];
            nuevoHasta[i] = original.hastaSeg()[viejo];
            nuevaPenalizacion[i] = original.penalizacion()[viejo];
        }

        // Calcular nuevo primerNodoEntrega (depósitos + posiciones se mantienen al principio)
        int nuevoPrimerNodoEntrega = 0;
        for (int i = 0; i < nNuevos; i++) {
            if (original.esEntrega(nodosAccesibles.get(i))) {
                nuevoPrimerNodoEntrega = i;
                break;
            }
        }

        // Filtrar también el array de estadía en depósito
        long[] nuevaEstadiaDeposito = new long[nuevosInicios.length];
        // Los vehículos virtuales se mantienen igual (no se filtran), solo cambian los nodos
        System.arraycopy(original.estadiaDepositoSeg(), 0, nuevaEstadiaDeposito, 0, nuevosInicios.length);

        return new ModeloRuteo(
                List.copyOf(nuevosIds),
                List.copyOf(nuevasCoords),
                nuevosInicios,
                nuevosFines,
                original.vehiculos(),
                filtrarEntregas(original, nodosAccesibles),
                nuevoPrimerNodoEntrega,
                nuevaSalida,
                nuevoLimite,
                nuevaCapacidad,
                nuevaDemanda,
                nuevoServicio,
                nuevoDesde,
                nuevoHasta,
                nuevaPenalizacion,
                nuevaEstadiaDeposito);
    }

    /** Filtra la lista de entregas para mantener solo las accesibles. */
    private static List<vrp.engine.model.Entrega> filtrarEntregas(ModeloRuteo original, List<Integer> nodosAccesibles) {
        List<vrp.engine.model.Entrega> filtradas = new ArrayList<>();
        for (int nodo : nodosAccesibles) {
            if (original.esEntrega(nodo)) {
                filtradas.add(original.entregaDeNodo(nodo));
            }
        }
        return List.copyOf(filtradas);
    }
}