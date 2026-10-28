package lat.funkypunky.fama;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;

import java.util.UUID;

/**
 * Única clase que toca Towny. Solo se crea si Towny está activo:
 * por eso el plugin no truena cuando Towny no está.
 * Se usa el UUID del pueblo (no el nombre): si una Banda paga los $1,000 y se renombra, conserva su Fama.
 */
final class TownyBandas implements Bandas {

    @Override
    public Info de(UUID jugador) {
        Resident r = TownyAPI.getInstance().getResident(jugador);
        if (r == null || !r.hasTown()) return null;
        Town t = r.getTownOrNull();
        return t == null ? null : new Info(t.getUUID(), t.getName());
    }

    @Override
    public String nombreActual(UUID banda) {
        Town t = TownyAPI.getInstance().getTown(banda);
        return t == null ? null : t.getName();
    }

    @Override
    public Info buscar(String nombre) {
        Town t = TownyAPI.getInstance().getTown(nombre);
        return t == null ? null : new Info(t.getUUID(), t.getName());
    }
}
