<h1 align="center">BossMode - RandomCraft</h1>
Este sistema de Boss que permite realizar cambios a un jugador para simular un
Boss que permite obtener mayor vida, habilidades especiales, entre otros mas.

## How to config / Como configurarlo
```boss-attributes:
  health: 1000.0             # Total health
  scale: 1.5                 # Tamaño (1.0 is normal, 1.5 is giant)
  movement-speed: 0.15       # Velocidad (0.1 is normal)
  attack-damage: 15.0        # Daño por Hit
  knockback-resistance: 1.0  # 1.0 = Immune al Knockback
  attack-knockback: 2.0      # Cuanto puede empujar el Boss
```
## Comandos
```
/boss start
/boss stop
/boss reload
```
## Mensajes en el Config.yml
```
# System messages (Use & for colors)
messages:
  prefix: "&c[BossMode] &r"
  no-permission: "&cYou don't have permission."
  player-not-found: "&cPlayer not found."
  already-running: "&cAn event is already running."
  event-start: "&c&lTHE BOSS HAS APPEARED! &eDefeat %player%!"
  event-stop: "&eThe event has ended: %reason%"
  boss-defeated: "The Boss has been annihilated!"
  top-damage-header: "&6&l--- FINAL TOP DAMAGE ---"
  top-damage-entry: "&e#%rank% %player% - &c%damage%"
  cooldown-wait: "&cAbility on cooldown. Wait &e%time%s&c."
  health-full: "&aYour health is already full!"
```

### SHOWCASE PLUGIN
<details>
  <summary>Scoreboard</summary>

  <br>

  <img src="https://i.imgur.com/JcmHHhA.png" width="400"/>

</details>
<details>
  <summary>Mensajes</summary>

  <br>

  <img src="https://i.imgur.com/Cfc3X2d.png" width="450"/>
  <img src="https://i.imgur.com/A7bEeVd.png" width="450"/>

</details>
