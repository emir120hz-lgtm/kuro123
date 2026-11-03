package kuro;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Safe Anchor: yakindaki respawn anchor ile senin aranda engel yoksa, ikinizin arasina (onune)
 * GLOWSTONE koyar -> patlama hasari engellenir. Sadece glowstone kullanir.
 * Anchor'in kendisine tiklamaz (glowstone anchor'i sarj eder); bunun yerine aradaki bos blogun
 * yanindaki saglam bir blogun yuzune koyar.
 *
 * Yerlestirme: aradaki TUM bos konumlar ve komsu yuzler denenir; her aday icin gozden yuze dogru
 * gercek bir isin (raycast) atilip yuzun gercekten vurulabildigi dogrulanir. Tiklama, (bayat olabilen)
 * mc.crosshairTarget yerine kendi raycast'imizle yapilir; boylece "bazen koyamiyor" sorunu olmaz.
 * Bir aday tutmazsa kara listeye alinip siradaki aday denenir.
 *
 * Patlatma: glowstone'u biz koyduktan sonra (anchor dolu ve aramiz kapaliysa) yavasca bekler, anchor'in
 * gorunen bir yuzune bakar ve glowstone OLMAYAN bir esyayla sag tiklayip anchor'i kendisi patlatir.
 * Nether'de anchor patlamadigi icin orada calismaz.
 */
public final class SafeAnchor {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final double REACH = 4.4;

   private static int cd;
   private static int react = -1;
   private static int idle;
   private static int restore = -1;
   private static int prevSlot = -1;
   private static int stuck;
   private static int warn;
   private static Vec3d aimOff = Vec3d.ZERO;
   private static BlockPos lastAnchor;

   // kara liste: basarisiz (destek blogu, yuz) -> kalan tick
   private static final Map<Long, Integer> bad = new HashMap<>();
   private static Plan current;

   // patlatma
   private static boolean armed;
   private static int armTimer;
   private static int explodeWait = -1;
   private static int exposeFail;
   private static int explodeStuck;

   private record Plan(BlockPos pos, BlockPos support, Direction side, Vec3d aim) {
   }

   private SafeAnchor() {
   }

   public static void reset() {
      react = -1;
      idle = 0;
      stuck = 0;
      lastAnchor = null;
      current = null;
      bad.clear();
      disarm();
      restoreSlot();
   }

   private static void disarm() {
      armed = false;
      armTimer = 0;
      explodeWait = -1;
      exposeFail = 0;
      explodeStuck = 0;
   }

   private static void restoreSlot() {
      if (prevSlot >= 0 && mc.player != null) {
         mc.player.getInventory().selectedSlot = prevSlot;
      }

      prevSlot = -1;
      restore = -1;
   }

   private static long key(BlockPos sup, Direction side) {
      return sup.asLong() * 6L + side.ordinal();
   }

   public static void tick() {
      ClientPlayerEntity p = mc.player;
      if (p == null || mc.world == null || mc.currentScreen != null || Freecam.active) {
         reset();
         return;
      }

      if (restore >= 0 && restore-- == 0) {
         restoreSlot();
      }

      if (cd > 0) {
         cd--;
      }

      if (warn > 0) {
         warn--;
      }

      if (!bad.isEmpty()) {
         Iterator<Map.Entry<Long, Integer>> it = bad.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<Long, Integer> en = it.next();
            if (en.getValue() <= 1) {
               it.remove();
            } else {
               en.setValue(en.getValue() - 1);
            }
         }
      }

      if (armed && --armTimer <= 0) {
         disarm();
      }

      if (mc.world.getRegistryKey() == World.NETHER) {
         reset();
         return;
      }

      Vec3d eye = p.getEyePos();
      boolean fast = Config.saInstant >= 0.5F || Legit.power() >= 2;
      BlockPos anchor = cachedAnchor(p, eye, fast);
      if (anchor == null) {
         idleTick();
         return;
      }

      // arada zaten bir blok varsa korunuyoruz
      if (shielded(eye, anchor)) {
         idle = 0;
         stuck = 0;
         current = null;
         if (armed) {
            explode(p, eye, anchor);
         }

         return;
      }

      int slot = glowstoneSlot(p);
      if (slot < 0) {
         if (warn <= 0) {
            Mods.msg("SafeAnchor: hotbar'da glowstone yok!");
            warn = 200;
         }

         return;
      }

      if (!ready()) {
         return;
      }

      if (!anchor.equals(lastAnchor)) {
         lastAnchor = anchor;
         aimOff = new Vec3d(Legit.noise(0.2F), Legit.noise(0.2F), Legit.noise(0.2F));
         current = null;
         bad.clear();
      }

      // gecerli bir plan yoksa (ya da eskisi artik gecersizse) yenisini bul
      if (current == null || !stillValid(p, eye, current)) {
         current = plan(p, eye, anchor);
      }

