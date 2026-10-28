package lat.funkypunky.fama;

import java.util.UUID;

/**
 * Lo que el plugin necesita saber de las Bandas, sin depender de Towny directamente.
 * Así, si Towny no está instalado, el plugin funciona igual (solo Fama de jugadores).
 */
interface Bandas {

    record Info(UUID id, String nombre) {}

    /** La Banda actual del jugador, o null si no tiene. */
    Info de(UUID jugador);

    /** Nombre actual de una Banda (por si la renombraron), o null si ya no existe. */
    String nombreActual(UUID banda);

    /** Buscar una Banda por nombre, o null. */
    Info buscar(String nombre);
}
