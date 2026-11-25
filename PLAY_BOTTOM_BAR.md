# Play Store bottom bar: 52.9.21-24 (85292100)

## Confirmed from the device and dex

- Package `com.android.vending`, activity `.AssetBrowserActivity`. A single full-screen `ComposeView` hosts the page **and** the bottom bar. Do not hide, scale, reparent, or mirror that view. Its five bottom AndroidView children supply icons, not labels.
- `Lamwa.a` lists the tabs in order. `Latis.eY` iterates the list and compares the loop index directly with `Lamwa.b`. Use that model as the selection source, rather than the selected flag of an embedded icon. Both `Latis.ff` and `Latis.fg` receive the model as argument zero.
- Legacy: `ff → hq → fvt.b → fvo → fvt.a`. Material: `fg → bmrx.cT → fsv.a → fsw.a`; the examined `ffm` implementation also calls `fvt.b`, via `fsx`. `fg` additionally adds a rectangular fill with `bcn.c` ahead of the surface.
- `fvt.a` builds shadow, border, background, and clipping as separate modifiers. `bcn.b` adds a fill modifier; its `bcq.gR` implementation draws the fill before `jaj.H()` draws the content. **Do not** hook `bcn` or `fvt` unconditionally: they are shared with other Compose surfaces. **Do not** drop `eX` or `eV`: they render native tab content, and `eX` includes icon animation paths.
- The accessibility capture shows a row of five virtual tabs on the tested device. The selected virtual tab is focusable but not clickable; the other four are clickable. The icons alone are not valid click targets. Drag release dispatches the same callback as the native tab item (`Lamwa.f.a.le(index)`); `Lamwa.b` confirms the change.

## Implementation and device checks

- Exact-build gate; tag bottom-bar background modifiers and their draw nodes using identity. Check the actual Compose host and five live AndroidView slots before replacing the fill. The replacement invokes the native draw-content callback once and leaves foreground, shape clipping and navigation untouched.
- The Play-specific renderer uses translucent neutral material, not live page refraction: the page shares the same Compose owner, and capturing that owner would feed foreground and the glass back into the rendering. Only the capsule background has its own springs.
- A horizontal drag originating on the capsule cancels the native pointer stream once. Ordinary taps and vertical gestures remain native. The capsule waits for the native model to acknowledge the click before abandoning the optimistic selection.
- Device checks pending: all five tabs; long labels; unchanged foreground colors while dragging; press enlargement and release spring; native page transition; no rebound to the previous tab; no glass on routes without the native bar.