      Plan plan = current;
      if (plan == null) {
         return;
      }

      if (fast) {
         Legit.snap(plan.aim());
      } else {
         Legit.aim(plan.aim(), Config.saAimSpeed, Config.acAimNoise);
      }

      BlockHitResult bhr = lookHit(p, eye);
      if (cd <= 0 && bhr != null && bhr.getBlockPos().equals(plan.support()) && bhr.getSide() == plan.side()) {
         if (prevSlot < 0) {
            prevSlot = p.getInventory().selectedSlot;
         }

         p.getInventory().selectedSlot = slot;
         mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
         p.swingHand(Hand.MAIN_HAND);
         cd = fast ? 0 : Legit.rand(Config.saDelayMin, Config.saDelayMax);
         restore = 2;
         stuck = 0;
         // glowstone'u biz koyduk: bir sure sonra anchor'i yavasca patlatabiliriz
         armed = true;
         armTimer = 120;
         explodeWait = -1;
         exposeFail = 0;
         explodeStuck = 0;
      } else if (++stuck > (fast ? 6 : 14)) {
         // nisan bu adayda tutmuyor: kara listeye al, siradaki adayi dene
         bad.put(key(plan.support(), plan.side()), 40);
         current = null;
         stuck = 0;
      }
   }

   private static BlockPos cachedAnchor;
   private static int scanCd;

   /** Uzun menzilde her tick tum kupu taramamak icin sonucu 2 tick saklar; fast modda her tick tarar. */
   private static BlockPos cachedAnchor(ClientPlayerEntity p, Vec3d eye, boolean fast) {
      if (cachedAnchor != null && !mc.world.getBlockState(cachedAnchor).isOf(Blocks.RESPAWN_ANCHOR)) {
         cachedAnchor = null;
         scanCd = 0;
      }

      if (scanCd-- <= 0 || fast && Config.saRange <= 7.0F) {
         cachedAnchor = nearestAnchor(p, eye);
         scanCd = Config.saRange > 5.0F ? 1 : 0;
      }

      return cachedAnchor;
   }

   private static boolean ready() {
      if (Config.saInstant >= 0.5F || Legit.power() >= 2) {
         idle = 0;
         return true;
      }

      idle = 0;
      if (react < 0) {
         react = Legit.rand(Config.saReactMin, Config.saReactMax);
      }

      if (react > 0) {
         react--;
         return false;
      }

      return true;
   }

   private static void idleTick() {
      if (++idle > 4) {
         react = -1;
         stuck = 0;
         lastAnchor = null;
         current = null;
         if (idle > 40) {
            disarm();
         }
      }
   }

   // ------------------------------------------------------------------ yerlestirme

   /** Su an baktigimiz yonde (sessiz aci aciksa o aciyla) 4.5 blokluk isin; vurulan blogu dondurur. */
   private static BlockHitResult lookHit(ClientPlayerEntity p, Vec3d eye) {
      float yaw = SilentRot.enabled() && SilentRot.active ? SilentRot.yaw : p.getYaw();
      float pitch = SilentRot.enabled() && SilentRot.active ? SilentRot.pitch : p.getPitch();
      Vec3d look = Vec3d.fromPolar(pitch, yaw);
      BlockHitResult r = mc.world.raycast(new RaycastContext(eye, eye.add(look.multiply(4.5)), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
      return r.getType() == HitResult.Type.BLOCK ? r : null;
   }

   /** Gozden verilen noktaya isin at; ilk vurulan blok/yuz beklenen mi? Beklenen ise vurus sonucunu dondurur. */
   private static BlockHitResult sees(ClientPlayerEntity p, Vec3d eye, Vec3d pt, BlockPos block, Direction side) {
      Vec3d dir = pt.subtract(eye);
      double len = dir.length();
      if (len < 0.05 || len > REACH) {
         return null;
      }

      Vec3d end = eye.add(dir.multiply((len + 0.25) / len));
      BlockHitResult r = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
      if (r.getType() != HitResult.Type.BLOCK || !r.getBlockPos().equals(block)) {
         return null;
      }

      if (side != null && r.getSide() != side) {
         return null;
      }

      return r;
   }

   /** Bir blok yuzunun ustunde nisan noktalari: once sapmali nokta, sonra merkez, sonra 4 kenar noktasi. */
   private static Vec3d[] facePoints(BlockPos sup, Direction side, boolean jitter) {
      Vec3d c = Vec3d.ofCenter(sup).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
      Vec3d u;
      Vec3d v;
      switch (side.getAxis()) {
         case X -> {
            u = new Vec3d(0.0, 1.0, 0.0);
            v = new Vec3d(0.0, 0.0, 1.0);
         }
         case Y -> {
            u = new Vec3d(1.0, 0.0, 0.0);
            v = new Vec3d(0.0, 0.0, 1.0);
         }
         default -> {
            u = new Vec3d(1.0, 0.0, 0.0);
            v = new Vec3d(0.0, 1.0, 0.0);
         }
      }

      double a = jitter ? Math.max(-0.2, Math.min(0.2, aimOff.x)) : 0.0;
      double b = jitter ? Math.max(-0.2, Math.min(0.2, aimOff.y)) : 0.0;
      return new Vec3d[]{
         c.add(u.multiply(a)).add(v.multiply(b)),
         c,
         c.add(u.multiply(0.25)), c.subtract(u.multiply(0.25)),
         c.add(v.multiply(0.25)), c.subtract(v.multiply(0.25))
      };
   }

   private static boolean stillValid(ClientPlayerEntity p, Vec3d eye, Plan pl) {
      if (bad.containsKey(key(pl.support(), pl.side()))) {
         return false;
      }

      if (!mc.world.getBlockState(pl.pos()).isReplaceable()) {
         return false;
      }

      BlockState ss = mc.world.getBlockState(pl.support());
      if (!ss.isSolidBlock(mc.world, pl.support())) {
         return false;
      }

      return sees(p, eye, pl.aim(), pl.support(), pl.side()) != null;
   }

   /**
    * Goz -> anchor dogrusu uzerindeki TUM bos konumlari ve onlarin komsu saglam yuzlerini dener.
    * Yuzun gercekten gorulup vurulabildigi raycast ile dogrulanir. En az donus gerektiren aday secilir.
    */
   private static Plan plan(ClientPlayerEntity p, Vec3d eye, BlockPos anchor) {
      Vec3d target = Vec3d.ofCenter(anchor);
      Vec3d dir = target.subtract(eye);
      double len = dir.length();
      if (len < 0.8) {
         return null;
      }

      dir = dir.multiply(1.0 / len);
      Box pb = p.getBoundingBox();
      Vec3d look = Vec3d.fromPolar(SilentRot.enabled() && SilentRot.active ? SilentRot.pitch : p.getPitch(),
         SilentRot.enabled() && SilentRot.active ? SilentRot.yaw : p.getYaw());

      Set<BlockPos> line = new LinkedHashSet<>();
      for (double t = 0.6; t < len - 0.45; t += 0.25) {
         BlockPos pos = BlockPos.ofFloored(eye.add(dir.multiply(t)));
         if (!pos.equals(anchor)) {
            line.add(pos);
         }
      }

      Plan best = null;
      double bestScore = Double.MAX_VALUE;
      for (BlockPos pos : line) {
         if (!mc.world.getBlockState(pos).isReplaceable()) {
            continue;
         }

         Box bx = new Box(pos);
         if (pb.intersects(bx) || !mc.world.getOtherEntities(p, bx, e -> !(e instanceof ItemEntity)).isEmpty()) {
            continue;
         }

         for (Direction d : Direction.values()) {
            BlockPos sup = pos.offset(d);
            if (sup.equals(anchor)) {
               continue;
            }

            BlockState ss = mc.world.getBlockState(sup);
            if (!ss.isSolidBlock(mc.world, sup) || unusable(ss.getBlock())) {
               continue;
            }

            Direction side = d.getOpposite();
            if (bad.containsKey(key(sup, side))) {
               continue;
            }

            Vec3d[] pts = facePoints(sup, side, true);
            for (Vec3d pt : pts) {
               if (sees(p, eye, pt, sup, side) == null) {
                  continue;
               }

               Vec3d to = pt.subtract(eye).normalize();
               double ang = Math.acos(Math.max(-1.0, Math.min(1.0, to.dotProduct(look))));
               double score = ang + pt.distanceTo(eye) * 0.04;
               if (score < bestScore) {
                  bestScore = score;
                  best = new Plan(pos, sup, side, pt);
               }

               break;
            }
         }
      }

      return best;
   }

   // ------------------------------------------------------------------ patlatma

   /** Gozden anchor'in gorunen bir yuzune (glowstone'un yanindan) nokta bulur. */
   private static Vec3d exposedPoint(ClientPlayerEntity p, Vec3d eye, BlockPos anchor) {
      Vec3d look = Vec3d.fromPolar(SilentRot.enabled() && SilentRot.active ? SilentRot.pitch : p.getPitch(),
         SilentRot.enabled() && SilentRot.active ? SilentRot.yaw : p.getYaw());
      Vec3d best = null;
      double bestAng = Double.MAX_VALUE;
      for (Direction d : Direction.values()) {
         Vec3d fc = Vec3d.ofCenter(anchor).add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
         double facing = (eye.x - fc.x) * d.getOffsetX() + (eye.y - fc.y) * d.getOffsetY() + (eye.z - fc.z) * d.getOffsetZ();
         if (facing <= 0.0) {
            continue;
         }

         for (Vec3d pt : facePoints(anchor, d, false)) {
            if (sees(p, eye, pt, anchor, d) == null) {
               continue;
            }

            double ang = Math.acos(Math.max(-1.0, Math.min(1.0, pt.subtract(eye).normalize().dotProduct(look))));
            if (ang < bestAng) {
               bestAng = ang;
               best = pt;
            }
         }
      }

      return best;
   }

   private static int nonGlowstoneSlot(ClientPlayerEntity p) {
      int cur = p.getInventory().selectedSlot;
      if (!p.getInventory().getStack(cur).isOf(Items.GLOWSTONE)) {
         return cur;
      }

      for (int i = 0; i < 9; i++) {
         if (!p.getInventory().getStack(i).isOf(Items.GLOWSTONE)) {
            return i;
         }
      }

      return -1;
   }

   /** Glowstone'u koyduktan sonra YAVASCA bekler, anchor'a bakar ve glowstone'suz elle sag tiklayip patlatir. */
   private static void explode(ClientPlayerEntity p, Vec3d eye, BlockPos anchor) {
      if (Config.saExplode < 0.5F) {
         return;
      }

      BlockState st = mc.world.getBlockState(anchor);
      if (!st.isOf(Blocks.RESPAWN_ANCHOR) || st.get(RespawnAnchorBlock.CHARGES) <= 0) {
         disarm();
         return;
      }

      if (p.getOffHandStack().isOf(Items.GLOWSTONE)) {
         // ofhand'de glowstone varken anchor'a tiklamak patlatmaz, sarj eder
         if (warn <= 0) {
            Mods.msg("SafeAnchor: offhand'de glowstone var, patlatılamıyor");
            warn = 200;
         }

         disarm();
         return;
      }

      if (cd > 0 || restore >= 0) {
         return;
      }

      if (explodeWait < 0) {
         explodeWait = Legit.randRaw(Config.saExplodeMin, Config.saExplodeMax);
      }

      if (explodeWait > 0) {
         explodeWait--;
         return;
      }

      Vec3d pt = exposedPoint(p, eye, anchor);
      if (pt == null) {
         if (++exposeFail > 40) {
            disarm();
         }

         return;
      }

      exposeFail = 0;
      // patlatirken hep insan hizinda ve yavas nisan al
      Legit.aim(pt, Config.saAimSpeed, Config.acAimNoise);
      BlockHitResult bhr = lookHit(p, eye);
      if (bhr != null && bhr.getBlockPos().equals(anchor)) {
         int slot = nonGlowstoneSlot(p);
         if (slot < 0) {
            disarm();
            return;
         }

         if (prevSlot < 0) {
            prevSlot = p.getInventory().selectedSlot;
         }

         p.getInventory().selectedSlot = slot;
         mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr);
         p.swingHand(Hand.MAIN_HAND);
         restore = 2;
         cd = 10;
         disarm();
      } else if (++explodeStuck > 60) {
         disarm();
      }
   }

   /** Sag tikta GUI acan / etkilesen bloklara tiklama. */
   private static boolean unusable(Block b) {
      return b instanceof BlockWithEntity || b instanceof CraftingTableBlock || b instanceof RespawnAnchorBlock;
   }

   private static BlockPos nearestAnchor(ClientPlayerEntity p, Vec3d eye) {
      double range = Config.saRange;
      int r = (int)Math.ceil(range);
      BlockPos o = p.getBlockPos();
      BlockPos best = null;
      double bd = range * range;
      boolean onlyCharged = Config.saOnlyCharged >= 0.5F;

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               BlockPos pos = new BlockPos(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
               BlockState st = mc.world.getBlockState(pos);
               if (!st.isOf(Blocks.RESPAWN_ANCHOR)) {
                  continue;
               }

               if (onlyCharged && st.get(RespawnAnchorBlock.CHARGES) <= 0) {
                  continue;
               }

               double d = Vec3d.ofCenter(pos).squaredDistanceTo(eye);
               if (d < bd) {
                  bd = d;
                  best = pos;
               }
            }
         }
      }

      return best;
   }

   /** Anchor ile goz arasinda baska bir blok varsa korunmus say. */
   private static boolean shielded(Vec3d eye, BlockPos anchor) {
      Vec3d c = Vec3d.ofCenter(anchor);
      BlockHitResult r = mc.world.raycast(new RaycastContext(eye, c, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
      return r.getType() == HitResult.Type.BLOCK && !r.getBlockPos().equals(anchor);
   }

   /** Sadece glowstone (hotbar). */
   private static int glowstoneSlot(ClientPlayerEntity p) {
      for (int i = 0; i < 9; i++) {
         ItemStack s = p.getInventory().getStack(i);
         if (s.isOf(Items.GLOWSTONE)) {
            return i;
         }
      }

      return -1;
   }
}
