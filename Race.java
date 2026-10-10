package smp.kurogane;

public enum Race {
    ONI("👹", "Oni", "&c", "Below 5 hearts", "Demon Smash", "Demon Rush", "Oni Rampage"),
    KITSUNE("🦊", "Kitsune", "&6", "Gain 9 tails", "Flaming Tails", "Fox Trample", "Nine Tailed Fox"),
    SAMURAI("⚔", "Samurai", "&f", "Use Perfect Parry 5 times", "Iaijutsu", "Perfect Parry", "Samurai's Flow"),
    CURSED("🩸", "Cursed", "&4", "Reawaken after death", "Cursed Mark", "Cursed Flames", "Awakened Curse"),
    SHINOBI("🥷", "Shinobi", "&8", "Land 10 hits in Silent Strikes", "Kunai Barrage", "Silent Strikes", "Shadow Killer"),
    KAPPA("🐸", "Kappa", "&b", "None", "Water Prison", "Tsunami", "Flood"),
    SHOGUN("👑", "Shogun", "&e", "None", "Commander's Touch", "Execution (passive)", "Divine Command");

    public final String emoji, display, color, req;
    public final String[] abilities;

    Race(String emoji, String display, String color, String req, String... abilities) {
        this.emoji = emoji; this.display = display; this.color = color; this.req = req; this.abilities = abilities;
    }

    /** Texture id used by the resource pack, e.g. oni_1 */
    public String icon(int slot) { return name().toLowerCase() + "_" + (slot + 1); }

    public static Race parse(String s) {
        for (Race r : values()) if (r.name().equalsIgnoreCase(s)) return r;
        return null;
    }
}
