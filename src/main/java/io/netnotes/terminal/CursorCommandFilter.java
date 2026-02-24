package io.netnotes.terminal;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.ui.containers.ContainerCommands;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;

/**
 * Utility to detect and remove cursor-control commands.
 *
 * We key off command names to keep this generic across renderer backends.
 */
final class CursorCommandFilter {

    private static final Set<String> CURSOR_COMMAND_EXACT = Set.of(
        "set_cursor",
        "set_cursor_pos",
        "set_cursor_position",
        "set_caret_pos",
        "set_caret_position",
        "set_pointer_pos",
        "set_pointer_position",
        "set_mouse_pos",
        "set_mouse_position",
        "warp_cursor",
        "warp_pointer",
        "warp_mouse",
        "mouse_move_abs",
        "mouse_move_rel"
    );

    static final class FilterResult {
        private final NoteBytesMap command;
        private final int removedCursorCommands;
        private final boolean blocked;

        FilterResult(NoteBytesMap command, int removedCursorCommands, boolean blocked) {
            this.command = command;
            this.removedCursorCommands = removedCursorCommands;
            this.blocked = blocked;
        }

        NoteBytesMap command() {
            return command;
        }

        int removedCursorCommands() {
            return removedCursorCommands;
        }

        boolean blocked() {
            return blocked;
        }
    }

    static final class ClampResult {
        private final NoteBytesMap command;
        private final int clampedCursorCommands;
        private final int clampedCoordinates;

        ClampResult(NoteBytesMap command, int clampedCursorCommands, int clampedCoordinates) {
            this.command = command;
            this.clampedCursorCommands = clampedCursorCommands;
            this.clampedCoordinates = clampedCoordinates;
        }

        NoteBytesMap command() {
            return command;
        }

        int clampedCursorCommands() {
            return clampedCursorCommands;
        }

        int clampedCoordinates() {
            return clampedCoordinates;
        }
    }

    private static final class Bounds {
        private final double minX;
        private final double minY;
        private final double maxX;
        private final double maxY;

