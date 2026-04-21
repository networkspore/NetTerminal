# Alternate Screen Buffer Deep Dive

## Purpose

Technical reference for implementing terminal alternate screen buffer ("fullscreen" or "exploded") mode in the NetTerminal rendering system.

---

## Terminal Standards

### ANSI Escape Sequences

| Sequence | Hex | Description | Capability Name |
|----------|-----|-------------|-----------------|
| `ESC[?1049h` | `1b 5b 3f 31 30 34 39 68` | Enter alternate screen buffer | `smcup` |
| `ESC[?1049l` | `1b 5b 3f 31 30 34 39 6c` | Exit alternate screen buffer | `rmcup` |
| `ESC[2J` | `1b 5b 32 4a` | Clear entire screen | `clear` |
| `ESC[H` | `1b 5b 48` | Move cursor to home position (0,0) | `home` |
| `ESC[?1000h` | `1b 5b 3f 31 30 30 30 68` | Enable mouse tracking | - |
| `ESC[?1000l` | `1b 5b 3f 31 30 30 30 6c` | Disable mouse tracking | - |

### JLine3 Implementation

```java
// Get terminal instance
Terminal terminal = TerminalBuilder.builder().build();

// Enter alternate buffer
terminal.puts(InfoCmp.Capability.enter_ca_mode);

// Clear screen
terminal.puts(InfoCmp.Capability.clear_screen);

// Exit alternate buffer
terminal.puts(InfoCmp.Capability.exit_ca_mode);
terminal.flush();
```

### Terminal Capabilities

Not all terminals support alternate buffer. Check capability:

```java
boolean supportsAlternateBuffer = terminal.getStringCapability(InfoCmp.Capability.enter_ca_mode) != null;
```

---

## How Alternate Buffer Works

### Normal Mode (Main Buffer)
```
┌─────────────────────────────────┐
│ Line 1: Previous command output │
│ Line 2: More output            │
│ Line 3: Log message            │
│ Line 4:                        │
│ Line 5: User input: █          │
└─────────────────────────────────┘
        ↑ Cursor here

When application exits, previous content is visible
```

### Alternate Buffer Mode
```
┌─────────────────────────────────┐
│                                 │
│     [Application UI]           │
│                                 │
│     [Content rendering]        │
│                                 │
└─────────────────────────────────┘
        ↑ Cursor here

Separate buffer - previous content is preserved
On exit, previous content is restored
```

### Mode Switching

```
┌─────────────┐                    ┌─────────────┐
│ Terminal    │ ──smcup───▶      │ Alternate   │
│ Main Buffer │ ◀──rmcup───       │ Buffer      │
└─────────────┘                    └─────────────┘

Main buffer: History preserved, scrollback available
Alt buffer: Clean slate, no scrollback, application controls all
```

---

## Implementation Strategy

### Level 1: Basic Buffer Switching

**Minimal Implementation**:

```java
public class TerminalRenderer {

    private boolean inAlternateBuffer = false;

    public void enterAlternateBuffer() {
        if (inAlternateBuffer) return;
        
        // Send enter alternate buffer sequence
        output.write("\u001b[?1049h");
        output.flush();
        
        // Clear screen
        output.write("\u001b[2J");
        output.write("\u001b[H");  // Move cursor home
        output.flush();
        
        inAlternateBuffer = true;
    }

    public void exitAlternateBuffer() {
        if (!inAlternateBuffer) return;
        
        // Send exit alternate buffer sequence
        output.write("\u001b[?1049l");
        output.flush();
        
        inAlternateBuffer = false;
    }

    public boolean isInAlternateBuffer() {
        return inAlternateBuffer;
    }
}
```

### Level 2: With Cleanup/Restoration

**Proper Implementation**:

