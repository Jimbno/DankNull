#!/usr/bin/env node
/*
 * Import the Blockbench dock export into the mod's resources.
 *
 *   node tools/import-dock.js [source-dir]
 *
 * Defaults to the Blockbench project directory. Re-run after every export.
 *
 * Same job as import-obj.sh does for the /dank/null item, and the same fixes, because Forge 1.7.10's OBJ loader
 * (net.minecraftforge.client.model.obj.WavefrontObject) is stricter than Blockbench's exporter:
 *
 *  1. Group names. The parser's pattern only allows [\w\d.]+ and THROWS ModelFormatException on a name containing
 *     a space.
 *  2. Number format. Every v/vn/vt component needs a plain decimal. Integers ("2") and scientific notation
 *     ("2.22e-16") are both rejected - and an invalid vt is SILENTLY DROPPED, which shifts every later UV index
 *     and garbles the texture with no error at all.
 *  3. UV scale. Blockbench normalises UVs against the *project* resolution, not the texture, so faces need
 *     rescaling by (resolution / texture width). For the dock these are currently both 64, i.e. a no-op, but it
 *     is computed rather than assumed so a texture resize does not silently skew every UV.
 *  4. Degenerate faces. Zero-area quads only produce z-fighting, so they are dropped.
 *
 * The dock body is entirely on DankDock.png - Dank.png appears in the export only for the `dankerino`
 * reference group, which is skipped - so a single block sprite still covers it.
 *
 * The texture is installed as a block sprite (textures/blocks/dock/base.png) because the body is drawn into the
 * chunk mesh from the terrain atlas, not with its own bound texture.
 *
 * The `dankerino` group is skipped. It is a copy of the /dank/null used as placement reference in Blockbench, but
 * the docked item is drawn from the real stack by TESRDankNullDock, so shipping it would double-draw it and would
 * never reflect the actual tier. Its position IS used - see TESRDankNullDock's placement constants.
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

/** Groups that make up the dock body; everything else in the export is reference geometry. */
const BODY_GROUP = 'cube';

const f6 = v => v.toFixed(6);
const sanitize = s => s.replace(/[^A-Za-z0-9_.]/g, '_');

/** PNG width from the IHDR chunk. */
function pngWidth(file) {
    return fs.readFileSync(file).readUInt32BE(16);
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

    const V = [], VT = [], out = [];
    let group = null, kept = 0, dropped = 0, skippedGroups = new Set();

    for (const line of fs.readFileSync(OBJ, 'utf8').split(/\r?\n/)) {
        const p = line.trim().split(/\s+/);
        switch (p[0]) {
            case 'v':
                V.push([+p[1], +p[2], +p[3]]);
                break;
            case 'vt':
                VT.push([+p[1], +p[2]]);
                break;
        }
    }

    // Second pass emits, so faces can be tested against the vertex table above.
    let emitting = false;
    for (const line of fs.readFileSync(OBJ, 'utf8').split(/\r?\n/)) {
        const p = line.trim().split(/\s+/);
        if (p[0] === 'o') {
            group = line.slice(2).trim();
            emitting = group === BODY_GROUP;
            if (!emitting) {
                skippedGroups.add(group);
                continue;
            }
            out.push(`o ${sanitize(group)}`);
        } else if (p[0] === 'v' || p[0] === 'vn') {
            // Vertex tables are global and shared by index, so they must all be emitted regardless of group.
            out.push(`${p[0]} ${f6(+p[1])} ${f6(+p[2])} ${f6(+p[3])}`);
        } else if (p[0] === 'vt') {
            // Blockbench normalises V with the origin at the bottom; rescale about that same origin.
            out.push(`vt ${f6(+p[1] * k)} ${f6(1 - (1 - +p[2]) * k)}`);
        } else if (p[0] === 'f') {
            if (!emitting) continue;
            const verts = p.slice(1).map(s => V[+s.split('/')[0] - 1]);
            const uniq = new Set(verts.map(v => v.join(',')));
            if (uniq.size < 3) {
                dropped++;
                continue;
            }
            kept++;
            out.push(line.trim());
        } else if (p[0] === 'mtllib' || p[0] === 'usemtl') {
            // Dropped: the TESR binds the texture itself, so no material library is needed or wanted.
            continue;
        }
    }

    const dest = path.join(ASSETS, 'models/danknull_dock.obj');
    fs.writeFileSync(dest, out.join('\n') + '\n');
    // Block-atlas path: the body is drawn into the chunk mesh, so the texture has to be a stitched block sprite.
    // Must stay in step with BlockDankNullDock.setBlockTextureName, which also drives break particles.
    fs.copyFileSync(TEX, path.join(ASSETS, 'textures/blocks/dock/base.png'));

    console.log(`wrote ${kept} faces -> ${path.relative(ROOT, dest)} (dropped ${dropped} zero-area)`);
    if (skippedGroups.size) console.log(`skipped reference groups: ${[...skippedGroups].join(', ')}`);

    const us = VT.map(t => t[0] * k), vs = VT.map(t => 1 - (1 - t[1]) * k);
    console.log(`UV range U ${Math.min(...us).toFixed(3)}..${Math.max(...us).toFixed(3)}  `
        + `V ${Math.min(...vs).toFixed(3)}..${Math.max(...vs).toFixed(3)}`);
}

main();
