package io.netnotes.debug;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.terminal.layout.TerminalLayoutGroup;
import io.netnotes.terminal.layout.TerminalLayoutNode;
import io.netnotes.terminal.layout.TerminalSizeable;

public final class RenderDiagnostics {
    private static final LogLevel LOG_LEVEL = LogLevel.IMPORTANT;
    private static final long DEFAULT_SUPPRESS_NS = 750_000_000L;
    private static final long SWAP_TRACE_WINDOW_NS = 5_000_000_000L;
    private static final Map<String, Long> LAST_LOGGED_NS = new ConcurrentHashMap<>();
    private static final Map<String, SwapTraceWatch> ACTIVE_SWAP_TRACE = new ConcurrentHashMap<>();

    private static final class SwapTraceWatch {
        private final String owner;
        private final String reason;
        private final long expiresAtNs;

        private SwapTraceWatch(String owner, String reason, long expiresAtNs) {
            this.owner = owner;
            this.reason = reason;
            this.expiresAtNs = expiresAtNs;
        }
    }

    private RenderDiagnostics() {
    }

    public static void logImportant(String key, String message) {
        logImportant(key, DEFAULT_SUPPRESS_NS, () -> message);
    }

    public static void logImportant(String key, Supplier<String> messageSupplier) {
        logImportant(key, DEFAULT_SUPPRESS_NS, messageSupplier);
    }

    public static void logImportant(String key, long suppressNs, Supplier<String> messageSupplier) {
        if (!shouldLog(key, suppressNs)) {
            return;
        }
        Log.logMsg(messageSupplier.get(), LOG_LEVEL);
    }

    public static void logRenderDrop(String key, String stage, String reason, Supplier<String> detailsSupplier) {
        logRenderDrop(key, DEFAULT_SUPPRESS_NS, stage, reason, detailsSupplier);
    }

    public static void logRenderDrop(
        String key,
        long suppressNs,
        String stage,
        String reason,
        Supplier<String> detailsSupplier
    ) {
        logImportant(key, suppressNs, () -> formatDiagnostic("RenderDrop", stage, reason, detailsSupplier));
    }

    public static void logRenderBlocker(String key, String stage, String reason, Supplier<String> detailsSupplier) {
        logRenderBlocker(key, DEFAULT_SUPPRESS_NS, stage, reason, detailsSupplier);
    }

    public static void logRenderBlocker(
        String key,
        long suppressNs,
        String stage,
        String reason,
        Supplier<String> detailsSupplier
    ) {
        logImportant(key, suppressNs, () -> formatDiagnostic("RenderBlocker", stage, reason, detailsSupplier));
    }

    public static void armSwapTrace(String owner, String reason, TerminalRenderable... renderables) {
        long expiresAtNs = System.nanoTime() + SWAP_TRACE_WINDOW_NS;
        StringBuilder watched = new StringBuilder("[");
        boolean appended = false;

        if (renderables != null) {
            for (TerminalRenderable renderable : renderables) {
                if (renderable == null) {
                    continue;
                }
                ACTIVE_SWAP_TRACE.put(
                    renderable.getName(),
                    new SwapTraceWatch(owner, reason, expiresAtNs)
                );
                if (appended) {
                    watched.append(", ");
                }
                watched.append(renderable.getName());
                appended = true;
            }
        }

        watched.append(']');
        logImportant(
            "swap-trace-arm:" + owner,
            0L,
            () -> formatDiagnostic(
                "SwapTrace",
                "arm",
                "watch-installed",
                () -> "owner=" + owner
                    + "\n\treason=" + reason
                    + "\n\twatch=" + watched
            )
        );
    }

    public static void logSwapTraceEvent(String owner, String stage, Supplier<String> detailsSupplier) {
        logImportant(
            "swap-trace-event:" + owner + ":" + stage,
            0L,
            () -> formatDiagnostic("SwapTrace", stage, owner, detailsSupplier)
        );
    }

    public static void logSwapTrace(String stage, TerminalRenderable renderable, Supplier<String> detailsSupplier) {
        SwapTraceWatch watch = getSwapTraceWatch(renderable);
        if (watch == null) {
            return;
        }

        logImportant(
            "swap-trace:" + watch.owner + ":" + stage + ":" + renderable.getName(),
            0L,
            () -> formatDiagnostic(
                "SwapTrace",
                stage,
                watch.reason,
                () -> "owner=" + watch.owner
                    + "\n\trenderable=" + summarizeRenderable(renderable)
                    + appendDetails(detailsSupplier)
            )
        );
    }

