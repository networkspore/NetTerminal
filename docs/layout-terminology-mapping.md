# Layout Terminology Mapping Reference

This document provides a comprehensive mapping between the existing idiosyncratic layout terminology and standard HTML/CSS/JavaScript terminology.

## Core Layout Concepts

### Container Types
| Current Term | New Term | Notes |
|-------------|----------|-------|
| TerminalPanel | TerminalPanel | Keep (component-based naming) |
| TerminalStackPanel | TerminalStackPanel | Keep (component-based naming) |
| TerminalVStack | TerminalVStack | Keep (directional clarity) |
| TerminalHStack | TerminalHStack | Keep (directional clarity) |

## Size Preferences

| Current Term | New Term | CSS Equivalent | Notes |
|-------------|----------|----------------|-------|
| SizePreference.STATIC | SizePreference.FIXED | width/height: 100px | Fixed sizing |
| SizePreference.FILL | SizePreference.FILL | flex-grow: 1 | Takes available space |
| SizePreference.FIT_CONTENT | SizePreference.FIT_CONTENT | width/height: fit-content | Content-sized |
| SizePreference.PERCENT | SizePreference.PERCENTAGE | width: 50% | Percentage sizing |
| SizePreference.INHERIT | SizePreference.INHERIT | inherit | Inherits parent value |

## Alignment System

### Current Limited Support
- Simple alignment properties in TerminalPanel

### New Standard Alignment
#### MainAxisAlignment (Primary Axis)
| Value | Description | CSS Equivalent |
|-------|-------------|----------------|
| FLEX_START | Items aligned at start | flex-start |
| CENTER | Items centered | center |
| FLEX_END | Items aligned at end | flex-end |
| SPACE_BETWEEN | Items spaced evenly | space-between |
| SPACE_AROUND | Items with equal space around | space-around |
| SPACE_EVENLY | Items with equal space everywhere | space-evenly |

#### CrossAxisAlignment (Cross Axis)
| Value | Description | CSS Equivalent |
|-------|-------------|----------------|
| FLEX_START | Items aligned at start | flex-start |
| CENTER | Items centered | center |
| FLEX_END | Items aligned at end | flex-end |
| STRETCH | Items stretched to fill | stretch |
| BASELINE | Items aligned to baseline | baseline |

## Overflow Handling

| Current Term | New Term | CSS Equivalent | Notes |
|-------------|----------|----------------|-------|
| LayoutOverflowStrategy.CLIP | Overflow.HIDDEN | overflow: hidden | Content clipped |
| LayoutOverflowStrategy.OVERFLOW | Overflow.VISIBLE | overflow: visible | Content visible |
| (New) | Overflow.AUTO | overflow: auto | Scroll when needed |
| (New) | Overflow.SCROLL | overflow: scroll | Always scrollable |

## Axis Direction

| Current Term | New Term | Notes |
|-------------|----------|-------|
| VStack (implicit) | mainAxis: Vertical | Explicit vertical axis |
| HStack (implicit) | mainAxis: Horizontal | Explicit horizontal axis |

## Migration Path

### Tier 1: Basic Enum Renaming
1. Rename SizePreference.STATIC → FIXED
2. Rename SizePreference.PERCENT → PERCENTAGE
3. Rename LayoutOverflowStrategy.CLIP → Overflow.HIDDEN
4. Rename LayoutOverflowStrategy.OVERFLOW → Overflow.VISIBLE

### Tier 2: Alignment System Introduction
1. Add MainAxisAlignment enum
2. Add CrossAxisAlignment enum
3. Map existing alignment to new system

### Tier 3: Axis Explicit Support
1. Add mainAxis and crossAxis properties
2. Maintain backward compatibility

### Tier 4: Advanced Features
1. Complete Flexbox compatibility
2. Implement full STRETCH behavior
3. Add advanced sizing interactions