package lat.funkypunky.fama;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pone en las NPCs de FancyNpcs la skin del jugador que va en cada puesto de un top de ajLeaderboards.
 *
 * 1) Guarda la skin (URL + si es slim) de cada jugador mientras está conectado.
 *    La skin ya viene puesta por Mojang (Java premium), Geyser/Floodgate (Bedrock) o SkinsRestorer (no premium).
 * 2) Cada minuto pregunta a ajLeaderboards quién va en cada puesto.
 * 3) Si cambió, ejecuta desde la consola: npc skin <npc> <url> [--slim]
 *
 * Solo se crea si PlaceholderAPI está activo.
 */
final class SkinsNpc implements Listener {

    private static final Pattern NOMBRE_JAVA = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final FunkyFama pl;
    /** npc -> lo último que se le aplicó (para no repetir el comando cada minuto) */
    private final Map<String, String> aplicado = new HashMap<>();

    SkinsNpc(FunkyFama pl) {
        this.pl = pl;
    }

    // ---------- 1) guardar la skin de cada jugador ----------

    void guardar(Player p) {
        try {
            PlayerTextures t = p.getPlayerProfile().getTextures();
            URL url = t.getSkin();
            if (url == null) return;                         // todavía no tiene skin aplicada
            Datos.Skin s = pl.datos.skins.computeIfAbsent(p.getUniqueId(), k -> new Datos.Skin());
            String nueva = url.toString();
            boolean slim = t.getSkinModel() == PlayerTextures.SkinModel.SLIM;
            if (!nueva.equals(s.url) || s.slim != slim || !p.getName().equals(s.nombre)) {
                s.url = nueva;
                s.slim = slim;
                s.nombre = p.getName();
                pl.datos.marcarCambio();
            }
        } catch (Exception e) {
            // Un perfil raro no debe romper nada: simplemente no se guarda esa vez.
        }
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        // Floodgate y SkinsRestorer ponen la skin unos segundos DESPUÉS de entrar: esperamos 5 s.
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (p.isOnline()) guardar(p);
        }, 100L);
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent e) {
        guardar(e.getPlayer());
    }

    // ---------- 2) y 3) revisar los tops y cambiar las skins ----------

    /** Se llama cada minuto. */
    void revisar() {
        if (!pl.getConfig().getBoolean("npcs-top.activado", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) guardar(p);

        ConfigurationSection lista = pl.getConfig().getConfigurationSection("npcs-top.lista");
        if (lista == null) return;
        String tipo = pl.getConfig().getString("npcs-top.tipo", "alltime");

        for (String npc : lista.getKeys(false)) {
            String[] partes = lista.getString(npc, "").split(":");
            if (partes.length != 2) {
                pl.getLogger().warning("npcs-top.lista." + npc + " debe ser 'top:puesto', por ejemplo 'statistic_hours_played:1'");
                continue;
            }
            String base = "%ajlb_lb_" + partes[0] + "_" + partes[1] + "_" + tipo + "_";
            String uuidTexto = PlaceholderAPI.setPlaceholders((OfflinePlayer) null, base + "skin_uuid%");
            String nombre = PlaceholderAPI.setPlaceholders((OfflinePlayer) null, base + "name%");

            String skin = elegirSkin(uuidTexto, nombre);
            if (skin == null) continue;                      // puesto vacío o datos cargando: no tocar la NPC
            if (skin.equals(aplicado.get(npc))) continue;   // ya tiene esa skin

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc skin " + npc + " " + skin);
            aplicado.put(npc, skin);
            pl.getLogger().info("NPC " + npc + ": skin de " + nombre + " aplicada.");
        }
    }

    /** Devuelve "url [--slim]" o un nombre de Java, o null si no hay nada seguro que aplicar. */
    private String elegirSkin(String uuidTexto, String nombre) {
        UUID id;
        try {
            id = UUID.fromString(uuidTexto.trim());
        } catch (Exception e) {
            return null;                                     // "---", "Loading", "Board does not exist"...
        }
        Datos.Skin s = pl.datos.skins.get(id);
        if (s != null && s.url != null) return s.url + (s.slim ? " --slim" : "");
        // Sin skin guardada: el nombre solo sirve si es una cuenta Java válida (Mojang la busca por nombre).
        if (nombre != null && NOMBRE_JAVA.matcher(nombre).matches()) return nombre;
        return null;                                         // Bedrock sin skin guardada: esperar a que entre
    }

    /** Olvida lo aplicado para que la próxima revisión vuelva a poner todas las skins. */
    void forzar() {
        aplicado.clear();
        revisar();
    }
}
