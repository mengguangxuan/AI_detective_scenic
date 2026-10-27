"""文档阶段的 CasePackage 结构／最小引用校验；不是 Scala 后端实现。"""
from __future__ import annotations

import copy
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / 'docs/backend/case_truth_types.md'
FIXTURE = ROOT / 'docs/fixtures/minimal_investigation.json'


def require(ok: bool, message: str) -> None:
    if not ok:
        raise ValueError(message)


def split_types(value: str) -> list[str]:
    depth, start, result = 0, 0, []
    for index, char in enumerate(value):
        depth += (char == '[') - (char == ']')
        if char == ',' and depth == 0:
            result.append(value[start:index].strip())
            start = index + 1
    result.append(value[start:].strip())
    return [item for item in result if item]


def fields(value: str) -> dict[str, str]:
    return {name.strip(): kind.strip() for item in split_types(value)
            for name, kind in [item.split(':', 1)]}


def schema() -> tuple[dict, dict]:
    source = '\n'.join(re.findall(r'```scala\n(.*?)```', CONTRACT.read_text(), re.S))
    classes = {name: fields(body) for name, body in re.findall(
        r'final case class (\w+)\((.*?)\)', source, re.S)}
    enums = {}
    for match in re.finditer(r'^enum (\w+):\n((?:[ \t].*\n|\n)*)', source, re.M):
        variants = {}
        for entry in re.finditer(r'case\s+(\w+)\((.*?)\)|case\s+([\w, ]+)', match[2], re.S):
            if entry[1]:
                variants[entry[1]] = fields(entry[2])
            else:
                for name in entry[3].split(','):
                    variants[name.strip()] = {}
        enums[match[1]] = variants
    return classes, enums


def validate_shape(value: object, kind: str, path: str, classes: dict, enums: dict) -> None:
    def check(item: object, expected: str, suffix: str) -> None:
        validate_shape(item, expected, path + suffix, classes, enums)

    generic = re.fullmatch(r'(\w+)\[(.*)\]', kind, re.S)
    if generic:
        container, args = generic[1], split_types(generic[2])
        if container == 'Option':
            if value is not None:
                check(value, args[0], '')
        elif container in ('Vector', 'Set', 'NonEmptyVector'):
            require(isinstance(value, list), f'{path}: expected array')
            if container == 'NonEmptyVector':
                require(bool(value), f'{path}: empty NonEmptyVector')
            if container == 'Set':
                require(len({json.dumps(x, sort_keys=True) for x in value}) == len(value), f'{path}: duplicate set member')
            for i, item in enumerate(value):
                check(item, args[0], f'[{i}]')
        elif container == 'Map':
            require(isinstance(value, dict), f'{path}: expected map')
            for key, item in value.items():
                check(key, args[0], '.key')
                check(item, args[1], f'.{key}')
        else:
            raise ValueError(f'{path}: unsupported container {container}')
        return
    if kind in ('String', 'URI', 'Instant', 'OffsetDateTime'):
        require(isinstance(value, str) and bool(value.strip()), f'{path}: expected nonempty string')
        return
    if kind in ('Int', 'Long', 'Double'):
        require(type(value) in ((int, float) if kind == 'Double' else (int,)) and math.isfinite(value), f'{path}: invalid number')
        return
    if kind == 'Boolean':
        require(type(value) is bool, f'{path}: expected boolean')
        return
    # ID / Text use the scalar JSON representation recorded in the API draft.
    if kind.endswith('Id') or kind == 'Text':
        check(value, 'String', '')
        return
    if kind in enums:
        variants = enums[kind]
        if all(not fs for fs in variants.values()):
            require(isinstance(value, str) and value in variants, f'{path}: invalid {kind}')
            return
        require(isinstance(value, dict) and value.get('kind') in variants, f'{path}: invalid tagged {kind}')
        expected = variants[value['kind']]
        require(set(value) == {'kind', *expected}, f'{path}: wrong variant fields')
        for name, field_kind in expected.items():
            check(value[name], field_kind, '.' + name)
        return
    require(kind in classes, f'{path}: unknown contract type {kind}')
    expected = classes[kind]
    require(isinstance(value, dict) and set(value) == set(expected), f'{path}: wrong fields for {kind}')
    for name, field_kind in expected.items():
        check(value[name], field_kind, '.' + name)


def index(items: list[dict]) -> dict:
    result = {item['id']: item for item in items}
    require(len(result) == len(items), 'duplicate ID')
    return result


