package lat.funkypunky.fama;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Trial Chambers:
 * 1) Vault abierto con llave  -> Fama (+ botín x2 si hay evento) y se anota cuándo lo abrió.
 * 2) Cofre/barril de trial     -> Fama (+ botín x2) y se anota el cofre para rellenarlo mañana.
 * 3) Clic derecho en un vault o cofre de trial -> si ya pasó el reinicio diario, se "rearma".
 */
final class TrialListener implements Listener {

    private static final String PREFIJO = "chests/trial_chambers/";

    private final FunkyFama pl;

    TrialListener(FunkyFama pl) {
        this.pl = pl;
    }

    // ---------- 1) Vaults ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alAbrirVault(BlockDispenseLootEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;                              // los trial spawners sueltan botín sin jugador
        Block b = e.getBlock();
        if (b.getType() != Material.VAULT) return;

        boolean siniestro = b.getBlockData().getAsString().contains("ominous=true");
        FileConfiguration c = pl.getConfig();

        if (pl.x2.duplica("duplicar-botin-vaults")) {
            e.setDispensedLoot(X2.multiplicar(e.getDispensedLoot(), pl.x2.multiplicador()));
        }

        pl.datos.vaults.computeIfAbsent(Datos.clave(b), k -> new HashMap<>())
                .put(p.getUniqueId(), System.currentTimeMillis());
        pl.datos.marcarCambio();

        long base = siniestro ? c.getLong("trials.vault-siniestro", 25) : c.getLong("trials.vault-normal", 10);
        pl.fama.otorgar(p, base, siniestro ? "vault-siniestro" : "vault", true);

        double dinero = c.getDouble("trials.dinero-por-vault", 0);
        if (dinero > 0 && pl.economia != null && !pl.fama.excluido(p)) pl.economia.depositar(p, dinero);
    }

    // ---------- 2) Cofres de trial ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alGenerarBotin(LootGenerateEvent e) {
        NamespacedKey k = e.getLootTable().getKey();
        if (!NamespacedKey.MINECRAFT.equals(k.getNamespace()) || !k.getKey().startsWith(PREFIJO)) return;

        InventoryHolder holder = e.getInventoryHolder();
        if (holder == null) return;                         // solo contenedores (no vaults ni otras cosas)

        if (pl.getConfig().getBoolean("reinicio.cofres", true) && holder instanceof Container cont) {
            Datos.Cofre cofre = new Datos.Cofre();
            cofre.loot = k.toString();
            cofre.armado = System.currentTimeMillis();
            pl.datos.cofres.put(Datos.clave(cont.getLocation()), cofre);
            pl.datos.marcarCambio();
        }

        if (!(e.getEntity() instanceof Player p)) return;   // una tolva no gana Fama

        if (pl.x2.duplica("duplicar-botin-cofres")) {
            e.setLoot(X2.multiplicar(new ArrayList<>(e.getLoot()), pl.x2.multiplicador()));
        }

        String nombre = k.getKey().substring(PREFIJO.length());
        FileConfiguration c = pl.getConfig();
        long base = c.contains("trials.cofres." + nombre)
                ? c.getLong("trials.cofres." + nombre)
                : c.getLong("trials.cofre-por-defecto", 1);
        pl.fama.otorgar(p, base, "cofre", true);
    }

    // ---------- 3) Reinicio diario al hacer clic ----------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        if (b.getType() == Material.VAULT) {
            if (pl.getConfig().getBoolean("reinicio.vaults", true)) rearmarVault(e.getPlayer(), b);
            return;
        }
        if (!pl.getConfig().getBoolean("reinicio.cofres", true)) return;
        String clave = Datos.clave(b);
        Datos.Cofre cofre = pl.datos.cofres.get(clave);
        if (cofre != null) rearmarCofre(b, clave, cofre);
    }

    private void rearmarVault(Player p, Block b) {
        if (!(b.getState() instanceof org.bukkit.block.Vault vault)) return;
        UUID u = p.getUniqueId();
        if (!vault.hasRewardedPlayer(u)) return;            // nunca lo abrió: el vault funciona normal

        String clave = Datos.clave(b);
        Map<UUID, Long> aperturas = pl.datos.vaults.get(clave);
        Long cuando = aperturas == null ? null : aperturas.get(u);

        if (cuando != null && cuando >= pl.inicioPeriodo()) {
            p.sendActionBar(pl.msg("vault-ya-abierto", "{tiempo}", FunkyFama.tiempo(pl.msParaReinicio())));
            return;
        }

        // Lo abrió antes del último reinicio (o antes de instalar el plugin): se le borra del vault.
        vault.removeRewardedPlayer(u);
        vault.update();
        if (aperturas != null) aperturas.remove(u);
        pl.datos.marcarCambio();
        p.sendActionBar(pl.msg("vault-reiniciado"));
    }

    private void rearmarCofre(Block b, String clave, Datos.Cofre cofre) {
        if (cofre.armado >= pl.inicioPeriodo()) return;     // ya se llenó en este periodo

        BlockState st = b.getState();
        if (!(st instanceof Container) || !(st instanceof Lootable lootable)) {
            pl.datos.cofres.remove(clave);                   // ya no hay cofre ahí
            pl.datos.marcarCambio();
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(cofre.loot);
        LootTable tabla = key == null ? null : Bukkit.getLootTable(key);
        if (tabla == null) {
            pl.datos.cofres.remove(clave);
            pl.datos.marcarCambio();
            return;
        }
        // Al abrirlo, Minecraft genera el botín de nuevo (y vuelve a disparar alGenerarBotin).
        // No borra lo que el jugador haya guardado: solo llena huecos.
        lootable.setLootTable(tabla);
        st.update();
        cofre.armado = System.currentTimeMillis();
        pl.datos.marcarCambio();
    }
}
