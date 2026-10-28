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
 *  %funkyfama_evento%                   Texto: x3 ACTIVO (tiempo) / apagado
 *  %funkyfama_evento_activo%            true / false (para condiciones de TAB)
 *  %funkyfama_evento_multiplicador%     2, 3... (1 si no hay evento)
 *  %funkyfama_evento_restante%          Tiempo que le queda al evento
 *  %funkyfama_reinicio%                 Tiempo para el próximo reinicio de los trials
 *  %funkyfama_top_jugador_<n>_nombre%   y _valor
 *  %funkyfama_top_banda_<n>_nombre%     y _valor   (Bandas por Fama)
 *  %funkyfama_top_banda_dinero_<n>_nombre%  y _valor  (Bandas por dinero en su banco de Towny)
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

        // El orden importa: "top_banda_dinero_" empieza igual que "top_banda_", así que va primero.
        if (p.startsWith("top_jugador_")) return top(pl.tops.jugadores(), p.substring("top_jugador_".length()), "");
        if (p.startsWith("top_banda_dinero_")) return top(pl.tops.bandasDinero(), p.substring("top_banda_dinero_".length()), "$");
        if (p.startsWith("top_banda_")) return top(pl.tops.bandas(), p.substring("top_banda_".length()), "");

        switch (p) {
            case "evento", "x2": return pl.evento.activo()
                    ? pl.texto("evento-si", "{multiplicador}", String.valueOf(pl.evento.multiplicador()),
                            "{tiempo}", FunkyFama.tiempo(pl.evento.restante()))
                    : pl.texto("evento-no");
            case "evento_activo", "x2_activo": return String.valueOf(pl.evento.activo());
            case "evento_multiplicador": return String.valueOf(pl.evento.activo() ? pl.evento.multiplicador() : 1);
            case "evento_restante", "x2_restante": return pl.evento.activo() ? FunkyFama.tiempo(pl.evento.restante()) : "0s";
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
    private String top(List<Tops.Fila> lista, String resto, String simbolo) {
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
            case "valor" -> simbolo + FunkyFama.num(f.fama());
            default -> null;
        };
    }
}
