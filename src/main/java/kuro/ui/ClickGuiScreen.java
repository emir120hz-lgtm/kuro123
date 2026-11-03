package kuro.ui;

import static org.lwjgl.nanovg.NanoVG.*;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import kuro.Config;
import kuro.Kuro;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** Dort sutunlu panel gorunumlu menu (kategori basliklari, alt sekmeler, yuvarlak dugmeler). */
public class ClickGuiScreen extends Screen {
   private static final float PW = 168.0F;
   private static final float GAP = 8.0F;
   private static final float TOP = 26.0F;
   private static final float HEAD = 30.0F;
   private static final float TABS_H = 24.0F;
   private static final float BTN_H = 22.0F;
   private static final float BTN_STEP = 25.0F;
   private static final float TOG_H = 24.0F;
   private static final float SLD_H = 30.0F;

   private static final int PANEL_BG = 0xF2101013;
   private static final int PANEL_BORDER = 0x2EFFFFFF;
   private static final int BTN = 0xFF1B1B1F;
   private static final int BTN_HOVER = 0xFF26262B;
   private static final int BTN_ON = 0xFF3E3E46;
   private static final int ROW_BG = 0xFF151518;

   private record Entry(String name, int mod, MenuScreen.Slider set) {
   }

   private record Group(String name, Entry[] items) {
   }

   private record Panel(String title, Group[] groups) {
   }

   private static Entry m(int id) {
      return new Entry(Kuro.NAMES[id], id, null);
   }

   private static Entry t(String name, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
      return new Entry(name, -1, new MenuScreen.Slider(name, 0.0F, 1.0F, 1.0F, () -> get.getAsBoolean() ? 1.0 : 0.0, v -> set.accept(v >= 0.5)));
   }

   private static final Panel[] PANELS = new Panel[]{
      new Panel("Savaş", new Group[]{
         new Group("Saldırı", new Entry[]{m(Kuro.KILLAURA), m(Kuro.TRIGGERBOT), m(Kuro.REACH), m(Kuro.HITBOXES), m(Kuro.SHIELDBREAKER), m(Kuro.COBWEB), m(Kuro.AUTOCLICKER), m(Kuro.CRITICALS), m(Kuro.WTAP), m(Kuro.SILENTROT)}),
         new Group("Kristal", new Entry[]{m(Kuro.CRYSTAL), m(Kuro.CRYSTALBASE), m(Kuro.CRYSTALOPT), m(Kuro.MISPLACE), m(Kuro.CMACRO), m(Kuro.AMACRO)}),
         new Group("Koruma", new Entry[]{m(Kuro.TOTEM), m(Kuro.SAFEANCHOR), m(Kuro.VELOCITY), m(Kuro.JUMPRESET), m(Kuro.AUTOARMOR), m(Kuro.AUTOLOG), m(Kuro.ANTITRAP), m(Kuro.AIMASSIST), m(Kuro.KEYPEARL), m(Kuro.HOVERTOTEM), m(Kuro.DOUBLEHAND), m(Kuro.SURROUND), m(Kuro.FASTXP), m(Kuro.REPLENISH)})
      }),
      new Panel("Hareket", new Group[]{
         new Group("Temel", new Entry[]{m(Kuro.FLY), m(Kuro.SPEED), m(Kuro.SPRINT), m(Kuro.NOFALL), m(Kuro.ELYTRASWAP), m(Kuro.STEP)})
      }),
      new Panel("Görsel", new Group[]{
         new Group("ESP", new Entry[]{m(Kuro.PLAYER), m(Kuro.STORAGE), m(Kuro.BLOCK), m(Kuro.XRAY), m(Kuro.CLEARRENDER), m(Kuro.ITEMESP), m(Kuro.NAMETAGS), m(Kuro.GLOWESP), m(Kuro.TARGETESP), m(Kuro.PEARLTRAJ), m(Kuro.JUMPCIRCLES)}),
         new Group("Animasyon", new Entry[]{m(Kuro.VIEWMODEL), m(Kuro.SWINGANIM), m(Kuro.NOHURTCAM)}),
         new Group("Kozmetik", new Entry[]{m(Kuro.FULLBRIGHT), m(Kuro.WORLDTIME), m(Kuro.ZOOM), m(Kuro.TARGETHUD), m(Kuro.NOBOB), m(Kuro.RADAR), m(Kuro.SPOTIFY), m(Kuro.HITPARTICLES), m(Kuro.LOWHP), m(Kuro.CROSSHAIR), m(Kuro.COLORGRADE), m(Kuro.ATMOSPHERE)})
      }),
      new Panel("Donut", new Group[]{
         new Group("Varlık ESP", new Entry[]{m(Kuro.PILLAGERESP), m(Kuro.VILLAGERESP), m(Kuro.WANDERESP), m(Kuro.DROWNEDESP), m(Kuro.INVISESP)}),
         new Group("Yapı bulucu", new Entry[]{m(Kuro.TUNNELESP), m(Kuro.HOLE1ESP), m(Kuro.VOIDESP), m(Kuro.AMETHYSTESP), m(Kuro.KELPESP), m(Kuro.SPAWNERESP), m(Kuro.STASHESP), m(Kuro.CHUNKESP)}),
         new Group("Blok ESP", new Entry[]{m(Kuro.DEEPSLATEESP), m(Kuro.ROTDEEPESP), m(Kuro.DRIPESP), m(Kuro.VINEESP), m(Kuro.BERRYESP), m(Kuro.BEEESP), m(Kuro.PISTONESP), m(Kuro.LIGHTESP), m(Kuro.COLLECTESP), m(Kuro.SKELETONESP)})
      }),
      new Panel("Araçlar", new Group[]{
         new Group("Dünya", new Entry[]{m(Kuro.FREECAM), m(Kuro.FREELOOK), m(Kuro.BASES), m(Kuro.FASTPLACE), m(Kuro.AUTOTOOL), m(Kuro.CHESTSTEALER), m(Kuro.AUTOEAT), m(Kuro.AUTORESPAWN)}),
         new Group("Test", new Entry[]{m(Kuro.FAKEPLAYER), m(Kuro.MONEYFMT), m(Kuro.FAKEBAN), m(Kuro.FAKESCOREBOARD)}),
         new Group("Ayarlar", new Entry[]{
            m(Kuro.CFGCOPY),
            m(Kuro.CFGPASTE),
            m(Kuro.HUDEDIT),
            t("Filigran", () -> Config.watermark, v -> Config.watermark = v),
            t("Modül listesi", () -> Config.moduleList, v -> Config.moduleList = v),
            t("Kısayol paneli", () -> Config.keybinds, v -> Config.keybinds = v),
            t("Bilgi çubuğu", () -> Config.info, v -> Config.info = v),
            t("Bildirimler", () -> Config.toasts, v -> Config.toasts = v),
            t("Sütunlu menü", () -> Config.columnMenu, v -> Config.columnMenu = v),
            t("3D GUI", () -> Config.gui3d, v -> Config.gui3d = v),
            new Entry("3D mesafe", -1, new MenuScreen.Slider("3D mesafe", 0.0F, 2.0F, 1.0F, () -> Config.gui3dDist, v -> Config.gui3dDist = (float)v)),
            new Entry("Tema rengi", -1, new MenuScreen.Slider("Tema rengi", 0.0F, 7.0F, 1.0F, () -> Config.accent, v -> Config.accent = (int)v))
         })
      })
   };

