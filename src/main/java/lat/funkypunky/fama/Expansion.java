package lat.funkypunky.fama;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * Variables para TAB, DecentHolograms, ajLeaderboards, etc.
 *
 *  %funkyfama_fama%                     Fama del jugador (número sin formato, para ajLeaderboards)
 *  %funkyfama_fama_formato%             Fama con comas (1,234)
 *  %funkyfama_puesto%                   Puesto en el top de jugadores (0 = sin Fama)
 *  %funkyfama_banda%                    Nombre de su Banda (o "—")
 *  %funkyfama_fama_banda%               Fama de su Banda, con comas
 *  %funkyfama_mobs_hoy%                 Fama de mobs ganada hoy
 *  %funkyfama_mobs_limite%              Límite diario de Fama por mobs
 *  %funkyfama_x2%                       Texto: ACTIVO (tiempo) / apagado
 *  %funkyfama_x2_activo%                true / false (para condiciones de TAB)
 *  %funkyfama_x2_restante%              Tiempo que le queda al x2
 *  %funkyfama_reinicio%                 Tiempo para el próximo reinicio de los trials
 *  %funkyfama_top_jugador_<n>_nombre%   y _valor
 *  %funkyfama_top_banda_<n>_nombre%     y _valor
 */
final class Expansion extends PlaceholderExpansion {

    private final FunkyFama pl;

    Expansion(FunkyFama pl) {
        this.pl = pl;
    }

    @Override
    public String getIdentifier() {
        return "funkyfama";
    }

    @Override
    public String getAuthor() {
        return "SAULPLAY112";
    }

    @Override
    public String getVersion() {
        return pl.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // que /papi reload no la borre
    }

    @Override
    public String onRequest(OfflinePlayer jugador, String params) {
        String p = params.toLowerCase();

        if (p.startsWith("top_jugador_")) return top(pl.tops.jugadores(), p.substring("top_jugador_".length()));
        if (p.startsWith("top_banda_")) return top(pl.tops.bandas(), p.substring("top_banda_".length()));

        switch (p) {
            case "x2": return pl.x2.activo()
                    ? pl.texto("x2-si", "{tiempo}", FunkyFama.tiempo(pl.x2.restante()))
                    : pl.texto("x2-no");
            case "x2_activo": return String.valueOf(pl.x2.activo());
            case "x2_restante": return pl.x2.activo() ? FunkyFama.tiempo(pl.x2.restante()) : "0s";
            case "reinicio": return FunkyFama.tiempo(pl.msParaReinicio());
            case "mobs_limite": return String.valueOf(pl.getConfig().getLong("mobs.limite-diario", 50));
            default: break;
        }

        if (jugador == null) return "";
        switch (p) {
            case "fama": return String.valueOf(pl.fama.de(jugador.getUniqueId()));
            case "fama_formato": return FunkyFama.num(pl.fama.de(jugador.getUniqueId()));
            case "puesto": return String.valueOf(pl.tops.puesto(jugador.getUniqueId()));
            case "mobs_hoy": {
                Datos.Jugador j = pl.datos.jugadores.get(jugador.getUniqueId());
                return (j == null || j.mobsDesde < pl.inicioPeriodo()) ? "0" : String.valueOf(j.mobsHoy);
            }
            case "banda": {
                Bandas.Info b = pl.bandas == null ? null : pl.bandas.de(jugador.getUniqueId());
                return b == null ? pl.texto("sin-banda") : b.nombre();
            }
            case "fama_banda": {
                Bandas.Info b = pl.bandas == null ? null : pl.bandas.de(jugador.getUniqueId());
                return b == null ? pl.texto("sin-banda") : FunkyFama.num(pl.fama.deBanda(b.id()));
            }
            default: return null; // variable desconocida
        }
    }

    /** resto = "<n>_nombre" o "<n>_valor" */
    private String top(List<Tops.Fila> lista, String resto) {
        int guion = resto.indexOf('_');
        if (guion <= 0) return null;
        int n;
        try {
            n = Integer.parseInt(resto.substring(0, guion));
        } catch (NumberFormatException e) {
            return null;
        }
        String campo = resto.substring(guion + 1);
        if (n < 1 || n > lista.size()) return campo.equals("valor") ? "0" : "---";
        Tops.Fila f = lista.get(n - 1);
        return switch (campo) {
            case "nombre" -> f.nombre();
            case "valor" -> FunkyFama.num(f.fama());
            default -> null;
        };
    }
}
