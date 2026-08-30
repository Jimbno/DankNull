#!/usr/bin/env node
/*
 * Import the Blockbench /dank/null and panel exports into the mod's resources.
 *
 *   node tools/import-obj.js [source-dir]
 *
 * Defaults to the Blockbench project directory. Re-run after every export.
 *
 * Both are the same framed-glass shape sharing the same two textures, so they get identical treatment; the only
 * difference is that a panel has no contained stack. Unlike the dock (see import-dock.js) these are drawn in
 * immediate mode with their own texture bound, so their UVs are not confined to an atlas sprite and the texture
 * may be any size.
 *
 * Forge 1.7.10's OBJ loader (net.minecraftforge.client.model.obj.WavefrontObject) is stricter than Blockbench's
 * exporter, so the export is normalised on the way in:
 *
 *  1. Group names. The parser's pattern only allows [\w\d.]+ and throws ModelFormatException on a name
 *     containing a space, so "SIDE CUT" becomes "SIDE_CUT".
 *  2. Number format. Every v/vn/vt component must be a plain decimal - scientific notation ("2.22e-16") matches
 *     none of the parser's patterns, and a rejected line aborts the whole model.
 *  3. UV scale. Blockbench normalises UVs against the *project* resolution rather than the texture, so faces on
 *     the main sheet are rescaled by (resolution / texture width). Faces on the glass mesh are not: Blockbench
 *     already normalised those against glass.png, so they pass through untouched.
 *  4. Degenerate faces. A zero-thickness pane exports as a flattened box - one good face, a coincident back face
 *     whose UVs land outside the sheet, and four zero-area edge quads. All of that only causes stretching and
 *     z-fighting, so the zero-area quads and the out-of-range glass backfaces are dropped.
 *  5. One drawing mode per object. The loader fixes a group's mode from its FIRST face and rejects any later face
 *     with a different vertex count - "Invalid number of points for face (expected 4, found 3)" - which aborts
 *     the model and leaves the item invisible. Blockbench readily mixes a mesh element's triangles with a box
 *     element's quads in one group, so any group holding both is triangulated.
 *
 * Triangulating is the right trade here, where import-dock.js splits into separate objects instead: these
 * renderers select geometry by group NAME (DankNullItemRenderer's frame/glass part lists), so a group cannot be
 * split without those names drifting. They are also drawn in immediate mode a few items at a time, so the extra
 * vertices cost nothing - where the dock's go into the chunk mesh.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2] || 'C:/Users/Timbo/OneDrive/Documents/Blockbench/DAnk';
const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/danknull');

/** The group Blockbench normalised against glass.png rather than the main sheet. */
const GLASS_GROUP = 'glass';

const LINES = /\r?\n/;
const WS = /\s+/;

const f6 = v => v.toFixed(6);
const sanitize = s => s.replace(/[^A-Za-z0-9_.]/g, '_');

/** PNG width from the IHDR chunk. */
const pngWidth = file => fs.readFileSync(file)
    .readUInt32BE(16);