```java
public class TerminalRenderer {

    private boolean inAlternateBuffer = false;
    private TerminalRectangle savedRegion = null;
    private int savedScrollX = 0;
    private int savedScrollY = 0;

    // Called before entering alternate buffer
    public void saveApplicationState(TerminalScrollPanel panel) {
        savedRegion = panel.getRegion();
        savedScrollX = panel.getScrollX();
        savedScrollY = panel.getScrollY();
    }

    // Called after exiting alternate buffer
    public void restoreApplicationState(TerminalScrollPanel panel) {
        if (savedRegion != null) {
            panel.setRegion(savedRegion);
            // panel.scrollTo(savedScrollX, savedScrollY);  // Optional
            savedRegion = null;
        }
    }

    public void enterAlternateBuffer() {
        if (inAlternateBuffer) return;
        
        // Hide cursor during switch
        output.write("\u001b[?25l");  // DECTCEM - hide cursor
        
        // Enter alternate buffer
        output.write("\u001b[?1049h");
        output.flush();
        
        // Clear and position cursor
        output.write("\u001b[2J");
        output.write("\u001b[H");
        output.flush();
        
        // Show cursor in alternate buffer
        output.write("\u001b[?25h");  // DECTCEM - show cursor
        output.flush();
        
        inAlternateBuffer = true;
    }

    public void exitAlternateBuffer() {
        if (!inAlternateBuffer) return;
        
        // Hide cursor during switch
        output.write("\u001b[?25l");
        
        // Exit alternate buffer (automatically restores main buffer)
        output.write("\u001b[?1049l");
        output.flush();
        
        // Show cursor
        output.write("\u001b[?25h");
        output.flush();
        
        inAlternateBuffer = false;
    }
}
```

### Level 3: Scroll Awareness

**With Scroll Position Sync**:

```java
public class TerminalRenderer {

    private int terminalScrollRow = 0;
    private int contentRows = 0;

    /**
     * Notify renderer that content has changed size.
     * In alternate buffer, we may need to adjust terminal scroll area.
     */
    public void updateContentSize(int rows) {
        this.contentRows = rows;
        
        if (inAlternateBuffer && contentRows > terminalScreenHeight) {
            // Terminal will handle scrolling natively
            // We just need to ensure we render all content lines
        }
    }

    /**
     * Set terminal scroll position.
     * Called by TerminalScrollPanel when scrolling.
     */
    public void setTerminalScrollPosition(int row) {
        if (!inAlternateBuffer) return;
        
        this.terminalScrollRow = row;
        
        // In alternate buffer, the cursor position determines view
        // Move cursor to show that portion of the screen
        // ESC[{row};{col}H moves cursor
        output.write(String.format("\u001b[%d;1H", row + 1));
        output.flush();
    }

    /**
     * Get current terminal scroll position.
     * Terminal may scroll via mouse wheel - we need to detect this.
     */
    public int getTerminalScrollPosition() {
        return terminalScrollRow;
    }
}
```

---

## Mouse Support in Alternate Buffer

### Enabling Mouse Tracking

```java
// Enable basic mouse tracking (button press/release)
output.write("\u001b[?1000h");
// Enable mouse highlighting
output.write("\u001b[?1001h");
// Enable focus events (for mouse enter/leave)
output.write("\u001b[?1002h");
output.flush();
```

### Mouse Event Format

Mouse events come as escape sequences:

```
ESC[M <button> <x+32> <y+32>

Example: ESC[M !"#
- ! = button 1 (1 + 32 = 33 = '!')
- " = column 2 (2 + 32 = 34 = '"')
- # = row 3 (3 + 32 = 35 = '#')
```

Button encoding:
- 0 + 32 = Space (button 1 pressed)
- 1 + 32 = ! (button 2 pressed)
- 2 + 32 = " (button 3 pressed)
- 3 + 32 = # (button released)
- 32 + 32 = @ (shift+button1)
- etc.

### Handling in JLine3

```java
// JLine3 handles mouse events through LineReader
LineReader reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .build();

// Enable mouse in the LineReader
reader.getTerminal().puts(Capability.key_mouse);

// Mouse events come through as special key sequences
// Usually need to handle at the input layer
```

### Handling Mouse Wheel

Mouse wheel generates scroll events:

```
Scroll up:    ESC[M a <x> <y>
Scroll down:  ESC[M ` <x> <y>
```

In alternate buffer, the terminal may handle wheel natively for scrolling.
Our application needs to:
1. Detect mouse wheel events
2. Adjust scroll position
3. Re-render visible portion

---

## Text Selection

### Native Selection (Terminal Handled)

In alternate buffer:
1. Terminal handles text selection natively (Shift+Click, Drag)
2. Terminal copy buffer (usually Ctrl+Shift+C to copy)
3. Application has no visibility into selection

### Application-Controlled Selection

To implement app-controlled selection:
1. Track mouse down/up in alternate buffer
2. Calculate selected region (row start/col start to row end/col end)
3. Maintain selection state
4. Render with selected style (reverse video)
5. Handle copy command (usually Ctrl+C, intercept before terminal)

```java
public class SelectionManager {
    
    private int selectionStartRow = -1;
    private int selectionStartCol = -1;
    private int selectionEndRow = -1;
    private int selectionEndCol = -1;
    private boolean isSelecting = false;