def validate(case: dict) -> None:
    classes, enums = schema()
    validate_shape(case, 'CasePackage', '$', classes, enums)
    world, truth, scene, investigation = (case[x] for x in ('world', 'truth', 'scene', 'investigation'))
    places, entities, nodes, points, assets, facts, topics, clues, routes, operations, sources = (
        index(items) for items in (world['places'], world['entities'], scene['nodes'], scene['points'], case['assets'],
                                  truth['facts'], truth['topics'], investigation['clues'], investigation['routes'],
                                  investigation['operations'], truth['sources']))
    # This stage supports one explicit investigation-only profile, never accepts
    # unsupported features by silently ignoring them.
    require(investigation['assessment'] == {'kind': 'InvestigationOnly'}, 'only investigation fixture supported')
    for group in ('questions', 'options', 'rules', 'scheduled'):
        require(not investigation[group], f'unsupported fixture feature: {group}')
    for group in ('persons', 'privateContexts', 'motives', 'events', 'incidents', 'claims'):
        require(not truth[group], f'unsupported fixture truth: {group}')
    require(not world['connections'] and not world['relationships'] and not world['environments'] and not world['scenes'], 'unsupported fixture world features')
    require(not scene['requirementBindings'] and not scene['cues'], 'fixture is not scene publication approval')
    require(case['revision']['value'] > 0 and case['schemaVersion']['value'] == 2, 'invalid fixture version')
    require(scene['movement']['maximumStepMeters'] > 0, 'invalid movement policy')
    for place in places.values():
        require(place['parent'] is None and place['walkable'], 'fixture requires root walkable place')
        for bounds in place['walkable']:
            require(all(bounds['min'][axis] < bounds['max'][axis] for axis in 'xyz'), 'invalid walkable bounds')
    for entity in entities.values():
        require(entity['initialPlace'] in places and entity['owner'] is None, 'invalid entity location / owner')
    for node in nodes.values():
        require(node['place'] in places and node['asset'] in assets, 'invalid node reference')
        require(assets[node['asset']]['kind'] == 'Model', 'node asset must be Model')
        require(node['represents'] is None or node['represents'] in entities, 'unknown represented entity')
        require(all(x > 0 for x in node['transform']['scale'].values()), 'invalid scale')
        seen, cursor = set(), node
        while cursor['parent'] is not None:
            require(cursor['id'] not in seen, 'cyclic node parent chain')
            seen.add(cursor['id'])
            require(cursor['parent'] in nodes, 'missing parent')
            parent = nodes[cursor['parent']]
            require(parent['place'] == node['place'], 'cross-place parent')
            cursor = parent
    for topic in topics.values():
        require(topic['subject']['kind'] == 'Entity' and topic['subject']['id'] in entities, 'unsupported / missing topic subject')
        require(topic['when']['earliest']['seconds'] <= topic['when']['latest']['seconds'], 'invalid topic time')
    require(len({f['topic'] for f in facts.values()}) == len(facts), 'multiple facts for one topic')
    for fact in facts.values():
        require(fact['topic'] in topics and not fact['requiresOccurred'], 'invalid fact topic / event')
        require(set(fact['sources']) <= sources.keys(), 'missing fact source')
        require(fact['value']['kind'] == 'AuthoredDetail', 'fixture only supports authored fact')
    for clue in clues.values():
        require(set(clue['facts']) <= facts.keys(), 'missing clue fact')
        require(not clue['relatedClaims'] and not clue['suggests'] and not clue['result']['media'], 'unsupported clue references')
    for point in points.values():
        require(point['subject']['kind'] == 'Entity' and point['subject']['id'] in entities, 'invalid point subject')
        require(point['entry']['kind'] == 'OnNode' and point['entry']['node'] in nodes and point['entry']['radiusMeters'] > 0, 'invalid fixture entry')
        require(set(point['topics']) <= topics.keys(), 'missing point topic')
    for route in routes.values():
        require(route['point'] in points and route['method']['kind'] == 'Fixed', 'invalid fixed route')
        require(route['grants'] and set(route['grants']) <= clues.keys() and not route['reports'], 'invalid route grants')
        condition = route['condition']
        require(condition['kind'] == 'OperationDone' and condition['operation'] in operations, 'unsupported / missing route condition')
    for operation in operations.values():
        require(operation['point'] in points and operation['condition'] == {'kind': 'All', 'items': []}, 'invalid operation')
        for effect in operation['effects']:
            require(effect['kind'] == 'World', 'unsupported operation effect')
            effect = effect['effect']
            require(effect['kind'] in ('Open', 'SetNodeVisible'), 'unsupported world effect')
            require(effect.get('entity', effect.get('node')) in (entities if effect['kind'] == 'Open' else nodes), 'missing effect target')
    initial = investigation['initial']
    require(initial['spawnPlace'] in places, 'missing spawn place')
    pos = initial['spawnPosition']
    require(any(all(b['min'][a] <= pos[a] <= b['max'][a] for a in 'xyz') for b in places[initial['spawnPlace']]['walkable']), 'spawn outside walkable')
    require(set(initial['opened']) <= entities.keys() and set(initial['unlocked']) <= entities.keys(), 'invalid initial entity')
    require(not initial['occurredEvents'] and not initial['npcs'], 'unsupported initial state')
    bg = case['publicInfo']['background']
    require(set(bg['commonKnowledge']) <= facts.keys(), 'missing public fact')
    require(not bg['persons'] and not bg['openingReports'] and not bg['openingMaterials'] and not case['publicInfo']['reports'], 'unsupported public references')


