package net.sf.chttpc.demo;

import android.support.annotation.Px;

import com.qozix.tileview.detail.DetailLevel;
import com.qozix.tileview.detail.DetailLevelManager;

import java.util.Collections;

public final class TileZLevelManager extends DetailLevelManager {
    private static final int TILE_SIZE = 256;

    private static final int MAX_Z_LEVEL = 19;

    private static final double[] Z_LEVELS_SCALE = new double[] {
            360d, 180d, 90d, 45d, 22.5d, 11.25d, 5.625d, 2.813d, 1.406d, 0.703d, 0.352d, 0.176d, 0.088d, 0.044d, 0.022d, 0.011d, 0.005d, 0.003d, 0.001d, 0.0005d,
    };

    private static final long[] Z_LEVELS_TILE_COUNT = new long[] {
            1, 4, 16, 64, 256, 1_024, 4_096, 16_384, 65_536, 262_144, 1_048_576, 4_194_304, 16_777_216, 67_108_864, 268_435_456, 1_073_741_824, 4_294_967_296L, 17_179_869_184L, 68_719_476_736L, 274_877_906_944L,
    };

    private final double ppx;
    private final int defaultLevel;
    private final ZLevel def;

    public TileZLevelManager(int defaultLevel, double ppx) {
        super();

        this.ppx = ppx;
        this.defaultLevel = defaultLevel;
        this.def = new ZLevel(defaultLevel);

        addTileLevel(defaultLevel);
    }

    public void update() {
        super.update();
    }

    public static double worldSizeInTiles(int zLevel) {
        return Math.sqrt(Z_LEVELS_TILE_COUNT[zLevel]);
    }

    public static double worldSizeInPx(int zLevel, double ppx) {
        return 256L * worldSizeInTiles(zLevel) * ppx;
    }

    public static float calculateScale(int z, int defaultLevel) {
        return (float) (Z_LEVELS_SCALE[defaultLevel] / Z_LEVELS_SCALE[z]);
    }

    @SuppressWarnings("unchecked")
    public void addTileLevel(int z) {
        if (z < 0 || z > MAX_Z_LEVEL) {
            throw new IndexOutOfBoundsException();
        }

        final ZLevel detailLevel = new ZLevel(z);
        if(!mDetailLevelLinkedList.contains(detailLevel)) {
            mDetailLevelLinkedList.add( detailLevel );
            Collections.sort( mDetailLevelLinkedList );
            update();
        }
    }

    public @Px long getMapSize() {
        return def.worldSizePx();
    }

    public final class ZLevel extends DetailLevel {
        private final int z;
        private final int sizeTiles;
        private final long sizePx;

        public ZLevel(int z) {
            super(TileZLevelManager.this, calculateScale(z, defaultLevel), null, TILE_SIZE, TILE_SIZE);

            this.z = z;
            this.sizeTiles = (int) Math.floor(worldSizeInTiles(z));
            this.sizePx = (long) Math.floor(worldSizeInPx(z, ppx));
        }

        public int getZ() {
            return z;
        }

        public int worldSizeTiles() {
            return sizeTiles;
        }

        public @Px long worldSizePx() {
            return sizePx;
        }

        @Override
        public String toString() {
            return "Z: " + z;
        }
    }
}
