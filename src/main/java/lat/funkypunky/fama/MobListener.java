package lat.funkypunky.fama;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Fama por matar mobs. */
final class MobListener implements Listener {

    private final FunkyFama pl;

    MobListener(FunkyFama pl) {
        this.pl = pl;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alMorir(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        if (mob instanceof Player) return;              // sin PvP: matar jugadores nunca da Fama
        Player asesino = mob.getKiller();
        if (asesino == null) return;                    // murió por caída, lava, otro mob...

        FileConfiguration c = pl.getConfig();
        List<String> mundos = c.getStringList("mobs.mundos");
        if (!mundos.isEmpty() && !mundos.contains(mob.getWorld().getName())) return;

        SpawnReason razon = mob.getEntitySpawnReason(); // de dónde salió el mob (API de Paper)
        if (razon == SpawnReason.SPAWNER && c.getBoolean("mobs.ignorar-spawners", true)) return;

        if (pl.evento.duplica("duplicar-drops-mobs")) {
            List<ItemStack> dobles = Evento.multiplicar(e.getDrops(), pl.evento.multiplicador());
            e.getDrops().clear();
            e.getDrops().addAll(dobles);
        }

        String tipo = mob.getType().name();
        long base;
        if (c.contains("mobs.valores." + tipo)) base = c.getLong("mobs.valores." + tipo);
        else if (mob instanceof Enemy) base = c.getLong("mobs.hostil-por-defecto", 1);
        else base = c.getLong("mobs.pasivo-por-defecto", 0);

        if (base > 0 && razon == SpawnReason.TRIAL_SPAWNER) base += c.getLong("mobs.extra-trial-spawner", 1);

        pl.fama.otorgarMob(asesino, base);
    }
}
