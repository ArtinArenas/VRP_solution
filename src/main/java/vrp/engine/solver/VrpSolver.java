package vrp.engine.solver;

import vrp.engine.routing.MatrizRuteo;

public interface VrpSolver {

    /** Asigna entregas a vehículos y las ordena; las que no entran quedan como omitidas. */
    SolucionVrp resolver(ModeloRuteo modelo, MatrizRuteo matriz, ParametrosSolver parametros);
}