    private static SwapTraceWatch getSwapTraceWatch(TerminalRenderable renderable) {
        if (renderable == null) {
            return null;
        }
        SwapTraceWatch watch = ACTIVE_SWAP_TRACE.get(renderable.getName());
        if (watch == null) {
            return null;
        }
        if (System.nanoTime() <= watch.expiresAtNs) {
            return watch;
        }
        ACTIVE_SWAP_TRACE.remove(renderable.getName(), watch);
        return null;
    }

    private static String appendDetails(Supplier<String> detailsSupplier) {
        if (detailsSupplier == null) {
            return "";
        }
        String details = detailsSupplier.get();
        if (details == null || details.isBlank()) {
            return "";
        }
        if (details.startsWith("\n")) {
            return details;
        }
        return "\n\t" + details;
    }

    private static boolean shouldLog(String key, long suppressNs) {
        long now = System.nanoTime();
        Long last = LAST_LOGGED_NS.get(key);
        if (last != null && now - last < suppressNs) {
            return false;
        }
        LAST_LOGGED_NS.put(key, now);
        return true;
    }

    private static String formatDiagnostic(
        String category,
        String stage,
        String reason,
        Supplier<String> detailsSupplier
    ) {
        StringBuilder sb = new StringBuilder()
            .append('[').append(category).append("] ")
            .append(stage)
            .append(" reason=").append(reason);
        String details = detailsSupplier != null ? detailsSupplier.get() : null;
        if (details != null && !details.isBlank()) {
            if (details.startsWith("\n")) {
                sb.append(details);
            } else {
                sb.append("\n\t").append(details);
            }
        }
        return sb.toString();
    }

    public static String summarizeText(String text, int maxChars) {
        if (text == null) {
            return "null";
        }
        String normalized = text
            .replace("\\", "\\\\")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
        if (normalized.length() <= maxChars) {
            return "\"" + normalized + "\"";
        }
        int clippedLength = Math.max(0, maxChars - 3);
        return "\"" + normalized.substring(0, clippedLength) + "...\""
            + " (len=" + text.length() + ")";
    }

    public static String summarizeRegion(TerminalRectangle region) {
        if (region == null) {
            return "null";
        }
        return String.format(
            "[x=%d,y=%d,w=%d,h=%d,absX=%d,absY=%d]",
            region.getX(),
            region.getY(),
            region.getWidth(),
            region.getHeight(),
            region.getAbsoluteX(),
            region.getAbsoluteY()
        );
    }

    public static String summarizeRenderable(TerminalRenderable renderable) {
        if (renderable == null) {
            return "null";
        }
        return renderable.getName()
            + "{region=" + summarizeRegion(renderable.getRegion())
            + ", requested=" + summarizeRegion(renderable.getRequestedRegion())
            + ", visible=" + renderable.isVisible()
            + ", effectiveVisible=" + renderable.isEffectivelyVisible()
            + ", hidden=" + renderable.isHidden()
            + ", invisible=" + renderable.isInvisible()
            + ", attached=" + renderable.isAttachedToLayoutManager()
            + "}";
    }

    public static String summarizeSizing(TerminalRenderable renderable) {
        if (renderable == null) {
            return "sizing=null";
        }

        StringBuilder sb = new StringBuilder("sizing{");
        if (renderable instanceof TerminalSizeable sizeable) {
            SizePreference widthPref = sizeable.getWidthPreference();
            SizePreference heightPref = sizeable.getHeightPreference();
            sb.append("wPref=").append(widthPref)
                .append(", hPref=").append(heightPref)
                .append(", min=").append(sizeable.getMinWidth()).append('x').append(sizeable.getMinHeight())
                .append(", percent=").append(sizeable.getPercentWidth()).append('x').append(sizeable.getPercentHeight())
                .append(", hiddenManaged=").append(sizeable.isHiddenManaged());
        } else {
            sb.append("non-sizeable");
        }

        TerminalRectangle requested = renderable.getRequestedRegion();
        if (requested != null) {
            sb.append(", requested=").append(summarizeRegion(requested));
        }

        TerminalRectangle region = renderable.getRegion();
        if (region != null) {
            sb.append(", region=").append(summarizeRegion(region));
        }

        sb.append('}');
        return sb.toString();
    }