function convert(objFile, bbFile, destFile, uvFactor) {
    const source = fs.readFileSync(objFile, 'utf8')
        .split(LINES);

    // Positions and scaled UVs are gathered first so a face can be tested before it is emitted.
    const positions = [], coords = [];
    let scanGroup = null;
    for (const line of source) {
        const p = line.trim()
            .split(WS);
        if (p[0] === 'o') {
            scanGroup = line.slice(2)
                .trim();
        } else if (p[0] === 'v') {
            // Rounded, not exact: a zero-thickness pane exports as a flattened box whose opposing vertices differ
            // only in the far decimals. Comparing at full precision would see those edge quads as real geometry
            // and leave them in to z-fight.
            positions.push([+p[1], +p[2], +p[3]].map(n => n.toFixed(4))
                .join(','));
        } else if (p[0] === 'vt') {
            coords.push(
                scanGroup === GLASS_GROUP ? [+p[1], +p[2]]
                    : [+p[1] * uvFactor, 1 - (1 - +p[2]) * uvFactor]);
        }
    }

    const out = [];
    const groups = new Map();
    let group = null, kept = 0, zeroArea = 0, backface = 0;

    for (const line of source) {
        const p = line.trim()
            .split(WS);
        if (p[0] === 'o') {
            group = line.slice(2)
                .trim();
            if (!groups.has(group)) {
                groups.set(group, []);
            }
        } else if (p[0] === 'v' || p[0] === 'vn') {
            out.push(`${p[0]} ${f6(+p[1])} ${f6(+p[2])} ${f6(+p[3])}`);
        } else if (p[0] === 'vt') {
            const uv = coords[out.filter(l => l.startsWith('vt ')).length];
            out.push(`vt ${f6(uv[0])} ${f6(uv[1])}`);
        } else if (p[0] === 'f' && group !== null) {
            const points = p.slice(1);
            const distinct = new Set(points.map(t => positions[+t.split('/')[0] - 1]));
            if (distinct.size < 3) {
                zeroArea++;
                continue;
            }
            if (group === GLASS_GROUP) {
                const outside = points.some(t => {
                    const q = +t.split('/')[1];
                    if (!q) {
                        return false;
                    }
                    const [u, v] = coords[q - 1];
                    return u < -0.001 || u > 1.001 || v < -0.001 || v > 1.001;
                });
                if (outside) {
                    backface++;
                    continue;
                }
            }
            groups.get(group)
                .push(points);
            kept++;
        }
    }

    // A group holding both triangles and quads is triangulated so its drawing mode is single-valued. Names are
    // preserved because the renderers select parts by name.
    let triangulated = 0;
    const body = [];
    for (const [name, faces] of groups) {
        if (!faces.length) {
            continue;
        }
        const counts = new Set(faces.map(f => f.length));
        const mixed = counts.size > 1;
        if (mixed) {
            triangulated++;
        }
        body.push(`o ${sanitize(name)}`);
        for (const face of faces) {
            if (!mixed || face.length === 3) {
                body.push(`f ${face.join(' ')}`);
                continue;
            }
            // Fan the polygon from its first vertex; every Blockbench face is convex.
            for (let i = 1; i + 1 < face.length; i++) {
                body.push(`f ${face[0]} ${face[i]} ${face[i + 1]}`);
            }
        }
    }

    fs.writeFileSync(destFile, out.concat(body)
        .join('\n') + '\n');
    console.log(
        `${path.basename(destFile)}: ${kept} faces (dropped ${zeroArea} zero-area, ${backface} glass backfaces`
            + `${triangulated ? `; triangulated ${triangulated} mixed group(s)` : ''})`);
    report(destFile);
}

/** Every group must sit within 0..1: these UVs address the model's own bound texture. */
function report(file) {
    const lines = fs.readFileSync(file, 'utf8')
        .split(LINES);
    const uv = [];
    const stats = new Map();
    let group = null;
    for (const line of lines) {
        const p = line.trim()
            .split(WS);
        if (p[0] === 'vt') {
            uv.push([+p[1], +p[2]]);
        } else if (p[0] === 'o') {
            group = line.slice(2)
                .trim();
            stats.set(group, { faces: 0, uMin: Infinity, uMax: -Infinity, vMin: Infinity, vMax: -Infinity });
        } else if (p[0] === 'f' && group !== null) {
            const s = stats.get(group);
            s.faces++;
            for (const t of p.slice(1)) {
                const q = +t.split('/')[1];
                if (!q) {
                    continue;
                }
                const [u, v] = uv[q - 1];
                s.uMin = Math.min(s.uMin, u);
                s.uMax = Math.max(s.uMax, u);
                s.vMin = Math.min(s.vMin, v);
                s.vMax = Math.max(s.vMax, v);
            }
        }
    }
    for (const [name, s] of [...stats].sort()) {
        const flag = s.uMin < -0.001 || s.uMax > 1.001 || s.vMin < -0.001 || s.vMax > 1.001 ? '  <-- OUTSIDE 0..1' : '';
        console.log(
            `  ${name.padEnd(12)} faces=${String(s.faces).padEnd(3)} `
                + `U ${s.uMin.toFixed(2)}..${s.uMax.toFixed(2)}  V ${s.vMin.toFixed(2)}..${s.vMax.toFixed(2)}${flag}`);
    }
}

function main() {
    const models = [
        { obj: 'DANK.obj', bb: 'DANK.bbmodel', dest: 'models/dank_null.obj' },
        { obj: 'DankPanel.obj', bb: 'DankPanel.bbmodel', dest: 'models/dank_null_panel.obj' },
    ];
    const mainTexture = path.join(SRC, 'Dank.png');
    const glassTexture = path.join(SRC, 'glass.png');
    for (const f of [mainTexture, glassTexture, ...models.map(m => path.join(SRC, m.obj))]) {
        if (!fs.existsSync(f)) {
            console.error(`missing ${f}`);
            process.exit(1);
        }
    }

    const tex = pngWidth(mainTexture);
    for (const model of models) {
        const res = JSON.parse(fs.readFileSync(path.join(SRC, model.bb), 'utf8')).resolution.width;
        console.log(`${model.obj}: project resolution ${res}, main texture ${tex}px -> UV factor ${res / tex}`);
        convert(path.join(SRC, model.obj), path.join(SRC, model.bb), path.join(ASSETS, model.dest), res / tex);
    }

    fs.copyFileSync(mainTexture, path.join(ASSETS, 'textures/models/dank_null.png'));
    fs.copyFileSync(glassTexture, path.join(ASSETS, 'textures/models/dank_null_glass.png'));
}

main();
