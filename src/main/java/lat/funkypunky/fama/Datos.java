package lat.funkypunky.fama;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Todo lo que el plugin recuerda, guardado en plugins/FunkyFama/datos.yml.
 * Regla: solo se modifica en el hilo principal del servidor; los mapas son
 * ConcurrentHashMap porque TAB y PlaceholderAPI pueden LEER desde otro hilo.
 */
final class Datos {

    static final class Jugador {
        String nombre;
        long fama;
        long mobsHoy;     // Fama de mobs ganada en el periodo actual (sin x2)
        long mobsDesde;   // inicio del periodo en que se contó mobsHoy
    }

    static final class Banda {
        String nombre;
        long fama;
    }

    static final class Cofre {
        String loot;      // ej. minecraft:chests/trial_chambers/reward
        long armado;      // cuándo se llenó por última vez
    }

    final Map<UUID, Jugador> jugadores = new ConcurrentHashMap<>();
    final Map<UUID, Banda> bandas = new ConcurrentHashMap<>();
    /** vault ("mundo;x;y;z") -> jugador -> cuándo lo abrió */
    final Map<String, Map<UUID, Long>> vaults = new HashMap<>();
    final Map<String, Cofre> cofres = new HashMap<>();
    volatile long x2Fin;
    volatile long x2Duracion;
    volatile long reinicioManual;

    private final File archivo;
    private boolean cambios;

    Datos(File carpeta) {
        this.archivo = new File(carpeta, "datos.yml");
    }

    static String clave(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    static String clave(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    Jugador jugador(UUID id, String nombre) {
        Jugador j = jugadores.computeIfAbsent(id, k -> new Jugador());
        if (nombre != null) j.nombre = nombre;
        return j;
    }

    Banda banda(UUID id, String nombre) {
        Banda b = bandas.computeIfAbsent(id, k -> new Banda());
        if (nombre != null) b.nombre = nombre;
        return b;
    }

    void marcarCambio() {
        cambios = true;
    }

    boolean hayCambios() {
        return cambios;
    }

    void cargar() {
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);

        ConfigurationSection js = y.getConfigurationSection("jugadores");
        if (js != null) for (String k : js.getKeys(false)) {
            UUID id = uuid(k);
            if (id == null) continue;
            Jugador j = new Jugador();
            j.nombre = js.getString(k + ".nombre", "?");
            j.fama = js.getLong(k + ".fama");
            j.mobsHoy = js.getLong(k + ".mobs-hoy");
            j.mobsDesde = js.getLong(k + ".mobs-desde");
            jugadores.put(id, j);
        }

        ConfigurationSection bs = y.getConfigurationSection("bandas");
        if (bs != null) for (String k : bs.getKeys(false)) {
            UUID id = uuid(k);
            if (id == null) continue;
            Banda b = new Banda();
            b.nombre = bs.getString(k + ".nombre", "?");
            b.fama = bs.getLong(k + ".fama");
            bandas.put(id, b);
        }

        ConfigurationSection vs = y.getConfigurationSection("vaults");
        if (vs != null) for (String k : vs.getKeys(false)) {
            ConfigurationSection v = vs.getConfigurationSection(k);
            if (v == null) continue;
            Map<UUID, Long> m = new HashMap<>();
            for (String u : v.getKeys(false)) {
                UUID id = uuid(u);
                if (id != null) m.put(id, v.getLong(u));
            }
            vaults.put(k, m);
        }

        ConfigurationSection cs = y.getConfigurationSection("cofres");
        if (cs != null) for (String k : cs.getKeys(false)) {
            Cofre c = new Cofre();
            c.loot = cs.getString(k + ".loot");
            c.armado = cs.getLong(k + ".armado");
            if (c.loot != null) cofres.put(k, c);
        }

        x2Fin = y.getLong("x2-fin");
        x2Duracion = y.getLong("x2-duracion");
        reinicioManual = y.getLong("reinicio-manual");
    }

    /**
     * Arma el texto del archivo en el hilo principal (foto del momento).
     * Las aperturas de vaults anteriores al periodo actual se borran: ya no sirven.
     */
    String foto(long inicioPeriodo) {
        YamlConfiguration y = new YamlConfiguration();
        jugadores.forEach((id, j) -> {
            String p = "jugadores." + id;
            y.set(p + ".nombre", j.nombre);
            y.set(p + ".fama", j.fama);
            y.set(p + ".mobs-hoy", j.mobsHoy);
            y.set(p + ".mobs-desde", j.mobsDesde);
        });
        bandas.forEach((id, b) -> {
            y.set("bandas." + id + ".nombre", b.nombre);
            y.set("bandas." + id + ".fama", b.fama);
        });
        vaults.values().forEach(m -> m.values().removeIf(t -> t < inicioPeriodo));
        vaults.values().removeIf(Map::isEmpty);
        vaults.forEach((k, m) -> m.forEach((u, t) -> y.set("vaults." + k + "." + u, t)));
        cofres.forEach((k, c) -> {
            y.set("cofres." + k + ".loot", c.loot);
            y.set("cofres." + k + ".armado", c.armado);
        });
        y.set("x2-fin", x2Fin);
        y.set("x2-duracion", x2Duracion);
        y.set("reinicio-manual", reinicioManual);
        cambios = false;
        return y.saveToString();
    }

    /** Escribe primero a un temporal y luego lo mueve: si se apaga a mitad, no se corrompe datos.yml. */
    void escribir(String texto) throws IOException {
        File carpeta = archivo.getParentFile();
        if (!carpeta.exists() && !carpeta.mkdirs()) throw new IOException("No se pudo crear " + carpeta);
        File tmp = new File(carpeta, "datos.yml.tmp");
        Files.writeString(tmp.toPath(), texto, StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), archivo.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    File archivo() {
        return archivo;
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
