package vrp.engine.io;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import vrp.engine.model.Deposito;
import vrp.engine.model.DireccionPostal;
import vrp.engine.model.Entrega;
import vrp.engine.model.ProblemaVRP;
import vrp.engine.model.Vehiculo;
import vrp.engine.resultado.ErrorLectura;

public final class ValidadorProblema {

    private ValidadorProblema() {
    }

    /** Valida la estructura del problema y devuelve todos los errores encontrados. */
    public static List<ErrorLectura> validar(ProblemaVRP p) {
        List<ErrorLectura> errores = new ArrayList<>();

        if (p.fecha() == null) errores.add(new ErrorLectura("problema", "Falta la fecha de la jornada"));
        if (p.depositos().isEmpty()) errores.add(new ErrorLectura("problema", "Debe haber al menos un deposito"));
        if (p.vehiculos().isEmpty()) errores.add(new ErrorLectura("problema", "Debe haber al menos un vehiculo"));

        // Los ids de depósitos y entregas deben ser únicos entre sí.
        Set<String> idsUbicaciones = new HashSet<>();
        for (Deposito d : p.depositos()) {
            if (vacio(d.id())) {
                errores.add(new ErrorLectura("deposito", "Deposito sin id"));
                continue;
            }
            if (!idsUbicaciones.add(d.id())) {
                errores.add(new ErrorLectura(d.id(), "Id duplicado"));
            }
            errores.addAll(validarUbicacion(d.id(), d.direccion(), d.coordenada() != null));
        }

        for (Entrega e : p.entregas()) {
            if (vacio(e.id())) {
                errores.add(new ErrorLectura("entrega", "Entrega sin id"));
                continue;
            }
            if (!idsUbicaciones.add(e.id())) {
                errores.add(new ErrorLectura(e.id(), "Id duplicado"));
            }
            errores.addAll(validarUbicacion(e.id(), e.direccion(), e.coordenada() != null));
            if (e.demanda() < 0) errores.add(new ErrorLectura(e.id(), "La demanda no puede ser negativa"));
            if (e.servicioMin() < 0) errores.add(new ErrorLectura(e.id(), "El tiempo de servicio no puede ser negativo"));
            if (e.ventana() != null) {
                if (e.ventana().desde() == null || e.ventana().hasta() == null) {
                    errores.add(new ErrorLectura(e.id(), "La ventana horaria requiere desde y hasta"));
                } else if (e.ventana().desde().isAfter(e.ventana().hasta())) {
                    errores.add(new ErrorLectura(e.id(), "La ventana horaria tiene desde posterior a hasta"));
                }
            }
        }

        Set<String> idsVehiculos = new HashSet<>();
        for (Vehiculo v : p.vehiculos()) {
            if (vacio(v.id())) {
                errores.add(new ErrorLectura("vehiculo", "Vehiculo sin id"));
                continue;
            }
            if (!idsVehiculos.add(v.id())) errores.add(new ErrorLectura(v.id(), "Id de vehiculo duplicado"));
            if (v.capacidad() < 0) errores.add(new ErrorLectura(v.id(), "La capacidad no puede ser negativa"));
            if (v.horaSalida() == null) errores.add(new ErrorLectura(v.id(), "Falta la hora de salida"));
            if (v.horaSalida() != null && v.horaFin() != null && !v.horaFin().isAfter(v.horaSalida())) {
                errores.add(new ErrorLectura(v.id(), "La hora de fin debe ser posterior a la de salida"));
            }
            if (v.depositoSalidaId() == null && p.depositos().size() > 1) {
                errores.add(new ErrorLectura(v.id(), "Falta el deposito de salida (hay mas de un deposito)"));
            }
            if (v.depositoSalidaId() != null && p.deposito(v.depositoSalidaId()) == null) {
                errores.add(new ErrorLectura(v.id(), "Deposito de salida inexistente: " + v.depositoSalidaId()));
            }
            if (v.depositoLlegadaId() != null && p.deposito(v.depositoLlegadaId()) == null) {
                errores.add(new ErrorLectura(v.id(), "Deposito de llegada inexistente: " + v.depositoLlegadaId()));
            }
        }
        return errores;
    }

    /** Una ubicación sin coordenadas necesita dirección y provincia para poder geocodificarse. */
    private static List<ErrorLectura> validarUbicacion(String id, DireccionPostal direccion, boolean tieneCoordenada) {
        List<ErrorLectura> errores = new ArrayList<>();
        if (tieneCoordenada) return errores;
        if (direccion == null || vacio(direccion.direccion())) {
            errores.add(new ErrorLectura(id, "Falta la direccion (y no hay coordenadas)"));
        } else if (vacio(direccion.provincia())) {
            errores.add(new ErrorLectura(id, "Falta la provincia"));
        }
        return errores;
    }

    /** Verdadero si el texto es nulo o está en blanco. */
    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