def check_scenario(case: dict) -> None:
    """纯内存契约实验；不声称已实现生产并发、数据库或 API。"""
    nodes = index(case['scene']['nodes'])
    operation = case['investigation']['operations'][0]
    route = case['investigation']['routes'][0]
    clue = case['investigation']['clues'][0]
    points = index(case['scene']['points'])
    state = dict(sequence=0, visible={n['id'] for n in nodes.values() if n['initiallyVisible']},
                 opened=set(), completed=set(), clues=set(), facts=set())
    initial = copy.deepcopy(state)

    def accessible(current: dict, point: dict) -> bool:
        cursor = nodes[point['entry']['node']]
        while True:
            if cursor['id'] not in current['visible']:
                return False
            if cursor['parent'] is None:
                return cursor['place'] == case['investigation']['initial']['spawnPlace']
            cursor = nodes[cursor['parent']]

    def apply(current: dict, changes: list[dict]) -> dict:
        result = copy.deepcopy(current)
        for change in changes:
            kind = change['kind']
            if kind == 'WorldChanged':
                for effect in change['effects']:
                    if effect['kind'] == 'Open':
                        result['opened'].add(effect['entity'])
                    elif effect['kind'] == 'SetNodeVisible':
                        if effect['visible']:
                            result['visible'].add(effect['node'])
                        else:
                            result['visible'].discard(effect['node'])
                    else:
                        raise ValueError('unsupported replay effect')
            elif kind == 'OperationCompleted':
                result['completed'].add(change['operation'])
            elif kind == 'Acquired':
                information = change['record']['information']
                result['facts'].update(information['facts'])
                result['clues'].update(c['clue'] for c in information['clues'])
            elif kind != 'Investigated':
                raise ValueError('unsupported replay change')
        result['sequence'] += 1
        return result

    require(accessible(state, points['point_drawer']), 'closed drawer shell must remain accessible')
    require(not accessible(state, points[route['point']]), 'hidden ancestor must block direct inquiry')
    open_changes = [dict(kind='WorldChanged', effects=[e['effect'] for e in operation['effects']]),
                    dict(kind='OperationCompleted', operation=operation['id'])]
    state = apply(state, open_changes)
    require(accessible(state, points[route['point']]) and operation['id'] in state['completed'], 'open must reveal route')
    investigation = dict(id='record_read', point=route['point'], playerAction='阅读信件',
                         route=route['id'], operation=None, result=clue['result'], grantedClues=[clue['id']])
    acquisition = dict(id='record_learn', recipient='Player',
                       sources=[dict(kind='SessionRecord', record='record_read')],
                       information=dict(facts=clue['facts'], clues=[dict(clue=clue['id'], noticed=clue['finding'],
                                        interpretation='读取原文，不额外推断', confirmedFacts=clue['facts'])],
                                        privateContexts=[], heardClaims=[], reports=[]), account='通过固定检查读取信件')
    # Observer is a mixed enum; Player must use the same tagged-object encoding.
    acquisition['recipient'] = dict(kind='Player')
    read_changes = [dict(kind='Investigated', record=investigation), dict(kind='Acquired', record=acquisition)]
    classes, enums = schema()
    for change in open_changes + read_changes:
        validate_shape(change, 'SessionChange', '$scenario', classes, enums)
    state = apply(state, read_changes)
    require(set(clue['facts']) <= state['facts'] and clue['id'] in state['clues'], 'Acquired must update knowledge')
    replayed = apply(apply(initial, open_changes), read_changes)
    require(replayed == state, 'replay must restore identical state')
    print('PASS: hidden-parent access, open/reveal, typed Investigated + Acquired, deterministic replay')


def main() -> None:
    case = json.loads(FIXTURE.read_text())
    validate(case)
    print('PASS: complete CasePackage shape and supported fixture references')
    check_scenario(case)
    mutations = [
        ('empty clue facts', lambda c: c['investigation']['clues'][0].update(facts=[])),
        ('missing fact', lambda c: c['investigation']['clues'][0].update(facts=['missing'])),
        ('parent cycle', lambda c: c['scene']['nodes'][0].update(parent='node_letter')),
        ('unsupported condition', lambda c: c['investigation']['routes'][0].update(condition={'kind': 'All', 'items': []})),
        ('unsupported scored package', lambda c: c['investigation'].update(assessment={'kind': 'Scored', 'requiredIncidents': [], 'solution': []})),
        ('missing effect target', lambda c: c['investigation']['operations'][0]['effects'][0]['effect'].update(entity='missing')),
    ]
    for name, mutate in mutations:
        invalid = copy.deepcopy(case)
        mutate(invalid)
        try:
            validate(invalid)
        except ValueError:
            print('PASS: rejects ' + name)
        else:
            raise AssertionError('accepted invalid fixture: ' + name)


if __name__ == '__main__':
    main()
