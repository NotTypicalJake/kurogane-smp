package smp.kurogane;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KuroganePlugin extends JavaPlugin implements Listener {
    /** Cooldown seconds for ability slots 1,2,3 */
    public static final int[] CD = {20, 15, 120};

    public NamespacedKey swordKey;
    final Map<UUID, Race> races = new HashMap<>();
    final Map<UUID, Set<UUID>> trust = new HashMap<>();
    final Map<String, Long> cooldowns = new HashMap<>();
    final Map<UUID, Integer> tails = new HashMap<>(), parryCasts = new HashMap<>(), silentHits = new HashMap<>(),
            parryCharges = new HashMap<>(), crits = new HashMap<>();
    final Map<UUID, Long> flowUntil = new HashMap<>(), silentUntil = new HashMap<>(), stunned = new HashMap<>();
    final Set<UUID> trample = new HashSet<>(), awaken = new HashSet<>();
    final Map<UUID, UUID> minions = new HashMap<>();
    boolean abilityDamage;
    Abilities ab;
    File dataFile;

    static class AbilityHolder implements InventoryHolder { public Inventory getInventory() { return null; } }
    static class MarkHolder implements InventoryHolder { public Inventory getInventory() { return null; } }

    // ---------- helpers ----------
    public static Component txt(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
    void msg(Player p, String s) { p.sendMessage(txt(s)); }
    void bar(Player p, String s) { p.sendActionBar(txt(s)); }

    boolean trustsId(UUID a, UUID b) { return trust.getOrDefault(a, Set.of()).contains(b); }
    boolean trusts(Player a, Entity b) { return trustsId(a.getUniqueId(), b.getUniqueId()); }

    boolean isStunned(Entity e) {
        Long u = stunned.get(e.getUniqueId());
        return u != null && u > System.currentTimeMillis();
    }

    void stun(LivingEntity e, int ticks) {
        if (e instanceof Player p) {
            stunned.put(p.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
            bar(p, "&c&lSTUNNED");
        } else if (e instanceof Mob m) {
            m.setAI(false);
            Bukkit.getScheduler().runTaskLater(this, () -> { if (!m.isDead()) m.setAI(true); }, ticks);
        }
        e.getWorld().spawnParticle(Particle.CRIT, e.getEyeLocation().add(0, 0.4, 0), 15, 0.3, 0.2, 0.3, 0.1);
    }

    long left(Player p, String key) {
        long l = cooldowns.getOrDefault(p.getUniqueId() + ":" + key, 0L) - System.currentTimeMillis();
        return l > 0 ? l / 1000 + 1 : 0;
    }
    void setCd(Player p, String key, int sec) {
        cooldowns.put(p.getUniqueId() + ":" + key, System.currentTimeMillis() + sec * 1000L);
    }

    // ---------- lifecycle ----------
    @Override public void onEnable() {
        swordKey = new NamespacedKey(this, "sword");
        ab = new Abilities(this);
        dataFile = new File(getDataFolder(), "data.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        for (String c : new String[]{"ability", "trust", "kurogane"}) {
            getCommand(c).setExecutor(this);
            getCommand(c).setTabCompleter(this);
        }
    }
    @Override public void onDisable() { save(); }

    void load() {
        YamlConfiguration d = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection s = d.getConfigurationSection("races");
        if (s != null) for (String k : s.getKeys(false)) { Race r = Race.parse(s.getString(k)); if (r != null) races.put(UUID.fromString(k), r); }
        s = d.getConfigurationSection("trust");
        if (s != null) for (String k : s.getKeys(false)) {
            Set<UUID> set = new HashSet<>();
            for (String u : s.getStringList(k)) set.add(UUID.fromString(u));
            trust.put(UUID.fromString(k), set);
        }
        loadInts(d, "parry", parryCasts); loadInts(d, "silent", silentHits);
    }
    void loadInts(YamlConfiguration d, String path, Map<UUID, Integer> m) {
        ConfigurationSection s = d.getConfigurationSection(path);
        if (s != null) for (String k : s.getKeys(false)) m.put(UUID.fromString(k), s.getInt(k));
    }
    void save() {
        YamlConfiguration d = new YamlConfiguration();
        races.forEach((k, v) -> d.set("races." + k, v.name()));
        trust.forEach((k, v) -> d.set("trust." + k, v.stream().map(UUID::toString).toList()));
        parryCasts.forEach((k, v) -> d.set("parry." + k, v));
        silentHits.forEach((k, v) -> d.set("silent." + k, v));
        try { getDataFolder().mkdirs(); d.save(dataFile); } catch (IOException ex) { getLogger().warning(ex.getMessage()); }
    }

    // ---------- race handling ----------
    void setRace(Player p, Race r) {
        races.put(p.getUniqueId(), r);
        tails.remove(p.getUniqueId());
        save();
        p.showTitle(Title.title(txt(r.color + r.emoji + " " + r.display), txt("&7Your path has been chosen. Use &f/ability")));
        if (r == Race.SAMURAI) giveKatana(p);
        updateTails(p);
    }

    void giveKatana(Player p) {
        for (ItemStack it : p.getInventory().getContents())
            if (Sword.of(this, it) == Sword.STEEL_KATANA) return;
        p.getInventory().addItem(Sword.STEEL_KATANA.create(this));
    }

    void addTail(Player p) {
        tails.merge(p.getUniqueId(), 1, (a, b) -> Math.min(9, a + b));
        updateTails(p);
    }

    void updateTails(Player p) {
        if (races.get(p.getUniqueId()) != Race.KITSUNE) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            return;
        }
        int n = tails.getOrDefault(p.getUniqueId(), 0);
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("tails", Criteria.DUMMY, txt("&6🦊 Tails " + n + "/9"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < 9; i++)
            o.getScore((i < n ? "§6✦ Tail " : "§8✧ Tail ") + "§" + i).setScore(9 - i);
        p.setScoreboard(sb);
    }

    void cleanAttrs(Player p) {
        String[] keys = {"rampage_kb", "rampage_as", "flow_as", "awaken_hp", "flood_as"};
        for (Attribute a : new Attribute[]{Attribute.KNOCKBACK_RESISTANCE, Attribute.ATTACK_SPEED, Attribute.MAX_HEALTH}) {
            AttributeInstance ai = p.getAttribute(a);
            if (ai != null) for (String k : keys) ai.removeModifier(new NamespacedKey(this, k));
        }
    }

    // ---------- casting ----------
    public void cast(Player p, int slot) {
        Race r = races.get(p.getUniqueId());
        if (r == null) { msg(p, "&cYou have no race."); return; }
        if (isStunned(p)) { msg(p, "&cYou are stunned!"); return; }
        long l = left(p, "a" + slot);
        if (l > 0) { bar(p, "&c" + r.abilities[slot] + " on cooldown: " + l + "s"); return; }
        if (ab.useRace(p, r, slot)) {
            setCd(p, "a" + slot, CD[slot]);
            bar(p, r.color + "&l" + r.abilities[slot]);
        }
    }

    void openMenu(Player p) {
        Race r = races.get(p.getUniqueId());
        if (r == null) { msg(p, "&cYou have no race."); return; }
        Inventory inv = Bukkit.createInventory(new AbilityHolder(), 27, txt(r.color + r.emoji + " " + r.display));
        ItemStack fill = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = fill.getItemMeta(); fm.displayName(Component.text(" ")); fill.setItemMeta(fm);
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);
        int[] slots = {11, 13, 15};
        for (int i = 0; i < 3; i++) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setItemModel(new NamespacedKey("kurogane", r.icon(i)));
            m.displayName(txt(r.color + "&l" + r.abilities[i]));
            List<Component> lore = new ArrayList<>();
            lore.add(txt("&7Cooldown: &f" + CD[i] + "s"));
            if (i == 2) lore.add(txt("&7Requirement: &f" + r.req));
            lore.add(txt("&eClick to cast"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(slots[i], it);
        }
        p.openInventory(inv);
    }

    // ---------- commands ----------
    static String norm(String s) { return s.toLowerCase().replace("'", "").replace(" ", "_").replace("(passive)", "").replaceAll("_$", ""); }

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        switch (c.getName().toLowerCase()) {
            case "ability" -> {
                if (!(s instanceof Player p)) return true;
                if (a.length == 0) { openMenu(p); return true; }
                Race r = races.get(p.getUniqueId());
                if (r == null) { msg(p, "&cYou have no race."); return true; }
                int slot = -1;
                try { slot = Integer.parseInt(a[0]) - 1; } catch (NumberFormatException ex) {
                    for (int i = 0; i < 3; i++) if (norm(r.abilities[i]).equals(a[0].toLowerCase())) slot = i;
                }
                if (slot < 0 || slot > 2) { msg(p, "&cUsage: /ability [1|2|3|name]"); return true; }
                cast(p, slot);
            }
            case "trust" -> {
                if (!(s instanceof Player p)) return true;
                Set<UUID> set = trust.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
                String sub = a.length > 0 ? a[0].toLowerCase() : "list";
                if (sub.equals("list")) {
                    if (set.isEmpty()) { msg(p, "&7You trust nobody."); return true; }
                    msg(p, "&aTrusted: &f" + String.join(", ", set.stream().map(u -> Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse("?")).toList()));
                    return true;
                }
                if (a.length < 2 || !(sub.equals("add") || sub.equals("remove"))) { msg(p, "&cUsage: /trust <add|remove|list> [player]"); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) { msg(p, "&cUnknown player."); return true; }
                if (t.getUniqueId().equals(p.getUniqueId())) { msg(p, "&cYou can't trust yourself."); return true; }
                if (sub.equals("add")) {
                    set.add(t.getUniqueId()); msg(p, "&aYou now trust &f" + t.getName());
                    if (t.isOnline()) msg(t.getPlayer(), "&a" + p.getName() + " now trusts you.");
                } else { set.remove(t.getUniqueId()); msg(p, "&cYou no longer trust &f" + t.getName()); }
                save();
            }
            case "kurogane" -> {
                if (a.length == 0) { s.sendMessage("/kurogane race <player> <race> | reroll <player> | give <sword> [player]"); return true; }
                switch (a[0].toLowerCase()) {
                    case "race" -> {
                        Player t = a.length > 1 ? Bukkit.getPlayer(a[1]) : null; Race r = a.length > 2 ? Race.parse(a[2]) : null;
                        if (t == null || r == null) { s.sendMessage("Usage: /kurogane race <player> <race>"); return true; }
                        setRace(t, r);
                    }
                    case "reroll" -> {
                        Player t = a.length > 1 ? Bukkit.getPlayer(a[1]) : null;
                        if (t == null) { s.sendMessage("Usage: /kurogane reroll <player>"); return true; }
                        setRace(t, Race.values()[new Random().nextInt(Race.values().length)]);
                    }
                    case "give" -> {
                        Sword w = a.length > 1 ? Sword.parse(a[1]) : null;
                        Player t = a.length > 2 ? Bukkit.getPlayer(a[2]) : (s instanceof Player pp ? pp : null);
                        if (w == null || t == null) { s.sendMessage("Usage: /kurogane give <sword> [player]"); return true; }
                        t.getInventory().addItem(w.create(this));
                    }
                    default -> s.sendMessage("Unknown subcommand.");
                }
            }
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String n = c.getName().toLowerCase();
        if (n.equals("ability") && a.length == 1 && s instanceof Player p) {
            out.addAll(List.of("1", "2", "3"));
            Race r = races.get(p.getUniqueId());
            if (r != null) for (String x : r.abilities) out.add(norm(x));
        } else if (n.equals("trust")) {
            if (a.length == 1) out.addAll(List.of("add", "remove", "list"));
            else if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (n.equals("kurogane")) {
            if (a.length == 1) out.addAll(List.of("race", "reroll", "give"));
            else if (a.length == 2 && a[0].equals("give")) for (Sword w : Sword.values()) out.add(w.id());
            else if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            else if (a.length == 3 && a[0].equals("race")) for (Race r : Race.values()) out.add(r.name().toLowerCase());
        }
        String last = a.length > 0 ? a[a.length - 1].toLowerCase() : "";
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }

    // ---------- events ----------
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        cleanAttrs(p);
        if (!races.containsKey(p.getUniqueId())) setRace(p, Race.values()[new Random().nextInt(Race.values().length)]);
        else updateTails(p);
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (races.get(p.getUniqueId()) == Race.SAMURAI) giveKatana(p);
            if (awaken.contains(p.getUniqueId())) bar(p, "&4Your curse has awakened...");
        }, 2);
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        if (races.get(v.getUniqueId()) == Race.CURSED) awaken.add(v.getUniqueId());
        flowUntil.remove(v.getUniqueId()); parryCharges.remove(v.getUniqueId());
        Player k = v.getKiller();
        if (k != null && races.get(k.getUniqueId()) == Race.CURSED) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) head.getItemMeta();
            m.setOwningPlayer(v);
            m.displayName(txt("&4" + v.getName() + "'s Head"));
            head.setItemMeta(m);
            e.getDrops().add(head);
        }
    }

    @EventHandler public void onMinionDeath(EntityDeathEvent e) {
        if (minions.remove(e.getEntity().getUniqueId()) != null) { e.getDrops().clear(); e.setDroppedExp(0); }
    }

    @EventHandler public void onTarget(EntityTargetLivingEntityEvent e) {
        UUID owner = minions.get(e.getEntity().getUniqueId());
        if (owner != null && e.getTarget() != null) {
            UUID t = e.getTarget().getUniqueId();
            if (t.equals(owner) || trustsId(owner, t) || minions.get(t) != null && minions.get(t).equals(owner)) e.setCancelled(true);
        }
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!isStunned(e.getPlayer())) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getX() != t.getX() || f.getY() != t.getY() || f.getZ() != t.getZ()) {
            Location n = f.clone(); n.setYaw(t.getYaw()); n.setPitch(t.getPitch()); e.setTo(n);
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Sword s = Sword.of(this, e.getItem());
        if (s == null || isStunned(e.getPlayer())) return;
        ab.useSword(e.getPlayer(), s, e.getPlayer().isSneaking());
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AbilityHolder) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        int slot = switch (e.getRawSlot()) { case 11 -> 0; case 13 -> 1; case 15 -> 2; default -> -1; };
        if (slot >= 0) { p.closeInventory(); cast(p, slot); }
    }

    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MarkHolder && e.getPlayer() instanceof Player p)
            ab.processMark(p, e.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (abilityDamage || !(e.getEntity() instanceof LivingEntity v)) return;
        Entity d = e.getDamager();
        LivingEntity atk = d instanceof LivingEntity l ? l : (d instanceof Projectile pr && pr.getShooter() instanceof LivingEntity s ? s : null);
        Player att = atk instanceof Player pl ? pl : null;

        if (att != null) {
            if (isStunned(att) || trusts(att, v)) { e.setCancelled(true); return; }
        }
        // victim side: Samurai parry / flow
        if (v instanceof Player vp && races.get(vp.getUniqueId()) == Race.SAMURAI) {
            UUID id = vp.getUniqueId();
            if (flowUntil.getOrDefault(id, 0L) > System.currentTimeMillis()) {
                e.setCancelled(true); vp.getWorld().spawnParticle(Particle.ENCHANTED_HIT, vp.getLocation().add(0, 1, 0), 10, 0.4, 0.5, 0.4, 0.1); return;
            }
            int ch = parryCharges.getOrDefault(id, 0);
            if (ch > 0) {
                e.setCancelled(true);
                parryCharges.put(id, ch - 1);
                vp.getWorld().playSound(vp.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.4f);
                bar(vp, "&fParried! &7(" + (ch - 1) + " left)");
                if (atk != null) { ab.hurt(vp, atk, 2); stun(atk, 20); }
                return;
            }
        }
        if (att == null) return;
        UUID id = att.getUniqueId();
        Race r = races.get(id);
        double bonus = 0;
        if (trample.remove(id)) { stun(v, 80); bar(att, "&6Fox Trample!"); }
        if (silentUntil.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            int h = silentHits.merge(id, 1, Integer::sum);
            bar(att, "&8Silent hits: " + Math.min(h, 10) + "/10");
        }
        if (r == Race.SHOGUN && v.getHealth() < 12) { bonus += 4; v.getWorld().spawnParticle(Particle.CRIT, v.getEyeLocation(), 20, 0.3, 0.3, 0.3, 0.2); }
        if (flowUntil.getOrDefault(id, 0L) > System.currentTimeMillis()) bonus += 1;
        if (bonus > 0) e.setDamage(e.getDamage() + bonus);

        Sword sw = Sword.of(this, att.getInventory().getItemInMainHand());
        boolean crit = att.getFallDistance() > 0 && !att.isOnGround() && !att.isSprinting() && !att.isInWater()
                && !att.isClimbing() && !att.hasPotionEffect(PotionEffectType.BLINDNESS);
        if (crit && (sw == Sword.DEVILS_EDGE || sw == Sword.RAIJIN)) {
            int c = crits.merge(id, 1, Integer::sum);
            if (c >= 3) {
                crits.put(id, 0);
                if (sw == Sword.DEVILS_EDGE) v.addPotionEffect(new org.bukkit.potion.PotionEffect(PotionEffectType.WITHER, 100, 1));
                else v.getWorld().strikeLightning(v.getLocation());
            }
        }
    }
}
