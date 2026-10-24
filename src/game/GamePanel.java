package game;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements MouseListener, MouseMotionListener, KeyListener {
    static final int HUD_H = 96, WALL_H = 56, HOUSE_H = 74, TOWN_WALL_H = 40, MAP_SIZE = 72;
    static final int HW = Iso.TW / 2, HH = Iso.TH / 2;
    static final double FIREBALL_COST = 7, NOVA_COST = 16;

    private static final Color[] FLOOR_COLORS = {
            new Color(92, 84, 74), new Color(86, 79, 70), new Color(98, 89, 77), new Color(82, 76, 68)};
    private static final Color[] GRASS_COLORS = {
            new Color(70, 96, 48), new Color(64, 90, 44), new Color(76, 102, 52), new Color(60, 86, 42)};
    private static final Color[] ROAD_COLORS = {
            new Color(128, 118, 100), new Color(120, 111, 95), new Color(134, 124, 106), new Color(115, 106, 90)};
    private static final Color FLOOR_EDGE = new Color(58, 52, 46);
    private static final Color ROAD_EDGE = new Color(92, 84, 70);
    private static final Color GRASS_TUFT = new Color(95, 128, 62);
    // top, left face, right face, mortar
    private static final Color[] DUNGEON_WALL = {new Color(122, 110, 94), new Color(98, 86, 72), new Color(72, 62, 52), new Color(50, 43, 36)};
    private static final Color[] TOWN_WALL = {new Color(150, 148, 140), new Color(120, 118, 110), new Color(92, 90, 84), new Color(70, 68, 62)};
    private static final Color[] HOUSE_WALL = {new Color(140, 62, 45), new Color(206, 190, 155), new Color(165, 150, 120), new Color(80, 55, 35)};
    private static final Color[] STAIR_COLORS = {
            new Color(64, 54, 44), new Color(48, 40, 33), new Color(34, 28, 23), new Color(20, 16, 13)};
    private static final Color[] STAIR_UP_COLORS = {
            new Color(96, 88, 78), new Color(112, 104, 92), new Color(128, 120, 106), new Color(146, 138, 122)};
    private static final Color[] TREE_COLORS = {new Color(38, 72, 32), new Color(52, 98, 42), new Color(72, 124, 54)};
    private static final Color PANEL_BG = new Color(18, 14, 12, 238);
    private static final Color PANEL_BORDER = new Color(125, 100, 62);
    private static final Color TEXT = new Color(215, 200, 170);
    private static final Color GOLD_TEXT = new Color(255, 215, 0);
    private static final Color FIRE = new Color(255, 140, 30);
    private static final Item HP_ICON = Loot.potion(true), MP_ICON = Loot.potion(false), TP_ICON = Loot.scroll();

    private final Font titleFont = new Font("Serif", Font.BOLD, 46);
    private final Font bigTitleFont = new Font("Serif", Font.BOLD, 64);
    private final Font bannerFont = new Font("Serif", Font.BOLD, 30);
    private final Font uiFont = new Font("Serif", Font.BOLD, 15);
    private final Font smallFont = new Font("SansSerif", Font.PLAIN, 12);
    private final Font labelFont = new Font("SansSerif", Font.BOLD, 12);
    private final Font dmgFont = new Font("SansSerif", Font.BOLD, 15);
    private final Font critFont = new Font("SansSerif", Font.BOLD, 21);

    private enum Screen { MENU, PLAYING }

    private record Button(Rectangle r, boolean enabled, Runnable action) {}

    private final Random rnd = new Random();
    private final Audio audio = new Audio();
    private double alertCooldown;
    private Screen screen = Screen.MENU;
    private Player player;
    private Level level, town, portalLevel;
    // shortcuts into the current level
    private Dungeon dungeon;
    private List<Monster> monsters;
    private List<ItemDrop> drops;
    private List<Decal> decals;
    private List<Npc> npcs;
    private int depth, maxDepth;

    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<FloatingText> texts = new ArrayList<>();
    private final List<Entity> drawList = new ArrayList<>();
    private final List<Creature> creatures = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    private int vw = 1280, vh = 720;
    private double camX, camY, time;
    private int mouseX = 640, mouseY = 300;
    private boolean leftDown, rightDown, holdMove;
    private Monster hovered;
    private ItemDrop hoveredDrop;
    private Npc hoveredNpc, shopNpc;
    private Portal hoveredPortal;
    private boolean showInventory, showMap, showHelp, paused, showWaypoints, saveExists;
    private Rectangle waypointRect;
    private double deathTimer, bannerTime, noticeTime;
    private String banner = "", notice = "";
    private Color bannerColor = Color.WHITE;
    private long lastNanos;
    static final int PX = 2; // screen pixels per retro pixel
    private BufferedImage vignette, canvas;
    private final int[] px = new int[4], py = new int[4];

    public GamePanel() {
        setPreferredSize(new Dimension(vw, vh));
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false); // let Tab reach us
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        saveExists = SaveGame.exists();
        newGame(); // gives the main menu a town to show behind it
        screen = Screen.MENU;
        bannerTime = 0;
    }

    public void start() {
        audio.start();
        audio.setMusic(Audio.Music.TOWN);
        requestFocusInWindow();
        lastNanos = System.nanoTime();
        new Timer(15, e -> tick()).start();
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!paused) update(dt);
        repaint();
    }

    // ------------------------------------------------------------------ game flow

    private void newGame() {
        player = new Player();
        player.equipment.put(Item.Slot.WEAPON, Loot.starterWeapon());
        player.equipment.put(Item.Slot.ARMOR, Loot.starterArmor());
        player.hp = player.maxHp();
        player.mana = player.maxMana();
        maxDepth = 0;
        startInTown();
        screen = Screen.PLAYING;
        showHelp = true;
    }

    private void startInTown() {
        portalLevel = null;
        level = null; // a fresh start, not a return from the dungeon (no restock/autosave)
        town = createTown();
        deathTimer = 0;
        enterTown(town.dungeon.startX + 0.5, town.dungeon.startY + 0.5);
    }

    private Level createTown() {
        Dungeon d = Dungeon.town(rnd);
        Level t = new Level(d, 0);
        t.npcs.add(new Npc(Npc.Role.SMITH, "Torvald", "the Blacksmith", d.smithX + 0.5, d.smithY - 0.4));
        t.npcs.add(new Npc(Npc.Role.HEALER, "Maren", "the Healer", d.healerX + 0.5, d.healerY - 0.4));
        restock(t);
        return t;
    }

    private void restock(Level t) {
        for (Npc n : t.npcs) {
            n.stock.clear();
            if (n.role == Npc.Role.SMITH) n.stock.addAll(Loot.smithStock(maxDepth, rnd));
            else n.stock.addAll(List.of(Loot.potion(true), Loot.potion(false), Loot.scroll()));
        }
    }

    private void enterLevel(Level l, double x, double y) {
        level = l;
        dungeon = l.dungeon;
        monsters = l.monsters;
        drops = l.drops;
        decals = l.decals;
        npcs = l.npcs;
        depth = l.depth;
        projectiles.clear();
        particles.clear();
        texts.clear();
        player.x = x;
        player.y = y;
        player.stop();
        player.swing = 0;
        player.attackTarget = null;
        player.pickupTarget = null;
        player.useTarget = null;
        holdMove = false;
        shopNpc = null;
        hovered = null;
        hoveredDrop = null;
        hoveredNpc = null;
        hoveredPortal = null;
        showWaypoints = false;
        audio.setMusic(dungeon.town ? Audio.Music.TOWN : Audio.Music.DUNGEON);
        updateLighting();
        updateCamera();
    }

    /** Plays a sound at a world position, quieter with distance and panned left/right. */
    private void sfx(Audio.Sfx s, double x, double y, double vol) {
        double d = Math.hypot(x - player.x, y - player.y);
        double pan = (Iso.sx(x, y) - Iso.sx(player.x, player.y)) / 500.0;
        audio.play(s, vol * Math.max(0, 1 - d / 16), pan);
    }

    private void sfx(Audio.Sfx s) { audio.play(s); }

    private void enterTown(double x, double y) {
        boolean fromDungeon = level != null && level != town;
        if (fromDungeon) restock(town);
        enterLevel(town, x, y);
        showBanner("Tristfall Village", new Color(210, 190, 140));
        if (fromDungeon) saveGame(false);
    }

    private void goToDepth(int d) {
        closePortal();
        boolean newWaypoint = d % 3 == 1 && d > maxDepth && d > 1;
        maxDepth = Math.max(maxDepth, d);
        Level l = new Level(new Dungeon(MAP_SIZE, MAP_SIZE, rnd), d);
        sfx(Audio.Sfx.STAIRS);
        enterLevel(l, l.dungeon.startX + 1.5, l.dungeon.startY + 0.5);
        spawnMonsters();
        showBanner("Dungeon Level " + d, new Color(210, 175, 110));
        if (newWaypoint) notice("Waypoint discovered: you can now travel here from town");
    }

    private void closePortal() {
        if (portalLevel != null) portalLevel.portal = null;
        portalLevel = null;
        if (town != null) town.portal = null;
    }

    private void respawn() {
        player.dead = false;
        player.hp = player.maxHp();
        player.mana = player.maxMana();
        player.gold /= 2;
        closePortal();
        enterTown(town.dungeon.startX + 0.5, town.dungeon.startY + 0.5); // also autosaves
    }

    private void saveGame(boolean announce) {
        try {
            SaveGame.save(player, maxDepth);
            saveExists = true;
            if (announce) notice("Game saved");
        } catch (IOException e) {
            notice("Save failed: " + e.getMessage());
        }
    }

    /** Called when the window closes. */
    public void autoSave() {
        if (screen == Screen.PLAYING && !player.dead) saveGame(false);
    }

    private void loadGame() {
        try {
            SaveGame.Loaded l = SaveGame.load();
            player = l.player();
            maxDepth = l.maxDepth();
            level = null;
            startInTown();
            screen = Screen.PLAYING;
            paused = false;
            showInventory = false;
            notice("Welcome back, level " + player.level + " hero");
        } catch (Exception e) {
            notice("Could not load save: " + e.getMessage());
        }
    }

    private void spawnMonsters() {
        List<int[]> rooms = dungeon.rooms;
        for (int k = 1; k < rooms.size(); k++) {
            int[] r = rooms.get(k);
            if (rnd.nextDouble() < 0.15) continue;
            Monster.Type type = randomType();
            boolean champ = rnd.nextDouble() < 0.1 + depth * 0.01;
            int count = champ ? 2 + rnd.nextInt(2) : 2 + rnd.nextInt(3 + Math.min(4, depth / 2));
            if (type == Monster.Type.FALLEN) count += 2;
            for (int n = 0; n < count; n++) {
                for (int tries = 0; tries < 20; tries++) {
                    int x = r[0] + rnd.nextInt(r[2]), y = r[1] + rnd.nextInt(r[3]);
                    if (dungeon.tiles[x][y] == Dungeon.FLOOR) {
                        monsters.add(new Monster(type, depth, champ, x + 0.5, y + 0.5));
                        break;
                    }
                }
            }
        }
        if (depth % 3 == 0) {
            int[] r = dungeon.stairsRoom;
            for (int tries = 0; tries < 50; tries++) {
                int x = r[0] + rnd.nextInt(r[2]), y = r[1] + rnd.nextInt(r[3]);
                if (dungeon.tiles[x][y] == Dungeon.FLOOR) {
                    monsters.add(new Monster(Monster.Type.BUTCHER, depth, false, x + 0.5, y + 0.5));
                    break;
                }
            }
        }
    }

    private Monster.Type randomType() {
        List<Monster.Type> pool = new ArrayList<>(List.of(Monster.Type.ZOMBIE, Monster.Type.FALLEN));
        if (depth >= 2) pool.add(Monster.Type.SKELETON);
        if (depth >= 3) pool.add(Monster.Type.ARCHER);
        if (depth >= 4) pool.add(Monster.Type.SKELETON);
        return pool.get(rnd.nextInt(pool.size()));
    }

    /** A standable spot about 1-2.5 tiles from (x, y). */
    private double[] spotNear(double x, double y) {
        for (double r = 1.0; r <= 2.5; r += 0.5) {
            for (int k = 0; k < 12; k++) {
                double a = Math.PI / 4 + k * Math.PI / 6, nx = x + Math.cos(a) * r, ny = y + Math.sin(a) * r;
                if (player.canStand(dungeon, nx, ny)) return new double[]{nx, ny};
            }
        }
        return new double[]{x, y};
    }

    // ------------------------------------------------------------------ update

    void update(double dt) {
        time += dt;
        if (getWidth() > 0) {
            vw = getWidth();
            vh = getHeight();
        }
        bannerTime -= dt;
        noticeTime -= dt;
        alertCooldown -= dt;
        for (Npc n : npcs) n.animTime += dt;
        if (screen == Screen.MENU) {
            updateLighting();
            updateCamera();
            return;
        }
        if (player.dead) {
            deathTimer += dt;
            updateEffects(dt);
            return;
        }
        updateCamera();
        updateHover();
        updatePlayer(dt);
        for (Monster m : monsters) updateMonster(m, dt);
        for (Npc n : npcs) {
            if (n.distTo(player) < 4) n.facing = Math.atan2(player.y - n.y, player.x - n.x);
        }
        separate();
        updateProjectiles(dt);
        updateEffects(dt);
        monsters.removeIf(m -> m.dead);
        updateLighting();
        updateCamera();
        if (!player.dead) checkTile();
    }

    private void checkTile() {
        int t = dungeon.tiles[(int) player.x][(int) player.y];
        if (dungeon.town) {
            showWaypoints = t == Dungeon.STAIRS;
        } else if (t == Dungeon.STAIRS) {
            goToDepth(depth + 1);
        } else if (t == Dungeon.STAIRS_UP) {
            sfx(Audio.Sfx.STAIRS);
            closePortal();
            enterTown(town.dungeon.entranceX + 0.5, town.dungeon.entranceY - 1.5);
        }
    }

    private void updateCamera() {
        camX = Math.round(Iso.sx(player.x, player.y) - vw / 2.0);
        camY = Math.round(Iso.sy(player.x, player.y) - (vh - HUD_H) / 2.0 - 20);
    }

    private double mouseWX() { return Iso.wx(mouseX + camX, mouseY + camY); }

    private double mouseWY() { return Iso.wy(mouseX + camX, mouseY + camY); }

    private float lightAt(double x, double y) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        if (i < 0 || j < 0 || i >= dungeon.w || j >= dungeon.h) return 0;
        return dungeon.light[i][j];
    }

    private boolean overPanel() {
        return (showInventory && inventoryRect().contains(mouseX, mouseY))
                || (shopNpc != null && shopRect().contains(mouseX, mouseY))
                || (showWaypoints && waypointRect != null && waypointRect.contains(mouseX, mouseY))
                || mouseY > vh - HUD_H;
    }

    private void updateHover() {
        hovered = null;
        hoveredDrop = null;
        hoveredNpc = null;
        hoveredPortal = null;
        if (overPanel()) return;
        for (ItemDrop d : drops) {
            if (d.labelRect != null && d.labelRect.contains(mouseX, mouseY)) {
                hoveredDrop = d;
                return;
            }
        }
        if (level.portal != null) {
            double sx = Iso.sx(level.portal.x, level.portal.y) - camX, sy = Iso.sy(level.portal.x, level.portal.y) - camY;
            if (Math.abs(mouseX - sx) < 20 && mouseY > sy - 74 && mouseY < sy + 8) {
                hoveredPortal = level.portal;
                return;
            }
        }
        for (Npc n : npcs) {
            double sx = Iso.sx(n.x, n.y) - camX, sy = Iso.sy(n.x, n.y) - camY;
            if (Math.abs(mouseX - sx) < 16 && mouseY > sy - 54 && mouseY < sy + 8) {
                hoveredNpc = n;
                return;
            }
        }
        double best = -1e9;
        for (Monster m : monsters) {
            if (m.dead || lightAt(m.x, m.y) < 0.05) continue;
            double sx = Iso.sx(m.x, m.y) - camX, sy = Iso.sy(m.x, m.y) - camY;
            int[] sz = PixelSprites.size(m);
            if (Math.abs(mouseX - sx) <= sz[0] / 2.0 + 4 && mouseY >= sy - sz[1] - 4 && mouseY <= sy + 8) {
                if (m.depth() > best) {
                    best = m.depth();
                    hovered = m;
                }
            }
        }
    }

    private void updatePlayer(double dt) {
        Player p = player;
        p.animTime += dt;
        p.moving = false;
        p.hitFlash = Math.max(0, p.hitFlash - dt);
        p.cooldown -= dt;
        p.castCooldown -= dt;
        p.novaCooldown -= dt;
        p.potionCooldown -= dt;
        p.mana = Math.min(p.maxMana(), p.mana + dt * (1.5 + 0.15 * p.level));
        p.hp = Math.min(p.maxHp(), p.hp + dt * (0.4 + 0.05 * p.level));
        if (shopNpc != null && p.distTo(shopNpc) > 2.5) shopNpc = null;

        if (rightDown && !overPanel()) castFireball();

        if (leftDown && holdMove) {
            p.repathTimer -= dt;
            if (p.repathTimer <= 0) {
                moveTo(mouseWX(), mouseWY());
                p.repathTimer = 0.15;
            }
        }

        if (p.swing > 0) {
            p.swing -= dt;
            if (p.hitPending && p.swing <= p.swingTime * 0.45) {
                p.hitPending = false;
                Monster t = p.attackTarget;
                if (t != null && !t.dead && p.distTo(t) <= playerReach(t) + 0.4) meleeHit(t);
                if (!leftDown) p.attackTarget = null;
            }
            return;
        }

        Monster t = p.attackTarget;
        if (t != null) {
            if (t.dead) {
                p.attackTarget = null;
            } else if (p.distTo(t) <= playerReach(t)) {
                p.stop();
                p.facing = Math.atan2(t.y - p.y, t.x - p.x);
                if (p.cooldown <= 0) {
                    p.swing = p.swingTime;
                    p.hitPending = true;
                    p.cooldown = p.swingTime + 0.08;
                    sfx(Audio.Sfx.SWING);
                }
                return;
            } else {
                p.repathTimer -= dt;
                if (p.repathTimer <= 0 || !p.hasPath()) {
                    pathTo(p, t.x, t.y, 6000);
                    p.repathTimer = 0.25;
                }
            }
        }

        ItemDrop d = p.pickupTarget;
        if (d != null) {
            if (!drops.contains(d)) {
                p.pickupTarget = null;
            } else if (Math.hypot(d.x - p.x, d.y - p.y) < 0.9) {
                pickup(d);
                p.pickupTarget = null;
                p.stop();
            }
        }

        Entity u = p.useTarget;
        if (u != null && Math.hypot(u.x - p.x, u.y - p.y) < (u instanceof Npc ? 1.4 : 0.8)) {
            p.useTarget = null;
            p.stop();
            if (u instanceof Npc n) openShop(n);
            else if (u instanceof Portal pt) usePortal(pt);
            return;
        }

        p.followPath(dt, dungeon, 1);

        for (int i = drops.size() - 1; i >= 0; i--) {
            ItemDrop dr = drops.get(i);
            if (!dr.item.slot.equippable() && dr.bounce <= 0 && Math.hypot(dr.x - p.x, dr.y - p.y) < 0.6) pickup(dr);
        }
    }

    private double playerReach(Monster m) { return 0.75 + m.radius + player.radius * 0.5; }

    private void moveTo(double wx, double wy) {
        int tx = (int) Math.floor(wx), ty = (int) Math.floor(wy);
        if (!dungeon.walkable(tx, ty)) {
            double best = Double.MAX_VALUE;
            int bx = -1, by = -1;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    int x = tx + dx, y = ty + dy;
                    if (!dungeon.walkable(x, y)) continue;
                    double d = Math.hypot(x + 0.5 - wx, y + 0.5 - wy);
                    if (d < best) {
                        best = d;
                        bx = x;
                        by = y;
                    }
                }
            }
            if (bx < 0) return;
            pathTo(player, bx + 0.5, by + 0.5, 6000);
        } else {
            pathTo(player, wx, wy, 6000);
        }
    }

    private boolean pathTo(Creature c, double x, double y, int maxNodes) {
        List<double[]> path = Pathfinder.find(dungeon, (int) c.x, (int) c.y, (int) Math.floor(x), (int) Math.floor(y), maxNodes);
        if (path == null) return false;
        if (path.isEmpty()) path.add(new double[]{x, y});
        else path.set(path.size() - 1, new double[]{x, y});
        c.setPath(path);
        return true;
    }

    private void updateMonster(Monster m, double dt) {
        if (m.dead) return;
        m.animTime += dt;
        m.moving = false;
        m.hitFlash = Math.max(0, m.hitFlash - dt);
        m.slowTimer = Math.max(0, m.slowTimer - dt);
        double sm = m.slowTimer > 0 ? 0.45 : 1;
        m.cooldown -= dt * sm;
        double dx = player.x - m.x, dy = player.y - m.y, dist = Math.hypot(dx, dy);

        if (!m.aggro) {
            if (dist < 8.5 && dungeon.lineOfSight(m.x, m.y, player.x, player.y)) alert(m);
        } else if (dist > 20) {
            m.aggro = false;
        }

        if (m.swing > 0) {
            m.swing -= dt * sm;
            if (m.hitPending && m.swing <= m.swingTime * 0.4) {
                m.hitPending = false;
                if (m.type.ranged) shootArrow(m);
                else if (dist <= m.reach(player) + 0.5) damagePlayer(roll(m.minDmg, m.maxDmg));
            }
            return;
        }

        if (m.aggro) {
            double reach = m.reach(player);
            if (dist <= reach && (!m.type.ranged || dungeon.lineOfSight(m.x, m.y, player.x, player.y))) {
                m.stop();
                m.facing = Math.atan2(dy, dx);
                if (m.cooldown <= 0) {
                    m.swing = m.swingTime;
                    m.hitPending = true;
                    m.cooldown = m.type.attackCd;
                    if (!m.type.ranged) sfx(Audio.Sfx.SWING, m.x, m.y, 0.45);
                }
                return;
            }
            if (!m.type.ranged && dist < 3 && dungeon.lineOfSight(m.x, m.y, player.x, player.y)) {
                m.stop();
                m.moveDirect(player.x, player.y, dt, dungeon, sm);
                return;
            }
            m.repathTimer -= dt;
            if (m.repathTimer <= 0 || !m.hasPath()) {
                m.repathTimer = 0.4 + rnd.nextDouble() * 0.4;
                pathTo(m, player.x, player.y, 1500);
            }
            m.followPath(dt, dungeon, sm);
        } else {
            m.wanderTimer -= dt;
            if (m.wanderTimer <= 0) {
                m.wanderTimer = 2 + rnd.nextDouble() * 4;
                int tx = (int) m.x + rnd.nextInt(5) - 2, ty = (int) m.y + rnd.nextInt(5) - 2;
                if (dungeon.walkable(tx, ty) && rnd.nextBoolean()) pathTo(m, tx + 0.5, ty + 0.5, 200);
            }
            m.followPath(dt, dungeon, sm * 0.4);
        }
    }

    /** Wakes a monster and its nearby pack. */
    private void alert(Monster m) {
        m.aggro = true;
        for (Monster o : monsters) if (!o.aggro && o.distTo(m) < 5) o.aggro = true;
        if (alertCooldown <= 0 && !m.skeletal()) {
            alertCooldown = 1.2;
            sfx(Audio.Sfx.ALERT, m.x, m.y, 0.7);
        }
        if (m.type == Monster.Type.BUTCHER && !level.bossAnnounced) {
            level.bossAnnounced = true;
            sfx(Audio.Sfx.BUTCHER);
            showBanner("\"Ah... Fresh Meat!\"", new Color(230, 40, 30));
        }
    }

    private int roll(int min, int max) { return min + rnd.nextInt(Math.max(1, max - min + 1)); }

    private void separate() {
        creatures.clear();
        creatures.add(player);
        creatures.addAll(monsters);
        for (int a = 0; a < creatures.size(); a++) {
            Creature ca = creatures.get(a);
            if (ca.dead) continue;
            for (int b = a + 1; b < creatures.size(); b++) {
                Creature cb = creatures.get(b);
                if (cb.dead) continue;
                double dx = cb.x - ca.x, dy = cb.y - ca.y, d = Math.hypot(dx, dy), min = ca.radius + cb.radius;
                if (d >= min) continue;
                if (d < 1e-4) {
                    dx = rnd.nextDouble() - 0.5;
                    dy = rnd.nextDouble() - 0.5;
                    d = Math.hypot(dx, dy);
                }
                double push = min - d, nx = dx / d, ny = dy / d;
                double wa = ca == player ? 0.15 : 0.5, wb = 1 - wa;
                ca.tryMove(dungeon, -nx * push * wa, -ny * push * wa);
                cb.tryMove(dungeon, nx * push * wb, ny * push * wb);
            }
        }
    }

    // ------------------------------------------------------------------ town: shops & portals

    private void openShop(Npc n) {
        shopNpc = n;
        showInventory = true;
        showHelp = false;
        n.facing = Math.atan2(player.y - n.y, player.x - n.x);
        if (n.role == Npc.Role.HEALER && (player.hp < player.maxHp() || player.mana < player.maxMana())) {
            player.hp = player.maxHp();
            player.mana = player.maxMana();
            addText(player.x, player.y, 60, "Fully restored", new Color(140, 255, 140), labelFont);
            sfx(Audio.Sfx.POTION);
        }
    }

    private void buy(int idx) {
        Item it = shopNpc.stock.get(idx);
        int price = it.value();
        if (player.gold < price) {
            notice("You need " + price + " gold for that");
            return;
        }
        switch (it.slot) {
            case HEALTH_POTION -> player.healthPotions++;
            case MANA_POTION -> player.manaPotions++;
            case TOWN_PORTAL -> player.portalScrolls++;
            default -> {
                if (player.inventory.size() >= Player.INV_SIZE) {
                    notice("Inventory is full");
                    return;
                }
                player.inventory.add(it);
                shopNpc.stock.remove(idx);
            }
        }
        player.gold -= price;
        sfx(Audio.Sfx.GOLD);
        notice("Bought " + it.name + " for " + price + " gold");
    }

    private void sell(int idx) {
        Item it = player.inventory.remove(idx);
        player.gold += it.sellValue();
        sfx(Audio.Sfx.GOLD);
        notice("Sold " + it.name + " for " + it.sellValue() + " gold");
    }

    private void castTownPortal() {
        if (dungeon.town) {
            notice("You are already in town");
            return;
        }
        if (player.portalScrolls <= 0) {
            notice("No Town Portal scrolls (the healer sells them)");
            return;
        }
        player.portalScrolls--;
        closePortal();
        double[] s = spotNear(player.x + Math.cos(player.facing) * 0.5, player.y + Math.sin(player.facing) * 0.5);
        level.portal = new Portal(s[0], s[1], true);
        portalLevel = level;
        town.portal = new Portal(town.dungeon.townPortalX + 0.5, town.dungeon.townPortalY + 0.5, false);
        burst(s[0], s[1], 25, new Color(140, 190, 255), 2, 40);
        sfx(Audio.Sfx.PORTAL);
        notice("A portal to town shimmers open");
    }

    private void usePortal(Portal pt) {
        sfx(Audio.Sfx.PORTAL);
        if (pt.toTown) {
            double[] s;
            Level from = level;
            enterTown(town.portal.x, town.portal.y);
            s = spotNear(town.portal.x, town.portal.y);
            player.x = s[0];
            player.y = s[1];
            portalLevel = from;
        } else if (portalLevel != null) {
            Level l = portalLevel;
            Portal back = l.portal;
            closePortal();
            enterLevel(l, back.x, back.y);
            double[] s = spotNear(back.x, back.y);
            player.x = s[0];
            player.y = s[1];
            showBanner("Dungeon Level " + l.depth, new Color(210, 175, 110));
        }
        updateCamera();
    }

    // ------------------------------------------------------------------ combat

    private void meleeHit(Monster t) {
        int dmg = roll(player.minDmg(), player.maxDmg());
        boolean crit = rnd.nextDouble() < 0.08;
        if (crit) dmg *= 2;
        sfx(crit ? Audio.Sfx.CRIT : Audio.Sfx.HIT);
        damageMonster(t, dmg, crit ? new Color(255, 220, 80) : Color.WHITE, crit);
        if (player.leech() > 0) player.hp = Math.min(player.maxHp(), player.hp + dmg * player.leech() / 100.0);
    }

    private void damageMonster(Monster m, double dmg, Color c, boolean big) {
        if (m.dead) return;
        m.hp -= dmg;
        m.hitFlash = 0.18;
        if (!m.aggro) alert(m);
        addText(m.x, m.y, 50 * m.scale(), String.valueOf((int) Math.round(dmg)), c, big ? critFont : dmgFont);
        burst(m.x, m.y, 6, m.skeletal() ? new Color(220, 215, 190) : new Color(160, 0, 0), 1.8, 30 * m.scale());
        if (m.hp <= 0) killMonster(m);
    }

    private void killMonster(Monster m) {
        m.dead = true;
        if (m.type == Monster.Type.BUTCHER) sfx(Audio.Sfx.BUTCHER);
        else sfx(m.skeletal() ? Audio.Sfx.BONE_DIE : Audio.Sfx.MONSTER_DIE, m.x, m.y, 0.8);
        gainXp(m.xp);
        Decal d = new Decal();
        d.x = m.x;
        d.y = m.y;
        d.corpse = true;
        d.color = m.corpseColor();
        d.type = m.type;
        d.size = m.scale();
        d.rot = rnd.nextDouble() * 0.6 - 0.3;
        decals.add(d);
        if (decals.size() > 250) decals.remove(0);
        burst(m.x, m.y, 16, m.skeletal() ? new Color(220, 215, 190) : new Color(140, 0, 0), 2.5, 25 * m.scale());
        for (Item it : Loot.dropsFor(m, depth, rnd)) dropItem(it, m.x, m.y);
        if (m.type == Monster.Type.BUTCHER) showBanner("The Butcher has been slain!", new Color(230, 190, 90));
    }

    private void gainXp(int amount) {
        player.xp += amount;
        while (player.xp >= Player.xpForLevel(player.level)) {
            player.xp -= Player.xpForLevel(player.level);
            player.level++;
            player.baseMaxHp += 10;
            player.baseMaxMana += 5;
            player.baseMin += 1;
            player.baseMax += 1;
            player.hp = player.maxHp();
            player.mana = player.maxMana();
            showBanner("Level Up!  You are now level " + player.level, new Color(255, 230, 120));
            sfx(Audio.Sfx.LEVEL_UP);
            for (int i = 0; i < 40; i++) {
                double a = i * Math.PI * 2 / 40;
                spawnParticle(player.x, player.y, 5, Math.cos(a) * 2.5, Math.sin(a) * 2.5, 120,
                        new Color(255, 220, 100), 4, 0.9, 0);
            }
        }
    }

    private void damagePlayer(double raw) {
        if (player.dead) return;
        if (player.equipment.containsKey(Item.Slot.SHIELD) && rnd.nextDouble() < 0.15) {
            addText(player.x, player.y, 55, "Blocked", new Color(180, 180, 200), dmgFont);
            sfx(Audio.Sfx.BLOCK);
            return;
        }
        double armor = player.armor();
        double dmg = Math.max(1, raw * (1 - armor / (armor + 60.0)));
        player.hp -= dmg;
        player.hitFlash = 0.2;
        addText(player.x, player.y, 55, "-" + (int) Math.ceil(dmg), new Color(255, 70, 70), dmgFont);
        burst(player.x, player.y, 5, new Color(160, 0, 0), 1.5, 30);
        sfx(Audio.Sfx.HIT, player.x, player.y, 0.6);
        if (player.hp <= 0) {
            sfx(Audio.Sfx.DEATH);
            audio.setMusic(Audio.Music.NONE);
            player.hp = 0;
            player.dead = true;
        } else if (rnd.nextDouble() < 0.35) {
            sfx(Audio.Sfx.PLAYER_HURT);
        }
        if (player.dead) {
            player.stop();
            deathTimer = 0;
            leftDown = rightDown = holdMove = false;
            showInventory = false;
        }
    }

    private void shootArrow(Monster m) {
        double dx = player.x - m.x, dy = player.y - m.y, d = Math.max(0.01, Math.hypot(dx, dy)), sp = 8;
        projectiles.add(new Projectile(Projectile.Kind.ARROW, m.x, m.y, dx / d * sp, dy / d * sp,
                roll(m.minDmg, m.maxDmg), false, 1.6));
        sfx(Audio.Sfx.ARROW, m.x, m.y, 0.8);
    }

    private void castFireball() {
        Player p = player;
        if (p.castCooldown > 0 || p.swing > 0 || dungeon.town) return;
        if (p.mana < FIREBALL_COST) {
            notice("Not enough mana");
            p.castCooldown = 0.4;
            return;
        }
        double tx = mouseWX(), ty = mouseWY();
        if (hovered != null) {
            tx = hovered.x;
            ty = hovered.y;
        }
        double dx = tx - p.x, dy = ty - p.y, d = Math.hypot(dx, dy);
        if (d < 0.05) return;
        p.mana -= FIREBALL_COST;
        p.castCooldown = 0.45;
        p.facing = Math.atan2(dy, dx);
        p.stop();
        p.attackTarget = null;
        double dmg = (9 + p.level * 3) * (1 + p.spellBonus() / 100.0);
        sfx(Audio.Sfx.FIREBALL);
        projectiles.add(new Projectile(Projectile.Kind.FIREBALL, p.x + dx / d * 0.4, p.y + dy / d * 0.4,
                dx / d * 9, dy / d * 9, dmg, true, 1.6));
    }

    private void castNova() {
        Player p = player;
        if (p.novaCooldown > 0 || p.dead || dungeon.town) return;
        if (p.mana < NOVA_COST) {
            notice("Not enough mana");
            return;
        }
        p.mana -= NOVA_COST;
        p.novaCooldown = 0.8;
        sfx(Audio.Sfx.FROST);
        double dmg = (5 + p.level * 2) * (1 + p.spellBonus() / 100.0);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            projectiles.add(new Projectile(Projectile.Kind.FROST, p.x, p.y, Math.cos(a) * 7, Math.sin(a) * 7, dmg, true, 0.65));
        }
    }

    private void updateProjectiles(double dt) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile pr = projectiles.get(i);
            pr.life -= dt;
            double nx = pr.x + pr.vx * dt, ny = pr.y + pr.vy * dt;
            boolean done = false;
            if (!dungeon.walkable(nx, ny) || pr.life <= 0) {
                if (pr.kind == Projectile.Kind.FIREBALL) explode(pr);
                done = true;
            } else {
                pr.x = nx;
                pr.y = ny;
                if (pr.fromPlayer) {
                    for (Monster m : monsters) {
                        if (!m.dead && Math.hypot(m.x - pr.x, m.y - pr.y) < m.radius + 0.25) {
                            if (pr.kind == Projectile.Kind.FIREBALL) {
                                explode(pr);
                            } else {
                                damageMonster(m, pr.damage, new Color(150, 200, 255), false);
                                m.slowTimer = 2.5;
                            }
                            done = true;
                            break;
                        }
                    }
                } else if (Math.hypot(player.x - pr.x, player.y - pr.y) < player.radius + 0.2) {
                    damagePlayer(pr.damage);
                    done = true;
                }
            }
            if (pr.kind == Projectile.Kind.FIREBALL) {
                for (int k = 0; k < 2; k++) {
                    spawnParticle(pr.x, pr.y, pr.z + rnd.nextDouble() * 6 - 3, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5,
                            20 + rnd.nextDouble() * 30, rnd.nextBoolean() ? FIRE : new Color(255, 210, 90), 3, 0.35, -40);
                }
            } else if (pr.kind == Projectile.Kind.FROST && rnd.nextDouble() < 0.3) {
                spawnParticle(pr.x, pr.y, pr.z, 0, 0, 10, new Color(200, 235, 255), 2, 0.3, 0);
            }
            if (done) projectiles.remove(i);
        }
    }

    private void explode(Projectile pr) {
        sfx(Audio.Sfx.EXPLOSION, pr.x, pr.y, 0.9);
        for (Monster m : monsters) {
            double d = Math.hypot(m.x - pr.x, m.y - pr.y);
            if (!m.dead && d < 1.4 + m.radius) damageMonster(m, pr.damage * (d < 0.7 ? 1 : 0.7), FIRE, false);
        }
        for (int k = 0; k < 30; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = 1 + rnd.nextDouble() * 3;
            spawnParticle(pr.x, pr.y, pr.z, Math.cos(a) * s, Math.sin(a) * s, rnd.nextDouble() * 120,
                    rnd.nextBoolean() ? FIRE : new Color(255, 230, 120), 4, 0.5, 120);
        }
    }

    // ------------------------------------------------------------------ items

    private void dropItem(Item it, double x, double y) {
        for (int tries = 0; tries < 12; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 0.2 + rnd.nextDouble() * 0.8;
            double nx = x + Math.cos(a) * r, ny = y + Math.sin(a) * r;
            if (dungeon.walkable(nx, ny)) {
                drops.add(new ItemDrop(it, nx, ny));
                return;
            }
        }
        drops.add(new ItemDrop(it, x, y));
    }

    private void pickup(ItemDrop d) {
        Item it = d.item;
        sfx(it.slot == Item.Slot.GOLD ? Audio.Sfx.GOLD : Audio.Sfx.PICKUP);
        switch (it.slot) {
            case GOLD -> {
                player.gold += it.amount;
                addText(d.x, d.y, 20, "+" + it.amount + " gold", GOLD_TEXT, labelFont);
            }
            case HEALTH_POTION -> {
                player.healthPotions++;
                notice("Picked up a Healing Potion");
            }
            case MANA_POTION -> {
                player.manaPotions++;
                notice("Picked up a Mana Potion");
            }
            case TOWN_PORTAL -> {
                player.portalScrolls++;
                notice("Picked up a Scroll of Town Portal");
            }
            default -> {
                if (player.inventory.size() >= Player.INV_SIZE) {
                    notice("Inventory is full");
                    return;
                }
                player.inventory.add(it);
                notice("Picked up " + it.name);
            }
        }
        drops.remove(d);
    }

    private void drinkPotion(boolean health) {
        Player p = player;
        if (p.dead || p.potionCooldown > 0) return;
        if (health ? p.healthPotions <= 0 : p.manaPotions <= 0) {
            notice(health ? "No healing potions" : "No mana potions");
            return;
        }
        if (health) {
            p.healthPotions--;
            p.hp = Math.min(p.maxHp(), p.hp + p.maxHp() * 0.6);
        } else {
            p.manaPotions--;
            p.mana = Math.min(p.maxMana(), p.mana + p.maxMana() * 0.6);
        }
        p.potionCooldown = 0.5;
        sfx(Audio.Sfx.POTION);
        Color c = health ? new Color(255, 80, 80) : new Color(90, 140, 255);
        for (int i = 0; i < 18; i++) {
            spawnParticle(p.x + rnd.nextDouble() * 0.6 - 0.3, p.y + rnd.nextDouble() * 0.6 - 0.3, rnd.nextDouble() * 30,
                    0, 0, 40 + rnd.nextDouble() * 40, c, 3, 0.8, -20);
        }
    }

    private void equip(int idx) {
        Item it = player.inventory.remove(idx);
        Item old = player.equipment.put(it.slot, it);
        if (old != null) player.inventory.add(idx, old);
        sfx(Audio.Sfx.PICKUP);
        clampVitals();
    }

    private void unequip(Item.Slot s) {
        if (player.inventory.size() >= Player.INV_SIZE) {
            notice("Inventory is full");
            return;
        }
        player.inventory.add(player.equipment.remove(s));
        clampVitals();
    }

    private void clampVitals() {
        player.hp = Math.min(player.hp, player.maxHp());
        player.mana = Math.min(player.mana, player.maxMana());
    }

    // ------------------------------------------------------------------ effects

    private void spawnParticle(double x, double y, double z, double vx, double vy, double vz, Color c,
                               double size, double life, double gravity) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.z = z;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.color = c;
        p.size = size;
        p.life = p.maxLife = life * (0.7 + rnd.nextDouble() * 0.6);
        p.gravity = gravity;
        particles.add(p);
    }

    private void burst(double x, double y, int n, Color c, double speed, double z) {
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, s = rnd.nextDouble() * speed;
            spawnParticle(x, y, z, Math.cos(a) * s, Math.sin(a) * s, 40 + rnd.nextDouble() * 80, c, 3, 0.7, 300);
        }
        if (c.getRed() > 100 && c.getGreen() < 50 && rnd.nextDouble() < 0.5) {
            Decal d = new Decal();
            d.x = x + rnd.nextDouble() * 0.6 - 0.3;
            d.y = y + rnd.nextDouble() * 0.6 - 0.3;
            d.size = 0.6 + rnd.nextDouble();
            decals.add(d);
        }
    }

    private void addText(double x, double y, double z, String s, Color c, Font f) {
        FloatingText t = new FloatingText();
        t.x = x;
        t.y = y;
        t.z = z;
        t.text = s;
        t.color = c;
        t.font = f;
        texts.add(t);
    }

    private void showBanner(String s, Color c) {
        banner = s;
        bannerColor = c;
        bannerTime = 3.5;
    }

    private void notice(String s) {
        notice = s;
        noticeTime = 2.5;
    }

    private void updateEffects(double dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.vz -= p.gravity * dt;
            if (p.z < 0) {
                p.z = 0;
                p.vz = 0;
                p.vx *= 0.5;
                p.vy *= 0.5;
            }
        }
        for (int i = texts.size() - 1; i >= 0; i--) {
            FloatingText t = texts.get(i);
            t.life -= dt;
            t.z += 35 * dt;
            if (t.life <= 0) texts.remove(i);
        }
        for (ItemDrop d : drops) d.bounce = Math.max(0, d.bounce - dt * 2.2);
        for (int[] t : dungeon.torches) {
            if (rnd.nextDouble() < dt * 1.5 && Math.abs(t[0] - player.x) < 15 && Math.abs(t[1] - player.y) < 15) {
                double wx = t[2] == 0 ? t[0] + 0.5 : t[0] + 1.02, wy = t[2] == 0 ? t[1] + 1.02 : t[1] + 0.5;
                spawnParticle(wx, wy, 44, 0, 0, 20 + rnd.nextDouble() * 20, new Color(255, 170, 60), 2, 1.2, -5);
            }
        }
        if (level.portal != null && rnd.nextDouble() < dt * 12) {
            spawnParticle(level.portal.x + rnd.nextDouble() * 0.4 - 0.2, level.portal.y + rnd.nextDouble() * 0.4 - 0.2,
                    rnd.nextDouble() * 50, 0, 0, 30, new Color(170, 210, 255), 2.5, 0.8, 0);
        }
    }

    private void updateLighting() {
        dungeon.clearLight();
        if (dungeon.town) return;
        dungeon.addLight(player.x, player.y, 9.5, 1f, true);
        for (int[] t : dungeon.torches) {
            double lx = t[0] + 0.5 + (t[2] == 1 ? 1 : 0), ly = t[1] + 0.5 + (t[2] == 0 ? 1 : 0);
            if (Math.abs(lx - player.x) > 22 || Math.abs(ly - player.y) > 22) continue;
            float flick = (float) (0.75 + 0.08 * Math.sin(time * 9 + t[0]) + 0.05 * Math.sin(time * 23 + t[1]));
            dungeon.addLight(lx, ly, 4.5, flick, false);
        }
        for (Projectile p : projectiles) {
            if (p.kind == Projectile.Kind.FIREBALL) dungeon.addLight(p.x, p.y, 3.5, 0.95f, false);
        }
        if (level.portal != null) dungeon.addLight(level.portal.x, level.portal.y, 3.5, 0.8f, false);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        render((Graphics2D) g0);
    }

    /**
     * Retro pipeline: everything is drawn (in full-resolution layout coordinates) into a canvas PX times smaller,
     * the world is dithered down to the fixed palette, the UI is drawn on top and snapped to the palette
     * without dithering (so text stays crisp), and the result is scaled up with hard pixel edges.
     */
    void render(Graphics2D out) {
        if (getWidth() > 0) {
            vw = getWidth();
            vh = getHeight();
        }
        int lw = Math.max(1, vw / PX), lh = Math.max(1, vh / PX);
        if (canvas == null || canvas.getWidth() != lw || canvas.getHeight() != lh) {
            canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
        }
        Graphics2D g = canvas.createGraphics();
        g.scale(1.0 / PX, 1.0 / PX);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        buttons.clear();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, vw, vh);
        renderWorld(g);
        if (vignette == null || vignette.getWidth() != vw || vignette.getHeight() != vh) buildVignette();
        g.drawImage(vignette, 0, 0, null);
        Retro.quantize(canvas, true);
        renderUi(g);
        Retro.quantize(canvas, false);
        g.dispose();
        out.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        out.drawImage(canvas, 0, 0, lw * PX, lh * PX, null);
    }

    private void renderUi(Graphics2D g) {
        if (screen == Screen.MENU) {
            renderMainMenu(g);
            return;
        }
        renderLabels(g);
        if (showMap) renderAutomap(g);
        renderHud(g);
        if (shopNpc != null) renderShop(g);
        if (showInventory) renderInventory(g);
        waypointRect = null;
        if (showWaypoints) renderWaypoints(g);
        if (hoveredDrop != null) drawTooltip(g, hoveredDrop.item, mouseX, mouseY, null, null);
        if (showHelp && shopNpc == null && !paused) renderHelp(g);
        if (player.dead) renderDeath(g);
        else if (paused) renderPauseMenu(g);
    }

    private void buildVignette() {
        vignette = new BufferedImage(vw, vh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = vignette.createGraphics();
        float r = (float) (Math.hypot(vw, vh) * 0.6);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(vw / 2f, (vh - HUD_H) / 2f), r,
                new float[]{0f, 0.5f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 0), new Color(0, 0, 0, 210)}));
        g.fillRect(0, 0, vw, vh);
        g.dispose();
    }

    private void renderWorld(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double sx0 = camX - Iso.TW, sx1 = camX + vw + Iso.TW, sy0 = camY - Iso.TH, sy1 = camY + vh + HOUSE_H + Iso.TH;
        int i0 = clamp((int) Math.floor(Iso.wx(sx0, sy0)) - 1, dungeon.w), i1 = clamp((int) Math.ceil(Iso.wx(sx1, sy1)) + 1, dungeon.w);
        int j0 = clamp((int) Math.floor(Iso.wy(sx1, sy0)) - 1, dungeon.h), j1 = clamp((int) Math.ceil(Iso.wy(sx0, sy1)) + 1, dungeon.h);

        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                if (dungeon.isBlock(i, j)) continue;
                double b = dungeon.light[i][j];
                if (dungeon.explored[i][j]) b = Math.max(b, 0.12);
                if (b > 0.02) drawFloor(g, i, j, b);
            }
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (Decal d : decals) {
            double b = lightAt(d.x, d.y);
            if (b < 0.03) continue;
            double sx = Iso.sx(d.x, d.y) - camX, sy = Iso.sy(d.x, d.y) - camY;
            if (d.corpse) PixelSprites.corpse(g, d, sx, sy, b);
            else Sprites.blood(g, d, sx, sy, b);
        }
        for (ItemDrop d : drops) {
            double b = lightAt(d.x, d.y);
            if (b < 0.04) continue;
            double sx = Iso.sx(d.x, d.y) - camX, sy = Iso.sy(d.x, d.y) - camY;
            double lift = Math.sin(d.bounce * Math.PI) * 22;
            PixelArt.icon(g, d.item, sx, sy - 6 - lift, 22, Math.max(b, 0.35));
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        drawList.clear();
        drawList.add(player);
        for (Monster m : monsters) if (lightAt(m.x, m.y) >= 0.05) drawList.add(m);
        drawList.addAll(npcs);
        if (level.portal != null) drawList.add(level.portal);
        drawList.addAll(projectiles);
        drawList.sort(Comparator.comparingDouble(Entity::depth));

        int ei = 0;
        for (int s = i0 + j0; s <= i1 + j1; s++) {
            while (ei < drawList.size() && drawList.get(ei).depth() < s + 1) drawEntity(g, drawList.get(ei++));
            for (int i = Math.max(i0, s - j1); i <= Math.min(i1, s - j0); i++) {
                int j = s - i;
                int t = dungeon.tiles[i][j];
                if (t == Dungeon.WALL || t == Dungeon.HOUSE) drawWall(g, i, j);
                else if (t == Dungeon.TREE) drawTree(g, i, j);
            }
        }
        while (ei < drawList.size()) drawEntity(g, drawList.get(ei++));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        for (Particle p : particles) {
            double sx = Iso.sx(p.x, p.y) - camX, sy = Iso.sy(p.x, p.y) - camY - p.z;
            // Solid palette pixels on the retro grid: they darken with age, then flicker out.
            double a = p.life / p.maxLife;
            if (a < 0.3 && ((int) (time * 30) & 1) == 0) continue;
            Color c = a < 0.55 ? p.color.darker() : p.color;
            g.setColor(new Color(Retro.nearest(c.getRGB())));
            int s = (p.size >= 4 ? 2 : 1) * PX;
            g.fillRect((int) Math.round(sx / PX) * PX, (int) Math.round(sy / PX) * PX, s, s);
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        for (FloatingText t : texts) {
            double sx = Iso.sx(t.x, t.y) - camX, sy = Iso.sy(t.x, t.y) - camY - t.z;
            double a = Math.min(1, t.life / t.maxLife * 2);
            g.setFont(t.font);
            int w = PixelFont.width(g, t.text);
            g.setColor(new Color(0, 0, 0, (int) (200 * a)));
            PixelFont.draw(g, t.text, (int) sx - w / 2 + 1, (int) sy + 1);
            g.setColor(Shade.alpha(t.color, a));
            PixelFont.draw(g, t.text, (int) sx - w / 2, (int) sy);
        }
    }

    private static int clamp(int v, int size) { return Math.max(0, Math.min(size - 1, v)); }

    private void diamond(int x, int y, int lift) {
        px[0] = x;
        py[0] = y - lift;
        px[1] = x + HW;
        py[1] = y + HH - lift;
        px[2] = x;
        py[2] = y + 2 * HH - lift;
        px[3] = x - HW;
        py[3] = y + HH - lift;
    }

    private void drawFloor(Graphics2D g, int i, int j, double b) {
        int x = (int) (Iso.sx(i, j) - camX), y = (int) (Iso.sy(i, j) - camY);
        if (x < -HW || x > vw + HW || y < -Iso.TH || y > vh) return;
        int v = dungeon.variant[i][j], t = dungeon.tiles[i][j];
        boolean grass = dungeon.town && (t == Dungeon.FLOOR || t == Dungeon.TREE);
        Color base = !dungeon.town ? FLOOR_COLORS[v & 3] : grass ? GRASS_COLORS[v & 3] : ROAD_COLORS[v & 3];
        diamond(x, y, 0);
        g.setColor(Shade.of(base, b));
        g.fillPolygon(px, py, 4);
        if (grass) {
            g.setColor(Shade.of(GRASS_TUFT, b));
            int ox = (v % 30) - 15, oy = HH + (v % 9) - 4;
            g.drawLine(x + ox, y + oy, x + ox - 1, y + oy - 4);
            g.drawLine(x + ox + 2, y + oy, x + ox + 3, y + oy - 3);
            if (v % 5 == 0) g.drawLine(x - ox / 2, y + HH + 4, x - ox / 2 + 1, y + HH);
            if (v % 37 == 0) {
                g.setColor(Shade.of(v % 2 == 0 ? new Color(230, 210, 90) : new Color(220, 120, 160), b));
                g.fillOval(x + ox / 2, y + HH - 2, 3, 3);
            }
        } else {
            g.setColor(Shade.of(dungeon.town ? ROAD_EDGE : FLOOR_EDGE, b));
            g.drawLine(px[3], py[3], px[0], py[0]);
            g.drawLine(px[0], py[0], px[1], py[1]);
            if (v % 7 == 0) {
                g.drawLine(x - 10, y + 14, x + 4, y + 19);
                g.drawLine(x + 4, y + 19, x + 9, y + 15);
            }
            if (v % 11 == 0) {
                g.setColor(Shade.of(base, b * 1.3));
                g.fillOval(x + (v % 20) - 10, y + 12 + (v % 6), 5, 3);
            }
        }
        if (t == Dungeon.STAIRS || t == Dungeon.STAIRS_UP) {
            boolean up = t == Dungeon.STAIRS_UP;
            for (int k = 0; k < 4; k++) {
                double f = 0.85 - k * 0.18;
                int cy = y + HH + (up ? -k * 3 : k * 2), hw = (int) (HW * f), hh = (int) (HH * f);
                px[0] = x; py[0] = cy - hh;
                px[1] = x + hw; py[1] = cy;
                px[2] = x; py[2] = cy + hh;
                px[3] = x - hw; py[3] = cy;
                g.setColor(Shade.of(up ? STAIR_UP_COLORS[k] : STAIR_COLORS[k], b));
                g.fillPolygon(px, py, 4);
            }
            diamond(x, y, 0);
            int a = (int) (90 + 60 * Math.sin(time * 3));
            g.setColor(up ? new Color(150, 190, 255, a) : new Color(255, 200, 100, a));
            g.drawPolygon(px, py, 4);
        }
    }

    /** Walls in front of the player (that would hide them) are drawn see-through. */
    private boolean fadesPlayer(int i, int j) {
        double rel = (i + j + 1) - player.depth();
        return rel > 0 && rel < 4.5 && Math.abs((i - j) - (player.x - player.y)) < 2.5;
    }

    private void drawWall(Graphics2D g, int i, int j) {
        float l = dungeon.light[i][j];
        double b = l > 0.02f ? Math.max(l, 0.12) : (dungeon.explored[i][j] ? 0.12 : 0);
        if (b <= 0) return;
        int x = (int) (Iso.sx(i, j) - camX), y = (int) (Iso.sy(i, j) - camY);
        boolean house = dungeon.tiles[i][j] == Dungeon.HOUSE;
        int wh = house ? HOUSE_H : dungeon.town ? TOWN_WALL_H : WALL_H;
        if (x < -HW || x > vw + HW || y < -Iso.TH || y > vh + wh) return;
        Color[] pal = house ? HOUSE_WALL : dungeon.town ? TOWN_WALL : DUNGEON_WALL;
        int v = dungeon.variant[i][j];

        Composite oc = g.getComposite();
        if (fadesPlayer(i, j)) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));

        int q = wh / 4;
        if (!dungeon.isBlock(i, j + 1)) { // face toward +y (lower-left on screen)
            px[0] = x - HW; py[0] = y + HH;
            px[1] = x; py[1] = y + 2 * HH;
            px[2] = x; py[2] = y + 2 * HH - wh;
            px[3] = x - HW; py[3] = y + HH - wh;
            g.setColor(Shade.of(pal[1], b));
            g.fillPolygon(px, py, 4);
            g.setColor(Shade.of(pal[3], b));
            if (house) {
                g.drawPolygon(px, py, 4);
                g.drawLine(x - HW, y + HH - wh / 2, x, y + 2 * HH - wh / 2);
                if (dungeon.doorFace[i][j] == 1) {
                    face(g, x - HW, y + HH, HW, HH, 0.3, 0.7, 0, 36);
                    g.setColor(Shade.of(new Color(75, 48, 28), b));
                    g.fillPolygon(px, py, 4);
                } else if (v % 3 == 0) {
                    face(g, x - HW, y + HH, HW, HH, 0.35, 0.65, 40, 54);
                    g.setColor(Shade.of(new Color(255, 210, 120), Math.max(b, 0.8)));
                    g.fillPolygon(px, py, 4);
                }
            } else {
                for (int k = 0; k < 4; k++) {
                    int hgt = k * q;
                    if (k > 0) g.drawLine(x - HW, y + HH - hgt, x, y + 2 * HH - hgt);
                    double t = (k % 2 == 0) ? 0.33 : 0.72;
                    int jx = (int) (x - HW + HW * t), jy = (int) (y + HH + HH * t) - hgt;
                    g.drawLine(jx, jy, jx, jy - q);
                }
            }
        }
        if (!dungeon.isBlock(i + 1, j)) { // face toward +x (lower-right on screen)
            px[0] = x; py[0] = y + 2 * HH;
            px[1] = x + HW; py[1] = y + HH;
            px[2] = x + HW; py[2] = y + HH - wh;
            px[3] = x; py[3] = y + 2 * HH - wh;
            g.setColor(Shade.of(pal[2], b));
            g.fillPolygon(px, py, 4);
            g.setColor(Shade.of(pal[3], b));
            if (house) {
                g.drawPolygon(px, py, 4);
                g.drawLine(x, y + 2 * HH - wh / 2, x + HW, y + HH - wh / 2);
                if (v % 3 == 1) {
                    face(g, x, y + 2 * HH, HW, -HH, 0.35, 0.65, 40, 54);
                    g.setColor(Shade.of(new Color(255, 210, 120), Math.max(b, 0.8)));
                    g.fillPolygon(px, py, 4);
                }
            } else {
                for (int k = 0; k < 4; k++) {
                    int hgt = k * q;
                    if (k > 0) g.drawLine(x, y + 2 * HH - hgt, x + HW, y + HH - hgt);
                    double t = (k % 2 == 0) ? 0.4 : 0.8;
                    int jx = (int) (x + HW * t), jy = (int) (y + 2 * HH - HH * t) - hgt;
                    g.drawLine(jx, jy, jx, jy - q);
                }
            }
        }
        diamond(x, y, wh);
        g.setColor(Shade.of(pal[0], b));
        g.fillPolygon(px, py, 4);
        g.setColor(Shade.of(pal[3], b));
        g.drawPolygon(px, py, 4);
        if (house) g.drawLine(px[3] + HW / 2, py[3] - HH / 2, px[2] + HW / 2, py[2] - HH / 2);

        int tf = dungeon.torchFace[i][j];
        if (tf != 0) drawTorch(g, i, j, tf - 1);
        g.setComposite(oc);
    }

    /** Fills px/py with a quad on a wall face: from fraction t0..t1 along its bottom edge, h0..h1 pixels up. */
    private void face(Graphics2D g, int x0, int y0, int dx, int dy, double t0, double t1, int h0, int h1) {
        px[0] = (int) (x0 + dx * t0); py[0] = (int) (y0 + dy * t0) - h0;
        px[1] = (int) (x0 + dx * t1); py[1] = (int) (y0 + dy * t1) - h0;
        px[2] = px[1]; py[2] = py[1] - (h1 - h0);
        px[3] = px[0]; py[3] = py[0] - (h1 - h0);
    }

    private void drawTree(Graphics2D g, int i, int j) {
        double cx = Iso.sx(i + 0.5, j + 0.5) - camX, cy = Iso.sy(i + 0.5, j + 0.5) - camY;
        if (cx < -60 || cx > vw + 60 || cy < -10 || cy > vh + 100) return;
        double b = dungeon.light[i][j], s = 0.85 + (dungeon.variant[i][j] % 30) / 100.0;
        Composite oc = g.getComposite();
        if (fadesPlayer(i, j)) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0, 0, 0, 80));
        g.fill(new Ellipse2D.Double(cx - 22 * s, cy - 8 * s, 44 * s, 16 * s));
        g.setColor(Shade.of(new Color(85, 60, 38), b));
        g.fill(new Rectangle2D.Double(cx - 3.5, cy - 30 * s, 7, 30 * s));
        g.setColor(Shade.of(TREE_COLORS[0], b));
        g.fill(new Ellipse2D.Double(cx - 24 * s, cy - 66 * s, 48 * s, 40 * s));
        g.setColor(Shade.of(TREE_COLORS[1], b));
        g.fill(new Ellipse2D.Double(cx - 19 * s, cy - 78 * s, 36 * s, 34 * s));
        g.setColor(Shade.of(TREE_COLORS[2], b));
        g.fill(new Ellipse2D.Double(cx - 8 * s, cy - 76 * s, 22 * s, 20 * s));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setComposite(oc);
    }

    private void drawTorch(Graphics2D g, int i, int j, int face) {
        double wx = face == 0 ? i + 0.5 : i + 1, wy = face == 0 ? j + 1 : j + 0.5;
        double sx = Iso.sx(wx, wy) - camX, sy = Iso.sy(wx, wy) - camY - 30;
        double fl = Math.sin(time * 14 + i * 3.1) * 1.5 + Math.sin(time * 23 + j);
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(sx, sy - 6), 32f, new float[]{0f, 1f},
                new Color[]{new Color(255, 150, 50, 80), new Color(255, 120, 30, 0)}));
        g.fill(new Ellipse2D.Double(sx - 32, sy - 38, 64, 64));
        g.setColor(new Color(45, 35, 28));
        g.fill(new Rectangle2D.Double(sx - 2, sy - 4, 4, 11));
        g.setColor(new Color(255, 120, 20));
        g.fill(new Ellipse2D.Double(sx - 4, sy - 14 - fl, 8, 12 + fl));
        g.setColor(new Color(255, 235, 140));
        g.fill(new Ellipse2D.Double(sx - 2, sy - 9 - fl * 0.5, 4, 7));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private void drawEntity(Graphics2D g, Entity e) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double sx = Iso.sx(e.x, e.y) - camX, sy = Iso.sy(e.x, e.y) - camY;
        if (e instanceof Player p) {
            PixelSprites.player(g, p, sx, sy, Math.max(0.6, lightAt(p.x, p.y)));
        } else if (e instanceof Monster m) {
            PixelSprites.monster(g, m, sx, sy, lightAt(m.x, m.y), m == hovered);
            if (m.hp < m.maxHp()) {
                int[] sz = PixelSprites.size(m);
                int bw = Math.max(20, sz[0]), by = (int) sy - sz[1] - 10;
                g.setColor(new Color(0, 0, 0, 170));
                g.fillRect((int) sx - bw / 2 - 1, by - 1, bw + 2, 5);
                g.setColor(new Color(200, 20, 20));
                g.fillRect((int) sx - bw / 2, by, (int) (bw * Math.max(0, m.hp / m.maxHp())), 3);
            }
        } else if (e instanceof Npc n) {
            PixelSprites.npc(g, n, sx, sy, Math.max(0.5, lightAt(n.x, n.y)), n == hoveredNpc);
        } else if (e instanceof Portal pt) {
            PixelArt.portal(g, sx, sy, time, pt == hoveredPortal);
        } else if (e instanceof Projectile pr) {
            PixelArt.projectile(g, pr, sx, sy, time);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void renderLabels(Graphics2D g) {
        for (Npc n : npcs) {
            double sx = Iso.sx(n.x, n.y) - camX, sy = Iso.sy(n.x, n.y) - camY;
            centered(g, n.name + " " + n.title, (int) sx, (int) sy - 62, labelFont,
                    n == hoveredNpc ? Color.WHITE : new Color(230, 200, 120));
        }
        if (level.portal != null) {
            Portal pt = level.portal;
            double sx = Iso.sx(pt.x, pt.y) - camX, sy = Iso.sy(pt.x, pt.y) - camY;
            String s = pt.toTown ? "Town Portal" : portalLevel != null ? "Portal to Dungeon Level " + portalLevel.depth : "Portal";
            centered(g, s, (int) sx, (int) sy - 82, labelFont, pt == hoveredPortal ? Color.WHITE : new Color(150, 200, 255));
        }
        g.setFont(labelFont);
        List<Rectangle> placed = new ArrayList<>();
        for (ItemDrop d : drops) {
            d.labelRect = null;
            if (lightAt(d.x, d.y) < 0.05 || d.bounce > 0) continue;
            double sx = Iso.sx(d.x, d.y) - camX, sy = Iso.sy(d.x, d.y) - camY;
            int tw = PixelFont.width(g, d.item.name) + 10;
            Rectangle r = new Rectangle((int) sx - tw / 2, (int) sy - 40, tw, 17);
            for (int guard = 0; guard < 20; guard++) {
                boolean moved = false;
                for (Rectangle o : placed) {
                    if (o.intersects(r)) {
                        r.y = o.y - r.height - 1;
                        moved = true;
                    }
                }
                if (!moved) break;
            }
            placed.add(r);
            d.labelRect = r;
            g.setColor(d == hoveredDrop ? new Color(70, 55, 100, 230) : new Color(0, 0, 0, 175));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(d.item.color());
            PixelFont.draw(g, d.item.name, r.x + 5, r.y + 13);
        }
    }

    private void renderAutomap(Graphics2D g) {
        double cx = vw / 2.0, cy = (vh - HUD_H) / 2.0, s = 7, h = 3.5;
        g.setStroke(new BasicStroke(1.5f));
        Color line = new Color(200, 170, 110, 190);
        g.setColor(line);
        for (int i = 0; i < dungeon.w; i++) {
            for (int j = 0; j < dungeon.h; j++) {
                if (!dungeon.explored[i][j] || !dungeon.walkable(i, j)) continue;
                double ox = i - player.x, oy = j - player.y;
                if (!dungeon.walkable(i - 1, j)) mapLine(g, cx, cy, s, h, ox, oy, ox, oy + 1);
                if (!dungeon.walkable(i + 1, j)) mapLine(g, cx, cy, s, h, ox + 1, oy, ox + 1, oy + 1);
                if (!dungeon.walkable(i, j - 1)) mapLine(g, cx, cy, s, h, ox, oy, ox + 1, oy);
                if (!dungeon.walkable(i, j + 1)) mapLine(g, cx, cy, s, h, ox, oy + 1, ox + 1, oy + 1);
                int t = dungeon.tiles[i][j];
                if (t == Dungeon.STAIRS || t == Dungeon.STAIRS_UP) {
                    g.setColor(t == Dungeon.STAIRS ? new Color(255, 230, 80, 230) : new Color(140, 190, 255, 230));
                    double mx = cx + (ox - oy) * s, my = cy + (ox + oy + 1) * h;
                    g.fill(new Ellipse2D.Double(mx - 4, my - 4, 8, 8));
                    g.setColor(line);
                }
            }
        }
        g.setColor(new Color(255, 60, 60));
        g.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
    }

    private void mapLine(Graphics2D g, double cx, double cy, double s, double h, double ax, double ay, double bx, double by) {
        g.draw(new Line2D.Double(cx + (ax - ay) * s, cy + (ax + ay) * h, cx + (bx - by) * s, cy + (bx + by) * h));
    }

    private void renderHud(Graphics2D g) {
        int top = vh - HUD_H;
        g.setPaint(new GradientPaint(0, top, new Color(42, 35, 28), 0, vh, new Color(14, 11, 9)));
        g.fillRect(0, top, vw, HUD_H);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(0, top, vw, top);

        drawOrb(g, 86, vh - 70, 62, player.hp / player.maxHp(), new Color(230, 40, 30), new Color(80, 0, 0),
                (int) Math.ceil(player.hp) + " / " + (int) player.maxHp());
        drawOrb(g, vw - 86, vh - 70, 62, player.mana / player.maxMana(), new Color(70, 110, 255), new Color(0, 10, 80),
                (int) player.mana + " / " + (int) player.maxMana());

        // experience bar
        int bx = 170, bw = vw - 340, byy = top + 8;
        double xpFrac = player.xp / (double) Player.xpForLevel(player.level);
        g.setColor(new Color(10, 8, 6));
        g.fillRect(bx, byy, bw, 7);
        g.setColor(new Color(200, 170, 60));
        g.fillRect(bx, byy, (int) (bw * xpFrac), 7);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(bx, byy, bw, 7);

        // skill / belt slots
        String[] keys = {"LMB", "RMB", "Q", "1", "2", "T"};
        int n = keys.length, sw = 54, gap = 10, total = n * sw + (n - 1) * gap, x0 = vw / 2 - total / 2, y0 = top + 26;
        for (int k = 0; k < n; k++) {
            int x = x0 + k * (sw + gap);
            g.setColor(new Color(8, 6, 5));
            g.fillRect(x, y0, sw, sw);
            double cx = x + sw / 2.0, cy = y0 + sw / 2.0 + 2;
            switch (k) {
                case 0 -> {
                    Item w = player.equipment.get(Item.Slot.WEAPON);
                    if (w != null) PixelArt.icon(g, w, cx, cy, 40, 1);
                }
                case 1 -> {
                    g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), 16f, new float[]{0f, 0.4f, 1f},
                            new Color[]{new Color(255, 255, 210), FIRE, new Color(150, 30, 0)}));
                    g.fill(new Ellipse2D.Double(cx - 16, cy - 16, 32, 32));
                }
                case 2 -> {
                    g.setColor(new Color(170, 220, 255));
                    g.setStroke(new BasicStroke(2.5f));
                    for (int r = 0; r < 8; r++) {
                        double a = r * Math.PI / 4;
                        g.draw(new Line2D.Double(cx + Math.cos(a) * 4, cy + Math.sin(a) * 4, cx + Math.cos(a) * 18, cy + Math.sin(a) * 18));
                    }
                }
                case 3 -> PixelArt.icon(g, HP_ICON, cx, cy, 38, player.healthPotions > 0 ? 1 : 0.35);
                case 4 -> PixelArt.icon(g, MP_ICON, cx, cy, 38, player.manaPotions > 0 ? 1 : 0.35);
                default -> PixelArt.icon(g, TP_ICON, cx, cy, 36, player.portalScrolls > 0 ? 1 : 0.35);
            }
            boolean disabled = (k == 1 && player.mana < FIREBALL_COST) || (k == 2 && (player.mana < NOVA_COST || player.novaCooldown > 0))
                    || ((k == 1 || k == 2) && dungeon.town);
            if (disabled) {
                g.setColor(new Color(0, 0, 40, 150));
                g.fillRect(x, y0, sw, sw);
            }
            g.setColor(PANEL_BORDER);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(x, y0, sw, sw);
            g.setFont(smallFont);
            g.setColor(TEXT);
            PixelFont.draw(g, keys[k], x + 3, y0 + 12);
            if (k >= 3) {
                int count = k == 3 ? player.healthPotions : k == 4 ? player.manaPotions : player.portalScrolls;
                String c = String.valueOf(count);
                PixelFont.draw(g, c, x + sw - 4 - PixelFont.width(g, c), y0 + sw - 4);
            }
        }

        g.setFont(uiFont);
        g.setColor(TEXT);
        PixelFont.draw(g, "Level " + player.level, 170, top + 40);
        PixelFont.draw(g, dungeon.town ? "Tristfall Village" : "Dungeon Level " + depth, 170, top + 62);
        g.setColor(GOLD_TEXT);
        String gold = player.gold + " Gold";
        PixelFont.draw(g, gold, vw - 170 - PixelFont.width(g, gold), top + 40);
        g.setColor(TEXT);
        String hint = "I: Inventory   Tab: Map   Esc: Menu";
        g.setFont(smallFont);
        PixelFont.draw(g, hint, vw - 170 - PixelFont.width(g, hint), top + 62);

        // hovered target info
        if (hovered != null) {
            int w = 260, x = vw / 2 - w / 2, y = 14;
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(x - 2, y - 2, w + 4, 24);
            g.setColor(new Color(120, 10, 10));
            g.fillRect(x, y, (int) (w * Math.max(0, hovered.hp / hovered.maxHp())), 20);
            g.setColor(PANEL_BORDER);
            g.drawRect(x - 2, y - 2, w + 4, 24);
            Color nc = hovered.type == Monster.Type.BUTCHER ? Item.Rarity.UNIQUE.color
                    : hovered.champion ? Item.Rarity.MAGIC.color : Color.WHITE;
            centered(g, hovered.name, vw / 2, y + 15, uiFont, nc);
        }

        if (bannerTime > 0) {
            double a = Math.min(1, bannerTime);
            centered(g, banner, vw / 2, (int) (vh * 0.22), bannerFont, Shade.alpha(bannerColor, a));
        }
        if (noticeTime > 0) {
            centered(g, notice, vw / 2, top - 14, uiFont, Shade.alpha(TEXT, Math.min(1, noticeTime)));
        }
    }

    private void drawOrb(Graphics2D g, int cx, int cy, int r, double frac, Color c1, Color c2, String label) {
        Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color(12, 8, 8));
        g.fill(circle);
        Shape oldClip = g.getClip();
        g.clip(circle);
        frac = Math.max(0, Math.min(1, frac));
        double surface = cy + r - 2 * r * frac;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.3, cy - r * 0.3), r * 1.5f,
                new float[]{0f, 1f}, new Color[]{c1, c2}));
        Path2D liquid = new Path2D.Double();
        liquid.moveTo(cx - r, cy + r);
        for (int k = 0; k <= 20; k++) {
            double x = cx - r + k * (2.0 * r / 20);
            liquid.lineTo(x, surface + Math.sin(time * 3 + k * 0.6) * (frac < 1 ? 2 : 0));
        }
        liquid.lineTo(cx + r, cy + r);
        liquid.closePath();
        g.fill(liquid);
        g.setClip(oldClip);
        g.setColor(new Color(255, 255, 255, 45));
        g.fill(new Ellipse2D.Double(cx - r * 0.6, cy - r * 0.85, r * 0.9, r * 0.55));
        g.setStroke(new BasicStroke(4f));
        g.setColor(PANEL_BORDER);
        g.draw(circle);
        if (Math.hypot(mouseX - cx, mouseY - cy) < r || frac < 0.3) centered(g, label, cx, cy + 5, uiFont, Color.WHITE);
    }

    private void centered(Graphics2D g, String s, int cx, int y, Font f, Color c) {
        g.setFont(f);
        int w = PixelFont.width(g, s);
        g.setColor(new Color(0, 0, 0, Math.min(220, c.getAlpha())));
        PixelFont.draw(g, s, cx - w / 2 + 2, y + 2);
        g.setColor(c);
        PixelFont.draw(g, s, cx - w / 2, y);
    }

    private void panel(Graphics2D g, Rectangle r) {
        g.setColor(PANEL_BG);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setStroke(new BasicStroke(2f));
        g.setColor(PANEL_BORDER);
        g.drawRect(r.x, r.y, r.width, r.height);
        g.setColor(new Color(60, 48, 30));
        g.drawRect(r.x + 4, r.y + 4, r.width - 8, r.height - 8);
    }

    private void button(Graphics2D g, Rectangle r, String label, boolean enabled, Runnable action) {
        boolean hov = enabled && r.contains(mouseX, mouseY);
        g.setColor(hov ? new Color(85, 60, 35) : new Color(32, 24, 18));
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(enabled ? PANEL_BORDER : new Color(70, 60, 45));
        g.drawRect(r.x, r.y, r.width, r.height);
        centered(g, label, r.x + r.width / 2, r.y + r.height / 2 + 6, uiFont,
                !enabled ? new Color(100, 90, 75) : hov ? Color.WHITE : TEXT);
        buttons.add(new Button(r, enabled, action));
    }

    // ------------------------------------------------------------------ menus

    /** Darkens the screen the 8-bit way: a pattern of solid pixels (1 = 1/4, 2 = checkerboard, 3 = 3/4). */
    private void dim(Graphics2D g, Color c, int density) {
        BufferedImage pat = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        int rgb = c.getRGB() | 0xFF000000;
        pat.setRGB(0, 0, rgb);
        if (density >= 2) pat.setRGB(1, 1, rgb);
        if (density >= 3) pat.setRGB(1, 0, rgb);
        g.setPaint(new TexturePaint(pat, new Rectangle(0, 0, 2 * PX, 2 * PX)));
        g.fillRect(0, 0, vw, vh);
    }

    private void renderMainMenu(Graphics2D g) {
        dim(g, Color.BLACK, 3);
        int cx = vw / 2, y = vh / 2 - 140;
        float glow = (float) (0.6 + 0.2 * Math.sin(time * 2));
        centered(g, "Depths of Tristfall", cx, y, bigTitleFont, new Color(200, (int) (60 * glow), 20));
        centered(g, "An isometric action RPG", cx, y + 36, uiFont, TEXT);
        int bw = 260, bh = 40, bx = cx - bw / 2;
        y += 80;
        button(g, new Rectangle(bx, y, bw, bh), "Continue", saveExists, this::loadGame);
        button(g, new Rectangle(bx, y + 55, bw, bh), "New Game", true, this::newGame);
        button(g, new Rectangle(bx, y + 110, bw, bh), "Quit", true, () -> System.exit(0));
        if (saveExists) {
            centered(g, "Starting a new game replaces your saved hero the next time it saves.", cx, y + 190, smallFont,
                    new Color(150, 135, 110));
        }
        centered(g, "Saved to " + SaveGame.FILE, cx, vh - 20, smallFont, new Color(110, 100, 85));
    }

    private void renderPauseMenu(Graphics2D g) {
        dim(g, Color.BLACK, 2);
        Rectangle r = new Rectangle(vw / 2 - 160, vh / 2 - 230, 320, 400);
        panel(g, r);
        centered(g, "Paused", vw / 2, r.y + 44, bannerFont, TEXT);
        int bx = r.x + 30, bw = r.width - 60, bh = 38, y = r.y + 72;
        button(g, new Rectangle(bx, y, bw, bh), "Resume", true, () -> paused = false);
        button(g, new Rectangle(bx, y + 50, bw, bh), "Save Game  (F5)", true, () -> saveGame(true));
        button(g, new Rectangle(bx, y + 100, bw, bh), "Load Game  (F9)", saveExists, this::loadGame);
        button(g, new Rectangle(bx, y + 150, bw, bh), "Save & Main Menu", true, () -> {
            saveGame(false);
            paused = false;
            screen = Screen.MENU;
        });
        button(g, new Rectangle(bx, y + 200, bw, bh), "Save & Exit", true, () -> {
            saveGame(false);
            System.exit(0);
        });
        int hw = (bw - 10) / 2;
        button(g, new Rectangle(bx, y + 258, hw, bh), "Music: " + (audio.musicOn() ? "On" : "Off"), true, audio::toggleMusic);
        button(g, new Rectangle(bx + hw + 10, y + 258, hw, bh), "Sound: " + (audio.soundOn() ? "On" : "Off"), true,
                audio::toggleSound);
    }

    private void renderWaypoints(Graphics2D g) {
        List<Integer> levels = new ArrayList<>();
        for (int k = 1; k <= Math.max(1, maxDepth); k += 3) levels.add(k);
        Rectangle r = new Rectangle(vw / 2 - 180, 70, 360, 78 + levels.size() * 46);
        waypointRect = r;
        panel(g, r);
        centered(g, "Dungeon Entrance", vw / 2, r.y + 32, uiFont.deriveFont(19f), TEXT);
        centered(g, "Waypoints unlock on levels 4, 7, 10...", vw / 2, r.y + 52, smallFont, new Color(150, 135, 110));
        for (int k = 0; k < levels.size(); k++) {
            int d = levels.get(k);
            button(g, new Rectangle(r.x + 30, r.y + 66 + k * 46, r.width - 60, 36), "Descend to Level " + d, true,
                    () -> goToDepth(d));
        }
    }

    // ------------------------------------------------------------------ inventory & shop UI

    private Rectangle inventoryRect() { return new Rectangle(vw - 420, 16, 404, 572); }

    private Rectangle shopRect() { return new Rectangle(16, 16, 404, 572); }

    private Rectangle slotRect(Item.Slot s) {
        int x0 = vw - 420, y0 = 16;
        return switch (s) {
            case HELM -> new Rectangle(x0 + 170, y0 + 44, 64, 60);
            case ARMOR -> new Rectangle(x0 + 170, y0 + 112, 64, 84);
            case BOOTS -> new Rectangle(x0 + 170, y0 + 204, 64, 56);
            case WEAPON -> new Rectangle(x0 + 62, y0 + 80, 64, 100);
            case SHIELD -> new Rectangle(x0 + 278, y0 + 80, 64, 100);
            case RING -> new Rectangle(x0 + 68, y0 + 196, 52, 52);
            default -> new Rectangle();
        };
    }

    private Rectangle cellRect(int idx) {
        int x0 = vw - 420 + 20, y0 = 16 + 400;
        return new Rectangle(x0 + (idx % 7) * 52, y0 + (idx / 7) * 52, 52, 52);
    }

    private Rectangle shopCell(int idx) {
        return new Rectangle(16 + 20 + (idx % 7) * 52, 16 + 92 + (idx / 7) * 52, 52, 52);
    }

    private void inventoryClick(boolean left) {
        for (Item.Slot s : Item.Slot.values()) {
            if (!s.equippable() || !slotRect(s).contains(mouseX, mouseY)) continue;
            if (left && player.equipment.containsKey(s)) unequip(s);
            return;
        }
        for (int idx = 0; idx < player.inventory.size(); idx++) {
            if (!cellRect(idx).contains(mouseX, mouseY)) continue;
            if (left) {
                equip(idx);
            } else if (shopNpc != null) {
                sell(idx);
            } else {
                Item it = player.inventory.remove(idx);
                dropItem(it, player.x, player.y);
                notice("Dropped " + it.name);
            }
            return;
        }
    }

    private void shopClick(boolean left) {
        if (!left) return;
        for (int idx = 0; idx < shopNpc.stock.size(); idx++) {
            if (shopCell(idx).contains(mouseX, mouseY)) {
                buy(idx);
                return;
            }
        }
    }

    private void renderShop(Graphics2D g) {
        Rectangle r = shopRect();
        panel(g, r);
        centered(g, shopNpc.name + " " + shopNpc.title, r.x + r.width / 2, r.y + 32, bannerFont.deriveFont(22f), TEXT);
        centered(g, "Your gold: " + player.gold, r.x + r.width / 2, r.y + 58, uiFont, GOLD_TEXT);
        String greet = shopNpc.role == Npc.Role.SMITH ? "\"Steel for the brave. Take a look.\""
                : "\"Rest a moment, I have tended your wounds.\"";
        centered(g, greet, r.x + r.width / 2, r.y + 80, smallFont, new Color(180, 165, 135));
        Item hoverItem = null;
        for (int idx = 0; idx < 35; idx++) {
            Rectangle c = shopCell(idx);
            boolean hov = c.contains(mouseX, mouseY);
            g.setColor(hov ? new Color(45, 38, 30) : new Color(10, 8, 7));
            g.fillRect(c.x, c.y, c.width, c.height);
            g.setColor(new Color(80, 64, 40));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(c.x, c.y, c.width, c.height);
            if (idx < shopNpc.stock.size()) {
                Item it = shopNpc.stock.get(idx);
                if (it.slot.equippable()) {
                    g.setColor(Shade.alpha(it.rarity.color, 0.12));
                    g.fillRect(c.x + 1, c.y + 1, c.width - 1, c.height - 1);
                }
                PixelArt.icon(g, it, c.getCenterX(), c.getCenterY(), 38, player.gold >= it.value() ? 1 : 0.45);
                if (hov) hoverItem = it;
            }
        }
        String[] help = {
                "Click an item to buy it.",
                "Right-click your own items to sell them.",
                shopNpc.role == Npc.Role.HEALER ? "Potions and scrolls are always in stock." : "New wares arrive every time you return."
        };
        for (int k = 0; k < help.length; k++) {
            centered(g, help[k], r.x + r.width / 2, r.y + 400 + k * 20, smallFont, new Color(150, 135, 110));
        }
        if (hoverItem != null) {
            int price = hoverItem.value();
            drawTooltip(g, hoverItem, mouseX, mouseY, "Price: " + price + " gold",
                    player.gold >= price ? GOLD_TEXT : new Color(255, 90, 80));
        }
    }

    private void renderInventory(Graphics2D g) {
        Rectangle r = inventoryRect();
        panel(g, r);
        centered(g, "Inventory", r.x + r.width / 2, r.y + 30, bannerFont.deriveFont(22f), TEXT);
        Item hoverItem = null;

        for (Item.Slot s : Item.Slot.values()) {
            if (!s.equippable()) continue;
            Rectangle sr = slotRect(s);
            Item it = player.equipment.get(s);
            boolean hov = sr.contains(mouseX, mouseY);
            g.setColor(hov ? new Color(45, 38, 30) : new Color(10, 8, 7));
            g.fillRect(sr.x, sr.y, sr.width, sr.height);
            g.setColor(it != null ? Shade.of(it.rarity.color, 0.6) : new Color(80, 64, 40));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(sr.x, sr.y, sr.width, sr.height);
            if (it != null) {
                PixelArt.icon(g, it, sr.getCenterX(), sr.getCenterY(), Math.min(sr.width, sr.height) * 0.8, 1);
                if (hov) hoverItem = it;
            } else {
                g.setFont(smallFont);
                g.setColor(new Color(110, 95, 70));
                int w = PixelFont.width(g, s.label);
                PixelFont.draw(g, s.label, (int) sr.getCenterX() - w / 2, (int) sr.getCenterY() + 4);
            }
        }

        String[][] stats = {
                {"Level", String.valueOf(player.level)},
                {"Experience", player.xp + " / " + Player.xpForLevel(player.level)},
                {"Life", (int) Math.ceil(player.hp) + " / " + (int) player.maxHp()},
                {"Mana", (int) player.mana + " / " + (int) player.maxMana()},
                {"Damage", player.minDmg() + " - " + player.maxDmg()},
                {"Armor", String.valueOf(player.armor())},
                {"Spell Damage", "+" + player.spellBonus() + "%"},
                {"Life Leech", player.leech() + "%"},
                {"Fireball", (int) ((9 + player.level * 3) * (1 + player.spellBonus() / 100.0)) + " dmg"},
                {"Gold", String.valueOf(player.gold)},
        };
        g.setFont(smallFont);
        for (int k = 0; k < stats.length; k++) {
            int col = k % 2, row = k / 2;
            int x = r.x + 24 + col * 190, y = r.y + 290 + row * 20;
            g.setColor(new Color(160, 140, 105));
            PixelFont.draw(g, stats[k][0], x, y);
            g.setColor(Color.WHITE);
            int w = PixelFont.width(g, stats[k][1]);
            PixelFont.draw(g, stats[k][1], x + 170 - w, y);
        }

        for (int idx = 0; idx < Player.INV_SIZE; idx++) {
            Rectangle c = cellRect(idx);
            boolean hov = c.contains(mouseX, mouseY);
            g.setColor(hov ? new Color(45, 38, 30) : new Color(10, 8, 7));
            g.fillRect(c.x, c.y, c.width, c.height);
            g.setColor(new Color(80, 64, 40));
            g.setStroke(new BasicStroke(1f));
            g.drawRect(c.x, c.y, c.width, c.height);
            if (idx < player.inventory.size()) {
                Item it = player.inventory.get(idx);
                g.setColor(Shade.alpha(it.rarity.color, 0.12));
                g.fillRect(c.x + 1, c.y + 1, c.width - 1, c.height - 1);
                PixelArt.icon(g, it, c.getCenterX(), c.getCenterY(), 38, 1);
                if (hov) hoverItem = it;
            }
        }
        String hint = shopNpc != null ? "Click: equip / unequip      Right-click: sell"
                : "Click: equip / unequip      Right-click: drop";
        centered(g, hint, r.x + r.width / 2, r.y + r.height - 6, smallFont, new Color(140, 120, 90));
        if (hoverItem != null) {
            boolean inBag = player.inventory.contains(hoverItem);
            drawTooltip(g, hoverItem, mouseX, mouseY,
                    shopNpc != null && inBag ? "Sells for " + hoverItem.sellValue() + " gold" : null, GOLD_TEXT);
        }
    }

    private void drawTooltip(Graphics2D g, Item it, int mx, int my, String extra, Color extraColor) {
        List<String> lines = it.describe();
        Item equipped = it.slot.equippable() ? player.equipment.get(it.slot) : null;
        g.setFont(uiFont);
        int w = PixelFont.width(g, it.name);
        for (String s : lines) w = Math.max(w, PixelFont.width(g, s));
        if (extra != null) w = Math.max(w, PixelFont.width(g, extra));
        w += 24;
        int h = 30 + lines.size() * 18 + (it.slot.equippable() ? 18 : 0) + (extra != null ? 22 : 0);
        int x = mx + 18, y = my - h - 8;
        if (x + w > vw - 4) x = mx - w - 18;
        if (y < 4) y = my + 20;
        g.setColor(new Color(8, 6, 6, 240));
        g.fillRect(x, y, w, h);
        g.setColor(Shade.of(it.color(), 0.7));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(x, y, w, h);
        centered(g, it.name, x + w / 2, y + 20, uiFont, it.color());
        int ly = y + 20;
        if (it.slot.equippable()) {
            ly += 18;
            String sub = it.slot.label + (equipped == it ? "  (equipped)" : "");
            centered(g, sub, x + w / 2, ly, smallFont, new Color(150, 140, 120));
        }
        for (int k = 0; k < lines.size(); k++) {
            ly += 18;
            boolean base = k == 0 && it.rarity.ordinal() < Item.Rarity.RARE.ordinal()
                    || (k <= 1 && it.rarity.ordinal() >= Item.Rarity.RARE.ordinal());
            centered(g, lines.get(k), x + w / 2, ly, uiFont, base ? Color.WHITE : new Color(140, 150, 255));
        }
        if (extra != null) centered(g, extra, x + w / 2, ly + 22, uiFont, extraColor);
    }

    private void renderHelp(Graphics2D g) {
        String[] lines = {
                "Left Click  -  Move / Attack / Pick up / Talk  (hold to keep going)",
                "Right Click  -  Fireball  (7 mana)",
                "Q  -  Frost Nova  (16 mana, slows enemies)",
                "1 / 2  -  Drink Healing / Mana potion",
                "T  -  Read a Scroll of Town Portal",
                "I  -  Inventory & character       Tab  -  Automap",
                "F5 / F9  -  Quick save / load      Esc  -  Menu",
                "M  -  Music on / off  (sound toggle in the Esc menu)",
                "H  -  Toggle this help",
                "",
                "Visit Torvald (gear) and Maren (potions, healing),",
                "then take the stairs at the south end of town.",
                "Something terrible waits on every third level...",
        };
        g.setFont(smallFont);
        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, PixelFont.width(g, s));
        Rectangle r = new Rectangle(16, 16, maxW + 32, 26 + lines.length * 19);
        panel(g, r);
        g.setFont(smallFont);
        for (int k = 0; k < lines.length; k++) {
            g.setColor(k >= 10 ? new Color(220, 160, 100) : TEXT);
            PixelFont.draw(g, lines[k], r.x + 16, r.y + 26 + k * 19);
        }
    }

    private void renderDeath(Graphics2D g) {
        double a = Math.min(1, deathTimer / 1.5);
        dim(g, new Color(40, 0, 0), a < 0.4 ? 1 : a < 0.8 ? 2 : 3);
        centered(g, "You Have Died", vw / 2, vh / 2 - 40, titleFont, Shade.alpha(new Color(200, 30, 20), a));
        centered(g, "Slain on dungeon level " + depth + " at character level " + player.level,
                vw / 2, vh / 2, uiFont, Shade.alpha(TEXT, a));
        if (deathTimer > 1.5) {
            centered(g, "Press Enter to return to town  (you will lose half your gold)", vw / 2, vh / 2 + 30, uiFont, TEXT);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        mouseX = e.getX();
        mouseY = e.getY();
        boolean left = SwingUtilities.isLeftMouseButton(e), right = SwingUtilities.isRightMouseButton(e);
        if (left) {
            for (Button b : buttons) {
                if (b.enabled() && b.r().contains(mouseX, mouseY)) {
                    sfx(Audio.Sfx.CLICK);
                    b.action().run();
                    return;
                }
            }
        }
        if (screen == Screen.MENU || paused || player.dead) return;
        if (shopNpc != null && shopRect().contains(mouseX, mouseY)) {
            shopClick(left);
            return;
        }
        if (showInventory && inventoryRect().contains(mouseX, mouseY)) {
            inventoryClick(left);
            return;
        }
        if (overPanel()) return;
        updateHover();
        if (left) {
            leftDown = true;
            holdMove = false;
            player.useTarget = null;
            if (hoveredDrop != null) {
                player.pickupTarget = hoveredDrop;
                player.attackTarget = null;
                pathTo(player, hoveredDrop.x, hoveredDrop.y, 6000);
            } else if (hoveredNpc != null || hoveredPortal != null) {
                Entity u = hoveredNpc != null ? hoveredNpc : hoveredPortal;
                player.useTarget = u;
                player.attackTarget = null;
                player.pickupTarget = null;
                pathTo(player, u.x, u.y, 6000);
            } else if (hovered != null) {
                player.attackTarget = hovered;
                player.pickupTarget = null;
                player.repathTimer = 0;
            } else {
                player.attackTarget = null;
                player.pickupTarget = null;
                holdMove = true;
                player.repathTimer = 0.15;
                moveTo(mouseWX(), mouseWY());
            }
        } else if (right) {
            rightDown = true;
            castFireball();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            leftDown = false;
            holdMove = false;
        }
        if (SwingUtilities.isRightMouseButton(e)) rightDown = false;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) { mouseMoved(e); }

    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (screen == Screen.MENU) {
            if (k == KeyEvent.VK_ENTER) {
                if (saveExists) loadGame();
                else newGame();
            }
            return;
        }
        if (player.dead) {
            if (k == KeyEvent.VK_ENTER && deathTimer > 1.5) respawn();
            return;
        }
        if (k == KeyEvent.VK_ESCAPE) {
            if (shopNpc != null || showInventory) {
                shopNpc = null;
                showInventory = false;
            } else {
                paused = !paused;
            }
            return;
        }
        if (paused) return;
        switch (k) {
            case KeyEvent.VK_I, KeyEvent.VK_C -> {
                showInventory = !showInventory;
                if (!showInventory) shopNpc = null;
            }
            case KeyEvent.VK_TAB -> showMap = !showMap;
            case KeyEvent.VK_H, KeyEvent.VK_F1 -> showHelp = !showHelp;
            case KeyEvent.VK_1 -> drinkPotion(true);
            case KeyEvent.VK_2 -> drinkPotion(false);
            case KeyEvent.VK_Q -> castNova();
            case KeyEvent.VK_T -> castTownPortal();
            case KeyEvent.VK_M -> {
                audio.toggleMusic();
                notice("Music " + (audio.musicOn() ? "on" : "off"));
            }
            case KeyEvent.VK_F5 -> saveGame(true);
            case KeyEvent.VK_F9 -> {
                if (saveExists) loadGame();
                else notice("No saved game yet");
            }
            default -> { }
        }
    }

    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void keyReleased(KeyEvent e) { }
    @Override public void keyTyped(KeyEvent e) { }
}
