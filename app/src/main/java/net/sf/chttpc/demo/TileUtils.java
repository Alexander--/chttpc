package net.sf.chttpc.demo;

import com.qozix.tileview.tiles.Tile;

public class TileUtils {
    private TileUtils() {}

    public static long getTileKey(Tile tile) {
        final TileZLevelManager.ZLevel zLevel = (TileZLevelManager.ZLevel) tile.getDetailLevel();
        return ((long) tile.getColumn()) << 35 | ((long) tile.getRow()) << 10 |  (long) zLevel.getZ();
    }
}
