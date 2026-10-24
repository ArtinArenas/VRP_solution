package vrp.engine.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vrp.engine.model.Coordenada;
import vrp.engine.model.Deposito;
import vrp.engine.model.Entrega;
import vrp.engine.model.EstadoParada;
import vrp.engine.model.PosicionVehiculo;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.Vehiculo;
import vrp.engine.plan.Advertencia;
import vrp.engine.plan.EstadoPlan;
import vrp.engine.plan.Omision;
import vrp.engine.plan.Parada;
import vrp.engine.plan.PlanDeRuteo;
import vrp.engine.plan.RutaVehiculo;
import vrp.engine.resultado.ErrorLectura;
import vrp.engine.resultado.Geocodificado;
import vrp.engine.resultado.PlanGenerado;
import vrp.engine.resultado.ProblemaInvalido;
import vrp.engine.resultado.ResultadoFase1;
import vrp.engine.resultado.ResultadoFase2;
import vrp.engine.resultado.ResultadoPlanificacion;

public final class Reoptimizador {

    private final Planificador planificador;

    public Reoptimizador(Planificador planificador) {
        this.planificador = planificador;
    }

    /** Recalcula solo las rutas afectadas por los cambios; las demás quedan idénticas. */
    public ResultadoPlanificacion reoptimizar(PlanDeRuteo original, CambiosJornada cambios) {
        List<ErrorLectura> errores = validar(original, cambios);
        if (!errores.isEmpty()) return new ProblemaInvalido(errores);

        // Las acciones de la web se convierten en estados antes de recalcular.
        PlanDeRuteo plan = aplicarAcciones(original, cambios);
        Map<String, ModificacionEntrega> modificaciones = new HashMap<>();
        modificacionesEfectivas(cambios).forEach(m -> modificaciones.put(m.entregaId(), m));

        // Solo se recalculan las rutas afectadas; el resto queda intacto.
        List<RutaVehiculo> afectadas = plan.rutas().stream()
                .filter(r -> afectada(r, modificaciones))
                .toList();
        if (afectadas.isEmpty()) return new PlanGenerado(plan);

        Map<String, Coordenada> posiciones = posicionesActuales(plan, cambios);
        long ahoraSeg = Duration.between(plan.fecha().atStartOfDay(), cambios.ahora()).getSeconds();

        Map<String, PlanDeRuteo> parciales = new LinkedHashMap<>();
        // Cada ruta afectada se resuelve como un problema de un solo vehículo.
        for (RutaVehiculo ruta : afectadas) {
            ProblemaVRP sub = subproblema(plan, ruta, modificaciones);

            ResultadoFase1 fase1 = planificador.geocodificar(sub);
            // Una dirección modificada que no se resuelve corta el proceso sin cambiar el plan.
            if (!(fase1 instanceof Geocodificado g)) return fase1;

            // El chofer parte de su posición actual, no del depósito.
            Map<String, Coordenada> posicion = Map.of(ruta.vehiculo().id(), posiciones.get(ruta.vehiculo().id()));
            ResultadoFase2 fase2 = planificador.planificar(g.problema(), posicion, ahoraSeg, plan.version() + 1);
            if (!(fase2 instanceof PlanGenerado generado)) return fase2;
            parciales.put(ruta.vehiculo().id(), generado.plan());
        }

        // Recién con todas las rutas resueltas se arma el plan nuevo: se aplica todo o nada.
        return new PlanGenerado(fusionar(plan, parciales, posiciones));
    }

    /** Verifica acciones y modificaciones antes de tocar el plan. */
    private static List<ErrorLectura> validar(PlanDeRuteo plan, CambiosJornada cambios) {
        List<ErrorLectura> errores = new ArrayList<>();
        Set<String> devueltas = new LinkedHashSet<>();
        Set<String> reprogramadas = new LinkedHashSet<>();

        for (String id : cambios.devolverADeposito()) {
            if (!devueltas.add(String.valueOf(id))) errores.add(new ErrorLectura(id, "Entrega repetida en devolverADeposito"));
            validarAccion(plan, id, "devolverse al deposito", errores);
        }
        for (Reprogramacion r : cambios.reprogramar()) {
            String id = r.entregaId();
            if (!reprogramadas.add(String.valueOf(id))) errores.add(new ErrorLectura(id, "Entrega repetida en reprogramar"));
            if (devueltas.contains(String.valueOf(id))) errores.add(new ErrorLectura(id, "No se puede devolver y reprogramar la misma entrega"));
            validarAccion(plan, id, "reprogramarse", errores);
        }

        Set<String> vistos = new LinkedHashSet<>();
        for (ModificacionEntrega m : modificacionesEfectivas(cambios)) {
            String id = m.entregaId();
            Parada p = id == null ? null : plan.parada(id).orElse(null);
            if (!vistos.add(String.valueOf(id))) {
                errores.add(new ErrorLectura(id, "Hay mas de una modificacion para la misma entrega"));
            }
            if (p == null) {
                errores.add(new ErrorLectura(id, "La entrega no pertenece a ninguna ruta del plan"));
                continue;
            }
            if (!reoptimizable(p)) {
                errores.add(new ErrorLectura(id, "El estado " + p.estado().name().toLowerCase() + " no admite modificaciones"));
            }
            if (devueltas.contains(id)) errores.add(new ErrorLectura(id, "No se puede modificar una entrega que se devuelve al deposito"));
            if (m.sinCambios()) errores.add(new ErrorLectura(id, "La modificacion no especifica ningun cambio"));
            if (m.prioridad() != null && m.prioridad() < 1) errores.add(new ErrorLectura(id, "La prioridad debe ser mayor o igual a 1"));
            if (m.servicioMin() != null && m.servicioMin() < 0) errores.add(new ErrorLectura(id, "El tiempo de servicio no puede ser negativo"));
            if (m.ventana() != null && m.quitarVentana()) errores.add(new ErrorLectura(id, "No se puede definir y quitar la ventana a la vez"));
        }
        return errores;
    }