   private static final int T_TAB = 0;
   private static final int T_MOD = 1;
   private static final int T_SLIDER = 2;

   private record Hit(int type, int a, int b, MenuScreen.Slider s, float x, float y, float w, float h, int pan) {
      boolean contains(float px, float py) {
         return px >= x && px <= x + w && py >= y && py <= y + h;
      }
   }

   private final List<Hit> hits = new ArrayList<>();
   private Hit drag;
   private static final int SP = PANELS.length; // arama sonuc paneli icin ek slot
   private static final float SPW = 232.0F;
   private String query = "";
   private final int[] tab = new int[PANELS.length + 1];
   private final Anim[] scroll = new Anim[PANELS.length + 1];
   private final float[] scrollTarget = new float[PANELS.length + 1];
   private final float[] contentH = new float[PANELS.length + 1];
   private final float[] viewH = new float[PANELS.length + 1];
   private final float[] panelX = new float[PANELS.length + 1];
   private final float[] panelY = new float[PANELS.length + 1];
   private final Anim[] hover = new Anim[Kuro.NAMES.length];
   private final Anim[] on = new Anim[Kuro.NAMES.length];
   private final Anim[] exp = new Anim[Kuro.NAMES.length];
   private final Anim[] setHover = new Anim[64];
   private final Anim[] setOn = new Anim[64];
   private int listening = -1;
   private int curPan;
   private float prog;
   private boolean closing;
   private long last = System.nanoTime();
   private float uiScale = 1.0F;
   private float totalW;

