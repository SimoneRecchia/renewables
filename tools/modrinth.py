#!/usr/bin/env python3
"""The mod on Modrinth: its page, its versions and its review, made to match this repository.

    python3 tools/modrinth.py check                         # offline: what would be sent, against Modrinth's own rules
    python3 tools/modrinth.py status                        # the project as Modrinth has it, and what review would say
    python3 tools/modrinth.py project [--create]            # the page: text, links, licence, icon, gallery, disclosure
    python3 tools/modrinth.py version JAR [--changelog F]   # a built jar, as the version its mods.toml says it is
    python3 tools/modrinth.py submit                        # the draft, to the moderators

Everything but `check` takes the token from MODRINTH_TOKEN. Nothing here is a copy: the project id, name, summary,
licence and Minecraft version are gradle.properties', the dependencies mods.toml's, the page README.md's (less what
sits between modrinth_exclude markers, links made absolute), the icon mods.toml's logo. What is Modrinth's alone is the
tables below. publish-package.yml runs `project` and `version` on every release, so a page edited on the site is
overwritten by the next one.
"""

import argparse
import difflib
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = os.environ.get('MODRINTH_API', 'https://api.modrinth.com').rstrip('/')
REPO = 'SimoneRecchia/renewables'
BRANCH = 'main'
USER_AGENT = '%s (tools/modrinth.py; +https://github.com/%s)' % (REPO, REPO)

ICON = 'src/main/resources/assets/renewables/icon.png'
MODS_TOML = 'src/main/resources/META-INF/mods.toml'

# Featured categories are at most three; Modrinth flags more than eight in all.
CATEGORIES = ['technology']
ADDITIONAL_CATEGORIES = []

LINKS = {
    'issues_url': 'https://github.com/%s/issues' % REPO,
    'source_url': 'https://github.com/%s' % REPO,
    'wiki_url': 'https://github.com/%s/tree/%s/docs' % (REPO, BRANCH),
}

# Every modId mods.toml depends on, as a Modrinth slug; forge and minecraft are the loader and the game.
DEPENDENCIES = {'mekanism': 'mekanism', 'computercraft': 'cc-tweaked'}

# The first is featured. Modrinth names an image by its sha1, so a redrawn file replaces the one of the same title;
# an image on the page that is not listed here is reported and left alone.
GALLERY = [
    ('docs/screenshots/showcase-c90.png', 'C90-3.0 wind turbine',
     'A 3 MW turbine with a 90 m rotor on the tallest tower it is certified for, eleven segments, its nacelle '
     'yawed into the wind.'),
    ('docs/screenshots/showcase-arrays.png', 'Every solar array',
     'The six array products, three of each. In front: the FT-415 and FT-430 flat tables and the TR-580 fixed rack. '
     'Behind: the TR-530 fixed rack, the HX-700 single-axis tracker and the AE-440 dual-axis pedestal.'),
    ('docs/screenshots/showcase-items-chest.png', 'Everything in the mod',
     "All 77 items at once, in the creative tab's order: the machines, the conductors and cables, and the parts "
     'they are crafted from.'),
    ('docs/screenshots/showcase-items-1.png', 'The creative tab',
     'The wrench, the three line conductors, transformers, the pole and the lattice towers, the cabin and the Power '
     'Box, six turbines, six arrays, DC cables, combiners, inverters and switchgear.'),
    ('docs/screenshots/showcase-items-2.png', 'The creative tab, continued',
     'The parts every machine is crafted from: steel, glass, silicon and copper, then cells, diodes, connectors and '
     'boards, up to blades, gearboxes, generators and tower sections.'),
    ('docs/screenshots/20-hv-line-to-substation.png', 'A 400 kV line',
     'A substation transformer sends 400 kV out on three bushings to lattice towers strung with the quad bundle.'),
    ('docs/screenshots/19-lattice-tension-tower.png', 'Tension tower',
     'Each phase is held by a dead-end string either side of the arm, and the jumpers loop round the tower.'),
    ('docs/screenshots/09-pole-insulators-and-conductors.png', 'Utility pole',
     'Eight pin insulators on two crossarms, carrying bare medium-voltage conductors and an aerial bundled cable.'),
    ('docs/screenshots/12-transformers.png', 'Transformers',
     'A machine transformer, and a substation transformer with its conservator and porcelain bushings.'),
    ('docs/screenshots/24-night-lantern.png', 'An inverter at night',
     'A central inverter and a Power Box beside a pole line.'),
]

