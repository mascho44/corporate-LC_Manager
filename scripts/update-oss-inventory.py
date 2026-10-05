#!/usr/bin/env python3
"""Generate the public runtime inventory from Maven's resolved list and local POMs.

First: mvn dependency:list -DincludeScope=runtime -DoutputFile=/tmp/lc-dependencies.txt
Then: python3 scripts/update-oss-inventory.py /tmp/lc-dependencies.txt
Uses apply_patch for the checked-in inventory; never downloads or executes dependencies.
"""
import json
import re
import subprocess
import sys
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
ROOT = Path(__file__).resolve().parents[1]
CACHE = Path.home() / '.m2/repository'


def metadata(group, artifact, version, seen=None):
    seen = set() if seen is None else seen
    coordinate = (group, artifact, version)
    if coordinate in seen:
        return {}, [], {}
    seen.add(coordinate)
    path = CACHE / group.replace('.', '/') / artifact / version / f'{artifact}-{version}.pom'
    tree = ET.parse(path).getroot()
    for node in tree.iter():
        node.tag = '{http://maven.apache.org/POM/4.0.0}' + node.tag.split('}')[-1]
    text = lambda node, key: node.findtext('m:' + key, '', NS).strip()
    props, parent_licenses, parent_info = {}, [], {}
    parent = tree.find('m:parent', NS)
    if parent is not None:
        props, parent_licenses, parent_info = metadata(text(parent, 'groupId'), text(parent, 'artifactId'), text(parent, 'version'), seen)
    props = dict(props)
    properties = tree.find('m:properties', NS)
    if properties is not None:
        props.update({child.tag.split('}')[-1]: (child.text or '').strip() for child in properties})
    props.update({'project.groupId': group, 'project.artifactId': artifact, 'project.version': version})

    def resolve(value):
        for _ in range(10):
            replaced = re.sub(r'\$\{([^}]+)\}', lambda m: props.get(m[1], m[0]), value)
            if replaced == value:
                break
            value = replaced
        return value

    licenses = [{'name': resolve(text(node, 'name')), 'url': resolve(text(node, 'url'))}
                for node in tree.findall('m:licenses/m:license', NS)] or parent_licenses
    info = {'name': resolve(text(tree, 'name')) or artifact,
            'url': resolve(text(tree, 'url')) or parent_info.get('url', '')}
    return props, licenses, info


lines = Path(sys.argv[1]).read_text()
lines = re.sub(r'\x1b\[[0-9;]*m', '', lines)
components = []
for match in re.finditer(r'^\s+([^:\s]+):([^:\s]+):jar:([^:\s]+):(compile|runtime)\b', lines, re.M):
    group, artifact, version, scope = match.groups()
    _, licenses, info = metadata(group, artifact, version)
    if not licenses or any('${' in license['name'] + license['url'] for license in licenses):
        raise SystemExit(f'Unresolved license for {group}:{artifact}:{version}; review before publishing.')
    components.append({'name': info['name'], 'coordinate': f'{group}:{artifact}',
                       'version': version, 'scope': scope, 'projectUrl': info['url'],
                       'licenses': licenses,
                       'metadataUrl': f'https://repo.maven.apache.org/maven2/{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}.pom'})
if not components:
    raise SystemExit('No Maven runtime dependencies found.')
components.sort(key=lambda item: item['coordinate'])
content = json.dumps({'applicationVersion': '0.5.0-SNAPSHOT',
                      'source': 'Resolved Maven runtime dependencies; licenses from published artifact POMs (including parent POMs).',
                      'components': components}, ensure_ascii=False, indent=2) + '\n'
path = ROOT / 'src/main/resources/static/oss-components.json'
if path.exists():
    old = path.read_text()
    patch = f'*** Begin Patch\n*** Update File: {path}\n@@\n' + ''.join('-' + line + '\n' for line in old.splitlines()) + ''.join('+' + line + '\n' for line in content.splitlines()) + '*** End Patch\n'
else:
    patch = f'*** Begin Patch\n*** Add File: {path}\n' + ''.join('+' + line + '\n' for line in content.splitlines()) + '*** End Patch\n'
subprocess.run(['apply_patch'], input=patch, text=True, check=True)
notices = {}
for component in components:
    group, artifact = component['coordinate'].split(':')
    jar = CACHE / group.replace('.', '/') / artifact / component['version'] / f'{artifact}-{component["version"]}.jar'
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not name.endswith('/') and re.search(r'(?:^|/)(?:license|notice|copying)[^/]*$', name, re.I):
                body = archive.read(name).decode('utf-8', errors='replace').strip()
                notices.setdefault(body, []).append(f'{component["coordinate"]}:{component["version"]} — {name}')
notice_content = 'Third-party license and copyright notices\nExtracted verbatim from the resolved runtime JARs. Each block lists its source artifacts.\n\n'
for body, sources in notices.items():
    notice_content += '=' * 72 + '\n' + '\n'.join(sources) + '\n\n' + body + '\n\n'
notice_path = ROOT / 'src/main/resources/static/oss-notices.txt'
operation = 'Update' if notice_path.exists() else 'Add'
notice_patch = f'*** Begin Patch\n*** {operation} File: {notice_path}\n'
if notice_path.exists():
    notice_patch += '@@\n' + ''.join('-' + line + '\n' for line in notice_path.read_text().splitlines())
notice_patch += ''.join('+' + line + '\n' for line in notice_content.splitlines()) + '*** End Patch\n'
subprocess.run(['apply_patch'], input=notice_patch, text=True, check=True)
print(f'{len(components)} runtime components inventoried.')
