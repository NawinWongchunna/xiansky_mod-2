package dev.ascension.rings.client.vfx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Vec3d;

import java.util.function.DoubleFunction;

/**
 * มังกรจีน 3 มิติ สร้างจากโค้ด (ไม่ต้องใช้ไฟล์โมเดล):
 *  ลำตัวเป็นท่อต่อกัน 64 ปล้อง เลื้อยตามเส้นทางที่หัวบิน + คลื่นลำตัว, หัว (กะโหลก ขากรรไกรอ้าหุบ ฟัน ตาเรืองแสง),
 *  เขา, หนวดพริ้ว, ครีบหลัง/แผงคอ, ขา 4 ข้างมีเล็บ  — ใส่เงาแบบ lambert + rim light ที่คำนวณเอง
 *  รองรับการตัดด้วยระนาบ (clip plane) เพื่อทำท่า "พุ่งออกจากหน้าคัมภีร์/ผิวน้ำ"
 */
final class DragonModel {
    static final class Scheme {
        final float[] back, belly, accent, rim, eye, horn;
        final float rimK, ambient;

        Scheme(float[] back, float[] belly, float[] accent, float[] rim, float[] eye, float[] horn, float rimK, float ambient) {
            this.back = back;
            this.belly = belly;
            this.accent = accent;
            this.rim = rim;
            this.eye = eye;
            this.horn = horn;
            this.rimK = rimK;
            this.ambient = ambient;
        }
    }

    static final Scheme WHITE = new Scheme(
            new float[]{0.96f, 0.94f, 0.88f}, new float[]{1.00f, 0.84f, 0.45f}, new float[]{1.00f, 0.78f, 0.22f},
            new float[]{0.70f, 0.90f, 1.00f}, new float[]{1.00f, 0.30f, 0.15f}, new float[]{1.00f, 0.84f, 0.32f}, 0.55f, 0.50f);
    static final Scheme BLACK = new Scheme(
            new float[]{0.16f, 0.10f, 0.24f}, new float[]{0.66f, 0.20f, 0.58f}, new float[]{0.84f, 0.36f, 1.00f},
            new float[]{0.72f, 0.38f, 1.00f}, new float[]{1.00f, 0.86f, 0.30f}, new float[]{0.72f, 0.32f, 0.96f}, 0.95f, 0.62f);

    private static final Vec3d LIGHT = new Vec3d(0.40, 0.80, 0.45).normalize();
    private static final Vec3d UP = new Vec3d(0, 1, 0);

    static final int N = 64;          // จำนวนปล้องลำตัว
    static final int M = 10;          // จุดรอบหน้าตัดท่อ
    static final double LENGTH = 34.0;
    static final double RMAX = 2.1;

    private DragonModel() {}

    // ------------------------------------------------------------------ เส้นทางลำตัว

    private static Vec3d lerp(Vec3d a, Vec3d b, double k) {
        return a.add(b.subtract(a).multiply(k));
    }

    /** วางจุดลำตัวตามประวัติเส้นทางของหัว โดยรักษาความยาวคงที่ (ระยะห่างเท่ากัน) */
    static Vec3d[] body(DoubleFunction<Vec3d> head, double t) {
        double ds = LENGTH / (N - 1);
        Vec3d[] out = new Vec3d[N];
        Vec3d cur = head.apply(t);
        out[0] = cur;
        int k = 1;
        double dist = 0.0, tt = t;
        int guard = 0;
        while (k < N && guard++ < 1600) {
            tt -= 0.25;
            Vec3d nx = head.apply(tt);
            double seg = nx.distanceTo(cur);
            while (k < N && k * ds <= dist + seg + 1.0e-9) {
                double fr = seg < 1.0e-9 ? 0.0 : (k * ds - dist) / seg;
                out[k] = lerp(cur, nx, fr);
                k++;
            }
            dist += seg;
            cur = nx;
        }
        while (k < N) out[k++] = cur;
        return out;
    }

