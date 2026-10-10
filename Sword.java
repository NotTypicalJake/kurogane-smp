package smp.kurogane;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.util.List;

public enum Sword {
    DEVILS_EDGE("Devil's Edge", Material.NETHERITE_SWORD, "&4", "Every 3 crits inflicts Wither"),
    SAKURA("Sakura", Material.IRON_SWORD, "&d", "Right-click: Petal Dance (45s)"),
    RAIJIN("Raijin", Material.GOLDEN_SWORD, "&e", "Every 3 crits strikes lightning"),
    SOUL_REAPER("Soul Reaper", Material.NETHERITE_HOE, "&8", "Right-click: Undead Call (120s)"),
    SHADOW_PIERCER("Shadow Piercer", Material.NETHERITE_SWORD, "&5", "Right-click: Dark Energy (90s)"),
    LEVIATHAN("Leviathan", Material.DIAMOND_SWORD, "&3", "Right-click: Deep Sea Grasp (60s)"),
    IMPERIAL_BLADE("Imperial Blade", Material.GOLDEN_SWORD, "&6", "Right-click: Imperial Slash (90s)", "Sneak + Right-click: Heaven's Wrath (120s)"),
    STEEL_KATANA("Steel Katana", Material.IRON_SWORD, "&7", "A plain steel katana");

    public final String display, color;
    public final Material base;
    public final String[] lore;

    Sword(String display, Material base, String color, String... lore) {
        this.display = display; this.base = base; this.color = color; this.lore = lore;
    }

    public String id() { return name().toLowerCase(); }

    public static Sword parse(String s) {
        for (Sword w : values()) if (w.id().equalsIgnoreCase(s)) return w;
        return null;
    }

    public ItemStack create(KuroganePlugin pl) {
        ItemStack it = new ItemStack(base);
        ItemMeta m = it.getItemMeta();
        m.displayName(KuroganePlugin.txt(color + display));
        m.lore(java.util.Arrays.stream(lore).map(l -> (Component) KuroganePlugin.txt("&7" + l)).toList());
        m.setUnbreakable(true);
        m.setItemModel(new NamespacedKey("kurogane", id()));
        m.getPersistentDataContainer().set(pl.swordKey, PersistentDataType.STRING, id());
        it.setItemMeta(m);
        return it;
    }

    public static Sword of(KuroganePlugin pl, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String v = it.getItemMeta().getPersistentDataContainer().get(pl.swordKey, PersistentDataType.STRING);
        return v == null ? null : parse(v);
    }
}