    /** Verifica que la entrega exista y esté en un estado que admita la acción. */
    private static void validarAccion(PlanDeRuteo plan, String id, String accion, List<ErrorLectura> errores) {
        Parada p = id == null ? null : plan.parada(id).orElse(null);
        if (p == null) {
            errores.add(new ErrorLectura(id, "La entrega no pertenece a ninguna ruta del plan"));
        } else if (!reoptimizable(p)) {
            errores.add(new ErrorLectura(id, "Una entrega en estado " + p.estado().name().toLowerCase() + " no puede " + accion));
        }
    }

    /** Junta las modificaciones con las ventanas nuevas de las reprogramaciones. */
    private static List<ModificacionEntrega> modificacionesEfectivas(CambiosJornada cambios) {
        List<ModificacionEntrega> todas = new ArrayList<>(cambios.modificaciones());
        for (Reprogramacion r : cambios.reprogramar()) {
            if (r.ventana() != null) todas.add(new ModificacionEntrega(r.entregaId(), r.ventana(), false, null, null, null));
        }
        return todas;
    }

    /** Pasa a devolver_a_deposito o reprogramado las paradas indicadas en las acciones. */
    private static PlanDeRuteo aplicarAcciones(PlanDeRuteo plan, CambiosJornada cambios) {
        PlanDeRuteo resultado = plan;
        for (String id : cambios.devolverADeposito()) {
            resultado = resultado.conParadaCambiada(id, p -> p.conOrdenYEstado(p.orden(), EstadoParada.DEVOLVER_A_DEPOSITO));
        }
        for (Reprogramacion r : cambios.reprogramar()) {
            resultado = resultado.conParadaCambiada(r.entregaId(), p -> p.conOrdenYEstado(p.orden(), EstadoParada.REPROGRAMADO));
        }
        return resultado;
    }

    /** Solo las paradas pendientes, en camino o reprogramadas se recalculan. */
    private static boolean reoptimizable(Parada p) {
        return switch (p.estado()) {
            case PENDIENTE, EN_CAMINO, REPROGRAMADO -> true;
            case ENTREGADO, CANCELADO, DEVOLVER_A_DEPOSITO -> false;
        };
    }

    /** Una ruta se recalcula si tiene una devolución, una reprogramación o una modificación. */
    private static boolean afectada(RutaVehiculo ruta, Map<String, ModificacionEntrega> modificaciones) {
        for (Parada p : ruta.paradas()) {
            if (p.estado() == EstadoParada.DEVOLVER_A_DEPOSITO || p.estado() == EstadoParada.REPROGRAMADO) return true;
            if (modificaciones.containsKey(p.entrega().id())) return true;
        }
        return false;
    }

    /** Problema de un solo vehículo con sus paradas reoptimizables y sus depósitos. */
    private static ProblemaVRP subproblema(PlanDeRuteo plan, RutaVehiculo ruta,
                                           Map<String, ModificacionEntrega> modificaciones) {
        Vehiculo vehiculo = ruta.vehiculo();
        ProblemaVRP base = new ProblemaVRP(plan.fecha(), plan.depositos(), List.of(), List.of(vehiculo));
        Deposito salida = base.depositoSalida(vehiculo);
        Deposito llegada = base.depositoLlegada(vehiculo);
        List<Deposito> depositos = new ArrayList<>();
        depositos.add(salida);
        if (!llegada.id().equals(salida.id())) depositos.add(llegada);

        List<Entrega> pendientes = new ArrayList<>();
        for (Parada p : ruta.paradas()) {
            if (reoptimizable(p)) {
                pendientes.add(aplicar(p.entrega(), modificaciones.get(p.entrega().id())));
            }
        }
        return new ProblemaVRP(plan.fecha(), depositos, pendientes, List.of(vehiculo));
    }

    /** Aplica a la entrega los cambios de la modificación; si cambia la dirección, queda sin coordenadas. */
    private static Entrega aplicar(Entrega e, ModificacionEntrega m) {
        if (m == null) return e;
        return new Entrega(e.id(), e.nombre(),
                m.direccion() != null ? m.direccion() : e.direccion(),
                m.direccion() != null ? null : e.coordenada(),
                e.demanda(),
                m.servicioMin() != null ? m.servicioMin() : e.servicioMin(),
                m.quitarVentana() ? null : (m.ventana() != null ? m.ventana() : e.ventana()),
                m.prioridad() != null ? m.prioridad() : e.prioridad());
    }