# Modrinth's content rules, 6.1: the disclosure is required when a substantial part of the code, any asset or the
# page itself is a product of generative AI. The commit history says this project is.
DISCLOSURE = {
    'type': 'ai_content',
    'uses': ['code', 'assets', 'text'],
    'note': "Developed with an AI coding assistant, under the author's direction and review: most of the Java and "
            'Python, the scripts that draw the models and textures, the documentation and this page.',
}

# Modrinth's own limits (labrinth's validators), so `check` fails where review would.
SUMMARY_CHARS = (25, 255)
BODY_CHARS = (125, 65536)
HEADER_CHARS = 80
ICON_BYTES = 256 * 1024
GALLERY_BYTES = 5 * 1024 * 1024
SLUG = re.compile(r'^[\w!@$()`.+,"\-\']{3,64}$')
VERSION = re.compile(r'^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$')


def path(relative):
    return os.path.join(ROOT, relative)


def text(relative):
    with open(path(relative), encoding='utf-8') as f:
        return f.read()


def data(relative):
    with open(path(relative), 'rb') as f:
        return f.read()


def properties():
    out = {}
    for line in text('gradle.properties').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            out[key.strip()] = value.strip()
    return out


def dependencies():
    """mods.toml's dependencies other than the loader and the game, as (modId, mandatory)."""
    out = []
    for block in text(MODS_TOML).split('[[dependencies.')[1:]:
        mod = re.search(r'modId\s*=\s*"([^"]+)"', block).group(1)
        if mod not in ('forge', 'minecraft'):
            out.append((mod, re.search(r'mandatory\s*=\s*(\w+)', block).group(1) == 'true'))
    return out


def channel(version):
    # release.yml marks the same three as a GitHub pre-release
    if '-alpha' in version:
        return 'alpha'
    if '-beta' in version or '-rc' in version:
        return 'beta'
    return 'release'


EXCLUDED = re.compile(r'<!-- modrinth_exclude\.start -->.*?<!-- modrinth_exclude\.end -->\n?', re.S)
LINK = re.compile(r'(!?)\[([^\]]*)\]\(([^)\s]+)\)')


def body():
    """README.md as Modrinth shows it: the page has its own title, and a relative link would resolve on modrinth.com."""
    markdown = re.sub(r'\A# [^\n]*\n+', '', EXCLUDED.sub('', text('README.md')))

    def absolute(match):
        image, label, target = match.groups()
        if re.match(r'[a-z]+:|#', target):
            return match.group(0)
        relative, _, anchor = target.partition('#')
        if image:
            url = 'https://raw.githubusercontent.com/%s/%s/%s' % (REPO, BRANCH, relative)
        else:
            kind = 'tree' if os.path.isdir(path(relative)) else 'blob'
            url = 'https://github.com/%s/%s/%s/%s' % (REPO, kind, BRANCH, relative) + ('#' + anchor if anchor else '')
        return '%s[%s](%s)' % (image, label, url)

    return re.sub(r'\n{3,}', '\n\n', LINK.sub(absolute, markdown)).strip() + '\n'


def page():
    """The fields of the project a PATCH sets, as the repository says they are."""
    props = properties()
    return dict(
        title=props['mod_name'],
        description=props['mod_description'],
        body=body(),
        categories=CATEGORIES,
        additional_categories=ADDITIONAL_CATEGORIES,
        license_id=props['mod_license'],
        **LINKS,
    )


