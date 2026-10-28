package lat.funkypunky.fama;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Las reglas de la Fama en un solo lugar:
 * - quién puede ganarla (el staff no),
 * - cuánto vale durante un evento,
 * - el límite diario de los mobs,
 * - y la suma a la Banda.
 */
final class Fama {

    private final FunkyFama pl;
    /** jugador -> periodo en que ya se le avisó que llegó al límite (para no llenarle el chat) */
    private final Map<UUID, Long> avisados = new HashMap<>();

    Fama(FunkyFama pl) {
        this.pl = pl;
    }

    boolean excluido(Player p) {
        return p.hasPermission("funkyfama.excluir");
    }

    /** Da Fama por jugar. Devuelve cuánta se dio al final (0 si no se dio). */
    long otorgar(Player p, long base, String fuente, boolean aplicaX2) {
        if (base <= 0 || excluido(p)) return 0;

        boolean conX2 = aplicaX2 && pl.evento.duplica("duplicar-fama");
        long total = conX2 ? base * pl.evento.multiplicador() : base;

        Datos.Jugador j = pl.datos.jugador(p.getUniqueId(), p.getName());
        j.fama += total;
        sumarABanda(p.getUniqueId(), total);
        pl.datos.marcarCambio();

        p.sendActionBar(pl.msg("actionbar",
                "{fama}", FunkyFama.num(total),
                "{fuente}", pl.texto("fuentes." + fuente),
                "{x2}", conX2 ? pl.texto("actionbar-evento", "{multiplicador}", String.valueOf(pl.evento.multiplicador())) : ""));
        return total;
    }

    /** Fama por mobs, con el límite diario (0 = sin límite; se cuenta SIN el multiplicador). */
    void otorgarMob(Player p, long base) {
        if (base <= 0 || excluido(p)) return;

        Datos.Jugador j = pl.datos.jugador(p.getUniqueId(), p.getName());
        long inicio = pl.inicioPeriodo();
        if (j.mobsDesde < inicio) {
            j.mobsHoy = 0;
            j.mobsDesde = inicio;
        }

        long limite = pl.getConfig().getLong("mobs.limite-diario", 50);
        if (limite > 0) {
            long restante = limite - j.mobsHoy;
            if (restante <= 0) {
                Long ya = avisados.get(p.getUniqueId());
                if (ya == null || ya < inicio) {
                    avisados.put(p.getUniqueId(), inicio);
                    p.sendMessage(pl.conPrefijo("limite-mobs"));
                }
                return;
            }
            base = Math.min(base, restante);
        }
        j.mobsHoy += base;
        otorgar(p, base, "mob", true);
    }

    /** Suma (o resta) a la Banda actual del jugador. */
    void sumarABanda(UUID jugador, long delta) {
        if (pl.bandas == null || delta == 0 || !pl.getConfig().getBoolean("bandas.sumar-fama", true)) return;
        Bandas.Info info = pl.bandas.de(jugador);
        if (info == null) return;
        Datos.Banda b = pl.datos.banda(info.id(), info.nombre());
        b.fama = Math.max(0, b.fama + delta);
    }

    long de(UUID jugador) {
        Datos.Jugador j = pl.datos.jugadores.get(jugador);
        return j == null ? 0 : j.fama;
    }

    long deBanda(UUID banda) {
        Datos.Banda b = pl.datos.bandas.get(banda);
        return b == null ? 0 : b.fama;
    }
}
