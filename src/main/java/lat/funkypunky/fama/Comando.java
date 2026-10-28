package lat.funkypunky.fama;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /fama                                    tu Fama
 * /fama ver <jugador>                      Fama de otro
 * /fama top [jugadores|bandas]             top 10
 * /fama info                               estado del x2 y del reinicio
 * /fama dar|quitar <jugador> <n> [motivo]  (staff) premios de eventos, correcciones
 * /fama establecer <jugador> <n>           (staff)
 * /fama banda dar|quitar <banda> <n> [motivo]
 * /fama x2 activar <tiempo> | desactivar   tiempo: 30m, 2h, 1d (número solo = minutos)
 * /fama reiniciar                          reinicia los trials ya
 * /fama temporada confirmar                borra la Fama de todos (guarda copia)
 * /fama recargar
 */
final class Comando implements TabExecutor {

    private record Objetivo(UUID id, String nombre) {}

    private final FunkyFama pl;

    Comando(FunkyFama pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String [] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) return ayuda(s);
            if (!s.hasPermission("funkyfama.ver")) return sinPermiso(s);
            Bandas.Info b = pl.bandas == null ? null : pl.bandas.de(p.getUniqueId());
            s.sendMessage(pl.conPrefijo("tu-fama",
                    "{fama}", FunkyFama.num(pl.fama.de(p.getUniqueId())),
                    "{banda}", b == null ? pl.texto("sin-banda") : b.nombre(),
                    "{fama_banda}", b == null ? "0" : FunkyFama.num(pl.fama.deBanda(b.id()))));
            return true;
        }

        switch (a[0].toLowerCase()) {
            case "ver": return ver(s, a);
            case "top": return top(s, a);
            case "info": return info(s);
            case "dar": return ajustar(s, a, 1);
            case "quitar": return ajustar(s, a, -1);
            case "establecer": return establecer(s, a);
            case "banda": return banda(s, a);
            case "x2": return x2(s, a);
            case "reiniciar": return reiniciar(s);
            case "temporada": return temporada(s, a);
            case "recargar": {
                if (!s.hasPermission("funkyfama.admin")) return sinPermiso(s);
                pl.recargar();
                s.sendMessage(pl.conPrefijo("recargado"));
                return true;
            }
            default: return ayuda(s);
        }
    }

    // ---------- jugadores ----------

    private boolean ver(CommandSender s, String[] a) {
        if (!s.hasPermission("funkyfama.ver.otros")) return sinPermiso(s);
        if (a.length < 2) return uso(s, "/fama ver <jugador>");
        Objetivo o = buscar(a[1]);
        if (o == null) return error(s, "jugador-no-encontrado");
        s.sendMessage(pl.conPrefijo("fama-otro", "{jugador}", o.nombre(), "{fama}", FunkyFama.num(pl.fama.de(o.id()))));
        return true;
    }

    private boolean top(CommandSender s, String[] a) {
        if (!s.hasPermission("funkyfama.top")) return sinPermiso(s);
        boolean deBandas = a.length >= 2 && a[1].toLowerCase().startsWith("banda");
        List<Tops.Fila> lista = deBandas ? pl.tops.bandas() : pl.tops.jugadores();
        s.sendMessage(pl.msg(deBandas ? "top-titulo-bandas" : "top-titulo-jugadores"));
        if (lista.isEmpty()) {
            s.sendMessage(pl.msg("top-vacio"));
            return true;
        }
        for (int i = 0; i < Math.min(10, lista.size()); i++) {
            Tops.Fila f = lista.get(i);
            s.sendMessage(pl.msg("top-linea", "{puesto}", String.valueOf(i + 1),
                    "{nombre}", f.nombre(), "{fama}", FunkyFama.num(f.fama())));
        }
        return true;
    }

    private boolean info(CommandSender s) {
        String x2 = pl.x2.activo()
                ? pl.texto("x2-si", "{tiempo}", FunkyFama.tiempo(pl.x2.restante()))
                : pl.texto("x2-no");
        s.sendMessage(pl.conPrefijo("info", "{x2}", x2, "{tiempo}", FunkyFama.tiempo(pl.msParaReinicio())));
        return true;
    }

    // ---------- staff ----------

    private boolean ajustar(CommandSender s, String[] a, int signo) {
        if (!s.hasPermission("funkyfama.admin")) return sinPermiso(s);
        if (a.length < 3) return uso(s, "/fama " + a[0].toLowerCase() + " <jugador> <cantidad> [motivo]");
        Objetivo o = buscar(a[1]);
        if (o == null) return error(s, "jugador-no-encontrado");
        Long n = numero(a[2]);
        if (n == null || n <= 0) return error(s, "numero-invalido");

        Datos.Jugador j = pl.datos.jugador(o.id(), o.nombre());
        long antes = j.fama;
        j.fama = Math.max(0, antes + signo * n);
        long aplicado = j.fama - antes;
        pl.fama.sumarABanda(o.id(), aplicado);
        pl.datos.marcarCambio();
        pl.tops.recalcular();

        String motivo = a.length > 3 ? String.join(" ", Arrays.copyOfRange(a, 3, a.length)) : "sin motivo";
        String accion = signo > 0 ? "Diste" : "Quitaste";
        pl.registrar(s.getName() + " " + (signo > 0 ? "dio " : "quitó ") + Math.abs(aplicado)
                + " de Fama a " + o.nombre() + " (" + antes + " -> " + j.fama + "). Motivo: " + motivo);
        s.sendMessage(pl.conPrefijo("admin-hecho", "{accion}", accion, "{cantidad}", FunkyFama.num(Math.abs(aplicado)),
                "{objetivo}", o.nombre(), "{fama}", FunkyFama.num(j.fama)));
        return true;
    }

    private boolean establecer(CommandSender s, String[] a) {
        if (!s.hasPermission("funkyfama.admin")) return sinPermiso(s);
        if (a.length < 3) return uso(s, "/fama establecer <jugador> <cantidad>");
        Objetivo o = buscar(a[1]);
        if (o == null) return error(s, "jugador-no-encontrado");
        Long n = numero(a[2]);
        if (n == null || n < 0) return error(s, "numero-invalido");

        Datos.Jugador j = pl.datos.jugador(o.id(), o.nombre());
        long antes = j.fama;
        j.fama = n;                                         // solo el jugador; la Banda no se toca
        pl.datos.marcarCambio();
        pl.tops.recalcular();
        pl.registrar(s.getName() + " estableció la Fama de " + o.nombre() + " en " + n + " (antes " + antes + ").");
        s.sendMessage(pl.conPrefijo("admin-hecho", "{accion}", "Estableciste", "{cantidad}", FunkyFama.num(n),
                "{objetivo}", o.nombre(), "{fama}", FunkyFama.num(j.fama)));
        return true;
    }

    private boolean banda(CommandSender s, String[] a) {
        if (!s.hasPermission("funkyfama.admin")) return sinPermiso(s);
        if (pl.bandas == null) return error(s, "sin-towny");
        if (a.length < 4) return uso(s, "/fama banda <dar|quitar> <banda> <cantidad> [motivo]");
        int signo = a[1].equalsIgnoreCase("dar") ? 1 : a[1].equalsIgnoreCase("quitar") ? -1 : 0;
        if (signo == 0) return uso(s, "/fama banda <dar|quitar> <banda> <cantidad> [motivo]");
        Bandas.Info info = pl.bandas.buscar(a[2]);
        if (info == null) return error(s, "banda-no-encontrada");
        Long n = numero(a[3]);
        if (n == null || n <= 0) return error(s, "numero-invalido");

        Datos.Banda b = pl.datos.banda(info.id(), info.nombre());
        long antes = b.fama;
        b.fama = Math.max(0, antes + signo * n);
        pl.datos.marcarCambio();
        pl.tops.recalcular();

        String motivo = a.length > 4 ? String.join(" ", Arrays.copyOfRange(a, 4, a.length)) : "sin motivo";
        pl.registrar(s.getName() + " " + (signo > 0 ? "dio " : "quitó ") + Math.abs(b.fama - antes)
                + " de Fama a la Banda " + info.nombre() + " (" + antes + " -> " + b.fama + "). Motivo: " + motivo);
        s.sendMessage(pl.conPrefijo("admin-hecho", "{accion}", signo > 0 ? "Diste" : "Quitaste",
                "{cantidad}", FunkyFama.num(Math.abs(b.fama - antes)),
                "{objetivo}", "la Banda " + info.nombre(), "{fama}", FunkyFama.num(b.fama)));
        return true;
    }

    private boolean x2(CommandSender s, String[] a) {
        if (!s.hasPermission("funkyfama.x2")) return sinPermiso(s);
        if (a.length >= 2 && a[1].equalsIgnoreCase("desactivar")) {
            pl.x2.desactivar(true);
            pl.registrar(s.getName() + " desactivó el x2.");
            return true;
        }
        if (a.length < 3 || !a[1].equalsIgnoreCase("activar")) return uso(s, "/fama x2 activar <30m|2h|1d> · /fama x2 desactivar");
        Long ms = duracion(a[2]);
        if (ms == null || ms <= 0) return error(s, "numero-invalido");
        pl.x2.activar(ms);
        pl.registrar(s.getName() + " activó el x2 por " + FunkyFama.tiempo(ms) + ".");
        return true;
    }

    private boolean reiniciar(CommandSender s) {
        if (!s.hasPermission("funkyfama.reiniciar")) return sinPermiso(s);
        pl.datos.reinicioManual = System.currentTimeMillis();
        pl.datos.marcarCambio();
        Bukkit.broadcast(pl.conPrefijo("reinicio-manual"));
        pl.registrar(s.getName() + " reinició los trials a mano.");
        return true;
    }

    private boolean temporada(CommandSender s, String[] a) {
        if (!s.hasPermission("funkyfama.temporada")) return sinPermiso(s);
        if (a.length < 2 || !a[1].equalsIgnoreCase("confirmar")) {
            s.sendMessage(FunkyFama.color("&c¡Ojo! Esto borra la Fama de TODOS los jugadores y Bandas. "
                    + "Se guarda una copia antes. Escribe &f/fama temporada confirmar&c para hacerlo."));
            return true;
        }
        String fecha = LocalDateTime.now(ZoneId.of("America/Mexico_City")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        File copia = new File(pl.getDataFolder(), "datos-temporada-" + fecha + ".yml");
        try {
            Files.writeString(copia.toPath(), pl.datos.foto(pl.inicioPeriodo()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            s.sendMessage(FunkyFama.color("&cNo se pudo guardar la copia, no se borró nada: " + e.getMessage()));
            return true;
        }
        pl.datos.jugadores.clear();
        pl.datos.bandas.clear();
        pl.datos.marcarCambio();
        pl.tops.recalcular();
        pl.registrar(s.getName() + " borró la Fama de la temporada. Copia: " + copia.getName());
        s.sendMessage(pl.conPrefijo("temporada-hecha", "{archivo}", copia.getName()));
        return true;
    }

    // ---------- ayudas ----------

    /** Busca por nombre: en línea, luego en los datos del plugin (sirve con el "." de Bedrock), luego en la caché del servidor. */
    private Objetivo buscar(String nombre) {
        Player en = Bukkit.getPlayerExact(nombre);
        if (en != null) return new Objetivo(en.getUniqueId(), en.getName());
        for (Map.Entry<UUID, Datos.Jugador> e : pl.datos.jugadores.entrySet()) {
            if (nombre.equalsIgnoreCase(e.getValue().nombre)) return new Objetivo(e.getKey(), e.getValue().nombre);
        }
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(nombre);
        if (op != null && op.getName() != null) return new Objetivo(op.getUniqueId(), op.getName());
        return null;
    }

    private static Long numero(String t) {
        try {
            return Long.parseLong(t.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 30m, 2h, 1d o un número solo (= minutos). */
    private static Long duracion(String t) {
        String x = t.toLowerCase().trim();
        long mult = 60_000L;
        if (x.endsWith("m")) x = x.substring(0, x.length() - 1);
        else if (x.endsWith("h")) { mult = 3_600_000L; x = x.substring(0, x.length() - 1); }
        else if (x.endsWith("d")) { mult = 86_400_000L; x = x.substring(0, x.length() - 1); }
        Long n = numero(x);
        return n == null ? null : n * mult;
    }

    private boolean sinPermiso(CommandSender s) {
        return error(s, "sin-permiso");
    }

    private boolean error(CommandSender s, String clave) {
        s.sendMessage(pl.conPrefijo(clave));
        return true;
    }

    private boolean uso(CommandSender s, String uso) {
        s.sendMessage(FunkyFama.color("&7Uso: &f" + uso));
        return true;
    }

    private boolean ayuda(CommandSender s) {
        List<String> l = new ArrayList<>();
        l.add("&#FF40FF&l✦ FunkyFama ✦");
        l.add("&f/fama &7tu Fama · &f/fama ver [jugador] · &f/fama top [jugadores|bandas] · &f/fama info");
        if (s.hasPermission("funkyfama.admin")) {
            l.add("&f/fama dar|quitar [jugador] [n] [motivo] · &f/fama establecer [jugador] [n]");
            l.add("&f/fama banda dar|quitar [banda] [n] [motivo] · &f/fama recargar");
        }
        if (s.hasPermission("funkyfama.x2")) l.add("&f/fama x2 activar [30m|2h|1d] · &f/fama x2 desactivar");
        if (s.hasPermission("funkyfama.reiniciar")) l.add("&f/fama reiniciar &7(reinicia los trials ya)");
        if (s.hasPermission("funkyfama.temporada")) l.add("&f/fama temporada confirmar &7(borra la Fama de todos)");
        for (String x : l) s.sendMessage(FunkyFama.color(x));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String [] a) {
        List<String> op = new ArrayList<>();
        if (a.length == 1) {
            op.addAll(List.of("ver", "top", "info"));
            if (s.hasPermission("funkyfama.admin")) op.addAll(List.of("dar", "quitar", "establecer", "banda", "recargar"));
            if (s.hasPermission("funkyfama.x2")) op.add("x2");
            if (s.hasPermission("funkyfama.reiniciar")) op.add("reiniciar");
            if (s.hasPermission("funkyfama.temporada")) op.add("temporada");
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "ver", "dar", "quitar", "establecer" -> Bukkit.getOnlinePlayers().forEach(p -> op.add(p.getName()));
                case "top" -> op.addAll(List.of("jugadores", "bandas"));
                case "banda" -> op.addAll(List.of("dar", "quitar"));
                case "x2" -> op.addAll(List.of("activar", "desactivar"));
                case "temporada" -> op.add("confirmar");
                default -> { }
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("x2") && a[1].equalsIgnoreCase("activar")) {
            op.addAll(List.of("30m", "1h", "2h", "1d"));
        }
        String ultimo = a[a.length - 1].toLowerCase();
        op.removeIf(x -> !x.toLowerCase().startsWith(ultimo));
        return op;
    }
}
