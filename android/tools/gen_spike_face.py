"""Generates the Phase 1 spike watchface.xml and a preview PNG."""
import math, struct, sys, zlib
from pathlib import Path

res = Path(sys.argv[1])
PKG = "io.github.karthikeyags07.scheduleclock"
CX = CY = 225
R = 219          # ring radius: band 213–225, flush with the edge (RULES R1)
HOT = 72         # hotspot box size

def hotspot(h):
    a = math.radians(h * 30 + 15)                      # middle of the hour
    x = round(CX + (R - 12) * math.sin(a) - HOT / 2)
    y = round(CY - (R - 12) * math.cos(a) - HOT / 2)
    return f'''    <PartDraw name="hour_{h}" x="{x}" y="{y}" width="{HOT}" height="{HOT}">
      <Rectangle x="0" y="0" width="{HOT}" height="{HOT}"><Fill color="#00000000"/></Rectangle>
      <Launch target="app://{PKG}/select?hour={h}"/>
    </PartDraw>'''

selected = "[COMPLICATION.RANGED_VALUE_VALUE]"
anim = '<Animation duration="0.52" interpolation="EASE_IN_OUT"/>'
xml = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Phase 1 spike face. Comments name the PRD §6.3 risk each block tests. -->
<WatchFace width="450" height="450">
  <Scene backgroundColor="#ff000000">

    <!-- Ring track (RULES R1) -->
    <PartDraw x="0" y="0" width="450" height="450">
      <Arc centerX="{CX}" centerY="{CY}" width="{2*R}" height="{2*R}" startAngle="0" endAngle="360">
        <Stroke color="#ff1c1c1f" thickness="12"/>
      </Arc>
    </PartDraw>

    <!-- Spike 2: the whole ring drawn by the data source as one SMALL_IMAGE (PHOTO_IMAGE is rejected for our provider) (fixes the 7-segment limit,
         spike 1 risk 9; also carries lane, clash and private layers). Risk 10: fixed to our data source. -->
    <ComplicationSlot slotId="0" name="ring" displayName="@string/slot_ring" x="0" y="0" width="450" height="450"
        supportedTypes="SMALL_IMAGE EMPTY" isCustomizable="FALSE">
      <DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY"
          primaryProvider="{PKG}/{PKG}.RingImageDataSourceService" primaryProviderType="SMALL_IMAGE"/>
      <BoundingOval x="0" y="0" width="450" height="450"/>
      <Complication type="SMALL_IMAGE">
        <PartImage x="0" y="0" width="450" height="450">
          <Image resource="[COMPLICATION.SMALL_IMAGE]"/>
        </PartImage>
      </Complication>
    </ComplicationSlot>

    <!-- Risks 2 + 3: VALUE = selected (0/1). The clock and details live inside this slot so their
         Transforms can read it; Animation should ease every change. -->
    <ComplicationSlot slotId="1" name="status" displayName="@string/slot_status" x="0" y="0" width="450" height="450"
        supportedTypes="RANGED_VALUE EMPTY" isCustomizable="FALSE">
      <DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY"
          primaryProvider="{PKG}/{PKG}.StatusDataSourceService" primaryProviderType="RANGED_VALUE"/>
      <BoundingOval x="60" y="60" width="330" height="330"/>
      <Complication type="RANGED_VALUE">
        <Group name="clock" x="0" y="110" width="450" height="110" pivotX="0.5" pivotY="0.5">
          <Transform target="scaleX" value="1 - 0.45 * {selected}">{anim}</Transform>
          <Transform target="scaleY" value="1 - 0.45 * {selected}">{anim}</Transform>
          <DigitalClock x="0" y="0" width="450" height="110">
            <TimeText x="0" y="0" width="450" height="110" format="h:mm" hourFormat="SYNC_TO_DEVICE" align="CENTER">
              <Font family="SYNC_TO_DEVICE" size="88" color="#fff2f2f2"/>
            </TimeText>
          </DigitalClock>
        </Group>
        <Group name="details" x="0" y="250" width="450" height="56" pivotX="0.5" pivotY="0.5">
          <Transform target="scaleX" value="1 + 0.15 * {selected}">{anim}</Transform>
          <Transform target="scaleY" value="1 + 0.15 * {selected}">{anim}</Transform>
          <PartText x="0" y="0" width="450" height="56">
            <Text align="CENTER" ellipsis="TRUE">
              <Font family="SYNC_TO_DEVICE" size="30" color="#fff2f2f2">
                <Template><![CDATA[%s]]><Parameter expression="[COMPLICATION.TEXT]"/></Template>
              </Font>
            </Text>
          </PartText>
        </Group>
        <!-- Risk 2 marker: an amber bar that should fade in (not snap) when something is selected -->
        <PartDraw x="185" y="320" width="80" height="6" alpha="0">
          <Transform target="alpha" value="255 * {selected}">{anim}</Transform>
          <Rectangle x="0" y="0" width="80" height="6"><Fill color="#ffffb547"/></Rectangle>
        </PartDraw>
      </Complication>
    </ComplicationSlot>

    <!-- Risk 3 control: a clock outside any complication (can't read the selected value) -->
    <DigitalClock x="0" y="360" width="450" height="30">
      <TimeText x="0" y="0" width="450" height="30" format="h:mm:ss" hourFormat="SYNC_TO_DEVICE" align="CENTER">
        <Font family="SYNC_TO_DEVICE" size="22" color="#ff8e8e93"/>
      </TimeText>
    </DigitalClock>

    <!-- Risks 1 + 7: twelve transparent one-hour hotspots on the ring, each a Launch deep link -->
{chr(10).join(hotspot(h) for h in range(12))}
  </Scene>
</WatchFace>
'''
(res / "raw").mkdir(parents=True, exist_ok=True)
(res / "raw" / "watchface.xml").write_text(xml)

# Preview: black face with the track and two amber meeting arcs (1–2, 4–5).
W = 450
px = bytearray(b"\x00\x00\x00" * W * W)
for y in range(W):
    for x in range(W):
        d = math.hypot(x - CX, y - CY)
        if 213 <= d <= 225:
            ang = (math.degrees(math.atan2(x - CX, CY - y)) + 360) % 360
            c = (0xff, 0xb5, 0x47) if (30 <= ang <= 60 or 120 <= ang <= 150) else (0x1c, 0x1c, 0x1f)
            i = (y * W + x) * 3
            px[i:i + 3] = bytes(c)
raw = b"".join(b"\x00" + bytes(px[y * W * 3:(y + 1) * W * 3]) for y in range(W))
def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", W, W, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
(res / "drawable").mkdir(parents=True, exist_ok=True)
(res / "drawable" / "preview.png").write_bytes(png)
print("wrote", res / "raw" / "watchface.xml", "and preview.png")
