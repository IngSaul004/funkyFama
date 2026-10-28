package lat.funkypunky.fama;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Puente con Vault (VaultUnlocked se registra como "Vault").
 * Solo se crea si Vault está instalado. La moneda real la maneja Essentials.
 */
final class Economia {

    private final Economy eco;

    private Economia(Economy eco) {
        this.eco = eco;
    }

    static Economia crear() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : new Economia(rsp.getProvider());
    }

    void depositar(OfflinePlayer p, double cantidad) {
        if (cantidad > 0) eco.depositPlayer(p, cantidad);
    }
}