        Bounds(double minX, double minY, double maxX, double maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    private static final class ValueClampResult {
        private final NoteBytes clamped;
        private final boolean changed;

        ValueClampResult(NoteBytes clamped, boolean changed) {
            this.clamped = clamped;
            this.changed = changed;
        }
    }

    private static final class CommandClampResult {
        private final NoteBytesMap command;
        private final int clampedCoordinates;

        CommandClampResult(NoteBytesMap command, int clampedCoordinates) {
            this.command = command;
            this.clampedCoordinates = clampedCoordinates;
        }

        boolean changed() {
            return clampedCoordinates > 0;
        }
    }

    private CursorCommandFilter() {}

    static FilterResult filter(NoteBytesMap command) {
        NoteBytes cmd = command.get(Keys.CMD);
        if (cmd == null) {
            return new FilterResult(command, 0, false);
        }

        if (isCursorControlCommand(cmd)) {
            return new FilterResult(null, 1, true);
        }

        if (!ContainerCommands.CONAINER_BATCH.equals(cmd)) {
            return new FilterResult(command, 0, false);
        }

        NoteBytes batchCommands = command.get(ContainerCommands.BATCH_COMMANDS);
        if (batchCommands == null || 
                batchCommands.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            return new FilterResult(command, 0, false);
        }

        NoteBytesReadOnly[] commandArray = batchCommands.getAsNoteBytesArrayReadOnly().getAsArray();
        ArrayList<NoteBytes> keptCommands = new ArrayList<>(commandArray.length);
        int removed = 0;

        for (NoteBytesReadOnly nextCommand : commandArray) {
            if (nextCommand == null) {
                continue;
            }

            if (nextCommand.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                keptCommands.add(nextCommand);
                continue;
            }

            NoteBytesMap nested = nextCommand.getAsNoteBytesMap();
            if (isCursorControlCommand(nested.get(Keys.CMD))) {
                removed++;
                continue;
            }

            keptCommands.add(nextCommand);
        }

        if (removed == 0) {
            return new FilterResult(command, 0, false);
        }

        if (keptCommands.isEmpty()) {
            return new FilterResult(null, removed, true);
        }

        NoteBytesMap sanitized = copyMap(command);
        sanitized.put(
            ContainerCommands.BATCH_COMMANDS,
            new NoteBytesArrayReadOnly(keptCommands.toArray(new NoteBytes[0]))
        );
        return new FilterResult(sanitized, removed, false);
    }

    static ClampResult clampToBounds(NoteBytesMap command, NoteBytes boundsBytes) {
        Bounds bounds = parseBounds(boundsBytes);
        if (bounds == null) {
            return new ClampResult(command, 0, 0);
        }

        NoteBytes cmd = command.get(Keys.CMD);
        if (cmd == null) {
            return new ClampResult(command, 0, 0);
        }

        if (isCursorControlCommand(cmd)) {
            CommandClampResult result = clampCursorCommand(command, bounds);
            if (!result.changed()) {
                return new ClampResult(command, 0, 0);
            }
            return new ClampResult(result.command, 1, result.clampedCoordinates);
        }

        if (!ContainerCommands.CONAINER_BATCH.equals(cmd)) {
            return new ClampResult(command, 0, 0);
        }

        NoteBytes batchCommands = command.get(ContainerCommands.BATCH_COMMANDS);
        if (batchCommands == null ||
                batchCommands.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            return new ClampResult(command, 0, 0);
        }

        NoteBytesReadOnly[] commandArray = batchCommands.getAsNoteBytesArrayReadOnly().getAsArray();
        ArrayList<NoteBytes> updatedCommands = null;
        int clampedCommands = 0;
        int clampedCoordinates = 0;

        for (int i = 0; i < commandArray.length; i++) {
            NoteBytesReadOnly nextCommand = commandArray[i];
            if (nextCommand == null) {
                continue;
            }

            NoteBytes commandToKeep = nextCommand;
            if (nextCommand.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesMap nested = nextCommand.getAsNoteBytesMap();
                if (isCursorControlCommand(nested.get(Keys.CMD))) {
                    CommandClampResult nestedResult = clampCursorCommand(nested, bounds);
                    if (nestedResult.changed()) {
                        if (updatedCommands == null) {
                            updatedCommands = new ArrayList<>(commandArray.length);
                            for (int j = 0; j < i; j++) {
                                updatedCommands.add(commandArray[j]);
                            }
                        }
                        clampedCommands++;
                        clampedCoordinates += nestedResult.clampedCoordinates;
                        commandToKeep = nestedResult.command.toNoteBytes();
                    }
                }
            }

            if (updatedCommands != null) {
                updatedCommands.add(commandToKeep);
            }
        }

        if (updatedCommands == null) {
            return new ClampResult(command, 0, 0);
        }

        NoteBytesMap clampedBatch = copyMap(command);
        clampedBatch.put(
            ContainerCommands.BATCH_COMMANDS,
            new NoteBytesArrayReadOnly(updatedCommands.toArray(new NoteBytes[0]))
        );

        return new ClampResult(clampedBatch, clampedCommands, clampedCoordinates);
    }

    private static NoteBytesMap copyMap(NoteBytesMap original) {
        NoteBytesMap copy = new NoteBytesMap();
        for (NoteBytes key : original.keySet()) {
            copy.put(key, original.get(key));
        }
        return copy;
    }

    private static CommandClampResult clampCursorCommand(NoteBytesMap command, Bounds bounds) {
        int clampedCoordinates = 0;
        NoteBytesMap updated = null;

        ValueClampResult clampedX = clampValue(command.get(Keys.X), bounds.minX, bounds.maxX);
        ValueClampResult clampedY = clampValue(command.get(Keys.Y), bounds.minY, bounds.maxY);

        if (clampedX.changed || clampedY.changed) {
            updated = copyMap(command);
            if (clampedX.changed) {
                updated.put(Keys.X, clampedX.clamped);
                clampedCoordinates++;
            }
            if (clampedY.changed) {
                updated.put(Keys.Y, clampedY.clamped);
                clampedCoordinates++;
            }
        }

        NoteBytesMap sourceForCoords = updated != null ? updated : command;
        NoteBytes coordinatesBytes = sourceForCoords.get(ContainerCommands.COORDINATES);
        if (coordinatesBytes != null &&
                coordinatesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            NoteBytesMap coordinates = coordinatesBytes.getAsNoteBytesMap();
            ValueClampResult clampedCoordinatesX =
                clampValue(coordinates.get(Keys.X), bounds.minX, bounds.maxX);
            ValueClampResult clampedCoordinatesY =
                clampValue(coordinates.get(Keys.Y), bounds.minY, bounds.maxY);

            if (clampedCoordinatesX.changed || clampedCoordinatesY.changed) {
                NoteBytesMap coordinatesCopy = copyMap(coordinates);
                if (clampedCoordinatesX.changed) {
                    coordinatesCopy.put(Keys.X, clampedCoordinatesX.clamped);
                    clampedCoordinates++;
                }
                if (clampedCoordinatesY.changed) {
                    coordinatesCopy.put(Keys.Y, clampedCoordinatesY.clamped);
                    clampedCoordinates++;
                }
                if (updated == null) {
                    updated = copyMap(command);
                }
                updated.put(ContainerCommands.COORDINATES, coordinatesCopy.toNoteBytes());
            }
        }

        return new CommandClampResult(updated != null ? updated : command, clampedCoordinates);
    }

    private static ValueClampResult clampValue(NoteBytes value, double min, double max) {
        if (value == null) {
            return new ValueClampResult(null, false);
        }

        byte type = value.getType();
        if (!isNumericType(type)) {
            return new ValueClampResult(value, false);
        }

        double current = value.getAsDouble();
        double clamped = clamp(current, min, max);
        if (Double.compare(current, clamped) == 0) {
            return new ValueClampResult(value, false);
        }

        switch (type) {
            case NoteBytesMetaData.BYTE_TYPE:
            case NoteBytesMetaData.SHORT_TYPE:
            case NoteBytesMetaData.SHORT_LE_TYPE:
            case NoteBytesMetaData.INTEGER_TYPE:
            case NoteBytesMetaData.INTEGER_LE_TYPE: {
                long minInt = (long) Math.ceil(min);
                long maxInt = (long) Math.floor(max);
                long clampedInt = Math.round(clamped);
                if (minInt <= maxInt) {
                    clampedInt = Math.max(minInt, Math.min(maxInt, clampedInt));
                }
                int valueAsInt = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, clampedInt));
                return new ValueClampResult(new NoteBytes(valueAsInt), true);
            }
            case NoteBytesMetaData.LONG_TYPE:
            case NoteBytesMetaData.LONG_LE_TYPE: {
                long minLong = (long) Math.ceil(min);
                long maxLong = (long) Math.floor(max);
                long clampedLong = Math.round(clamped);
                if (minLong <= maxLong) {
                    clampedLong = Math.max(minLong, Math.min(maxLong, clampedLong));
                }
                return new ValueClampResult(new NoteBytes(clampedLong), true);
            }
            default:
                return new ValueClampResult(new NoteBytes(clamped), true);
        }
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static boolean isNumericType(byte type) {
        return type == NoteBytesMetaData.BYTE_TYPE ||
            type == NoteBytesMetaData.SHORT_TYPE ||
            type == NoteBytesMetaData.SHORT_LE_TYPE ||
            type == NoteBytesMetaData.INTEGER_TYPE ||
            type == NoteBytesMetaData.INTEGER_LE_TYPE ||
            type == NoteBytesMetaData.FLOAT_TYPE ||
            type == NoteBytesMetaData.FLOAT_LE_TYPE ||
            type == NoteBytesMetaData.DOUBLE_TYPE ||
            type == NoteBytesMetaData.DOUBLE_LE_TYPE ||
            type == NoteBytesMetaData.LONG_TYPE ||
            type == NoteBytesMetaData.LONG_LE_TYPE ||
            type == NoteBytesMetaData.BIG_INTEGER_TYPE ||
            type == NoteBytesMetaData.BIG_DECIMAL_TYPE;
    }

