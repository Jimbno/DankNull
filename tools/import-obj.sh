#!/usr/bin/env bash
# Import a Blockbench OBJ export into the mod's resources.
#
#   tools/import-obj.sh [source-dir]
#
# Defaults to the Blockbench project directory. Re-run after every export.
#
# Forge 1.7.10's OBJ loader (net.minecraftforge.client.model.obj.WavefrontObject) is stricter than Blockbench's
# exporter, and Blockbench emits geometry that needs cleaning, so this fixes up four things:
#
#  1. Group names. The parser's pattern only allows [\w\d.]+ and THROWS ModelFormatException on a name containing
#     a space, so "SIDE CUT" becomes "SIDE_CUT".
#  2. Number format. Every v/vn/vt component needs a plain decimal. Integers ("2") and scientific notation
#     ("2.22e-16") are both rejected - and an invalid vt is SILENTLY DROPPED, which shifts every later UV index and
#     garbles the texture with no error at all.
#  3. UV scale. Blockbench normalises UVs against the *project* resolution, not the texture. Faces on the main
#     sheet therefore need rescaling by (resolution / texture width). Faces on the glass mesh do not - Blockbench
#     already normalised those against glass.png - so they are passed through untouched.
#  4. Degenerate faces. A zero-thickness mesh exports each pane as a flattened box: one good face, a coincident
#     back face whose UVs land outside the sheet, and four zero-area edge quads. All of that only produces
#     stretching and z-fighting, so the zero-area quads and the out-of-range glass backfaces are dropped.
#     (Giving the panes real thickness in Blockbench would avoid this at source.)
set -euo pipefail

SRC="${1:-C:/Users/Timbo/OneDrive/Documents/Blockbench/DAnk}"
DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$DEST/src/main/resources/assets/danknull"

png_width() { od -An -tu4 -j16 -N4 --endian=big "$1" | tr -d ' '; }

RES=$(grep -oE '"resolution":\{"width":[0-9]+' "$SRC/DANK.bbmodel" | grep -oE '[0-9]+$')
TEX=$(png_width "$SRC/Dank.png")
echo "project resolution ${RES}, main texture ${TEX}px -> UV factor ${RES}/${TEX}"

awk -v f="$RES" -v t="$TEX" '
BEGIN { k = f / t }
NR==FNR { if ($1=="v")  { nv++; px[nv]=sprintf("%.4f,%.4f,%.4f",$2+0,$3+0,$4+0) }
          if ($1=="vt") { nt++; tu[nt]=$2+0; tv[nt]=$3+0 }
          next }
/^o /  { g=substr($0,3); gsub(/[^A-Za-z0-9_.]/,"_",g); print "o " g; next }
/^vt / { if (g=="glass") printf "vt %.6f %.6f\n", $2, $3
         else            printf "vt %.6f %.6f\n", $2*k, 1-(1-$3)*k
         next }
/^(v|vn) / { printf "%s", $1; for (i=2;i<=4;i++) printf " %.6f", $i+0; printf "\n"; next }
/^f / { delete pos; c=0; bad=0
        for (i=2;i<=NF;i++) { split($i,p,"/")
          key=px[p[1]+0]; if (!(key in pos)) { pos[key]=1; c++ }
          q=p[2]+0; if (q>0 && (tu[q]<-0.001||tu[q]>1.001||tv[q]<-0.001||tv[q]>1.001)) bad=1 }
        if (c < 3)                { drop1++; next }
        if (g=="glass" && bad)    { drop2++; next }
        print; next }
{ print }
END { printf "dropped %d zero-area faces, %d out-of-range glass backfaces\n", drop1, drop2 > "/dev/stderr" }
' "$SRC/DANK.obj" "$SRC/DANK.obj" > "$ASSETS/models/dank_null.obj"

cp "$SRC/Dank.png"  "$ASSETS/textures/models/dank_null.png"
cp "$SRC/glass.png" "$ASSETS/textures/models/dank_null_glass.png"

echo "per-group UV ranges (all should sit within 0..1):"
awk '/^o /{g=substr($0,3)} /^vt /{n++;U[n]=$2;V[n]=$3}
/^f /{c[g]++; for(i=2;i<=NF;i++){split($i,p,"/"); q=p[2]+0; if(q>0){u=U[q];v=V[q]
  if(!(g in s)||u<a[g])a[g]=u; if(!(g in s)||u>b[g])b[g]=u
  if(!(g in s)||v<x[g])x[g]=v; if(!(g in s)||v>y[g])y[g]=v; s[g]=1}}}
END{for(g in s) printf "  %-12s faces=%-3d U %.2f..%.2f  V %.2f..%.2f\n", g, c[g], a[g],b[g], x[g],y[g]}' \
  "$ASSETS/models/dank_null.obj" | sort
