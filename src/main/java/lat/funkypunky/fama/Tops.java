package lat.funkypunky.fama;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Tops ya ordenados. Se recalculan cada 60 s (y tras cada comando de staff),
 * no cada vez que TAB pide una variable: TAB pregunta varias veces por segundo.
 */
final class Tops {

    record Fila(UUID id, String nombre, long fama) {}

    private final FunkyFama pl;
    private volatile List<Fila> jugadores = List.of();
    private volatile List<Fila> bandas = List.of();

    Tops(FunkyFama pl) {
        this.pl = pl;
    }

    void recalcular() {
        List<Fila> j = new ArrayList<>();
        pl.datos.jugadores.forEach((id, d) -> {
            if (d.fama > 0) j.add(new Fila(id, d.nombre, d.fama));
        });
        j.sort(Comparator.comparingLong(Fila::fama).reversed());

        List<Fila> b = new ArrayList<>();
        pl.datos.bandas.forEach((id, d) -> {
            if (d.fama <= 0) return;
            String nombre = d.nombre;
            if (pl.bandas != null) {
                String actual = pl.bandas.nombreActual(id);
                if (actual != null) {
                    nombre = actual;
                    d.nombre = actual;                       // la Banda se renombró
                }
            }
            b.add(new Fila(id, nombre, d.fama));
        });
        b.sort(Comparator.comparingLong(Fila::fama).reversed());

        jugadores = List.copyOf(j);
        bandas = List.copyOf(b);
    }

    List<Fila> jugadores() {
        return jugadores;
    }

    List<Fila> bandas() {
        return bandas;
    }

    /** Puesto del jugador (1 = primero), o 0 si no está en el top. */
    int puesto(UUID id) {
        List<Fila> l = jugadores;
        for (int i = 0; i < l.size(); i++) if (l.get(i).id().equals(id)) return i + 1;
        return 0;
    }
}