    private static Bounds parseBounds(NoteBytes boundsBytes) {
        if (boundsBytes == null ||
                boundsBytes.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            return null;
        }
        return parseBoundsMap(boundsBytes.getAsNoteBytesMap(), 0);
    }

    private static Bounds parseBoundsMap(NoteBytesMap map, int depth) {
        if (map == null || depth > 3) {
            return null;
        }

        Bounds direct = parseDirectBounds(map);
        if (direct != null) {
            return direct;
        }

        NoteBytes nestedRegion = map.get(ContainerCommands.REGION);
        if (nestedRegion != null &&
                nestedRegion.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            Bounds regionBounds = parseBoundsMap(nestedRegion.getAsNoteBytesMap(), depth + 1);
            if (regionBounds != null) {
                return regionBounds;
            }
        }

        NoteBytes nestedBounds = map.get(ContainerCommands.CONTENT_BOUNDS);
        if (nestedBounds != null &&
                nestedBounds.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            return parseBoundsMap(nestedBounds.getAsNoteBytesMap(), depth + 1);
        }

        return null;
    }

    private static Bounds parseDirectBounds(NoteBytesMap map) {
        Double left = getNumber(map, Keys.LEFT);
        Double top = getNumber(map, Keys.TOP);
        Double right = getNumber(map, Keys.RIGHT);
        Double bottom = getNumber(map, Keys.BOTTOM);
        if (left != null && top != null && right != null && bottom != null) {
            return fromEdges(left, top, right, bottom);
        }

        Double x = getNumber(map, Keys.X);
        Double y = getNumber(map, Keys.Y);
        Double width = getNumber(map, Keys.WIDTH);
        Double height = getNumber(map, Keys.HEIGHT);
        if (x != null && y != null && width != null && height != null) {
            return fromOriginAndDimensions(x, y, width, height);
        }

        NoteBytes coordinatesBytes = map.get(ContainerCommands.COORDINATES);
        NoteBytes dimensionsBytes = map.get(ContainerCommands.DIMENSIONS);
        if (coordinatesBytes != null &&
                coordinatesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE &&
                dimensionsBytes != null &&
                dimensionsBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            NoteBytesMap coordinates = coordinatesBytes.getAsNoteBytesMap();
            NoteBytesMap dimensions = dimensionsBytes.getAsNoteBytesMap();
            Double cx = getNumber(coordinates, Keys.X);
            Double cy = getNumber(coordinates, Keys.Y);
            Double cwidth = getNumber(dimensions, Keys.WIDTH);
            Double cheight = getNumber(dimensions, Keys.HEIGHT);
            if (cx != null && cy != null && cwidth != null && cheight != null) {
                return fromOriginAndDimensions(cx, cy, cwidth, cheight);
            }
        }

        return null;
    }