    public void onMouseDown(int row, int col) {
        selectionStartRow = row;
        selectionStartCol = col;
        selectionEndRow = row;
        selectionEndCol = col;
        isSelecting = true;
    }

    public void onMouseMove(int row, int col) {
        if (!isSelecting) return;
        selectionEndRow = row;
        selectionEndCol = col;
        // Invalidate affected region
        invalidateSelection();
    }

    public void onMouseUp(int row, int col) {
        if (!isSelecting) return;
        selectionEndRow = row;
        selectionEndCol = col;
        isSelecting = false;
        // Selection complete
    }

    public boolean isSelected(int row, int col) {
        if (selectionStartRow < 0) return false;
        
        int minRow = Math.min(selectionStartRow, selectionEndRow);
        int maxRow = Math.max(selectionStartRow, selectionEndRow);
        
        if (row < minRow || row > maxRow) return false;
        
        if (row == minRow && row == maxRow) {
            // Same row
            int minCol = Math.min(selectionStartCol, selectionEndCol);
            int maxCol = Math.max(selectionStartCol, selectionEndCol);
            return col >= minCol && col <= maxCol;
        }
        
        if (row == minRow) return col >= selectionStartCol;
        if (row == maxRow) return col <= selectionEndCol;
        
        return true; // Full row between start and end
    }
}
```

---

## Integration with TerminalScrollPanel

### Mode Transition

```java
public class TerminalScrollPanel {

    public void toggleFullTerminalMode() {
        if (viewMode == ViewMode.EMBEDDED) {
            enterFullTerminalMode();
        } else {
            exitFullTerminalMode();
        }
    }

    private void enterFullTerminalMode() {
        // Get renderer
        TerminalRenderer renderer = getTerminalRenderer();
        if (renderer == null) return;

        // Save state
        saveEmbeddedState();

        // Calculate content dimensions
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();

        // Enter alternate buffer
        renderer.enterAlternateBuffer();

        // Set up full-screen rendering
        // Panel now renders at 0,0 with terminal dimensions
        updateRegion(0, 0, terminalWidth, terminalHeight);

        // Enable terminal scrolling for content
        if (contentHeight > terminalHeight) {
            // Terminal will scroll content natively
            renderer.setScrollableContent(contentHeight);
        }

        // Re-render entire content
        invalidate();
        viewMode = ViewMode.FULL_TERMINAL;
    }

    private void exitFullTerminalMode() {
        TerminalRenderer renderer = getTerminalRenderer();
        if (renderer == null) return;

        // Exit alternate buffer
        renderer.exitAlternateBuffer();

        // Restore embedded state
        restoreEmbeddedState();

        viewMode = ViewMode.EMBEDDED;
    }

    // Override render to handle both modes
    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (viewMode == ViewMode.FULL_TERMINAL) {
            renderForTerminalBuffer(batch);
        } else {
            renderForEmbedded(batch);
        }
    }

    private void renderForTerminalBuffer(TerminalBatchBuilder batch) {
        // Full screen rendering - no clipping by parent
        // Content may scroll beyond screen
        
        // Get visible portion
        int firstVisibleRow = terminalScrollPosition;
        int lastVisibleRow = firstVisibleRow + terminalScreenHeight;
        
        // Render only visible lines
        for (int row = firstVisibleRow; row < lastVisibleRow && row < contentHeight; row++) {
            renderContentLine(batch, row);
        }
    }
}
```

---

## Scrollbar in Terminal Mode

### Understanding Terminal Scrollback

In the main buffer:
- Terminal maintains scrollback history
- Mouse wheel scrolls through history
- "Real" lines that scrolled off-screen are in history

In alternate buffer:
- No scrollback history
- Application draws whatever is visible
- If application wants a scrollbar, it must render it

### Rendering a "Virtual" Scrollbar

In full terminal mode with content taller than screen:

```
┌──────────────────────┬─┐
│ Line 1               │▲│
│ Line 2               │█│  ← Thumb
│ Line 3               │█│
│ Line 4               │█│
│ Line 5               │█│
│ Line 6               │█│
│ ...                  │█│
│ Line 20              │█│
│ Line 21              │█│
│ Line 22              │█│
│ Line 23              │█│
│ Line 24              │▼│
└──────────────────────┴─┘
```

The terminal viewport itself IS the content area - there's no separate scrollbar.
The scrollbar is drawn by the application OR terminal provides native scroll indicators.

Most terminals in alternate buffer do NOT show scrollbar indicators.
The application can:
1. Draw its own indicator on the right edge
2. Accept that no indicator is shown (terminal handles scroll via wheel)

---

## Terminal Compatibility

### Testing Terminals

| Terminal | smcup/rmcup | Mouse Support | Notes |
|----------|-------------|---------------|-------|
| xterm | Yes | Yes | Reference implementation |
| gnome-terminal | Yes | Yes | Uses VTE |
| konsole | Yes | Yes | KDE terminal |
| iTerm2 (macOS) | Yes | Yes | Popular Mac terminal |
| Windows Terminal | Yes | Yes | Modern Windows |
| cmd.exe | No | No | Legacy Windows |
| PowerShell | Yes* | Limited | Depends on version |
| VS Code Terminal | Yes | Yes | Chromium-based |

### Fallback Strategy

If terminal doesn't support alternate buffer:

```java
public void enterAlternateBuffer() {
    if (!supportsAlternateBuffer) {
        // Fallback: Clear screen and use full terminal
        output.write("\u001b[2J");
        output.write("\u001b[H");
        output.flush();
        // Note: Scrollback will be lost on exit
    } else {
        // Normal alternate buffer entry
        output.write("\u001b[?1049h");
        output.flush();
    }
}
```

---

## JLine3 Specifics

### Using JLine3 TerminalBuilder

```java
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;

