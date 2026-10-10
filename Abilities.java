package smp.kurogane;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.title.Title;

import java.util.*;
import java.util.function.Consumer;

public class Abilities {
    private final KuroganePlugin pl;
    private final Random rnd = new Random();
    private static final PotionEffectType[] NEG = {PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
            PotionEffectType.POISON, PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE};

    Abilities(KuroganePlugin pl) { this.pl = pl; }

    // ================= helpers =================
    boolean valid(Player p, Entity e) {
        if (!(e instanceof LivingEntity) || e.equals(p) || e instanceof ArmorStand || e.isDead()) return false;
        if (e instanceof Player o) {
            if (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE) return false;
            if (pl.trusts(p, o)) return false;
        }
        UUID own = pl.minions.get(e.getUniqueId());
        return own == null || (!own.equals(p.getUniqueId()) && !pl.trustsId(p.getUniqueId(), own));
    }

    List<LivingEntity> near(Player p, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(r, r, r)) if (valid(p, e)) out.add((LivingEntity) e);
        return out;
    }

    LivingEntity target(Player p, double range) {
        Entity t = p.getTargetEntity((int) range);
        if (t instanceof LivingEntity le && valid(p, le)) return le;
        return near(p, range).stream().min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).orElse(null);
    }

    LivingEntity needTarget(Player p, double range) {
        LivingEntity t = target(p, range);
        if (t == null) pl.bar(p, "&cNo target in range");
        return t;
    }

    void hurt(Player src, LivingEntity t, double dmg) {
        pl.abilityDamage = true;
        try { t.setNoDamageTicks(0); t.damage(dmg, src); } finally { pl.abilityDamage = false; }
    }

    void eff(LivingEntity e, PotionEffectType t, int ticks, int amp) { e.addPotionEffect(new PotionEffect(t, ticks, amp)); }

    void attr(Player p, Attribute a, String key, double amt, AttributeModifier.Operation op, int ticks) {
        AttributeInstance ai = p.getAttribute(a);
        if (ai == null) return;
        NamespacedKey k = new NamespacedKey(pl, key);
        ai.removeModifier(k);
        ai.addModifier(new AttributeModifier(k, amt, op));
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            AttributeInstance x = p.getAttribute(a);
            if (x != null) x.removeModifier(k);
        }, ticks);
    }

    void later(int ticks, Runnable r) { Bukkit.getScheduler().runTaskLater(pl, r, ticks); }

    /** Generic projectile: moves a particle along a ray and calls onHit for entities touched. */
    void shoot(Player p, Vector dir, double speed, double range, Particle part, int count, Object data,
               double hitR, boolean pierce, Consumer<LivingEntity> onHit) {
        Location loc = p.getEyeLocation();
        Vector step = dir.clone().normalize().multiply(speed);
        Set<UUID> hit = new HashSet<>();
        new BukkitRunnable() {
            double d = 0;
            public void run() {
                loc.add(step); d += speed;
                loc.getWorld().spawnParticle(part, loc, count, hitR / 3, hitR / 3, hitR / 3, 0, data);
                for (Entity e : loc.getWorld().getNearbyEntities(loc, hitR, hitR, hitR)) {
                    if (valid(p, e) && hit.add(e.getUniqueId())) {
                        onHit.accept((LivingEntity) e);
                        if (!pierce) { cancel(); return; }
                    }
                }
                if (d >= range || loc.getBlock().getType().isSolid()) cancel();
            }
        }.runTaskTimer(pl, 0, 1);
    }

    Vector spread(Vector v, double deg) {
        return v.clone().rotateAroundY(Math.toRadians((rnd.nextDouble() - 0.5) * deg))
                .add(new Vector(0, (rnd.nextDouble() - 0.5) * deg / 200, 0));
    }

    // ================= dispatcher =================
    boolean useRace(Player p, Race r, int s) {
        return switch (r) {
            case ONI -> oni(p, s);
            case KITSUNE -> kitsune(p, s);
            case SAMURAI -> samurai(p, s);
            case CURSED -> cursed(p, s);
            case SHINOBI -> shinobi(p, s);
            case KAPPA -> kappa(p, s);
            case SHOGUN -> shogun(p, s);
        };
    }

    // ================= ONI =================
    boolean oni(Player p, int s) {
        World w = p.getWorld();
        switch (s) {
            case 0: {
                w.spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 3, 2, 0.2, 2);
                w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                for (LivingEntity e : near(p, 15)) {
                    hurt(p, e, 6);
                    Vector v = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (v.lengthSquared() == 0) v = new Vector(0, 0, 1);
                    e.setVelocity(v.normalize().multiply(1.6).setY(0.6));
                }
                return true;
            }
            case 1: {
                p.setVelocity(p.getLocation().getDirection().setY(0.1).normalize().multiply(2.2));
                Set<UUID> hit = new HashSet<>();
                new BukkitRunnable() {
                    int t = 0;
                    public void run() {
                        if (++t > 10 || !p.isOnline()) { cancel(); return; }
                        w.spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1);
                        for (LivingEntity e : near(p, 2.5)) if (hit.add(e.getUniqueId())) hurt(p, e, 5);
                    }
                }.runTaskTimer(pl, 0, 1);
                return true;
            }
            default: {
                if (p.getHealth() >= 10) { pl.bar(p, "&cYou must be below 5 hearts!"); return false; }
                eff(p, PotionEffectType.STRENGTH, 200, 2);
                eff(p, PotionEffectType.RESISTANCE, 200, 1);
                eff(p, PotionEffectType.HASTE, 200, 2);
                attr(p, Attribute.KNOCKBACK_RESISTANCE, "rampage_kb", 1.0, AttributeModifier.Operation.ADD_NUMBER, 200);
                attr(p, Attribute.ATTACK_SPEED, "rampage_as", 0.5, AttributeModifier.Operation.ADD_SCALAR, 200);
                w.spawnParticle(Particle.ANGRY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5);
                w.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.6f);
                return true;
            }
        }
    }

    // ================= KITSUNE =================
    boolean kitsune(Player p, int s) {
        switch (s) {
            case 0: {
                Vector dir = p.getEyeLocation().getDirection();
                for (double a : new double[]{-12, 0, 12})
                    shoot(p, dir.clone().rotateAroundY(Math.toRadians(a)), 1.2, 30, Particle.SOUL_FIRE_FLAME, 4, null, 1.2, false, e -> {
                        hurt(p, e, 4); e.setFireTicks(200); eff(e, PotionEffectType.BLINDNESS, 200, 0);
                    });
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.4f);
                pl.addTail(p);
                return true;
            }
            case 1: {
                pl.trample.add(p.getUniqueId());
                later(300, () -> pl.trample.remove(p.getUniqueId()));
                pl.msg(p, "&6Your next attack will stun!");
                pl.addTail(p);
                return true;
            }
            default: {
                if (pl.tails.getOrDefault(p.getUniqueId(), 0) < 9) { pl.bar(p, "&cYou need 9 tails!"); return false; }
                LivingEntity tgt = needTarget(p, 40);
                if (tgt == null) return false;
                for (int i = 0; i < 9; i++) {
                    Location l = p.getLocation().add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2);
                    Zombie z = p.getWorld().spawn(l, Zombie.class, zz -> {
                        zz.setBaby(false);
                        zz.setShouldBurnInDay(false);
                        zz.customName(KuroganePlugin.txt("&6" + p.getName() + "'s Clone"));
                        zz.setCustomNameVisible(true);
                        zz.setPersistent(false);
                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta m = (SkullMeta) head.getItemMeta(); m.setOwningPlayer(p); head.setItemMeta(m);
                        zz.getEquipment().setHelmet(head);
                        zz.getEquipment().setHelmetDropChance(0);
                        zz.setTarget(tgt);
                    });
                    pl.minions.put(z.getUniqueId(), p.getUniqueId());
                    later(300, () -> { pl.minions.remove(z.getUniqueId()); z.remove(); });
                }
                eff(p, PotionEffectType.SPEED, 300, 2);
                pl.tails.put(p.getUniqueId(), 0);
                pl.updateTails(p);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FOX_SCREECH, 2f, 0.6f);
                return true;
            }
        }
    }

    // ================= SAMURAI =================
    boolean samurai(Player p, int s) {
        UUID id = p.getUniqueId();
        switch (s) {
            case 0: {
                p.setVelocity(p.getLocation().getDirection().multiply(1.8).setY(0.15));
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.5f);
                later(4, () -> {
                    List<LivingEntity> n = near(p, 3.5);
                    n.stream().min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation()))).ifPresent(e -> {
                        hurt(p, e, 7);
                        e.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getEyeLocation(), 3, 0.3, 0.2, 0.3);
                    });
                });
                return true;
            }
            case 1: {
                pl.parryCharges.put(id, 5);
                pl.parryCasts.merge(id, 1, Integer::sum);
                pl.save();
                later(200, () -> pl.parryCharges.remove(id));
                pl.msg(p, "&fParry stance: 5 blocks. &7(Casts: " + pl.parryCasts.get(id) + "/5)");
                return true;
            }
            default: {
                if (pl.parryCasts.getOrDefault(id, 0) < 5) { pl.bar(p, "&cUse Perfect Parry 5 times first (" + pl.parryCasts.getOrDefault(id, 0) + "/5)"); return false; }
                pl.parryCasts.put(id, 0);
                pl.flowUntil.put(id, System.currentTimeMillis() + 10000);
                attr(p, Attribute.ATTACK_SPEED, "flow_as", -0.4, AttributeModifier.Operation.ADD_SCALAR, 200);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.5f);
                return true;
            }
        }
    }

    // ================= CURSED =================
    boolean cursed(Player p, int s) {
        switch (s) {
            case 0: {
                Inventory inv = Bukkit.createInventory(new KuroganePlugin.MarkHolder(), org.bukkit.event.inventory.InventoryType.HOPPER,
                        KuroganePlugin.txt("&4Insert a Head"));
                p.openInventory(inv);
                return false; // cooldown is applied once a head is used
            }
            case 1: {
                Vector dir = p.getEyeLocation().getDirection();
                for (int i = 0; i < 5; i++) later(i * 5, () -> shoot(p, p.getEyeLocation().getDirection(), 1.3, 40, Particle.FLAME, 8, null, 1.3, false, e -> {
                    hurt(p, e, 3); e.setFireTicks(100);
                    eff(e, NEG[rnd.nextInt(NEG.length)], 100, 0);
                    e.getWorld().spawnParticle(Particle.EXPLOSION, e.getLocation().add(0, 1, 0), 1);
                }));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1f, 0.8f);
                return true;
            }
            default: {
                if (!pl.awaken.remove(p.getUniqueId())) { pl.bar(p, "&cYou must die to reawaken first"); return false; }
                attr(p, Attribute.MAX_HEALTH, "awaken_hp", 40, AttributeModifier.Operation.ADD_NUMBER, 240);
                p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
                eff(p, PotionEffectType.STRENGTH, 240, 2);
                later(241, () -> { double mh = p.getAttribute(Attribute.MAX_HEALTH).getValue(); if (p.getHealth() > mh) p.setHealth(mh); });
                shoot(p, p.getEyeLocation().getDirection(), 0.9, 50, Particle.FLAME, 40, null, 1.5, false, e -> {
                    Location c = e.getLocation();
                    c.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, c, 2, 1, 1, 1);
                    c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
                    for (Entity x : c.getWorld().getNearbyEntities(c, 4, 4, 4)) if (valid(p, x)) hurt(p, (LivingEntity) x, 10);
                });
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
                return true;
            }
        }
    }

    void processMark(Player p, Inventory inv) {
        ItemStack head = null;
        for (ItemStack it : inv.getContents()) {
            if (it == null) continue;
            if (head == null && it.getType() == Material.PLAYER_HEAD) { head = it; continue; }
            giveBack(p, it);
        }
        if (head == null) { pl.bar(p, "&cNo head inserted"); return; }
        OfflinePlayer op = ((SkullMeta) head.getItemMeta()).getOwningPlayer();
        Player t = op == null ? null : Bukkit.getPlayer(op.getUniqueId());
        if (t == null || !t.getWorld().equals(p.getWorld())) {
            pl.msg(p, "&cThat player isn't reachable right now.");
            giveBack(p, head);
            return;
        }
        if (head.getAmount() > 1) { ItemStack rest = head.clone(); rest.setAmount(head.getAmount() - 1); giveBack(p, rest); }
        pl.setCd(p, "a0", KuroganePlugin.CD[0]);
        pl.msg(p, "&4You have marked &f" + t.getName() + "&4. Follow the blood.");
        t.showTitle(Title.title(KuroganePlugin.txt("&4&lMARKED"), KuroganePlugin.txt("&cA cursed one is hunting you")));
        t.playSound(t.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.8f);
        new BukkitRunnable() {
            int n = 0;
            public void run() {
                if (++n > 150 || !p.isOnline() || !t.isOnline() || !p.getWorld().equals(t.getWorld())) { cancel(); return; }
                Location a = p.getLocation().add(0, 0.2, 0);
                Vector v = t.getLocation().toVector().subtract(a.toVector());
                double len = Math.min(v.length(), 30);
                if (v.lengthSquared() == 0) return;
                v.normalize();
                for (double d = 1; d <= len; d += 0.7)
                    p.spawnParticle(Particle.DUST, a.clone().add(v.clone().multiply(d)), 1, 0.05, 0.05, 0.05, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.3f));
            }
        }.runTaskTimer(pl, 0, 4);
    }

    void giveBack(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
    }

    // ================= SHINOBI =================
    boolean shinobi(Player p, int s) {
        UUID id = p.getUniqueId();
        switch (s) {
            case 0: {
                for (int i = 0; i < 7; i++) later(i * 2, () -> shoot(p, spread(p.getEyeLocation().getDirection(), 10), 2.0, 40,
                        Particle.CRIT, 1, null, 0.9, false, e -> hurt(p, e, 1)));
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.8f);
                return true;
            }
            case 1: {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, false));
                pl.silentUntil.put(id, System.currentTimeMillis() + 8000);
                p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.02);
                return true;
            }
            default: {
                int hits = pl.silentHits.getOrDefault(id, 0);
                if (hits < 10) { pl.bar(p, "&cLand 10 hits in Silent Strikes (" + hits + "/10)"); return false; }
                LivingEntity t = needTarget(p, 60);
                if (t == null) return false;
                pl.silentHits.put(id, 0);
                eff(t, PotionEffectType.DARKNESS, 400, 0);
                new BukkitRunnable() {
                    int n = 0;
                    public void run() {
                        n += 5;
                        if (n > 400 || t.isDead() || !p.isOnline()) { p.removePotionEffect(PotionEffectType.INVISIBILITY); cancel(); return; }
                        if (p.getWorld().equals(t.getWorld()) && p.getLocation().distance(t.getLocation()) <= 5)
                            p.removePotionEffect(PotionEffectType.INVISIBILITY);
                        else p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20, 0, false, false));
                    }
                }.runTaskTimer(pl, 0, 5);
                return true;
            }
        }
    }

    // ================= KAPPA =================
    boolean kappa(Player p, int s) {
        World w = p.getWorld();
        switch (s) {
            case 0: {
                LivingEntity t = needTarget(p, 30);
                if (t == null) return false;
                Location c = t.getLocation().add(0, 1, 0);
                pl.stun(t, 100);
                new BukkitRunnable() {
                    int n = 0;
                    public void run() {
                        if (++n > 25 || t.isDead()) { cancel(); return; }
                        for (int lat = -60; lat <= 60; lat += 30)
                            for (int lon = 0; lon < 360; lon += 30) {
                                double la = Math.toRadians(lat), lo = Math.toRadians(lon);
                                w.spawnParticle(Particle.SPLASH, c.clone().add(2.5 * Math.cos(la) * Math.cos(lo), 2.5 * Math.sin(la), 2.5 * Math.cos(la) * Math.sin(lo)), 1, 0, 0, 0, 0);
                            }
                        if (n % 5 == 0) { hurt(p, t, 2); w.playSound(c, Sound.ENTITY_PLAYER_SPLASH, 1f, 1f); }
                    }
                }.runTaskTimer(pl, 0, 4);
                return true;
            }
            case 1: {
                Location o = p.getLocation().clone();
                Vector d = o.getDirection().setY(0);
                if (d.lengthSquared() == 0) return false;
                d.normalize();
                Vector right = new Vector(-d.getZ(), 0, d.getX());
                Set<UUID> hit = new HashSet<>();
                w.playSound(o, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 3f, 0.5f);
                new BukkitRunnable() {
                    int t = 0;
                    public void run() {
                        if (++t > 35 || !p.isOnline()) { cancel(); return; }
                        Location c = o.clone().add(d.clone().multiply(t * 1.2 + 2));
                        for (int i = 0; i < 80; i++)
                            w.spawnParticle(Particle.SPLASH, c.clone().add(right.clone().multiply((rnd.nextDouble() - 0.5) * 50)).add(0, rnd.nextDouble() * 25, 0), 1, 0, 0, 0, 0);
                        for (Entity e : w.getNearbyEntities(c, 30, 50, 30)) {
                            if (!valid(p, e) || hit.contains(e.getUniqueId())) continue;
                            Vector rel = e.getLocation().toVector().subtract(c.toVector());
                            if (Math.abs(rel.dot(right)) <= 25 && Math.abs(rel.dot(d)) <= 2.5 && rel.getY() >= -4 && rel.getY() <= 50) {
                                hit.add(e.getUniqueId());
                                hurt(p, (LivingEntity) e, 6);
                                e.setVelocity(d.clone().multiply(2).setY(0.6));
                            }
                        }
                    }
                }.runTaskTimer(pl, 0, 1);
                return true;
            }
            default: {
                eff(p, PotionEffectType.REGENERATION, 200, 9);
                eff(p, PotionEffectType.RESISTANCE, 200, 1);
                eff(p, PotionEffectType.HASTE, 200, 2);
                attr(p, Attribute.ATTACK_SPEED, "flood_as", 0.5, AttributeModifier.Operation.ADD_SCALAR, 200);
                w.spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 120, 1.5, 1, 1.5, 0.1);
                w.playSound(p.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 2f, 0.6f);
                return true;
            }
        }
    }

    // ================= SHOGUN =================
    boolean shogun(Player p, int s) {
        World w = p.getWorld();
        switch (s) {
            case 0: {
                LivingEntity t = needTarget(p, 30);
                if (t == null) return false;
                Vector fwd = p.getLocation().getDirection().setY(0).normalize();
                Vector right = new Vector(-fwd.getZ(), 0, fwd.getX());
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(200, 150, 60), 1.6f);
                new BukkitRunnable() {
                    int n = 0;
                    public void run() {
                        n++;
                        if (t.isDead()) { cancel(); return; }
                        if (n == 1) t.setVelocity(new Vector(0, 1.0, 0));
                        if (n <= 14) {
                            Location base = t.getLocation().add(0, 3, 0);
                            for (double a = -1; a <= 1; a += 0.5) for (double b = 0; b <= 1.5; b += 0.5)
                                w.spawnParticle(Particle.DUST, base.clone().add(right.clone().multiply(a)).add(0, b, 0), 1, 0, 0, 0, 0, dust);
                            for (double f = -1; f <= 1; f += 0.66) for (double b = 1.8; b <= 3; b += 0.4)
                                w.spawnParticle(Particle.DUST, base.clone().add(right.clone().multiply(f)).add(0, b, 0), 1, 0, 0, 0, 0, dust);
                        }
                        if (n == 14) t.setVelocity(new Vector(0, -2.5, 0));
                        if (n >= 16 && (t.isOnGround() || n >= 30)) {
                            hurt(p, t, 8);
                            w.spawnParticle(Particle.EXPLOSION_EMITTER, t.getLocation(), 2, 1, 0.2, 1);
                            w.playSound(t.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                            cancel();
                        }
                    }
                }.runTaskTimer(pl, 0, 1);
                return true;
            }
            case 1: {
                pl.msg(p, "&eExecution is passive: enemies under 6 hearts take extra damage from you.");
                return false;
            }
            default: {
                for (LivingEntity e : near(p, 10)) pl.stun(e, 100);
                for (Entity e : p.getNearbyEntities(10, 10, 10))
                    if (e instanceof Player a && pl.trusts(p, a)) {
                        eff(a, PotionEffectType.STRENGTH, 100, 0);
                        eff(a, PotionEffectType.RESISTANCE, 100, 0);
                    }
                eff(p, PotionEffectType.STRENGTH, 100, 0);
                eff(p, PotionEffectType.RESISTANCE, 100, 0);
                for (int i = 0; i < 72; i++) {
                    double a = Math.toRadians(i * 5);
                    w.spawnParticle(Particle.END_ROD, p.getLocation().add(10 * Math.cos(a), 0.3, 10 * Math.sin(a)), 1, 0, 0, 0, 0);
                }
                w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);
                return true;
            }
        }
    }

    // ================= SWORDS =================
    void useSword(Player p, Sword s, boolean sneak) {
        String key; int cd;
        switch (s) {
            case SAKURA -> { key = "sakura"; cd = 45; }
            case SOUL_REAPER -> { key = "soul"; cd = 120; }
            case SHADOW_PIERCER -> { key = "shadow"; cd = 90; }
            case LEVIATHAN -> { key = "levi"; cd = 60; }
            case IMPERIAL_BLADE -> { key = sneak ? "wrath" : "slash"; cd = sneak ? 120 : 90; }
            default -> { return; }
        }
        long l = pl.left(p, key);
        if (l > 0) { pl.bar(p, "&c" + s.display + " cooldown: " + l + "s"); return; }
        World w = p.getWorld();
        boolean ok = true;
        switch (s) {
            case SAKURA -> {
                LivingEntity t = needTarget(p, 30);
                if (t == null) { ok = false; break; }
                new BukkitRunnable() {
                    int n = 0;
                    public void run() {
                        if (++n > 60 || t.isDead()) { cancel(); return; }
                        for (int k = 0; k < 3; k++) {
                            double a = n * 0.5 + k * 2.1, h = (n % 20) * 0.1;
                            w.spawnParticle(Particle.CHERRY_LEAVES, t.getLocation().add(2 * Math.cos(a), h + 0.3, 2 * Math.sin(a)), 3, 0.1, 0.1, 0.1, 0);
                        }
                        if (n == 20) { hurt(p, t, 5); w.playSound(t.getLocation(), Sound.BLOCK_CHERRY_LEAVES_BREAK, 2f, 1f); }
                    }
                }.runTaskTimer(pl, 0, 1);
            }
            case SOUL_REAPER -> {
                LivingEntity t = needTarget(p, 40);
                if (t == null) { ok = false; break; }
                for (int i = 0; i < 3; i++) {
                    WitherSkeleton ws = w.spawn(p.getLocation().add(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1), WitherSkeleton.class, z -> {
                        z.setPersistent(false); z.setTarget(t);
                        z.customName(KuroganePlugin.txt("&8" + p.getName() + "'s Soul")); z.setCustomNameVisible(true);
                    });
                    pl.minions.put(ws.getUniqueId(), p.getUniqueId());
                    later(400, () -> { pl.minions.remove(ws.getUniqueId()); ws.remove(); });
                }
                w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1.5f);
            }
            case SHADOW_PIERCER -> {
                Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(60, 0, 90), 1.5f);
                for (int i = 0; i < 5; i++) later(i * 3, () -> shoot(p, spread(p.getEyeLocation().getDirection(), 8), 1.4, 40, Particle.DUST, 6, dark, 1.0, false, e -> {
                    hurt(p, e, 1); eff(e, PotionEffectType.DARKNESS, 100, 0);
                }));
            }
            case LEVIATHAN -> {
                LivingEntity t = needTarget(p, 30);
                if (t == null) { ok = false; break; }
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2f, 0.7f);
                new BukkitRunnable() {
                    int n = 0;
                    public void run() {
                        if (++n > 15 || t.isDead() || !t.getWorld().equals(p.getWorld())) { cancel(); return; }
                        Vector v = p.getLocation().toVector().subtract(t.getLocation().toVector());
                        if (v.length() < 2) { cancel(); return; }
                        t.setVelocity(v.normalize().multiply(1.0).setY(0.2));
                        w.spawnParticle(Particle.SPLASH, t.getLocation().add(0, 1, 0), 30, 1, 0.5, 1, 0.1);
                    }
                }.runTaskTimer(pl, 0, 1);
            }
            case IMPERIAL_BLADE -> {
                if (!sneak) {
                    Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 2.5f);
                    shoot(p, p.getEyeLocation().getDirection(), 1.5, 40, Particle.DUST, 25, gold, 2.5, true, e -> hurt(p, e, 8));
                    w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.6f);
                } else {
                    LivingEntity t = needTarget(p, 40);
                    if (t == null) { ok = false; break; }
                    for (int i = 0; i < 6; i++) later(i * 8, () -> {
                        if (t.isDead()) return;
                        Location l2 = t.getLocation().add(rnd.nextDouble() * 8 - 4, 0, rnd.nextDouble() * 8 - 4);
                        w.strikeLightning(l2);
                    });
                }
            }
            default -> ok = false;
        }
        if (ok) { pl.setCd(p, key, cd); pl.bar(p, "&6&l" + s.display); }
    }
}