    private static void frames(Vec3d[] p, Vec3d[] tan, Vec3d[] side, Vec3d[] up) {
        int n = p.length;
        for (int k = 0; k < n; k++) {
            Vec3d d = p[Math.max(k - 1, 0)].subtract(p[Math.min(k + 1, n - 1)]);
            tan[k] = d.lengthSquared() < 1.0e-10 ? (k > 0 ? tan[k - 1] : new Vec3d(1, 0, 0)) : d.normalize();
        }
        Vec3d s0 = tan[0].crossProduct(UP);
        if (s0.lengthSquared() < 1.0e-4) s0 = tan[0].crossProduct(new Vec3d(1, 0, 0));
        side[0] = s0.normalize();
        for (int k = 1; k < n; k++) {
            Vec3d s = side[k - 1].subtract(tan[k].multiply(side[k - 1].dotProduct(tan[k])));
            if (s.lengthSquared() < 1.0e-8) s = tan[k].crossProduct(UP);
            side[k] = s.lengthSquared() < 1.0e-8 ? side[k - 1] : s.normalize();
        }
        for (int k = 0; k < n; k++) up[k] = side[k].crossProduct(tan[k]).normalize();
    }

    private static double radius(double u) {
        double r;
        if (u < 0.2) r = RMAX * (0.62 + 0.38 * (u / 0.2));
        else r = RMAX * Math.pow(1.0 - (u - 0.2) / 0.8, 0.85);
        return Math.max(0.12, r);
    }

    // ------------------------------------------------------------------ วาด

    private static final class Ctx {
        BufferBuilder buf;
        AscensionRenderer.Frame f;
        Scheme sc;
        float alpha;

        void v(Vec3d p, Vec3d n, float[] col, float emis) {
            Vec3d c = p.subtract(f.cam);
            double nl = Math.max(0.0, n.dotProduct(LIGHT));
            double len = c.length();
            Vec3d toCam = len < 1.0e-6 ? UP : c.multiply(-1.0 / len);
            double rim = Math.pow(1.0 - Math.min(1.0, Math.abs(n.dotProduct(toCam))), 2.5);
            float sh = (float) (sc.ambient + 0.55 * nl);
            float r = col[0] * (sh * (1f - emis) + emis) + (float) (rim * sc.rimK) * sc.rim[0];
            float g = col[1] * (sh * (1f - emis) + emis) + (float) (rim * sc.rimK) * sc.rim[1];
            float b = col[2] * (sh * (1f - emis) + emis) + (float) (rim * sc.rimK) * sc.rim[2];
            buf.vertex(f.m, (float) c.x, (float) c.y, (float) c.z)
                    .color(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), alpha).next();
        }

        /** quad เรียบ: normal ได้จาก cross แล้วพลิกให้ชี้ออกจาก center */
        void quad(Vec3d a, Vec3d b, Vec3d c, Vec3d d, Vec3d center, float[] col, float emis) {
            Vec3d n = b.subtract(a).crossProduct(d.subtract(a));
            if (n.lengthSquared() < 1.0e-12) return;
            n = n.normalize();
            if (center != null && n.dotProduct(a.subtract(center)) < 0) n = n.multiply(-1);
            v(a, n, col, emis);
            v(b, n, col, emis);
            v(c, n, col, emis);
            v(d, n, col, emis);
        }

        void tri(Vec3d a, Vec3d b, Vec3d c, Vec3d center, float[] col, float emis) {
            Vec3d n = b.subtract(a).crossProduct(c.subtract(a));
            if (n.lengthSquared() < 1.0e-12) return;
            n = n.normalize();
            if (center != null && n.dotProduct(a.subtract(center)) < 0) n = n.multiply(-1);
            v(a, n, col, emis);
            v(b, n, col, emis);
            v(c, n, col, emis);
            v(c, n, col, emis);
        }