Terminal terminal = TerminalBuilder.builder()
    .jna(true)  // Try JNA first for native access
    .system(true)  // Use system terminal
    .build();

// Enter alternate buffer
terminal.enterRawMode();
terminal.puts(InfoCmp.Capability.enter_ca_mode);
terminal.flush();

// ... application runs ...

// Exit alternate buffer
terminal.puts(InfoCmp.Capability.exit_ca_mode);
terminal.flush();
terminal.close();
```

### Handling Terminal Resize

```java
terminal.handle(Terminal.Signal.WINCH, signal -> {
    // Terminal size changed
    Size newSize = terminal.getSize();
    // Re-render with new dimensions
});
```

### Proper Shutdown

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    // Ensure terminal is restored
    if (inAlternateBuffer) {
        terminal.puts(InfoCmp.Capability.exit_ca_mode);
        terminal.flush();
    }
    terminal.close();
}));
```

---

## Error Handling

### Common Issues

1. **Buffer switch fails silently**
   - Check output is flushed
   - Verify escape sequence format (no extra characters)

2. **Cursor disappears**
   - Re-enable cursor on enter: `ESC[?25h`
   - Some terminals clear cursor on buffer switch

3. **Screen corruption on exit**
   - Ensure `rmcup` is sent
   - Check no partial writes
   - Flush before and after switch

4. **Content not visible in alternate buffer**
   - Clear screen after entering: `ESC[2J`
   - Move cursor home: `ESC[H`
   - Verify rendering coordinates (0,0 is top-left in alt buffer too)

### Debugging Tips

```java
// Log escape sequences sent
if (LOG_LEVEL.isEnabled()) {
    LOG.debug("Entering alternate buffer: ESC[?1049h");
}
output.write("\u001b[?1049h");
output.flush();

// Verify with echo command
// In shell: $ echo -e '\e[?1049h alternate buffer text \e[?1049l back to main'
```

---

## Related Escape Sequences

### Cursor Control

| Sequence | Description |
|----------|-------------|
| `ESC[?25l` | Hide cursor |
| `ESC[?25h` | Show cursor |
| `ESC[{row};{col}H` | Move cursor to position |
| `ESC[s` | Save cursor position |
| `ESC[u` | Restore cursor position |

### Screen Control

| Sequence | Description |
|----------|-------------|
| `ESC[2J` | Clear entire screen |
| `ESC[K` | Clear line from cursor |
| `ESC[1J` | Clear screen above cursor |

### Scrolling

| Sequence | Description |
|----------|-------------|
| `ESC[r` | Reset scroll region (full screen) |
| `ESC[{top};{bottom}r` | Set scroll region |

---

## Document History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-04-20 | Initial deep dive document |

---

## See Also

- `SCROLLPANEL_ARCHITECTURE.md` - TerminalScrollPanel component reference
- `SCROLLPANEL_DEVELOPMENT_PLAN.md` - Implementation roadmap for Phase 5
- XTerm Control Sequences: https://invisible-island.net/xterm/ctlseqs/ctlseqs.html
- ANSI Escape Codes: https://en.wikipedia.org/wiki/ANSI_escape_code
