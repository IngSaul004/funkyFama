package lat.funkypunky.fama;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Calcula cuándo fue el último reinicio diario y cuándo es el siguiente.
 * Idea clave: no guardamos "día 1, día 2"; guardamos la HORA en que pasó cada cosa
 * (abrir un vault, llenar un cofre) y la comparamos con el último reinicio.
 * Si pasó antes del reinicio, ya "venció".
 */
final class Reinicio {

    private final ZoneId zona;
    private final LocalTime hora;

    Reinicio(ZoneId zona, LocalTime hora) {
        this.zona = zona;
        this.hora = hora;
    }

    long ultimoProgramado(long ahora) {
        ZonedDateTime z = Instant.ofEpochMilli(ahora).atZone(zona);
        ZonedDateTime hoy = z.toLocalDate().atTime(hora).atZone(zona);
        if (z.isBefore(hoy)) hoy = hoy.minusDays(1);
        return hoy.toInstant().toEpochMilli();
    }

    long proximo(long ahora) {
        ZonedDateTime ultimo = Instant.ofEpochMilli(ultimoProgramado(ahora)).atZone(zona);
        return ultimo.plusDays(1).toInstant().toEpochMilli();
    }

    /** Inicio del periodo actual: el último reinicio programado o el manual, el que sea más reciente. */
    long inicioPeriodo(long ahora, long manual) {
        return Math.max(ultimoProgramado(ahora), manual);
    }
}