        /** แท่ง 4 เหลี่ยมระหว่างจุด A-B (ขา เขา เล็บ) */
        void limb(Vec3d a, Vec3d b, double r0, double r1, float[] col, float emis) {
            Vec3d w = b.subtract(a);
            if (w.lengthSquared() < 1.0e-10) return;
            w = w.normalize();
            Vec3d u1 = w.crossProduct(UP);
            if (u1.lengthSquared() < 1.0e-4) u1 = w.crossProduct(new Vec3d(1, 0, 0));
            u1 = u1.normalize();
            Vec3d u2 = w.crossProduct(u1).normalize();
            Vec3d[] da = {u1.add(u2), u1.subtract(u2), u1.multiply(-1).subtract(u2), u2.subtract(u1)};
            for (int i = 0; i < 4; i++) {
                Vec3d d0 = da[i].normalize(), d1 = da[(i + 1) % 4].normalize();
                Vec3d n = d0.add(d1).normalize();
                v(a.add(d0.multiply(r0)), d0, col, emis);
                v(a.add(d1.multiply(r0)), d1, col, emis);
                v(b.add(d1.multiply(r1)), d1, col, emis);
                v(b.add(d0.multiply(r1)), d0, col, emis);
            }
        }

        void chain(Vec3d[] pts, double r0, double r1, float[] col, float emis) {
            int n = pts.length - 1;
            for (int i = 0; i < n; i++) {
                double a = r0 + (r1 - r0) * i / n, b = r0 + (r1 - r0) * (i + 1) / n;
                limb(pts[i], pts[i + 1], a, b, col, emis);
            }
        }

