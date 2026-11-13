# CombatGuardChatColors

Paper plugin project targeting Minecraft/Paper 1.21.11 (Java 21).

## Build
Install JDK 21 and Maven, then run:

```bash
mvn clean package
```

The plugin JAR is generated in `target/CombatGuardChatColors-1.0.0.jar`.

## Commands
- `/chatcolors` — open the gradient selection GUI (operators only).
- `/chatcolor set <player>` — apply the gradient selected in the GUI.
- `/chatcolor set <player> <gradient>` — apply Water, Galaxy, Cherry_Blossom, Vampire, Christmas, Volcano, or Lava.
- `/chatcolor remove <player>` — remove a player's gradient.

## Features
- Player-versus-player damage applies or refreshes a 20-second combat tag.
- Combat-tagged players cannot execute commands or open storage/restocking inventories.
- Combat logout explicitly drops storage, armor, and offhand items at the logout location.
- Bukkit player teleport events and ender pearl launches are cancelled.
- Seven operator-managed gradients color chat names.

## Notes
- Teleport blocking covers Bukkit `PlayerTeleportEvent`; plugins that move players without firing it need their own integration.
- Gradient assignments are currently in memory and reset on restart.
- Other chat-formatting plugins may conflict with this plugin's chat listener.