    /** Posición de partida de cada chofer: GPS reportado, última guardada o última parada cerrada. */
    private static Map<String, Coordenada> posicionesActuales(PlanDeRuteo plan, CambiosJornada cambios) {
        Map<String, PosicionVehiculo> reportadas = new HashMap<>();
        for (PosicionVehiculo p : cambios.posiciones()) {
            PosicionVehiculo previa = reportadas.get(p.vehiculoId());
            if (previa == null || previa.reportadaEn() == null
                    || (p.reportadaEn() != null && p.reportadaEn().isAfter(previa.reportadaEn()))) {
                reportadas.put(p.vehiculoId(), p);
            }
        }

        Map<String, Coordenada> salida = new LinkedHashMap<>();
        for (RutaVehiculo ruta : plan.rutas()) {
            String id = ruta.vehiculo().id();
            if (reportadas.containsKey(id)) {
                salida.put(id, reportadas.get(id).coordenada());
            } else if (ruta.posicionActual() != null) {
                salida.put(id, ruta.posicionActual());
            } else {
                // Sin GPS ni posición guardada se usa la última parada cerrada, o el origen.
                Coordenada ultima = null;
                for (Parada p : ruta.paradas()) {
                    if (p.estado() == EstadoParada.ENTREGADO || p.estado() == EstadoParada.CANCELADO) {
                        ultima = p.entrega().coordenada();
                    }
                }
                salida.put(id, ultima != null ? ultima : ruta.origen());
            }
        }
        return salida;
    }

    /** Arma el plan nuevo reemplazando solo las rutas recalculadas. */
    private static PlanDeRuteo fusionar(PlanDeRuteo anterior,
                                        Map<String, PlanDeRuteo> parciales,
                                        Map<String, Coordenada> posiciones) {
        List<RutaVehiculo> rutas = new ArrayList<>();
        List<Omision> omisiones = new ArrayList<>(anterior.omisiones());
        List<Omision> nuevasOmisiones = new ArrayList<>();
        List<Advertencia> advertencias = new ArrayList<>();

        for (RutaVehiculo vieja : anterior.rutas()) {
            PlanDeRuteo parcial = parciales.get(vieja.vehiculo().id());
            if (parcial == null) {
                rutas.add(vieja);
                continue;
            }
            rutas.add(fusionarRuta(vieja, parcial.rutas().get(0), posiciones.get(vieja.vehiculo().id())));
            nuevasOmisiones.addAll(parcial.omisiones());
            advertencias.addAll(parcial.advertencias());
        }
        omisiones.addAll(nuevasOmisiones);

        double distancia = 0;
        long duracion = 0;
        for (RutaVehiculo r : rutas) {
            distancia += r.distanciaTotalM();
            duracion += r.duracionTotalSeg();
        }

        // Solo las omisiones nuevas exigen una nueva confirmación.
        EstadoPlan estado;
        if (!nuevasOmisiones.isEmpty()) estado = EstadoPlan.REQUIERE_CONFIRMACION;
        else if (omisiones.isEmpty()) estado = anterior.estado() == EstadoPlan.CONFIRMADO ? EstadoPlan.CONFIRMADO : EstadoPlan.VALIDO;
        else estado = anterior.estado();

        return new PlanDeRuteo(anterior.version() + 1, anterior.fecha(), estado, anterior.depositos(), rutas,
                omisiones, advertencias, distancia, duracion);
    }

    /** Reemplaza las paradas de una ruta por las recalculadas; la ruta pasa a ser el tramo restante. */
    private static RutaVehiculo fusionarRuta(RutaVehiculo vieja, RutaVehiculo nueva, Coordenada posicion) {
        Map<String, EstadoParada> estadoPrevio = new HashMap<>();
        vieja.paradas().forEach(p -> estadoPrevio.put(p.entrega().id(), p.estado()));

        List<Parada> paradas = new ArrayList<>();
        for (Parada p : nueva.paradas()) {
            // La primera parada conserva en_camino si ya lo tenía; el resto vuelve a pendiente.
            boolean seguiaEnCamino = paradas.isEmpty() && estadoPrevio.get(p.entrega().id()) == EstadoParada.EN_CAMINO;
            paradas.add(p.conOrdenYEstado(paradas.size() + 1,
                    seguiaEnCamino ? EstadoParada.EN_CAMINO : EstadoParada.PENDIENTE));
        }

        return new RutaVehiculo(vieja.vehiculo(), vieja.origen(), posicion, nueva.destino(), vieja.salida(),
                nueva.regreso(), paradas, nueva.distanciaRegresoM(), nueva.duracionRegresoSeg(),
                nueva.polilineaRegreso(), nueva.polilinea(), nueva.distanciaTotalM(), nueva.duracionTotalSeg());
    }
}