        /** ริบบิ้น (หนวด) กว้าง w ตามทิศ wide */
        void ribbon(Vec3d[] pts, Vec3d wide, double w0, double w1, float[] col, float emis) {
            int n = pts.length - 1;
            for (int i = 0; i < n; i++) {
                double a = w0 + (w1 - w0) * i / n, b = w0 + (w1 - w0) * (i + 1) / n;
                Vec3d na = pts[i].add(wide.multiply(a)), nb = pts[i].subtract(wide.multiply(a));
                Vec3d ma = pts[i + 1].add(wide.multiply(b)), mb = pts[i + 1].subtract(wide.multiply(b));
                Vec3d nrm = pts[i + 1].subtract(pts[i]).crossProduct(wide);
                if (nrm.lengthSquared() < 1.0e-12) continue;
                nrm = nrm.normalize();
                v(na, nrm, col, emis);
                v(nb, nrm, col, emis);
                v(mb, nrm, col, emis);
                v(ma, nrm, col, emis);
            }
        }
    }

    private static float[] mix(float[] a, float[] b, double k) {
        float kk = (float) Math.max(0, Math.min(1, k));
        return new float[]{a[0] + (b[0] - a[0]) * kk, a[1] + (b[1] - a[1]) * kk, a[2] + (b[2] - a[2]) * kk};
    }

    private static float[] scale(float[] a, double k) {
        return new float[]{(float) (a[0] * k), (float) (a[1] * k), (float) (a[2] * k)};
    }

    /** clip: คืน [f0,f1] ของส่วนที่มองเห็นบนเส้น a->b เทียบระนาบ (d>=0 คือด้านที่เห็น) หรือ null ถ้ามองไม่เห็น */
    private static double[] clipEdge(double da, double db) {
        if (da >= 0 && db >= 0) return new double[]{0, 1};
        if (da < 0 && db < 0) return null;
        double c = da / (da - db);
        return da >= 0 ? new double[]{0, c} : new double[]{c, 1};
    }

    /**
     * @param S     จุดกึ่งกลางลำตัวจากหัวไปหาง (ยาว N)
     * @param time  เวลา (tick) สำหรับแอนิเมชัน
     * @param clipP/clipN ระนาบตัด (ส่วนที่ d<0 ถูกซ่อน) หรือ null
     */
    static void render(AscensionRenderer.Frame f, Scheme sc, Vec3d[] S, double time, float alpha, Vec3d clipP, Vec3d clipN) {
        if (alpha <= 0.01f) return;
        int n = S.length;

        // 1) คลื่นลำตัว
        Vec3d[] tan = new Vec3d[n], side = new Vec3d[n], up = new Vec3d[n];
        frames(S, tan, side, up);
        Vec3d[] P = new Vec3d[n];
        for (int k = 0; k < n; k++) {
            double u = k / (double) (n - 1);
            double amp = 1.5 * Math.min(1.0, u * 3.5) * (0.35 + 0.9 * u);
            double ph = time * 0.28 - u * 9.0;
            P[k] = S[k].add(side[k].multiply(Math.sin(ph) * amp)).add(up[k].multiply(Math.cos(ph) * amp * 0.45));
        }
        frames(P, tan, side, up);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);

        Ctx c = new Ctx();
        c.buf = Tessellator.getInstance().getBuffer();
        c.buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        c.f = f;
        c.sc = sc;
        c.alpha = alpha;

        // 2) ท่อลำตัว
        Vec3d[][] rp = new Vec3d[n][M];
        Vec3d[][] rn = new Vec3d[n][M];
        float[][][] rc = new float[n][M][];
        double[][] rd = new double[n][M];
        for (int k = 0; k < n; k++) {
            double u = k / (double) (n - 1);
            double r = radius(u);
            float stripe = (k & 1) == 0 ? 1.0f : 0.88f;
            for (int j = 0; j < M; j++) {
                double phi = 2 * Math.PI * j / M;
                double cs = Math.cos(phi), sn = Math.sin(phi);
                Vec3d dir = side[k].multiply(cs).add(up[k].multiply(sn));
                rn[k][j] = dir;
                rp[k][j] = P[k].add(side[k].multiply(cs * r)).add(up[k].multiply(sn * r * 0.92));
                double bw = Math.max(0.0, Math.min(1.0, (-sn - 0.25) / 0.6));
                float[] col = mix(sc.back, sc.belly, bw);
                col = mix(col, sc.accent, Math.max(0.0, (u - 0.8) / 0.2) * 0.5);
                rc[k][j] = scale(col, stripe);
                rd[k][j] = clipP == null ? 1.0 : rp[k][j].subtract(clipP).dotProduct(clipN);
            }
        }
        for (int k = 0; k < n - 1; k++) {
            for (int j = 0; j < M; j++) {
                int j1 = (j + 1) % M;
                double[] e0 = clipEdge(rd[k][j], rd[k + 1][j]);
                double[] e1 = clipEdge(rd[k][j1], rd[k + 1][j1]);
                if (e0 == null || e1 == null) continue;
                Vec3d a0 = lerp(rp[k][j], rp[k + 1][j], e0[0]), b0 = lerp(rp[k][j], rp[k + 1][j], e0[1]);
                Vec3d a1 = lerp(rp[k][j1], rp[k + 1][j1], e1[0]), b1 = lerp(rp[k][j1], rp[k + 1][j1], e1[1]);
                c.v(a0, rn[k][j], rc[k][j], 0f);
                c.v(a1, rn[k][j1], rc[k][j1], 0f);
                c.v(b1, rn[k + 1][j1], rc[k + 1][j1], 0f);
                c.v(b0, rn[k + 1][j], rc[k + 1][j], 0f);
            }
        }

        // 3) ครีบหลัง / แผงคอ
        for (int k = 3; k < n - 3; k++) {
            if (clipP != null && P[k].subtract(clipP).dotProduct(clipN) < 0) continue;
            double u = k / (double) (n - 1);
            double r = radius(u) * 0.92;
            double h = radius(u) * 1.0 + 0.4 + (u < 0.3 ? 1.8 * (1 - u / 0.3) : 0.0) + 0.25 * Math.sin(time * 0.4 - k * 0.5);
            Vec3d a = P[k].add(up[k].multiply(r)), b = P[k + 1].add(up[k + 1].multiply(radius((k + 1) / (double) (n - 1)) * 0.92));
            Vec3d apex = a.add(up[k].multiply(h)).subtract(tan[k].multiply(0.9 + 0.5 * (1 - u))).add(side[k].multiply(Math.sin(time * 0.3 - k * 0.4) * 0.25));
            float[] fc = mix(scale(sc.accent, 0.85), sc.accent, 1.0);
            c.tri(a, b, apex, null, fc, 0.55f);
        }

        // 4) ขา 4 ข้าง
        int[] legK = {(int) (0.26 * n), (int) (0.52 * n)};
        for (int li = 0; li < 2; li++) {
            int k = legK[li];
            if (clipP != null && P[k].subtract(clipP).dotProduct(clipN) < 0) continue;
            double u = k / (double) (n - 1);
            double r = radius(u);
            for (int s = -1; s <= 1; s += 2) {
                double sw = Math.sin(time * 0.25 + li * 1.7 + (s > 0 ? 0 : 1.4)) * 0.35;
                Vec3d a = P[k].add(side[k].multiply(s * r * 0.7)).subtract(up[k].multiply(r * 0.55));
                Vec3d b = a.add(side[k].multiply(s * 1.5)).subtract(up[k].multiply(1.6)).subtract(tan[k].multiply(0.9 + sw));
                Vec3d d = b.add(side[k].multiply(s * 0.6)).subtract(up[k].multiply(1.3)).add(tan[k].multiply(0.7 - sw));
                c.limb(a, b, 0.55, 0.38, sc.back, 0f);
                c.limb(b, d, 0.38, 0.26, sc.belly, 0f);
                for (int cl = -1; cl <= 1; cl++) {
                    Vec3d tip = d.add(side[k].multiply(s * 0.35 + cl * 0.25)).subtract(up[k].multiply(0.7)).add(tan[k].multiply(0.45));
                    c.limb(d, tip, 0.16, 0.03, sc.horn, 0.4f);
                }
            }
        }

        // 5) หัว
        head(c, P[0], tan[0], side[0], up[0], time);

        Tessellator.getInstance().draw();
        RenderSystem.depthMask(false);
    }

    private static Vec3d loc(Vec3d o, Vec3d F, Vec3d U, Vec3d S, double x, double y, double z) {
        return o.add(F.multiply(x)).add(U.multiply(y)).add(S.multiply(z));
    }

    private static void head(Ctx c, Vec3d o, Vec3d F, Vec3d S, Vec3d U, double time) {
        Scheme sc = c.sc;
        double hs = 1.7;
        // กะโหกบน (frustum): หลัง -> จมูก
        Vec3d bBL = loc(o, F, U, S, -0.4 * hs, -0.95 * hs, -1.5 * hs), bBR = loc(o, F, U, S, -0.4 * hs, -0.95 * hs, 1.5 * hs);
        Vec3d bTL = loc(o, F, U, S, -0.4 * hs, 1.15 * hs, -1.2 * hs), bTR = loc(o, F, U, S, -0.4 * hs, 1.15 * hs, 1.2 * hs);
        Vec3d fBL = loc(o, F, U, S, 4.0 * hs, -0.35 * hs, -0.7 * hs), fBR = loc(o, F, U, S, 4.0 * hs, -0.35 * hs, 0.7 * hs);
        Vec3d fTL = loc(o, F, U, S, 4.0 * hs, 0.45 * hs, -0.55 * hs), fTR = loc(o, F, U, S, 4.0 * hs, 0.45 * hs, 0.55 * hs);
        Vec3d ctr = loc(o, F, U, S, 1.8 * hs, 0.1 * hs, 0);
        c.quad(bTL, bTR, fTR, fTL, ctr, sc.back, 0f);
        c.quad(bBL, bBR, fBR, fBL, ctr, sc.belly, 0f);
        c.quad(bBL, bTL, fTL, fBL, ctr, sc.back, 0f);
        c.quad(bBR, bTR, fTR, fBR, ctr, sc.back, 0f);
        c.quad(bBL, bBR, bTR, bTL, ctr, sc.back, 0f);
        c.quad(fBL, fBR, fTR, fTL, ctr, sc.belly, 0f);

        // ขากรรไกรล่าง อ้า/หุบ
        double ang = 0.16 + 0.12 * Math.sin(time * 0.18);
        double px = 0.2 * hs, py = -0.8 * hs;
        double[][] jaw = {
                {0.2, -0.7, 1.1}, {0.2, -1.15, 1.0}, {3.7, -0.62, 0.5}, {3.7, -0.95, 0.45}
        };
        Vec3d[] jl = new Vec3d[4], jr = new Vec3d[4];
        for (int i = 0; i < 4; i++) {
            double x = jaw[i][0] * hs, y = jaw[i][1] * hs, z = jaw[i][2] * hs;
            double dx = x - px, dy = y - py;
            double xr = px + dx * Math.cos(ang) + dy * Math.sin(ang);
            double yr = py - dx * Math.sin(ang) + dy * Math.cos(ang);
            jl[i] = loc(o, F, U, S, xr, yr, -z);
            jr[i] = loc(o, F, U, S, xr, yr, z);
        }
        Vec3d jc = loc(o, F, U, S, 1.8 * hs, -0.8 * hs, 0);
        c.quad(jl[0], jr[0], jr[2], jl[2], jc, sc.belly, 0f);   // บน
        c.quad(jl[1], jr[1], jr[3], jl[3], jc, sc.belly, 0f);   // ล่าง
        c.quad(jl[0], jl[1], jl[3], jl[2], jc, sc.back, 0f);    // ข้างซ้าย
        c.quad(jr[0], jr[1], jr[3], jr[2], jc, sc.back, 0f);    // ข้างขวา
        c.quad(jl[2], jr[2], jr[3], jl[3], jc, sc.belly, 0f);   // ปลาย

        // ฟัน
        float[] tooth = {0.98f, 0.96f, 0.90f};
        for (int s = -1; s <= 1; s += 2) {
            for (double x = 0.9; x < 3.9; x += 0.6) {
                double q = (x + 0.4) / 4.4;
                double hw = (1.3 + (0.62 - 1.3) * q) * 0.82 * hs;
                double yb = (-0.95 + 0.6 * q) * hs;
                Vec3d a = loc(o, F, U, S, (x - 0.13) * hs, yb, s * hw), b = loc(o, F, U, S, (x + 0.13) * hs, yb, s * hw);
                Vec3d tip = loc(o, F, U, S, x * hs, yb - 0.5 * hs, s * hw);
                c.tri(a, b, tip, null, tooth, 0.35f);
            }
        }

        // ตาเรืองแสง
        for (int s = -1; s <= 1; s += 2) {
            double ex = 1.3 * hs, ey = 0.55 * hs, ez = s * 1.0 * hs;
            Vec3d a = loc(o, F, U, S, ex - 0.28 * hs, ey - 0.16 * hs, ez), b = loc(o, F, U, S, ex + 0.28 * hs, ey - 0.16 * hs, ez);
            Vec3d d = loc(o, F, U, S, ex + 0.28 * hs, ey + 0.2 * hs, ez), e = loc(o, F, U, S, ex - 0.28 * hs, ey + 0.2 * hs, ez);
            c.quad(a, b, d, e, null, sc.eye, 1.0f);
        }

        // เขา 2 ข้าง (โค้งไปด้านหลัง)
        for (int s = -1; s <= 1; s += 2) {
            Vec3d[] pts = new Vec3d[5];
            for (int i = 0; i < 5; i++) {
                double u = i / 4.0;
                pts[i] = loc(o, F, U, S, (0.2 - 3.0 * u) * hs, (0.95 + 1.9 * u - 0.7 * u * u) * hs, s * (0.75 + 1.0 * u) * hs);
            }
            c.chain(pts, 0.3 * hs, 0.04, sc.horn, 0.25f);
        }

        // หนวด 2 เส้น พริ้วตามเวลา
        for (int s = -1; s <= 1; s += 2) {
            Vec3d[] pts = new Vec3d[9];
            for (int i = 0; i < 9; i++) {
                double u = i / 8.0;
                double lat = s * (0.7 + 1.1 * u * 5.0) * hs * 0.8 + Math.sin(time * 0.3 - u * 6.0 + s) * 0.9 * u * hs;
                double vert = (-0.2 - 0.9 * u * 1.0) * hs + Math.cos(time * 0.27 - u * 5.0) * 0.6 * u * hs;
                pts[i] = loc(o, F, U, S, (3.4 - 10.0 * u) * hs, vert, lat);
            }
            c.ribbon(pts, U, 0.2 * hs, 0.04, sc.accent, 0.5f);
        }
    }
}
