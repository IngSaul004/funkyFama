# FunkyFama

Plugin de **Fama** para FUNKY PUNKY MC (Paper 26.3, Java 25, crossplay con Geyser).

## Qué hace
| Acción | Fama por defecto | x2 |
| --- | --- | --- |
| Matar un mob hostil | 1 (jefes más, ver `mobs.valores`) · máx. 50 al día | Fama ×2 (drops de mobs no, a propósito) |
| Mob de un trial spawner | +1 extra | sí |
| Mob de spawner normal | 0 (anti granja AFK) | — |
| Abrir un vault de Trial Chamber | 10 | Fama ×2 y botín ×2 |
| Abrir un vault siniestro | 25 | Fama ×2 y botín ×2 |
| Abrir un cofre/barril de Trial Chamber | 1 a 12 según el cofre | Fama ×2 y botín ×2 |

- **Reinicio diario** a las 06:00 de México (mismo "nuevo día" que Towny y Jobs): cada jugador puede volver a usar su llave en el mismo vault y los cofres de trial se vuelven a llenar. También `/fama reiniciar`.
- **Bandas (Towny):** la Fama del jugador se suma a su Banda en el momento en que la gana. Si se sale, la Fama se queda en la Banda: no se puede traspasar.
- **Staff:** quien tenga `funkyfama.excluir` no gana Fama. El `*` del owner lo incluye, así que tu cuenta de owner nunca entra al top.
- Todo lo que hace el staff queda en `plugins/FunkyFama/registro.log`.

## Compilar (GitHub Actions, sin instalar nada)
1. Crea un repositorio en GitHub (privado), por ejemplo `funkyfama`.
2. Sube **todo el contenido** de esta carpeta (incluida `.github/workflows/compilar.yml`).
3. Pestaña **Actions** → "Compilar FunkyFama" → espera la palomita verde.
4. Abre la ejecución → sección **Artifacts** → descarga `FunkyFama` → adentro está `FunkyFama-1.0.0.jar`.
5. Si sale una ❌: abre el paso "Compilar", copia el error y pásamelo.

## Instalar
1. Backup. Servidor apagado. Sube el `.jar` a `plugins/`. Enciende.
2. En el log: `Towny encontrado`, `PlaceholderAPI encontrado`.
3. Permisos para el staff (desde la consola):
   - `lp group admin permission set funkyfama.admin true`
   - `lp group admin permission set funkyfama.x2 true`
   - `lp group admin permission set funkyfama.reiniciar true`
   - `lp group admin permission set funkyfama.excluir true` (admin, mod y helper no ganan Fama)
   - Repite `funkyfama.excluir` para `mod`, `helper` y `builder`. Owner ya lo tiene por `*`.

## Comandos
`/fama` · `/fama ver <jugador>` · `/fama top [jugadores|bandas]` · `/fama info`
Staff: `/fama dar|quitar <jugador> <n> [motivo]` · `/fama establecer <jugador> <n>` · `/fama banda dar|quitar <banda> <n> [motivo]` · `/fama x2 activar 2h` · `/fama x2 desactivar` · `/fama reiniciar` · `/fama recargar` · `/fama temporada confirmar`

## Variables (PlaceholderAPI → TAB, hologramas, ajLeaderboards)
`%funkyfama_fama%` · `%funkyfama_fama_formato%` · `%funkyfama_puesto%` · `%funkyfama_banda%` · `%funkyfama_fama_banda%` · `%funkyfama_mobs_hoy%` · `%funkyfama_mobs_limite%` · `%funkyfama_x2%` · `%funkyfama_x2_activo%` · `%funkyfama_x2_restante%` · `%funkyfama_reinicio%` · `%funkyfama_top_jugador_<n>_nombre%` / `_valor` · `%funkyfama_top_banda_<n>_nombre%` / `_valor`
