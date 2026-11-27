package dev.ascension.rings;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * "โลกหยินหยาง" ข้างในคัมภีร์ = มิติใหม่ (ascension_rings:yinyang) เป็นโลกว่างเปล่า (void)
 * ที่มีแท่นไท่จี๋ขนาดยักษ์ลอยอยู่ สร้างอัตโนมัติครั้งแรกที่มีคนเข้า
 *
 * ไฟล์มิติอยู่ใน data/ascension_rings/dimension_type/yinyang.json และ data/ascension_rings/dimension/yinyang.json
 */
public final class YinYangRealm {
    public static final Identifier ID = new Identifier(AscensionMod.MOD_ID, "yinyang");
    public static final RegistryKey<World> KEY = RegistryKey.of(RegistryKeys.WORLD, ID);

    /** รัศมีแท่น (บล็อก) และระดับความสูงของผิวแท่น */
    public static final int R = 60;
    public static final int Y = 64;

    // รหัสช่องของลายไท่จี๋
    public static final int WHITE = 0, BLACK = 1, EYE_W = 2, EYE_B = 3;

    private YinYangRealm() {}

    public static boolean isRealm(World w) {
        return w != null && w.getRegistryKey().equals(KEY);
    }

    /** จุดเกิดตอนเข้ามาในโลก (ขอบแท่นด้านใต้ มองเข้าหาศูนย์กลาง) */
    public static Vec3d spawn() {
        return new Vec3d(0.5, Y + 1.0, -R + 10 + 0.5);
    }

    /**
     * ลายไท่จี๋บนจานหน่วยรัศมี 1: คืน WHITE/BLACK/EYE_W/EYE_B หรือ -1 ถ้านอกวง
     * ครึ่งซ้ายขาว/ขวาดำ มีวงกลมเล็กดำ (บน) และขาว (ล่าง) พร้อม "ตา" สีตรงข้าม
     */
    public static int taiji(double x, double z) {
        double r = Math.hypot(x, z);
        if (r > 1.0) return -1;
        double dUp = Math.hypot(x, z - 0.5);
        double dDn = Math.hypot(x, z + 0.5);
        if (dUp < 0.125) return EYE_W;
        if (dDn < 0.125) return EYE_B;
        if (dUp < 0.5) return BLACK;
        if (dDn < 0.5) return WHITE;
        return x < 0 ? WHITE : BLACK;
    }

    private static BlockState block(int code, boolean ring) {
        if (ring) return Blocks.SEA_LANTERN.getDefaultState();
        switch (code) {
            case EYE_W:
                return Blocks.SEA_LANTERN.getDefaultState();
            case EYE_B:
                return Blocks.CRYING_OBSIDIAN.getDefaultState();
            case BLACK:
                return Blocks.BLACK_CONCRETE.getDefaultState();
            default:
                return Blocks.WHITE_CONCRETE.getDefaultState();
        }
    }

    /** สร้างแท่นไท่จี๋ลอยฟ้า (ทำครั้งเดียว ตรวจจากบล็อกกลางแท่น) */
    public static void ensureBuilt(ServerWorld w) {
        BlockPos center = new BlockPos(0, Y, 0);
        w.getChunk(0, 0); // โหลด/สร้าง chunk
        if (!w.getBlockState(center).isAir()) return;
        AscensionMod.LOGGER.info("Building the Yin-Yang realm platform...");

        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        int depth = 6;
        for (int d = 0; d <= depth; d++) {
            // ท้องแท่นโค้งแบบวงรี: ชั้นล่างๆ รัศมีเล็กลง
            double rd = R * Math.sqrt(Math.max(0.0, 1.0 - (d / (depth + 0.6)) * (d / (depth + 0.6))));
            int ri = (int) Math.ceil(rd);
            for (int x = -ri; x <= ri; x++) {
                for (int z = -ri; z <= ri; z++) {
                    double r = Math.hypot(x, z);
                    if (r > rd) continue;
                    int code = taiji(x / (double) R, z / (double) R);
                    if (code < 0) continue;
                    boolean ring = d == 0 && r >= R - 1.0;
                    if (d > 0) {
                        // ชั้นล่าง: ใช้สีของครึ่งนั้น ไม่มีตา/ขอบเรือง
                        code = (x < 0 ? WHITE : BLACK);
                        double dUp = Math.hypot(x, z - R / 2.0), dDn = Math.hypot(x, z + R / 2.0);
                        if (dUp < R / 2.0) code = BLACK;
                        else if (dDn < R / 2.0) code = WHITE;
                    }
                    m.set(x, Y - d, z);
                    w.setBlockState(m, block(code, ring), flags);
                }
            }
        }

        // เสาปากั้ว 8 ต้นรอบแท่น: ขาว/ดำสลับ หัวเสาเป็นตะเกียงทะเล
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4.0 * k;
            int px = (int) Math.round(Math.cos(a) * (R - 6)), pz = (int) Math.round(Math.sin(a) * (R - 6));
            BlockState col = (k & 1) == 0 ? Blocks.QUARTZ_PILLAR.getDefaultState() : Blocks.OBSIDIAN.getDefaultState();
            for (int h = 1; h <= 14; h++) {
                m.set(px, Y + h, pz);
                w.setBlockState(m, col, flags);
            }
            m.set(px, Y + 15, pz);
            w.setBlockState(m, Blocks.SEA_LANTERN.getDefaultState(), flags);
        }
    }
}