    public static String summarizeLayoutData(TerminalLayoutData layoutData) {
        if (layoutData == null) {
            return "null";
        }
        return "layout{region=" + summarizeRegion(layoutData.getSpatialRegion())
            + ", axis[x=" + layoutData.hasAxisChange(0)
            + ",y=" + layoutData.hasAxisChange(1)
            + ",w=" + layoutData.hasAxisChange(2)
            + ",h=" + layoutData.hasAxisChange(3)
            + "], hidden=" + layoutData.getHidden()
            + ", invisible=" + layoutData.getInvisible()
            + ", effectiveVisible=" + layoutData.getEffectivelyVisible()
            + "}";
    }

    public static String summarizeNode(TerminalLayoutNode node) {
        if (node == null) {
            return "null";
        }
        TerminalLayoutGroup group = node.getMemberGroup();
        return node.getName()
            + "{group=" + (group != null ? group.getGroupId() : "-")
            + ", depth=" + node.getDepth()
            + ", renderable=" + summarizeRenderable(node.getRenderable())
            + ", calculated=" + summarizeLayoutData(node.getCalculatedLayout())
            + "}";
    }

    public static String summarizeNodes(Collection<TerminalLayoutNode> nodes, int maxItems) {
        if (nodes == null || nodes.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        Iterator<TerminalLayoutNode> it = nodes.iterator();
        int index = 0;
        while (it.hasNext()) {
            TerminalLayoutNode node = it.next();
            if (index > 0) {
                sb.append(", ");
            }
            if (index >= maxItems) {
                sb.append("... +").append(nodes.size() - maxItems).append(" more");
                break;
            }
            sb.append(node.getName());
            TerminalLayoutGroup group = node.getMemberGroup();
            if (group != null) {
                sb.append('@').append(group.getGroupId());
            }
            TerminalRectangle requested = node.getRenderable().getRequestedRegion();
            if (requested != null) {
                sb.append("(req=").append(summarizeRegion(requested)).append(')');
            }
            index++;
        }
        sb.append(']');
        return sb.toString();
    }

    public static String summarizeRenderables(Collection<? extends TerminalRenderable> renderables, int maxItems) {
        if (renderables == null || renderables.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        Iterator<? extends TerminalRenderable> it = renderables.iterator();
        int index = 0;

        while (it.hasNext()) {
            TerminalRenderable renderable = it.next();
            if (index > 0) {
                sb.append(", ");
            }
            if (index >= maxItems) {
                sb.append("... +").append(renderables.size() - maxItems).append(" more");
                break;
            }

            sb.append(renderable != null ? summarizeRenderableSummary(renderable) : "null");
            index++;
        }

        sb.append(']');
        return sb.toString();
    }

    public static String summarizeDamage(TerminalRectangle[] rects, int maxItems) {
        if (rects == null || rects.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int emitted = 0;
        for (TerminalRectangle rect : rects) {
            if (rect == null) {
                continue;
            }
            if (emitted > 0) {
                sb.append(", ");
            }
            if (emitted >= maxItems) {
                sb.append("... +").append(rects.length - maxItems).append(" more");
                break;
            }
            sb.append(summarizeRegion(rect));
            emitted++;
        }
        sb.append(']');
        return sb.toString();
    }

    private static String summarizeRenderableSummary(TerminalRenderable renderable) {
        StringBuilder sb = new StringBuilder()
            .append(renderable.getName())
            .append("{hidden=").append(renderable.isHidden())
            .append(", invisible=").append(renderable.isInvisible())
            .append(", layoutExcluded=").append(renderable.isLayoutExcluded());

        if (renderable instanceof TerminalSizeable sizeable) {
            sb.append(", wPref=").append(sizeable.getWidthPreference())
                .append(", hPref=").append(sizeable.getHeightPreference())
                .append(", min=").append(sizeable.getMinWidth()).append('x').append(sizeable.getMinHeight());
        }

        TerminalRectangle region = renderable.getRegion();
        if (region != null) {
            sb.append(", region=").append(summarizeRegion(region));
        }

        sb.append('}');
        return sb.toString();
    }
}