   public ClickGuiScreen() {
      super(Text.literal("Kuro"));
      for (int i = 0; i <= PANELS.length; i++) {
         scroll[i] = new Anim(0.0F);
      }

      for (int i = 0; i < Kuro.NAMES.length; i++) {
         hover[i] = new Anim(0.0F);
         on[i] = new Anim(Kuro.on[i] ? 1.0F : 0.0F);
         exp[i] = new Anim(0.0F);
      }

      for (int i = 0; i < setHover.length; i++) {
         setHover[i] = new Anim(0.0F);
         setOn[i] = new Anim(0.0F);
      }

      totalW = PANELS.length * PW + (PANELS.length - 1) * GAP;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void close() {
      closing = true;
      listening = -1;
      drag = null;
   }

   // ---------------------------------------------------------------- yardimcilar

   /** Metin verilen genislige sigmazsa sonuna "…" koyup keser (taşmayı önler). */
   /** Sekme yazisi: sigana kadar kucult (en az 8.5). */
   private static float tabFont(String s, float maxW) {
      float[] sizes = {11.0F, 10.0F, 9.5F, 9.0F, 8.5F};
      for (float f : sizes) {
         if (Nvg.textWidth(s, f, false) <= maxW) {
            return f;
         }
      }

      return 8.5F;
   }

   private static String fit(String s, float size, float maxW) {
      if (Nvg.textWidth(s, size, false) <= maxW) {
         return s;
      }

      int n = s.length();
      while (n > 1 && Nvg.textWidth(s.substring(0, n) + "…", size, false) > maxW) {
         n--;
      }

      return s.substring(0, n).trim() + "…";
   }

   private static boolean isToggle(MenuScreen.Slider s) {
      return s.min() == 0.0F && s.max() == 1.0F && s.step() == 1.0F;
   }

   private static float rowH(MenuScreen.Slider s) {
      return isToggle(s) ? TOG_H : SLD_H;
   }

   private static float rowsH(MenuScreen.Slider[] sl) {
      float h = 0.0F;
      for (MenuScreen.Slider s : sl) {
         h += rowH(s);
      }

      return h;
   }

   private static boolean isAction(int mod) {
      return mod >= 0 && Kuro.isAction(mod);
   }

   private float mouseX() {
      if (mode3d) {
         return curX;
      }

      Window w = Kuro.mc.getWindow();
      return (float)(Kuro.mc.mouse.getX() * ((double)w.getFramebufferWidth() / (double)w.getWidth()) / uiScale);
   }

   private float mouseY() {
      if (mode3d) {
         return curY;
      }

      Window w = Kuro.mc.getWindow();
      return (float)(Kuro.mc.mouse.getY() * ((double)w.getFramebufferHeight() / (double)w.getHeight()) / uiScale);
   }

   private float itemsHeight(Group g) {
      float h = 0.0F;
      for (Entry e : g.items) {
         if (e.mod >= 0) {
            MenuScreen.Slider[] sl = MenuScreen.SLIDERS[e.mod];
            h += BTN_STEP + (sl == null ? 0.0F : exp[e.mod].v * (rowsH(sl) + 4.0F));
         } else if (isToggle(e.set)) {
            h += BTN_STEP;
         } else {
            h += SLD_H + 2.0F;
         }
      }

      return h;
   }

   // ---------------------------------------------------------------- arama

   private boolean searching() {
      return !query.isEmpty();
   }

   /** Tum panellerdeki modul ve ayarlar arasindan sorguya uyanlari (en iyi eslesme once) tek bir panel olarak dondurur. */
   private Panel searchPanel() {
      List<Entry> found = new ArrayList<>();
      List<Integer> score = new ArrayList<>();
      java.util.Set<Integer> seen = new java.util.HashSet<>();
      for (Panel pn : PANELS) {
         for (Group g : pn.groups) {
            for (Entry en : g.items) {
               int r;
               if (en.mod >= 0) {
                  if (!seen.add(en.mod)) {
                     continue;
                  }

                  r = Search.rankMod(en.mod, query);
               } else {
                  r = Search.rank(en.name, "", query);
               }

               if (r >= 0) {
                  int at = 0;
                  while (at < found.size() && score.get(at) <= r) {
                     at++;
                  }

                  found.add(at, en);
                  score.add(at, r);
               }
            }
         }
      }

      return new Panel("Arama", new Group[]{new Group("Sonuçlar (" + found.size() + ")", found.toArray(new Entry[0]))});
   }

   private void queryChanged() {
      scrollTarget[SP] = 0.0F;
      scroll[SP].snap(0.0F);
      tab[SP] = 0;
   }

   private void drawSearchBar(float lw, float dt, float e0) {
      float w = 230.0F;
      float h = 18.0F;
      float x = (lw - w) * 0.5F;
      float y = 4.0F;
      int accent = Theme.accent();
      boolean has = searching();
      nvgSave(Nvg.ctx);
      nvgGlobalAlpha(Nvg.ctx, e0);
      Nvg.rect(x, y, w, h, 9.0F, mode3d ? Theme.alpha(Theme.mix(0xFF140F16, accent, 0.30F), 0.78F) : 0xF2101013);
      Nvg.border(x, y, w, h, 9.0F, 1.0F, has ? Theme.a(accent, 0.7F) : PANEL_BORDER);
      // buyutec simgesi
      Nvg.circleStroke(x + 13.0F, y + 8.5F, 3.6F, 1.2F, has ? accent : Theme.TEXT_DIM);
      Nvg.line(x + 15.6F, y + 11.1F, x + 18.4F, y + 13.9F, 1.4F, has ? accent : Theme.TEXT_DIM);
      if (has) {
         String shown = fit(query, 11.5F, w - 70.0F);
         float tw = Nvg.textL(shown, x + 26.0F, y + h * 0.5F + 0.5F, 11.5F, false, Theme.TEXT);
         if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
            Nvg.line(x + 27.0F + tw, y + 4.5F, x + 27.0F + tw, y + h - 4.5F, 1.0F, accent);
         }

         Nvg.textR("Esc: temizle", x + w - 9.0F, y + h * 0.5F + 0.5F, 9.0F, false, Theme.TEXT_MUTED);
      } else {
         Nvg.textL("Modül ara… (yazmaya başla)", x + 26.0F, y + h * 0.5F + 0.5F, 11.0F, false, Theme.TEXT_MUTED);
      }

      nvgRestore(Nvg.ctx);
   }

   // ---------------------------------------------------------------- Screen

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      if (!Nvg.ensure()) {
         context.drawCenteredTextWithShadow(this.textRenderer, "NanoVG yüklenemedi (log dosyasına bak). Esc ile kapat.", this.width / 2, this.height / 2, 0xFFFFFF);
         if (closing) {
            Kuro.mc.setScreen(null);
         }

         return;
      }

      if (mode3d) {
         try {
            render3d(context);
         } catch (Throwable t) {
            Kuro.LOGGER.error("3D GUI hatası, 2D menüye dönülüyor", t);
            Config.gui3d = false;
            Config.save();
            freeFbo();
            Kuro.mc.execute(() -> Kuro.mc.setScreen(new ClickGuiScreen()));
         }

         return;
      }

      context.draw();
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;
      prog = Anim.clamp(prog + (closing ? -dt : dt) / 0.28F, 0.0F, 1.0F);

