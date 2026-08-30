#!/usr/bin/env node
/*
 * Import the Blockbench dock export into the mod's resources.
 *
 *   node tools/import-dock.js [source-dir]
 *
 * Defaults to the Blockbench project directory. Re-run after every export.
 *
 * Same job as import-obj.sh does for the /dank/null item, and mostly the same fixes, because Forge 1.7.10's OBJ
 * loader (net.minecraftforge.client.model.obj.WavefrontObject) is stricter than Blockbench's exporter:
 *
 *  1. Group names. The parser's pattern only allows [\w\d.]+ and throws ModelFormatException on a name
 *     containing a space.
 *  2. Number format. Every v/vn/vt component must be a plain decimal - scientific notation ("2.22e-16") matches
 *     none of the parser's patterns, and a rejected line aborts the whole model.
 *  3. UV scale. Blockbench normalises UVs against the *project* resolution, not the texture, so faces need
 *     rescaling by (resolution / texture width). For the dock these are currently both 64, i.e. a no-op, but it
 *     is computed rather than assumed so a texture resize does not silently skew every UV.
 *  4. Degenerate faces. Zero-area quads only produce z-fighting, so they are dropped.
 *  5. One drawing mode per object. The loader fixes a group's mode from its FIRST face and then rejects any face
 *     with a different vertex count - "Invalid number of points for face (expected 4, found 3)" - which aborts
 *     the model and leaves the block invisible. Blockbench readily puts a mesh element's triangles in the same
 *     group as a box element's quads, so faces are bucketed by (group, vertex count) and each bucket becomes its
 *     own object. Splitting rather than triangulating keeps the quads whole: DankNullDockRenderer emits each
 *     triangle as a degenerate quad, so triangulating would nearly double the chunk mesh.
 *
 * The dock body is entirely on DankDock.png - Dank.png appears in the export only for the `dankerino` reference
 * group, which is skipped - so a single block sprite covers everything that ships. It is installed as a block
 * sprite (textures/blocks/dock/base.png) because the body is drawn into the chunk mesh from the terrain atlas
 * rather than with its own bound texture.
 *
 * `dankerino` is a copy of the /dank/null used as placement reference in Blockbench. The docked item is drawn
 * from the real stack by TESRDankNullDock, so shipping it would double-draw it and would never reflect the actual
 * tier. Its position IS used - see TESRDankNullDock's placement constants.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2] || 'C:/Users/Timbo/OneDrive/Documents/Blockbench/DAnk';
const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/danknull');

const OBJ = path.join(SRC, 'DankDock.obj');
const BB = path.join(SRC, 'DankDock.bbmodel');
const TEX = path.join(SRC, 'DankDock.png');

/** The group making up the dock body; everything else in the export is reference geometry. */
const BODY_GROUP = 'cube';

const LINES = /\r?\n/;
const WS = /\s+/;

const f6 = v => v.toFixed(6);
const sanitize = s => s.replace(/[^A-Za-z0-9_.]/g, '_');

/** PNG width from the IHDR chunk. */
function pngWidth(file) {
    return fs.readFileSync(file)
        .readUInt32BE(16);
}

function main() {
    for (const f of [OBJ, BB, TEX]) {
        if (!fs.existsSync(f)) {
            console.error(`missing ${f}`);
            process.exit(1);
        }
    }
    const res = JSON.parse(fs.readFileSync(BB, 'utf8')).resolution.width;
    const tex = pngWidth(TEX);
    const k = res / tex;
    console.log(`project resolution ${res}, texture ${tex}px -> UV factor ${k}`);

    const source = fs.readFileSync(OBJ, 'utf8')
        .split(LINES);

    // Positions are collected first so a face can be tested for zero area before it is emitted.
    const positions = [];
    for (const line of source) {
        const p = line.trim()
            .split(WS);
        if (p[0] === 'v') {
            positions.push(`${+p[1]},${+p[2]},${+p[3]}`);
        }
    }

    const verts = [];
    const buckets = new Map();
    const skipped = new Set();
    let group = null, kept = 0, dropped = 0;

    for (const line of source) {
        const p = line.trim()
            .split(WS);
        if (p[0] === 'o') {
            group = line.slice(2)
                .trim();
            if (group !== BODY_GROUP) {
                skipped.add(group);
            }
        } else if (p[0] === 'v' || p[0] === 'vn') {
            // Vertex tables are global and addressed by index, so they are all emitted regardless of group.
            verts.push(`${p[0]} ${f6(+p[1])} ${f6(+p[2])} ${f6(+p[3])}`);
        } else if (p[0] === 'vt') {
            // Blockbench normalises V with the origin at the bottom; rescale about that same origin.
            verts.push(`vt ${f6(+p[1] * k)} ${f6(1 - (1 - +p[2]) * k)}`);
        } else if (p[0] === 'f' && group === BODY_GROUP) {
            const points = p.slice(1);
            const distinct = new Set(points.map(t => positions[+t.split('/')[0] - 1]));
            if (distinct.size < 3) {
                dropped++;
                continue;
            }
            const key = `${sanitize(group)}_${points.length}`;
            if (!buckets.has(key)) {
                buckets.set(key, []);
            }
            buckets.get(key)
                .push(`f ${points.join(' ')}`);
            kept++;
        }
    }

    // Every v/vt/vn goes up front. Indices are absolute and counted per type, so emitting each type in its
    // original order keeps them valid however the faces below are regrouped.
    const out = verts.slice();
    for (const [name, faces] of buckets) {
        out.push(`o ${name}`);
        out.push(...faces);
    }

    const dest = path.join(ASSETS, 'models/danknull_dock.obj');
    fs.writeFileSync(dest, out.join('\n') + '\n');
    // Must stay in step with BlockDankNullDock.setBlockTextureName, which also drives break particles.
    fs.copyFileSync(TEX, path.join(ASSETS, 'textures/blocks/dock/base.png'));

    console.log(`wrote ${kept} faces -> ${path.relative(ROOT, dest)} (dropped ${dropped} zero-area)`);
    if (skipped.size) {
        console.log(`skipped reference groups: ${[...skipped].join(', ')}`);
    }
    for (const [name, faces] of buckets) {
        console.log(`  ${name}: ${faces.length} faces`);
    }

    let uMin = Infinity, uMax = -Infinity, vMin = Infinity, vMax = -Infinity;
    for (const line of out) {
        if (line.startsWith('vt ')) {
            const p = line.split(WS);
            uMin = Math.min(uMin, +p[1]);
            uMax = Math.max(uMax, +p[1]);
            vMin = Math.min(vMin, +p[2]);
            vMax = Math.max(vMax, +p[2]);
        }
    }
    console.log(`UV range U ${uMin.toFixed(3)}..${uMax.toFixed(3)}  V ${vMin.toFixed(3)}..${vMax.toFixed(3)}`);
}

main();
