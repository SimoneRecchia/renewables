#!/usr/bin/env python3
"""The Modrinth page a release would send is one Modrinth accepts, checked offline.

    python3 tools/check_modrinth.py      # exit 1 on a problem

A release publishes the README as the project's page and the jar as a version (tools/modrinth.py). A relative link
to a file that moved, a heading review refuses, a gallery image gone from docs/ or a dependency mods.toml gained
without a Modrinth slug only showed once a release was half out; this is the same `problems()` that publish runs.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import modrinth                                                                  # noqa: E402


if __name__ == '__main__':
    sys.exit(modrinth.check(None))
