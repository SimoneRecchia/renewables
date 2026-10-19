#!/usr/bin/env python3
"""The GUI panel layouts: the vanilla dialog every machine's screen is drawn on.  No sprites, no block textures.

    python3 tools/gen_pv_textures.py        # textures/gui/<panel>.png for every entry of PANELS

PANELS drives the layouts check_gui_fits.py measures and check_generated_assets.py holds to the screens that blit
them.  Every inventory sprite belongs to gen_item_sprites.py and every block texture to gen_block_textures.py: this
file once wrote both, at an older resolution, and ran last, so it silently put the old drawings back on every
regeneration.  check_generated_assets.py fails the build if two generators claim one path.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from texlib import Canvas                                                       # noqa: E402

OUT = os.path.join('src', 'main', 'resources', 'assets', 'renewables', 'textures')


# ------------------------------------------------------------- panel layouts

# One per screen, keyed by its textures/gui name.  check_generated_assets.py holds every entry to the screen that
# draws it: width, height, bar wells and separators, and the sheet side PlantScreen blits from.
PANELS = {
    # 240 wide, the nameplate lines; the wind well under the power well, the tower line under the second rule
    'wind_turbine': dict(width=240, height=190, bar_x=12, bar_width=216, bars=(69, 97), separators=(40, 112)),
    'pv_inverter': dict(width=288, height=252, bar_x=12, bar_width=264, bars=(70, 98, 126), separators=(40, 142)),
    'pv_array': dict(width=288, height=244, bar_x=12, bar_width=264, bars=(70, 98, 126), separators=(40, 142)),
    'met_station': dict(width=288, height=208, bar_x=12, bar_width=264, bars=(), separators=(20, 176)),
    # shorter than the rest, because a combiner box is switchgear: one bar, and nothing to control
    'pv_combiner': dict(width=288, height=148, bar_x=12, bar_width=264, bars=(72,), separators=(40, 88)),
    # one bar for the plant's loading, and the three mode buttons and the setpoint slider under it
    'plant_controller': dict(width=288, height=178, bar_x=12, bar_width=264, bars=(58,), separators=(40, 78)),
}


# Side of the sheet each panel is drawn into.
PANEL_SHEET = 512


def panel(spec):
    """A vanilla-looking dialog: the raised body, the bevel, and the wells for the bars."""
    c = Canvas(PANEL_SHEET, PANEL_SHEET)
    w, h = spec['width'], spec['height']

    body = (198, 198, 198, 255)
    light = (255, 255, 255, 255)
    dark = (85, 85, 85, 255)
    well = (139, 139, 139, 255)
    wellDark = (55, 55, 55, 255)

    c.rect(0, 0, w, h, body)
    # the vanilla bevel: two light edges top and left, two dark bottom and right
    c.rect(0, 0, w, 1, light)
    c.rect(0, 0, 1, h, light)
    c.rect(1, 1, w - 1, 2, light)
    c.rect(1, 1, 2, h - 1, light)
    c.rect(0, h - 1, w, h, dark)
    c.rect(w - 1, 0, w, h, dark)
    c.rect(1, h - 2, w - 1, h - 1, dark)
    c.rect(w - 2, 1, w - 1, h - 1, dark)

    for y in spec['separators']:
        c.rect(11, y, w - 11, y + 1, well)
        c.rect(11, y + 1, w - 11, y + 2, light)

    for y in spec['bars']:
        x0, x1 = spec['bar_x'], spec['bar_x'] + spec['bar_width']
        c.rect(x0 - 1, y - 1, x1 + 1, y + 11, wellDark)
        c.rect(x0, y, x1, y + 10, well)
        c.rect(x0, y + 9, x1, y + 10, light)
        c.rect(x1 - 1, y, x1, y + 10, light)

    return c


# The panel sheets, named *_TEXTURES so check_generated_assets.py claims textures/gui/ for this file.
GUI_TEXTURES = {name: (lambda s=spec: panel(s)) for name, spec in PANELS.items()}


def main():
    for name, builder in GUI_TEXTURES.items():
        spec = PANELS[name]
        builder().write(os.path.join(OUT, 'gui', name + '.png'))
        print('gui/%s.png  %dx%d on a %d sheet, bars at %s'
              % (name, spec['width'], spec['height'], PANEL_SHEET, spec['bars']))


if __name__ == '__main__':
    main()
