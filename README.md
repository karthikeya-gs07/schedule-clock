# Schedule Clock

A Wear OS watch face that shows the time **and the shape of your day**: calendar meetings are drawn as arcs on the dial at the hours they occupy, with the next meeting's details under the clock.

**Status:** design prototype. No watch code yet.

## Files

| Path | What it is |
|---|---|
| [`PRD.md`](PRD.md) | Product requirements: concept, platform (Wear OS / Watch Face Format), open questions, risks, tooling, changelog |
| [`RULES.md`](RULES.md) | **Source of truth for behavior and design.** What the face does in every situation, and why (rule IDs W1, R4, C6, …) |
| [`prototype/index.html`](prototype/index.html) | Interactive HTML prototype implementing RULES.md: time slider, sample days, ambient mode, slow motion, fast-forward, live complication payload |
| `index.html` | Redirects the GitHub Pages site to the prototype |

## Live preview

**https://karthikeya-gs07.github.io/schedule-clock/**

Published with GitHub Pages from the root of `main`. Every push to `main` updates it, usually within a minute or two. The root `index.html` redirects to `prototype/`, and `.nojekyll` serves files as-is.

## Run locally

```bash
python3 -m http.server 8765 --directory prototype
```

Then open http://localhost:8765.

## Working on it from another device

Open this repository in Claude Code on the web (claude.ai/code) from your phone or iPad, make changes in a session, and push to `main`. The Pages preview updates automatically. Keep `RULES.md` and `PRD.md` updated with every design change.
