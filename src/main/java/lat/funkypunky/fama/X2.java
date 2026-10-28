package lat.funkypunky.fama;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Evento x2: se activa con /fama x2 activar <tiempo> y se apaga solo.
 * La hora de fin se guarda en datos.yml: si el servidor se reinicia a mitad del evento, sigue.
 */
final class X2 implements Listener {

    private final FunkyFama pl;
    private BossBar barra;

    X2(FunkyFama pl) {
        this.pl = pl;
    }

    boolean activo() {
        return pl.datos.x2Fin > System.currentTimeMillis();
    }

    int multiplicador() {
        return Math.max(1, pl.getConfig().getInt("x2.multiplicador", 2));
    }

    /** ¿Está activo el x2 y además esta parte (fama, botín...) se duplica según el config? */
    boolean duplica(String opcion) {
        return activo() && pl.getConfig().getBoolean("x2." + opcion, true);
    }

    long restante() {
        return Math.max(0, pl.datos.x2Fin - System.currentTimeMillis());
    }

    void activar(long ms) {
        pl.datos.x2Fin = System.currentTimeMillis() + ms;
        pl.datos.x2Duracion = ms;
        pl.datos.marcarCambio();
        Bukkit.broadcast(pl.conPrefijo("x2-activado", "{tiempo}", FunkyFama.tiempo(ms)));
        actualizarBarra();
    }

    void desactivar(boolean anunciar) {
        pl.datos.x2Fin = 0;
        pl.datos.x2Duracion = 0;
        pl.datos.marcarCambio();
        ocultarBarra();
        if (anunciar) Bukkit.broadcast(pl.conPrefijo("x2-terminado"));
    }

    /** Se llama cada segundo. */
    void tick() {
        if (pl.datos.x2Fin == 0) return;
        if (!activo()) {
            desactivar(true);
            return;
        }
        actualizarBarra();
    }

    private void actualizarBarra() {
        if (!pl.getConfig().getBoolean("x2.bossbar", true)) {
            ocultarBarra();
            return;
        }
        long dur = Math.max(1, pl.datos.x2Duracion);
        float progreso = Math.max(0f, Math.min(1f, restante() / (float) dur));
        var nombre = pl.msg("x2-bossbar", "{tiempo}", FunkyFama.tiempo(restante()));
        if (barra == null) {
            barra = BossBar.bossBar(nombre, progreso, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        } else {
            barra.name(nombre);
            barra.progress(progreso);
        }
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(barra);
    }

    void ocultarBarra() {
        if (barra == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barra);
        barra = null;
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        if (barra != null && activo()) e.getPlayer().showBossBar(barra);
    }

    /** Copia cada objeto del botín n veces (con n = 2, el doble). */
    static List<ItemStack> multiplicar(Collection<ItemStack> botin, int n) {
        List<ItemStack> r = new ArrayList<>();
        for (ItemStack it : botin) {
            if (it == null) continue;
            for (int i = 0; i < n; i++) r.add(it.clone());
        }
        return r;
    }
}
