#!/usr/bin/env python3
"""Run real navigation/event orchestration with deterministic entity ticks.
External menu opening, button recognition, MyPet instance and the server-backed
InventoryType enum are doubles; entity ticks are deterministic.
No server or real TrMenu/MyPet integration is claimed.
"""
import pathlib
import subprocess
import sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import build

root = build.ROOT
out = root / '.work/review-fix1/lifecycle'
out.mkdir(parents=True, exist_ok=True)
cp = build.dependency_classpath(pathlib.Path(sys.argv[1]))
fixtures = []
for fixture in (root / 'tests/lifecycle').glob('*.txt'):
    target = out / (fixture.stem + '.java')
    target.write_text(fixture.read_text())
    fixtures.append(target)
source = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else root / 'src/main/java'
production = [p for p in source.rglob('*.java')
              if p.stem not in ('TrMenuGateway', 'ReturnButton')]
subprocess.run(['javac', '--release', '21', '-encoding', 'UTF-8', '-cp', cp,
                '-d', str(out), *map(str, production + fixtures)], check=True)
run = subprocess.run(['java', '-ea', '-cp', str(out) + ':' + cp,
                'team.nomi.bridge.LifecycleTest'])
sys.exit(run.returncode)
