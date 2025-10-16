# Oversaid

Plugin for CensorCraft that bans the most commonly said words with some unique punishments.

## How it works

When a word is said, it increments that word's counter. Once a word exceeds the *minimum repetition count*, it's banned.

### Punishment distribution

| Bad (common)                                                                         | Very Bad (uncommon)                                                 | Crushing (very rare)                                                                                               |
|--------------------------------------------------------------------------------------|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| Minor, non-threatening punishments. Poisoning, scrambling inventory, blindness, etc. | Can pose a threat. Summoning hostile mobs, forcing you to MLG, etc. | Deadly. Can be a guaranteed death. Summoning the Warden, trapping you in a pit, dropping Anvils on your head, etc. |

# Todo

- Stress test a fully separated client / server environment
- Tinker with balancing
- Implement a system to prevent the same words from remaining at the top (rotations?)