    private static Bounds fromOriginAndDimensions(double x, double y, double width, double height) {
        double maxX = width <= 0 ? x : x + width;
        double maxY = height <= 0 ? y : y + height;

        // Integer dimensions usually represent counts, so clamp to the final index.
        if (width > 0 && Math.floor(width) == width) {
            maxX = x + width - 1;
        }
        if (height > 0 && Math.floor(height) == height) {
            maxY = y + height - 1;
        }

        return fromEdges(x, y, maxX, maxY);
    }

    private static Bounds fromEdges(double left, double top, double right, double bottom) {
        if (!Double.isFinite(left) ||
                !Double.isFinite(top) ||
                !Double.isFinite(right) ||
                !Double.isFinite(bottom)) {
            return null;
        }

        double minX = Math.min(left, right);
        double maxX = Math.max(left, right);
        double minY = Math.min(top, bottom);
        double maxY = Math.max(top, bottom);

        return new Bounds(minX, minY, maxX, maxY);
    }

    private static Double getNumber(NoteBytesMap map, NoteBytes key) {
        NoteBytes value = map.get(key);
        if (value == null) {
            return null;
        }

        if (!isNumericType(value.getType())) {
            return null;
        }

        double asDouble = value.getAsDouble();
        return Double.isFinite(asDouble) ? asDouble : null;
    }

    static boolean isCursorControlCommand(NoteBytes cmd) {
        if (cmd == null) {
            return false;
        }

        String commandString = cmd.getAsString();
        if (commandString == null || commandString.isEmpty()) {
            return false;
        }

        String normalized = commandString.toLowerCase(Locale.ROOT);
        if (CURSOR_COMMAND_EXACT.contains(normalized)) {
            return true;
        }

        if (normalized.contains("cursor") || normalized.contains("caret")) {
            return true;
        }

        if (normalized.contains("pointer") &&
                (normalized.contains("set") || normalized.contains("move") || normalized.contains("warp"))) {
            return true;
        }

        if (normalized.contains("mouse") &&
                normalized.contains("move") &&
                (normalized.contains("abs") || normalized.contains("rel") || normalized.contains("position"))) {
            return true;
        }

        return false;
    }
}