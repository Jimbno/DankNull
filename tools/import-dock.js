#!/usr/bin/env node
/*
 * Import the Blockbench dock export into the mod's resources.
 *
 *   node tools/import-dock.js [source-dir]
 *
 * Defaults to the Blockbench project directory. Re-run after every export.
 *
 * Unlike the /dank/null item (see import-obj.sh), the dock is a BLOCK, so it does not go through Forge's OBJ
 * loader. It is emitted as a vanilla-format JSON block model instead, which GTNHLib bakes into the chunk mesh:
 * that keeps lighting, ambient occlusion and culling correct and costs nothing per frame, where an OBJ would have
 * to be drawn from a TESR in immediate mode every tick.
 *
 * That is only possible because every visible dock element is an axis-aligned box. This script verifies that
 * rather than assuming it, and refuses to write a model it cannot represent.
 *
 * Reading the *baked* OBJ rather than the .bbmodel is deliberate: Blockbench stores element coordinates in a local
 * space that group transforms are applied to on export, so the .bbmodel's raw from/to can sit far outside the
 * block while the exported geometry is correctly placed.
 *
 * The `dankerino` group (a copy of the /dank/null, hidden in the editor) is skipped - the docked item is drawn by
 * TESRDankNullDock from the real stack, so baking a second copy into the block would double-draw it and would not
 * reflect the actual tier.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2] || 'C:/Users/Timbo/OneDrive/Documents/Blockbench/DAnk';
const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'src/main/resources/assets/danknull');

const OBJ = path.join(SRC, 'DankDock.obj');
const TEX = path.join(SRC, 'texture.png');

/** The group holding the dock body. Everything else in the export is reference geometry. */
const BODY_GROUP = 'cube';
/** Texture the emitted model binds; must match BlockDankNullDock.setBlockTextureName for particles. */
const TEXTURE_REF = 'danknull:blocks/dock/base';

function parseObj(text) {
    const V = [], VT = [], VN = [], objs = [];
    let cur = null;
    for (const line of text.split(/\r?\n/)) {
        const p = line.trim().split(/\s+/);
        switch (p[0]) {
            case 'v': V.push([+p[1], +p[2], +p[3]]); break;
            case 'vt': VT.push([+p[1], +p[2]]); break;
            case 'vn': VN.push([+p[1], +p[2], +p[3]]); break;
            case 'o': cur = { name: line.slice(2).trim(), faces: [] }; objs.push(cur); break;
            case 'f':
                if (cur) {
                    cur.faces.push(p.slice(1).map(s => {
                        const a = s.split('/');
                        return { v: +a[0] - 1, t: a[1] ? +a[1] - 1 : -1, n: a[2] ? +a[2] - 1 : -1 };
                    }));
                }
                break;
        }
    }
    return { V, VT, VN, objs };
}

/** Vanilla face name for a normal. Boxes only, so the dominant axis is the face. */
function faceOf(n) {
    const [x, y, z] = n;
    const ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
    if (ay >= ax && ay >= az) return y > 0 ? 'up' : 'down';
    if (az >= ax) return z > 0 ? 'south' : 'north';
    return x > 0 ? 'east' : 'west';
}

/** OBJ space is -0.5..0.5 across X/Z and 0..1 up; vanilla models are 0..16 from the block corner. */
const toModelX = v => round((v + 0.5) * 16);
const toModelY = v => round(v * 16);
const round = v => Math.round(v * 1000) / 1000;

function main() {
    for (const f of [OBJ, TEX]) {
        if (!fs.existsSync(f)) {
            console.error(`missing ${f}`);
            process.exit(1);
        }
    }
    const { V, VT, VN, objs } = parseObj(fs.readFileSync(OBJ, 'utf8'));
    const bodies = objs.filter(o => o.name === BODY_GROUP);
    const skipped = objs.filter(o => o.name !== BODY_GROUP).map(o => o.name);
    if (!bodies.length) {
        console.error(`no "${BODY_GROUP}" group in ${OBJ}`);
        process.exit(1);
    }

    const elements = [];
    for (const [i, o] of bodies.entries()) {
        const verts = [...new Set(o.faces.flat().map(f => f.v))].map(k => V[k]);
        if (verts.length !== 8 || o.faces.length !== 6) {
            console.error(`element ${i} is not a box (${verts.length} verts, ${o.faces.length} faces) - `
                + `a vanilla JSON model cannot represent it. Keep dock geometry to unrotated boxes.`);
            process.exit(1);
        }
        const min = [0, 1, 2].map(a => Math.min(...verts.map(v => v[a])));
        const max = [0, 1, 2].map(a => Math.max(...verts.map(v => v[a])));

        const faces = {};
        for (const f of o.faces) {
            const dir = faceOf(VN[f[0].n]);
            const us = f.map(x => VT[x.t][0]);
            const vs = f.map(x => VT[x.t][1]);
            // OBJ puts V=0 at the bottom of the texture, vanilla models put it at the top.
            faces[dir] = {
                texture: '#0',
                uv: [round(Math.min(...us) * 16), round((1 - Math.max(...vs)) * 16),
                    round(Math.max(...us) * 16), round((1 - Math.min(...vs)) * 16)],
            };
        }
        elements.push({
            name: `dock_${i}`,
            from: [toModelX(min[0]), toModelY(min[1]), toModelX(min[2])],
            to: [toModelX(max[0]), toModelY(max[1]), toModelX(max[2])],
            faces,
        });
    }

    const model = {
        __comment: 'GENERATED by tools/import-dock.js from the Blockbench DankDock export - do not hand-edit.',
        textures: { particle: TEXTURE_REF, 0: TEXTURE_REF },
        elements,
    };
    const out = path.join(ASSETS, 'models/block/danknull_dock.json');
    fs.writeFileSync(out, JSON.stringify(model, null, 2) + '\n');
    fs.copyFileSync(TEX, path.join(ASSETS, 'textures/blocks/dock/base.png'));

    const hi = elements.reduce((m, e) => Math.max(m, e.to[1]), 0);
    console.log(`wrote ${elements.length} boxes -> ${path.relative(ROOT, out)}`);
    if (skipped.length) console.log(`skipped reference groups: ${[...new Set(skipped)].join(', ')}`);
    for (const e of elements) console.log(`  ${e.name}: ${JSON.stringify(e.from)} -> ${JSON.stringify(e.to)}`);
    console.log(`model height is ${hi}/16 - BlockDankNullDock's bounding boxes must match this.`);
}

main();
