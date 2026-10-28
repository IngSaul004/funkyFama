package lat.funkypunky.fama;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;

/**
 * FunkyFama: plugin principal.
 * Arranca las piezas, lleva el reloj (cada segundo) y guarda los datos.
 */
public final class FunkyFama extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    Datos datos;
    Reinicio reinicio;
    Bandas bandas;       // null si no hay Towny
    Economia economia;   // null si no hay Vault
    Fama fama;
    Evento evento;
    Tops tops;
    SkinsNpc skinsNpc;   // null si no hay PlaceholderAPI

    private long periodoVisto;
    private int segundos;
    private BukkitTask tareaGuardado;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        datos = new Datos(getDataFolder());
        datos.cargar();
        cargarReinicio();

        // Integraciones opcionales: si el plugin no está, la función se apaga sola.
        if (getServer().getPluginManager().isPluginEnabled("Towny")) {
            bandas = new TownyBandas();
            getLogger().info("Towny encontrado: la Fama también se suma a las Bandas.");
        }
        if (getServer().getPluginManager().isPluginEnabled("Vault")) {
            economia = Economia.crear();
            if (economia != null) getLogger().info("Vault encontrado: se pueden dar FunkyCoins por vault.");
        }

        fama = new Fama(this);
        evento = new Evento(this);
        tops = new Tops(this);

        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        getServer().getPluginManager().registerEvents(new TrialListener(this), this);
        getServer().getPluginManager().registerEvents(evento, this);

        Comando comando = new Comando(this);
        PluginCommand pc = getCommand("fama");
        if (pc != null) {
            pc.setExecutor(comando);
            pc.setTabCompleter(comando);
        }

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new Expansion(this).register();
            skinsNpc = new SkinsNpc(this);
            getServer().getPluginManager().registerEvents(skinsNpc, this);
            getLogger().info("PlaceholderAPI encontrado: variables %funkyfama_...% listas para TAB y ajLeaderboards.");
        }

        periodoVisto = inicioPeriodo();
        tops.recalcular();
        getServer().getScheduler().runTaskTimer(this, this::cadaSegundo, 20L, 20L);
        programarGuardado();
    }

    @Override
    public void onDisable() {
        if (evento != null) evento.ocultarBarra();
        if (datos != null) {
            try {
                datos.escribir(datos.foto(inicioPeriodo()));
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "No se pudo guardar datos.yml", e);
            }
        }
    }

    void recargar() {
        reloadConfig();
        cargarReinicio();
        programarGuardado();
        periodoVisto = inicioPeriodo();
        evento.tick();
    }

    private void cargarReinicio() {
        ZoneId zona;
        LocalTime hora;
        try {
            zona = ZoneId.of(getConfig().getString("reinicio.zona-horaria", "America/Mexico_City"));
        } catch (Exception e) {
            getLogger().warning("reinicio.zona-horaria inválida, uso America/Mexico_City");
            zona = ZoneId.of("America/Mexico_City");
        }
        try {
            hora = LocalTime.parse(getConfig().getString("reinicio.hora", "06:00"));
        } catch (Exception e) {
            getLogger().warning("reinicio.hora inválida (formato HH:mm), uso 06:00");
            hora = LocalTime.of(6, 0);
        }
        reinicio = new Reinicio(zona, hora);
    }

    private void programarGuardado() {
        if (tareaGuardado != null) tareaGuardado.cancel();
        long ticks = Math.max(1, getConfig().getLong("guardar-cada-minutos", 5)) * 60L * 20L;
        tareaGuardado = getServer().getScheduler().runTaskTimer(this, this::guardarAsync, ticks, ticks);
    }

    /** La "foto" se toma en el hilo principal; escribir el disco va en otro hilo para no dar lag. */
    void guardarAsync() {
        if (!datos.hayCambios()) return;
        String texto = datos.foto(inicioPeriodo());
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                datos.escribir(texto);
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "No se pudo guardar datos.yml", e);
            }
        });
    }

    private void cadaSegundo() {
        evento.tick();

        long periodo = inicioPeriodo();
        if (periodo != periodoVisto) {
            boolean fueManual = periodo == datos.reinicioManual;
            periodoVisto = periodo;
            if (!fueManual && getConfig().getBoolean("reinicio.anunciar", true)) {
                Bukkit.broadcast(conPrefijo("reinicio-auto"));
            }
            getLogger().info("Nuevo periodo de trials: vaults y cofres disponibles otra vez.");
        }

        if (++segundos >= 60) {
            segundos = 0;
            tops.recalcular();
            if (skinsNpc != null) skinsNpc.revisar();
        }
    }

    // ---------- tiempo ----------

    long inicioPeriodo() {
        return reinicio.inicioPeriodo(System.currentTimeMillis(), datos.reinicioManual);
    }

    long msParaReinicio() {
        long ahora = System.currentTimeMillis();
        return reinicio.proximo(ahora) - ahora;
    }

    static String tiempo(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, seg = s % 60;
        if (h > 0) return h + "h " + String.format("%02d", m) + "m";
        if (m > 0) return m + "m " + String.format("%02d", seg) + "s";
        return seg + "s";
    }

    static String num(long n) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX")).format(n);
    }

    // ---------- mensajes ----------

    String texto(String clave, String... pares) {
        String t = getConfig().getString("mensajes." + clave, clave);
        for (int i = 0; i + 1 < pares.length; i += 2) t = t.replace(pares[i], pares[i + 1]);
        return t;
    }

    static Component color(String texto) {
        return LEGACY.deserialize(texto);
    }

    Component msg(String clave, String... pares) {
        return color(texto(clave, pares));
    }

    Component conPrefijo(String clave, String... pares) {
        return color(texto("prefijo") + texto(clave, pares));
    }

    /** Todo cambio hecho por el staff queda en plugins/FunkyFama/registro.log (como pide la regla de /eco give). */
    void registrar(String linea) {
        String fecha = LocalDateTime.now(ZoneId.of("America/Mexico_City"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String texto = "[" + fecha + "] " + linea + System.lineSeparator();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                Files.writeString(getDataFolder().toPath().resolve("registro.log"), texto, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                getLogger().warning("No se pudo escribir registro.log: " + e.getMessage());
            }
        });
        getLogger().info(linea);
    }
}