def headings(markdown):
    """(level, text, line) of every ATX heading outside a code fence."""
    out, fenced = [], False
    for number, line in enumerate(markdown.splitlines()):
        if line.lstrip().startswith('```'):
            fenced = not fenced
        elif not fenced:
            match = re.match(r'(#{1,6})\s+(.*?)\s*#*\s*$', line)
            if match:
                out.append((len(match.group(1)), match.group(2), number))
    return out


def problems():
    """What Modrinth would refuse, or its review would stop on, found without asking it."""
    props, fields, out = properties(), page(), []

    if not os.path.isfile(path('LICENSE')):
        out.append('LICENSE is missing: the page states %s and links nothing' % props.get('mod_license'))
    if not re.fullmatch(r'[A-Za-z0-9.+-]+', props.get('mod_license', '')):
        out.append('mod_license "%s" is not an SPDX id' % props.get('mod_license'))
    project = props.get('modrinth_project', '')
    if not SLUG.match(project):
        out.append('modrinth_project "%s" is neither a slug nor an id' % project)
    if not VERSION.match(props['mod_version']):
        out.append('mod_version "%s" is not a version release.yml would tag' % props['mod_version'])

    title, summary, markdown = fields['title'], fields['description'], fields['body']
    if not 3 <= len(title) <= 64:
        out.append('title "%s" is not 3 to 64 characters' % title)
    if not SUMMARY_CHARS[0] <= len(summary) <= SUMMARY_CHARS[1]:
        out.append('summary is %d characters, not %d to %d' % ((len(summary),) + SUMMARY_CHARS))
    if difflib.SequenceMatcher(None, title.lower(), summary.lower()).ratio() >= 0.8:
        out.append('the summary only repeats the title')
    if not BODY_CHARS[0] <= len(markdown) <= BODY_CHARS[1]:
        out.append('the page is %d characters, not %d to %d' % ((len(markdown),) + BODY_CHARS))

    heads = headings(markdown)
    lines = markdown.splitlines()
    for level, heading, _ in heads:
        if len(heading) > HEADER_CHARS:
            out.append('heading longer than %d characters: "%s"' % (HEADER_CHARS, heading))
    for (level, heading, line), (next_level, _, next_line) in zip(heads, heads[1:]):
        if level == next_level and not ''.join(lines[line + 1:next_line]).strip():
            out.append('heading "%s" is followed by another of its level with nothing between' % heading)
    if heads and not ''.join(lines[heads[-1][2] + 1:]).strip():
        out.append('the page ends with a heading')

    for _, _, target in LINK.findall(EXCLUDED.sub('', text('README.md'))):
        relative = target.partition('#')[0]
        if relative and not re.match(r'[a-z]+:', target) and not os.path.exists(path(relative)):
            out.append('README.md links %s, which is not in the repository' % relative)
    for image, label, target in LINK.findall(markdown):
        url = urllib.parse.urlparse(target)
        if target.startswith('#'):
            continue
        if url.scheme not in ('http', 'https') or not url.netloc:
            out.append('link "%s" does not resolve off GitHub' % target)
        elif not image and ('/releases/download/' in url.path or '/archive/refs/' in url.path
                            or (url.netloc == 'github.com' and '/raw/' in url.path)):
            out.append('link "%s" is a direct download, which Modrinth refuses' % target)
        if image and not label.strip():
            out.append('image "%s" has no alt text' % target)

    for field, url in LINKS.items():
        if not url.startswith('https://'):
            out.append('%s is not https' % field)
    if len(set(LINKS.values())) != len(LINKS):
        out.append('two link fields name the same URL, which review refuses')
    if len(CATEGORIES) > 3:
        out.append('more than three featured categories')
    if len(CATEGORIES) + len(ADDITIONAL_CATEGORIES) > 8:
        out.append('more than eight categories in all')

    for mod, _ in dependencies():
        if mod not in DEPENDENCIES:
            out.append('mods.toml depends on "%s", which DEPENDENCIES has no Modrinth slug for' % mod)

    if not os.path.isfile(path(ICON)):
        out.append('%s is missing' % ICON)
    elif len(data(ICON)) > ICON_BYTES:
        out.append('the icon is over %d KiB' % (ICON_BYTES // 1024))
    for image, image_title, description in GALLERY:
        if not os.path.isfile(path(image)):
            out.append('gallery image %s is missing' % image)
        elif len(data(image)) > GALLERY_BYTES:
            out.append('gallery image %s is over %d MiB' % (image, GALLERY_BYTES // 1024 // 1024))
        elif not image.endswith('.png'):
            out.append('gallery image %s is not a PNG' % image)
    titles = [image_title for _, image_title, _ in GALLERY]
    if len(set(titles)) != len(titles):
        out.append('two gallery images share a title, which is how a redrawn one is told apart')
    if not DISCLOSURE['note'].strip():
        out.append('the AI disclosure has no note')
    return out


# --- the API -------------------------------------------------------------------------------------------------------

def token():
    value = os.environ.get('MODRINTH_TOKEN', '').strip()
    if not value:
        sys.exit('MODRINTH_TOKEN is not set')
    return value


def call(method, route, payload=None, content_type=None, query=None, missing=False):
    url = API + route
    if query:
        url += '?' + urllib.parse.urlencode({k: str(v).lower() if isinstance(v, bool) else v for k, v in query.items()})
    headers = {'Authorization': token(), 'User-Agent': USER_AGENT}
    if payload is not None and content_type is None:
        payload, content_type = json.dumps(payload).encode('utf-8'), 'application/json'
    if content_type:
        headers['Content-Type'] = content_type
    request = urllib.request.Request(url, data=payload, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            raw = response.read()
    except urllib.error.HTTPError as error:
        if error.code == 404 and missing:
            return None
        detail = error.read().decode('utf-8', 'replace')
        hint = ''
        if error.code in (401, 403):
            hint = '\n(a token without the scope this needs, or a project that is not this account\'s)'
        sys.exit('%s %s: HTTP %d\n%s%s' % (method, route, error.code, detail, hint))
    try:
        return json.loads(raw) if raw.strip() else None
    except ValueError:
        sys.exit('%s %s: not JSON, is MODRINTH_API (%s) the API?\n%s' % (method, route, API, raw[:200]))


def multipart(parts):
    """(name, filename or None, content type, bytes) in order: Modrinth wants `data` before any file."""
    boundary = uuid.uuid4().hex
    out = []
    for name, filename, content_type, content in parts:
        disposition = 'form-data; name="%s"' % name + ('; filename="%s"' % filename if filename else '')
        out += [('--%s\r\nContent-Disposition: %s\r\nContent-Type: %s\r\n\r\n'
                 % (boundary, disposition, content_type)).encode('utf-8'), content, b'\r\n']
    out.append(('--%s--\r\n' % boundary).encode('utf-8'))
    return b''.join(out), 'multipart/form-data; boundary=%s' % boundary


def sha1(content):
    return hashlib.sha1(content).hexdigest()


def same(have, want):
    if isinstance(want, list):
        return sorted(have or []) == sorted(want)
    if isinstance(want, str):
        return (have or '').strip() == want.strip()
    return have == want


def stored_hash(url):
    # labrinth stores an image as <sha1>.<ext>, or <sha1>_<width>.<ext> once it has made a smaller copy
    return os.path.basename(urllib.parse.urlparse(url or '').path).split('.')[0].split('_')[0]


def fetch():
    ident = properties()['modrinth_project']
    return call('GET', '/v2/project/%s' % urllib.parse.quote(ident), missing=True)


def refuse(found):
    if found:
        sys.exit('nothing sent:\n  ' + '\n  '.join(found))


# --- the commands --------------------------------------------------------------------------------------------------

def check(_):
    found = problems()
    fields = page()
    print('title       %s' % fields['title'])
    print('summary     %s' % fields['description'])
    print('licence     %s' % fields['license_id'])
    print('categories  %s' % ', '.join(CATEGORIES + ADDITIONAL_CATEGORIES))
    print('page        %d characters from README.md, %d headings' % (len(fields['body']), len(headings(fields['body']))))
    print('gallery     %d images' % len(GALLERY))
    print('depends on  %s' % ', '.join('%s (%s)' % (DEPENDENCIES.get(mod, '?'), 'required' if need else 'optional')
                                       for mod, need in dependencies()))
    for problem in found:
        print('PROBLEM     ' + problem)
    if found:
        return 1
    print('the Modrinth page is what Modrinth accepts')
    return 0


def status(_):
    current = fetch()
    if current is None:
        print('no project "%s" that this token can see' % properties()['modrinth_project'])
        return 1
    print('%s (%s, slug %s): %s' % (current['title'], current['id'], current['slug'], current['status']))
    print('versions    %d' % len(current.get('versions') or []))
    # review's own check, which only a project not yet public has a use for
    if current['status'] not in ('draft', 'rejected', 'withheld'):
        return 0
    for nag in call('GET', '/v3/project/%s/validate' % current['id'])['nags']:
        print('%-11s %s %s' % (nag['severity'], nag['kind'], json.dumps(nag.get('details') or {})))
    return 0


def project(args):
    refuse(problems())
    want = page()
    current = fetch()
    if current is None:
        if not args.create:
            sys.exit('no project "%s" that this token can see; `project --create` makes it, as a draft'
                     % properties()['modrinth_project'])
        current = create(want)
    else:
        have = dict(current, license_id=(current.get('license') or {}).get('id'))
        changed = {key: value for key, value in want.items() if not same(have.get(key), value)}
        if changed:
            call('PATCH', '/v2/project/%s' % current['id'], changed)
            print('page: set %s' % ', '.join(sorted(changed)))
        else:
            print('page: as the repository says')
        icon(current)
    gallery(current)
    disclosure(current['id'])
    return 0


def create(want):
    props = properties()
    fields = dict(want, slug=props['modrinth_project'], project_type='mod', client_side='required',
                  server_side='required', is_draft=True, initial_versions=[])
    payload, content_type = multipart([
        ('data', None, 'application/json', json.dumps(fields).encode('utf-8')),
        ('icon', 'icon.png', 'image/png', data(ICON)),
    ])
    created = call('POST', '/v2/project', payload, content_type)
    print('created %s as a draft, at https://modrinth.com/mod/%s' % (created['id'], created['slug']))
    print('::notice::set modrinth_project=%s in gradle.properties: the slug can be renamed, the id cannot'
          % created['id'])
    return call('GET', '/v2/project/%s' % created['id'])


def icon(current):
    content = data(ICON)
    if stored_hash(current.get('icon_url')) == sha1(content):
        print('icon: current')
        return
    call('PATCH', '/v2/project/%s/icon' % current['id'], content, 'image/png', {'ext': 'png'})
    print('icon: uploaded')


def gallery(current):
    route = '/v2/project/%s/gallery' % current['id']
    have = list(current.get('gallery') or [])
    kept, sent = set(), 0
    for order, (image, title, description) in enumerate(GALLERY):
        content = data(image)
        want = {'featured': order == 0, 'title': title, 'description': description, 'ordering': order}
        found = next((item for item in have if stored_hash(item['url']) == sha1(content)), None)
        if found:
            kept.add(found['url'])
            if any(found.get(key) != value for key, value in want.items()):
                call('PATCH', route, query=dict(want, url=found['url']))
                print('gallery: updated "%s"' % title)
                sent += 1
            continue
        for stale in [item for item in have if item.get('title') == title and item['url'] not in kept]:
            call('DELETE', route, query={'url': stale['url']})
            have.remove(stale)
            print('gallery: removed the old "%s"' % title)
        call('POST', route, content, 'image/png', dict(want, ext='png'))
        print('gallery: uploaded "%s"' % title)
        sent += 1
    if not sent:
        print('gallery: %d images, current' % len(GALLERY))
    for item in have:
        if item['url'] not in kept and item.get('title') not in {title for _, title, _ in GALLERY}:
            print('gallery: "%s" is not in GALLERY, left alone (%s)' % (item.get('title'), item['url']))


def disclosure(project_id):
    route = '/v3/project/%s/disclosures' % project_id
    have = next((item for item in call('GET', route)['disclosures']
                 if item.get('type') == DISCLOSURE['type'] and not item.get('deleted_at')), None)
    if have and set(have.get('uses') or []) == set(DISCLOSURE['uses']) and have.get('note') == DISCLOSURE['note']:
        print('disclosure: current')
        return
    if have and have.get('lock_status') == 'fully_locked':
        print('disclosure: locked by a moderator, left as they set it')
        return
    call('PATCH', route, {'set': [DISCLOSURE], 'remove': []})
    print('disclosure: set')


def version(args):
    props = properties()
    with zipfile.ZipFile(args.jar) as jar:
        declared = re.search(r'^version\s*=\s*"([^"]+)"', jar.read('META-INF/mods.toml').decode('utf-8'), re.M).group(1)
    number = args.version or declared
    if number != declared:
        sys.exit('the jar says it is %s, not %s: Forge\'s update check compares the two' % (declared, number))
    current = fetch()
    if current is None:
        sys.exit('no project "%s" that this token can see; run `project --create` first' % props['modrinth_project'])

    existing = call('GET', '/v2/project/%s/version/%s' % (current['id'], urllib.parse.quote(number)), missing=True)
    if existing:
        print('%s is already on Modrinth (%s), left as it is' % (number, existing['id']))
        return 0

    requires = []
    for mod, mandatory in dependencies():
        depended = call('GET', '/v2/project/%s' % DEPENDENCIES[mod])
        requires.append({'project_id': depended['id'], 'dependency_type': 'required' if mandatory else 'optional'})
    changelog = open(args.changelog, encoding='utf-8').read().strip() if args.changelog else ''
    kind = channel(number)
    fields = {
        'project_id': current['id'],
        'name': '%s %s' % (props['mod_name'], number),
        'version_number': number,
        'changelog': changelog or 'No changelog.',
        'dependencies': requires,
        'game_versions': [props['minecraft_version']],
        'loaders': ['forge'],
        'environment': 'client_and_server',
        'version_type': kind,
        'featured': kind == 'release',
        'status': 'listed',
        'file_parts': ['jar'],
        'primary_file': 'jar',
    }
    payload, content_type = multipart([
        ('data', None, 'application/json', json.dumps(fields).encode('utf-8')),
        ('jar', os.path.basename(args.jar), 'application/java-archive', data(os.path.abspath(args.jar))),
    ])
    created = call('POST', '/v2/version', payload, content_type)
    print('published %s (%s) as %s: https://modrinth.com/mod/%s/version/%s'
          % (number, created['id'], kind, current['slug'], created['id']))
    return 0


def submit(_):
    current = fetch()
    if current is None:
        sys.exit('no project "%s" that this token can see' % properties()['modrinth_project'])
    if current['status'] not in ('draft', 'rejected', 'withheld'):
        print('%s is %s, nothing to submit' % (current['title'], current['status']))
        return 0
    nags = call('GET', '/v3/project/%s/validate' % current['id'])['nags']
    for nag in nags:
        print('%-11s %s %s' % (nag['severity'], nag['kind'], json.dumps(nag.get('details') or {})))
    if any(nag['severity'] == 'required' for nag in nags):
        sys.exit('not submitted: review would refuse it as it is')
    call('PATCH', '/v2/project/%s' % current['id'], {'status': 'processing'})
    print('submitted: https://modrinth.com/mod/%s is with the moderators' % current['slug'])
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('check').set_defaults(run=check)
    commands.add_parser('status').set_defaults(run=status)
    sync = commands.add_parser('project')
    sync.add_argument('--create', action='store_true', help='make the project, as a draft, if there is none')
    sync.set_defaults(run=project)
    upload = commands.add_parser('version')
    upload.add_argument('jar')
    upload.add_argument('--version', help='refuse the upload unless the jar says it is this version')
    upload.add_argument('--changelog', help='a Markdown file')
    upload.set_defaults(run=version)
    commands.add_parser('submit').set_defaults(run=submit)
    args = parser.parse_args()
    return args.run(args)


if __name__ == '__main__':
    sys.exit(main())
