# Benchmark Project Card Overlap Design

## Problem

The featured project layout intentionally overlaps `.visual` and `.content` by two grid columns. The terminal mock currently fills the visual card, so exposed content elements such as the status badge, title, tags, and links render directly over terminal text.

## Approved design

Keep the alternating featured-project composition and its opaque description overlay. Reserve the overlap side of each visual card as a clean safety zone by limiting `.mock-head` and `.mock-body` to 65% width. Left-side visual cards keep mock content aligned left; right-side visual cards align it right.

## Acceptance criteria

- All four project cards retain their alternating composition.
- The mock terminal content does not intersect the exposed badge, title, tags, or links.
- The existing description overlay, typography, colors, content, and interactions remain unchanged.
- The collision check passes at 1440×900, 1024×768, 768×1024, and 390×844.