      Window win = Kuro.mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0) {
         return;
      }

      uiScale = Math.min(Theme.scale(fh), (float)fw / (totalW + 24.0F));
      float lw = fw / uiScale;
      float lh = fh / uiScale;
      Theme.update(dt);

      Nvg.begin(lw, lh, uiScale);
      try {
         draw(lw, lh, dt);
      } finally {
         Nvg.end();
      }

      if (closing && prog <= 0.0F) {
         Kuro.mc.setScreen(null);
      }
   }

   private void draw(float lw, float lh, float dt) {
      hits.clear();
      rects.clear();
      float e0 = Anim.easeOutCubic(prog);
      if (!mode3d) {
         Nvg.rect(0.0F, 0.0F, lw, lh, 0.0F, Theme.alpha(0x000000, 0.34F * e0));
      }

      float mx = mouseX();
      float my = mouseY();
      int accent = Theme.accent();
      float x0 = (lw - totalW) * 0.5F;
      float maxH = lh - TOP * 2.0F;
      int setIdx = 0;

      boolean srch = searching();
      Panel[] view = srch ? new Panel[]{searchPanel()} : PANELS;
      for (int vp = 0; vp < view.length; vp++) {
         int p = srch ? SP : vp;
         float pw = srch ? SPW : PW;
         Panel pn = view[vp];
         float e = Anim.easeOutCubic(Anim.clamp(prog * 1.5F - vp * 0.1F, 0.0F, 1.0F));
         if (e <= 0.0F) {
            continue;
         }

         Group g = pn.groups[Math.min(tab[p], pn.groups.length - 1)];
         float x = srch ? (lw - pw) * 0.5F : x0 + p * (pw + GAP);
         float y = TOP;
         float body = itemsHeight(g);
         float h = Math.min(maxH, HEAD + TABS_H + 8.0F + body + 6.0F);
         float viewTop = y + HEAD + TABS_H + 6.0F;
         float viewBot = y + h - 6.0F;
         float viewHt = Math.max(0.0F, viewBot - viewTop);
         contentH[p] = body;
         viewH[p] = viewHt;
         panelX[p] = x;
         panelY[p] = y;
         panelW[p] = pw;
         panelH[p] = h;
         curPan = p;
         if (mode3d) {
            rects.add(new float[]{p, x, y, pw, h});
         }
         scrollTarget[p] = Anim.clamp(scrollTarget[p], 0.0F, Math.max(0.0F, body - viewHt));
         scroll[p].to(scrollTarget[p]).tick(dt, 18.0F);

         nvgSave(Nvg.ctx);
         nvgGlobalAlpha(Nvg.ctx, e);
         nvgTranslate(Nvg.ctx, 0.0F, (1.0F - e) * 14.0F);

         Nvg.glow(x, y + 3.0F, pw, h, 8.0F, 18.0F, 0x66000000);
         Nvg.rect(x, y, pw, h, 8.0F, mode3d ? Theme.alpha(Theme.mix(0xFF140F16, accent, 0.30F), 0.78F) : PANEL_BG);
         Nvg.border(x, y, pw, h, 8.0F, 1.0F, mode3d ? Theme.a(accent, 0.55F) : PANEL_BORDER);
         Nvg.textC(pn.title, x + pw * 0.5F, y + HEAD * 0.5F + 2.0F, 17.0F, true, accent);

         // alt sekmeler
         float tx = x + 6.0F;
         float tw = (pw - 12.0F) / pn.groups.length;
         for (int i = 0; i < pn.groups.length; i++) {
            boolean sel = i == tab[p];
            float bx = tx + i * tw;
            float by = y + HEAD;
            if (sel) {
               Nvg.border(bx + 2.0F, by, tw - 4.0F, 18.0F, 4.0F, 1.0F, 0xB0FFFFFF);
            }

            float tsz = tabFont(pn.groups[i].name, tw - 6.0F);
            Nvg.textC(fit(pn.groups[i].name, tsz, tw - 4.0F), bx + tw * 0.5F, by + 9.5F, tsz, false, sel ? Theme.TEXT : Theme.TEXT_DIM);
            hits.add(new Hit(T_TAB, p, i, null, bx, by, tw, 18.0F, p));
         }

         // icerik (kaydirilabilir)
         nvgSave(Nvg.ctx);
         nvgScissor(Nvg.ctx, x + 2.0F, viewTop, pw - 4.0F, viewHt);
         float cy = viewTop - scroll[p].v;
         float bx = x + 6.0F;
         float bw = pw - 12.0F;

         for (Entry en : g.items) {
            if (en.mod >= 0) {
               int id = en.mod;
               boolean action = isAction(id) || id == Kuro.BASES;
               MenuScreen.Slider[] sl = MenuScreen.SLIDERS[id];
               boolean over = mx >= bx && mx <= bx + bw && my >= cy && my <= cy + BTN_H && my >= viewTop && my <= viewBot;
               hover[id].to(over ? 1.0F : 0.0F).tick(dt, 16.0F);
               on[id].to(!action && Kuro.on[id] ? 1.0F : 0.0F).tick(dt, 14.0F);
               exp[id].to(sl != null && exp[id].t > 0.5F ? 1.0F : 0.0F).tick(dt, 14.0F);

               int fill = Theme.mix(Theme.mix(BTN, BTN_HOVER, hover[id].v), BTN_ON, on[id].v);
               Nvg.rect(bx, cy, bw, BTN_H, 6.0F, mode3d ? Theme.a(fill, 0.6F) : fill);
               int txt = Theme.mix(0xFFC8C8D2, Theme.TEXT, Math.max(on[id].v, hover[id].v * 0.7F));
               boolean bound = listening != id && Kuro.binds[id] >= 0;
               float keyW = bound ? Nvg.textWidth(Keys.name(Kuro.binds[id]), 9.5F, false) + 14.0F : 0.0F;
               float rightW = sl != null ? 22.0F : 8.0F;
               float side = Math.max(keyW, rightW);
               Nvg.textC(fit(en.name, 12.5F, bw - side * 2.0F), bx + bw * 0.5F, cy + BTN_H * 0.5F + 0.5F, 12.5F, false, txt);
               if (sl != null) {
                  Nvg.textR(exp[id].t > 0.5F ? "-" : "+", bx + bw - 8.0F, cy + BTN_H * 0.5F + 0.5F, 13.0F, false, Theme.TEXT_DIM);
               }

               if (listening == id) {
                  Nvg.rect(bx, cy, bw, BTN_H, 6.0F, 0xE6101013);
                  Nvg.textC("Tuşa bas...", bx + bw * 0.5F, cy + BTN_H * 0.5F + 0.5F, 12.0F, false, accent);
               } else if (Kuro.binds[id] >= 0) {
                  Nvg.textL(Keys.name(Kuro.binds[id]), bx + 6.0F, cy + BTN_H * 0.5F + 0.5F, 9.5F, false, Theme.TEXT_DIM);
               }

               addHit(T_MOD, id, 0, null, bx, cy, bw, BTN_H, viewTop, viewBot);
               cy += BTN_STEP;

               if (sl != null && exp[id].v > 0.01F) {
                  float extra = exp[id].v * (rowsH(sl) + 4.0F);
                  nvgSave(Nvg.ctx);
                  nvgIntersectScissor(Nvg.ctx, bx, cy - 3.0F, bw, extra);
                  nvgGlobalAlpha(Nvg.ctx, exp[id].v * e);
                  float ry = cy;
                  for (int i = 0; i < sl.length; i++) {
                     ry += drawRow(sl[i], id, i, bx, ry, bw, mx, my, viewTop, viewBot, dt, accent);
                  }

                  nvgRestore(Nvg.ctx);
                  cy += extra;
               }
            } else if (isToggle(en.set)) {
               int k = Math.min(setIdx++, setHover.length - 1);
               boolean val = en.set.get().getAsDouble() >= 0.5;
               boolean over = mx >= bx && mx <= bx + bw && my >= cy && my <= cy + BTN_H && my >= viewTop && my <= viewBot;
               setHover[k].to(over ? 1.0F : 0.0F).tick(dt, 16.0F);
               setOn[k].to(val ? 1.0F : 0.0F).tick(dt, 14.0F);
               int fill = Theme.mix(Theme.mix(BTN, BTN_HOVER, setHover[k].v), BTN_ON, setOn[k].v);
               Nvg.rect(bx, cy, bw, BTN_H, 6.0F, mode3d ? Theme.a(fill, 0.6F) : fill);
               Nvg.textC(fit(en.name, 12.5F, bw - 16.0F), bx + bw * 0.5F, cy + BTN_H * 0.5F + 0.5F, 12.5F, false, Theme.mix(0xFFC8C8D2, Theme.TEXT, Math.max(setOn[k].v, setHover[k].v * 0.7F)));
               addHit(T_SLIDER, 0, 0, en.set, bx, cy, bw, BTN_H, viewTop, viewBot);
               cy += BTN_STEP;
            } else {
               drawRow(en.set, -1, 0, bx, cy, bw, mx, my, viewTop, viewBot, dt, accent);
               cy += SLD_H + 2.0F;
            }
         }

         if (g.items.length == 0) {
            Nvg.textC("Sonuç yok", x + pw * 0.5F, viewTop + 18.0F, 11.5F, false, Theme.TEXT_MUTED);
         }

         nvgRestore(Nvg.ctx);
         nvgRestore(Nvg.ctx);
      }

      if (mode3d) {
         rects.add(new float[]{-1.0F, (lw - 230.0F) * 0.5F, 4.0F, 230.0F, 18.0F});
      }

      drawSearchBar(lw, dt, e0);
   }

   private void addHit(int type, int a, int b, MenuScreen.Slider s, float x, float y, float w, float h, float top, float bot) {
      float y0 = Math.max(y, top);
      float y1 = Math.min(y + h, bot);
      if (y1 > y0) {
         hits.add(new Hit(type, a, b, s, x, y0, w, y1 - y0, curPan));
      }
   }

   /** Ayar satiri (anahtar veya surgu). Yuksekligi dondurur. */
   private float drawRow(MenuScreen.Slider s, int mod, int idx, float x, float y, float w, float mx, float my, float top, float bot, float dt, int accent) {
      float h = rowH(s);
      double v = s.get().getAsDouble();
      float th = h - 2.0F;
      Nvg.rect(x, y, w, th, 5.0F, mode3d ? Theme.a(ROW_BG, 0.6F) : ROW_BG);
      if (isToggle(s)) {
         Nvg.textL(fit(s.name(), 11.0F, w - 50.0F), x + 8.0F, y + th * 0.5F + 0.5F, 11.0F, false, Theme.TEXT);
         float sx = x + w - 30.0F;
         float sy = y + (th - 12.0F) * 0.5F;
         boolean o = v >= 0.5;
         Nvg.rect(sx, sy, 22.0F, 12.0F, 6.0F, o ? accent : 0xFF2C2C32);
         Nvg.circle(sx + (o ? 16.0F : 6.0F), sy + 6.0F, 4.5F, 0xFFFFFFFF);
      } else {
         String vs = s.name().equals("3D mesafe") ? new String[]{"Yakın", "Normal", "Uzak"}[Math.max(0, Math.min(2, (int)Math.round(v)))] : fmt(v, s.step());
         Nvg.textL(fit(s.name(), 10.5F, w - 24.0F - Nvg.textWidth(vs, 10.5F, false)), x + 8.0F, y + 9.0F, 10.5F, false, Theme.TEXT_DIM);
         Nvg.textR(vs, x + w - 8.0F, y + 9.0F, 10.5F, false, Theme.TEXT);
         float t = Anim.clamp((float)((v - s.min()) / (s.max() - s.min())), 0.0F, 1.0F);
         float tx = x + 8.0F;
         float tw = w - 16.0F;
         float ty = y + 19.0F;
         Nvg.rect(tx, ty, tw, 4.0F, 2.0F, 0xFF2C2C32);
         Nvg.rect(tx, ty, Math.max(4.0F, tw * t), 4.0F, 2.0F, accent);
         Nvg.circle(tx + tw * t, ty + 2.0F, 4.5F, 0xFFFFFFFF);
      }

      addHit(T_SLIDER, mod, idx, s, x, y, w, th, top, bot);
      return h;
   }

   private static String fmt(double v, float step) {
      if (step >= 1.0F) {
         return Integer.toString((int)Math.round(v));
      }

      return step >= 0.1F ? Float.toString(Math.round(v * 10.0) / 10.0F) : Float.toString(Math.round(v * 100.0) / 100.0F);
   }

   // ---------------------------------------------------------------- 3D mod
   // Menu, NanoVG ile bir FBO'ya (dokuya) cizilir; her panel dunyada, oyuncunun cevresinde bir yay uzerinde
   // duran duz bir levha olarak perspektifle yeniden cizilir. Kamera fareyle donmeye devam eder, ekranin
   // ortasindaki nisangah imlectir (sol tik / sag tik / orta tik / tekerlek / surukleme).

   private static final float L3H = 520.0F; // 3D yerlesimin mantiksal yuksekligi
   private static final float M = 0.0032F; // 1 mantiksal piksel = 3.2 mm (dunya)
   private static final float RATIO = 2.0F; // doku cozunurlugu carpani
   private static final float MARGIN = 16.0F; // panel golgesi icin kenar payi
   private static final float[] DIST = {1.5F, 2.2F, 3.2F}; // yakin / normal / uzak (blok)

   private final boolean mode3d = Config.gui3d;
   private final List<float[]> rects = new ArrayList<>();
   private List<float[]> lastRects = new ArrayList<>();
   private final float[] panelW = new float[PANELS.length + 1];
   private final float[] panelH = new float[PANELS.length + 1];
   private float curX = -1000.0F;
   private float curY = -1000.0F;
   private int curPanel = -1;
   private int fbo;
   private int fboTex;
   private int fboRb;
   private int fboW;
   private int fboH;
   private Vec3d anchor = Vec3d.ZERO;
   private float baseYaw;
   private boolean anchored;
   private boolean haveMouse;
   private double lastMx;
   private double lastMy;
   private double fwdX;
   private double fwdZ;
   private double rgtX;
   private double rgtZ;
   private double distNow = 2.2;

   @Override
   protected void init() {
      if (!mode3d) {
         return;
      }

      if (!anchored && Kuro.mc.player != null) {
         Camera cam = Kuro.mc.gameRenderer.getCamera();
         anchor = cam.getPos();
         baseYaw = cam.getYaw();
         anchored = true;
      }

      // imleci gizle/kilitle: fare artik kamerayi cevirir
      GLFW.glfwSetInputMode(Kuro.mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
      haveMouse = false;
   }

   @Override
   public void removed() {
      if (mode3d) {
         GLFW.glfwSetInputMode(Kuro.mc.getWindow().getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
         freeFbo();
      }

      super.removed();
   }

   private float lw3() {
      return totalW + 24.0F;
   }

   private boolean ensureFbo(int w, int h) {
      if (fbo != 0 && w == fboW && h == fboH) {
         return true;
      }

      freeFbo();
      int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
      int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      int prevRb = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);

      fboTex = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, fboTex);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

      fbo = GL30.glGenFramebuffers();
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
      GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, fboTex, 0);
      fboRb = GL30.glGenRenderbuffers();
      GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, fboRb);
      GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, w, h);
      GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, fboRb);
      boolean ok = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;

      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
      GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, prevRb);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
      fboW = w;
      fboH = h;
      if (!ok) {
         freeFbo();
         return false;
      }

      return true;
   }

   private void freeFbo() {
      if (fbo != 0) {
         GL30.glDeleteFramebuffers(fbo);
         fbo = 0;
      }

      if (fboRb != 0) {
         GL30.glDeleteRenderbuffers(fboRb);
         fboRb = 0;
      }

      if (fboTex != 0) {
         GL11.glDeleteTextures(fboTex);
         fboTex = 0;
      }

      fboW = 0;
      fboH = 0;
   }

   // ---- geometri: yerlesim (x, y piksel) -> dunya

   /** Panelin ("cx" merkezli) yayin uzerindeki disa bakan normali. */
   private Vec3d normalAt(float cx) {
      double th = (cx - lw3() * 0.5F) * M / distNow;
      double c = Math.cos(th);
      double sn = Math.sin(th);
      return new Vec3d(fwdX * c + rgtX * sn, 0.0, fwdZ * c + rgtZ * sn);
   }

   /** Panelin yatay (sag) ekseni. */
   private Vec3d tangentAt(float cx) {
      double th = (cx - lw3() * 0.5F) * M / distNow;
      double c = Math.cos(th);
      double sn = Math.sin(th);
      return new Vec3d(rgtX * c - fwdX * sn, 0.0, rgtZ * c - fwdZ * sn);
   }

   private Vec3d toWorld(float cx, float x, float y) {
      Vec3d n = normalAt(cx);
      Vec3d t = tangentAt(cx);
      return anchor.add(n.multiply(distNow)).add(t.multiply((x - cx) * M)).add(0.0, (L3H * 0.5F - y) * M, 0.0);
   }

   /** Merkez isininin bir panel levhasini vurdugu yer (yerlesim pikseli) ve mesafe. clip: levha disini ele. */
   private float[] rayHit(float[] r, Vec3d eye, Vec3d f, boolean clip) {
      float x = r[1];
      float y = r[2];
      float w = r[3];
      float h = r[4];
      float cx = x + w * 0.5F;
      float cy = y + h * 0.5F;
      Vec3d n = normalAt(cx);
      double denom = f.dotProduct(n);
      if (denom <= 1.0E-4) {
         return null;
      }

      Vec3d pc = toWorld(cx, cx, cy);
      double t = pc.subtract(eye).dotProduct(n) / denom;
      if (t <= 0.0) {
         return null;
      }

      Vec3d rel = eye.add(f.multiply(t)).subtract(pc);
      float hx = cx + (float)(rel.dotProduct(tangentAt(cx)) / M);
      float hy = cy - (float)(rel.y / M);
      if (clip && (hx < x || hx > x + w || hy < y || hy > y + h)) {
         return null;
      }

      return new float[]{hx, hy, (float)t};
   }

   private void pick(Vec3d eye, Vec3d f) {
      curX = -1000.0F;
      curY = -1000.0F;
      curPanel = -1;
      float best = Float.MAX_VALUE;
      for (float[] r : lastRects) {
         if (r[0] < 0.0F) {
            continue;
         }

         float[] h = rayHit(r, eye, f, true);
         if (h != null && h[2] < best) {
            best = h[2];
            curX = h[0];
            curY = h[1];
            curPanel = (int)r[0];
         }
      }
   }

   private void render3d(DrawContext context) {
      if (!Nvg.ensure()) {
         context.drawCenteredTextWithShadow(this.textRenderer, "NanoVG yüklenemedi (log dosyasına bak). Esc ile kapat.", this.width / 2, this.height / 2, 0xFFFFFF);
         if (closing) {
            Kuro.mc.setScreen(null);
         }

         return;
      }

      ClientPlayerEntity pl = Kuro.mc.player;
      if (pl == null || Kuro.mc.world == null) {
         Kuro.mc.setScreen(null);
         return;
      }

      context.draw();
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (now - last) / 1.0E9F);
      last = now;
      prog = Anim.clamp(prog + (closing ? -dt : dt) / 0.28F, 0.0F, 1.0F);
      float e0 = Anim.easeOutCubic(prog);

      Window win = Kuro.mc.getWindow();
      int fw = win.getFramebufferWidth();
      int fh = win.getFramebufferHeight();
      if (fw <= 0 || fh <= 0) {
         return;
      }

      // --- fare: kamerayi cevirir (imlec kilitli, ham hareket)
      double mx = Kuro.mc.mouse.getX();
      double my = Kuro.mc.mouse.getY();
      if (haveMouse && !closing) {
         double s = Kuro.mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
         double k = s * s * s * 8.0;
         double inv = Kuro.mc.options.getInvertYMouse().getValue() ? -1.0 : 1.0;
         pl.changeLookDirection((mx - lastMx) * k, (my - lastMy) * k * inv);
      }

      lastMx = mx;
      lastMy = my;
      haveMouse = true;

      // --- kamera ve yerlesim
      Camera cam = Kuro.mc.gameRenderer.getCamera();
      Vec3d eye = cam.getPos();
      float yaw = cam.getYaw();
      float pitch = cam.getPitch();
      double yr = Math.toRadians(yaw);
      Vec3d f = Vec3d.fromPolar(pitch, yaw);
      Vec3d r = new Vec3d(-Math.cos(yr), 0.0, -Math.sin(yr));
      Vec3d u = r.crossProduct(f).normalize();

      double by = Math.toRadians(baseYaw);
      fwdX = -Math.sin(by);
      fwdZ = Math.cos(by);
      rgtX = -Math.cos(by);
      rgtZ = -Math.sin(by);
      distNow = DIST[Math.max(0, Math.min(2, Math.round(Config.gui3dDist)))] * (0.8 + 0.2 * e0);

      pick(eye, f);
      if (drag != null) {
         for (float[] rc : lastRects) {
            if ((int)rc[0] == drag.pan()) {
               float[] h = rayHit(rc, eye, f, false);
               if (h != null) {
                  applySlider(drag, h[0]);
               }

               break;
            }
         }
      }

      Theme.update(dt);

      // --- 2D arayuzu FBO'ya ciz
      float lw = lw3();
      int tw = Math.round(lw * RATIO);
      int th = Math.round(L3H * RATIO);
      if (!ensureFbo(tw, th)) {
         throw new IllegalStateException("3D GUI icin framebuffer olusturulamadi");
      }

      int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
      int[] vp = new int[4];
      GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
      boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
      try {
         GL11.glViewport(0, 0, tw, th);
         if (scissor) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
         }

         GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
         GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
         Nvg.begin(lw, L3H, RATIO);
         try {
            draw(lw, L3H, dt);
         } finally {
            Nvg.end();
         }
      } finally {
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
         GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
         if (scissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
         }
      }

      lastRects = new ArrayList<>(rects);

      // --- panelleri perspektifle ekrana ciz
      double fov = Kuro.mc.options.getFov().getValue();
      float kpx = (float)((fh * 0.5) / Math.tan(Math.toRadians(fov * 0.5)));
      float sf = (float)win.getScaleFactor();

      List<float[]> order = new ArrayList<>(lastRects);
      order.sort((a, b) -> {
         double da = toWorld(a[1] + a[3] * 0.5F, a[1] + a[3] * 0.5F, a[2] + a[4] * 0.5F).subtract(eye).dotProduct(f);
         double db = toWorld(b[1] + b[3] * 0.5F, b[1] + b[3] * 0.5F, b[2] + b[4] * 0.5F).subtract(eye).dotProduct(f);
         return Double.compare(db, da); // uzaktan yakina
      });

      RenderSystem.disableDepthTest();
      RenderSystem.disableCull();
      RenderSystem.enableBlend();
      // NanoVG onceden carpilmis (premultiplied) alfa uretir
      RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
         GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
      RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
      RenderSystem.setShaderTexture(0, fboTex);

      BufferBuilder b = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEX_COLOR);
      float[] sx = new float[4];
      float[] sy = new float[4];
      float[] cxs = new float[4];
      float[] cys = new float[4];
      for (float[] rc : order) {
         float mg = rc[0] < 0.0F ? 6.0F : MARGIN;
         float x0 = Math.max(0.0F, rc[1] - mg);
         float y0 = Math.max(0.0F, rc[2] - mg);
         float x1 = Math.min(lw, rc[1] + rc[3] + mg);
         float y1 = Math.min(L3H, rc[2] + rc[4] + mg);
         float cx = rc[1] + rc[3] * 0.5F;
         int nx = Math.max(1, (int)Math.ceil((x1 - x0) / 20.0F));
         int ny = Math.max(1, (int)Math.ceil((y1 - y0) / 32.0F));
         for (int i = 0; i < nx; i++) {
            for (int j = 0; j < ny; j++) {
               float ax = x0 + (x1 - x0) * i / nx;
               float bx = x0 + (x1 - x0) * (i + 1) / nx;
               float ay = y0 + (y1 - y0) * j / ny;
               float bY = y0 + (y1 - y0) * (j + 1) / ny;
               cxs[0] = ax; cys[0] = ay;
               cxs[1] = ax; cys[1] = bY;
               cxs[2] = bx; cys[2] = bY;
               cxs[3] = bx; cys[3] = ay;
               boolean ok = true;
               for (int k = 0; k < 4; k++) {
                  Vec3d rel = toWorld(cx, cxs[k], cys[k]).subtract(eye);
                  double zv = rel.dotProduct(f);
                  if (zv < 0.05) {
                     ok = false;
                     break;
                  }

                  double xv = rel.dotProduct(r);
                  double yv = rel.dotProduct(u);
                  sx[k] = (float)(fw * 0.5 + xv / zv * kpx) / sf;
                  sy[k] = (float)(fh * 0.5 - yv / zv * kpx) / sf;
               }

               if (!ok) {
                  continue;
               }

               for (int k = 0; k < 4; k++) {
                  b.vertex(sx[k], sy[k], 0.0F).texture(cxs[k] / lw, 1.0F - cys[k] / L3H).color(e0, e0, e0, e0);
               }
            }
         }
      }

      BuiltBuffer built = b.endNullable();
      if (built != null) {
         BufferRenderer.drawWithGlobalProgram(built);
      }

      RenderSystem.enableCull();
      RenderSystem.enableDepthTest();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableBlend();

      // --- nisangah (imlec)
      int ccx = this.width / 2;
      int ccy = this.height / 2;
      int col = curPanel >= 0 ? 0xFF000000 | Theme.accent() : 0xFFFFFFFF;
      context.fill(ccx - 6, ccy - 1, ccx + 7, ccy + 2, 0xAA000000);
      context.fill(ccx - 1, ccy - 6, ccx + 2, ccy + 7, 0xAA000000);
      context.fill(ccx - 5, ccy, ccx + 6, ccy + 1, col);
      context.fill(ccx, ccy - 5, ccx + 1, ccy + 6, col);

      if (closing && prog <= 0.0F) {
         Kuro.mc.setScreen(null);
      }
   }

   // ---------------------------------------------------------------- girdi

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (closing) {
         return true;
      }

      float mx = mouseX();
      float my = mouseY();
      for (int i = hits.size() - 1; i >= 0; i--) {
         Hit h = hits.get(i);
         if (!h.contains(mx, my)) {
            continue;
         }

         switch (h.type) {
            case T_TAB -> {
               if (button == 0 && tab[h.a] != h.b) {
                  tab[h.a] = h.b;
                  scrollTarget[h.a] = 0.0F;
                  scroll[h.a].snap(0.0F);
               }
            }
            case T_MOD -> {
               int id = h.a;
               if (button == 0) {
                  Kuro.toggle(id);
               } else if (button == 1) {
                  if (id == Kuro.FAKEBAN) {
                     Kuro.mc.setScreen(new kuro.FakeBanPicker(Kuro.mc.currentScreen));
                  } else if (MenuScreen.SLIDERS[id] != null) {
                     exp[id].to(exp[id].t > 0.5F ? 0.0F : 1.0F);
                  }
               } else if (button == 2 && !isAction(id)) {
                  listening = id;
               }
            }
            case T_SLIDER -> {
               if (button != 0) {
                  break;
               }

               if (isToggle(h.s)) {
                  boolean was = h.s.get().getAsDouble() >= 0.5;
                  h.s.set().accept(was ? 0.0 : 1.0);
                  Config.save();
                  if (!Config.columnMenu && !Config.gui3d) {
                     Kuro.mc.setScreen(new MenuScreen());
                  } else if (Config.gui3d != mode3d) {
                     Kuro.mc.setScreen(new ClickGuiScreen());
                  }
               } else {
                  drag = h;
                  applySlider(h, mx);
               }
            }
            default -> {
            }
         }

         return true;
      }

      return true;
   }

   private static void applySlider(Hit h, float mx) {
      MenuScreen.Slider s = h.s;
      float t = Anim.clamp((mx - (h.x + 8.0F)) / (h.w - 16.0F), 0.0F, 1.0F);
      float v = s.min() + t * (s.max() - s.min());
      v = Math.round(v / s.step()) * s.step();
      s.set().accept(Anim.clamp(v, s.min(), s.max()));
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (drag != null) {
         Config.save();
         drag = null;
      }

      return true;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (drag != null && !mode3d) {
         applySlider(drag, mouseX());
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (mode3d) {
         if (curPanel >= 0) {
            float max = Math.max(0.0F, contentH[curPanel] - viewH[curPanel]);
            scrollTarget[curPanel] = Anim.clamp(scrollTarget[curPanel] - (float)verticalAmount * 40.0F, 0.0F, max);
         }

         return true;
      }

      float mx = mouseX();
      for (int vp = 0; vp < (searching() ? 1 : PANELS.length); vp++) {
         int p = searching() ? SP : vp;
         if (mx >= panelX[p] && mx <= panelX[p] + (searching() ? SPW : PW)) {
            float max = Math.max(0.0F, contentH[p] - viewH[p]);
            scrollTarget[p] = Anim.clamp(scrollTarget[p] - (float)verticalAmount * 40.0F, 0.0F, max);
            break;
         }
      }

      return true;
   }

   @Override
   public boolean charTyped(char chr, int modifiers) {
      if (closing || listening >= 0 || chr < 32 || chr == 127 || query.length() >= 32) {
         return super.charTyped(chr, modifiers);
      }

      query += chr;
      queryChanged();
      return true;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (listening >= 0) {
         if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            listening = -1;
         } else {
            Kuro.binds[listening] = keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
            Kuro.saveBinds();
            listening = -1;
         }

         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE && searching()) {
         query = "";
         queryChanged();
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searching()) {
         query = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : query.substring(0, query.length() - 1);
         queryChanged();
         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && client != null) {
         String cb = client.keyboard.getClipboard();
         if (cb != null) {
            query = (query + cb.replaceAll("[\\r\\n]", " ").trim());
            query = query.substring(0, Math.min(32, query.length()));
            queryChanged();
         }

         return true;
      }

      if (keyCode == GLFW.GLFW_KEY_ESCAPE || Kuro.menuKey.matchesKey(keyCode, scanCode)) {
         close();
         return true;
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }
}
