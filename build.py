#!/usr/bin/env python3
"""Compile against local, unmodified dependencies; never download or vendor them."""

import argparse
import hashlib
import os
import pathlib
import shutil
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent
VERSION = '1.1.1'


def dependency_classpath(directory):
    """Select the required Purpur API and existing optional/API libraries."""
    jars = sorted(directory.rglob('*.jar'))
    purpur_api = [
        jar for jar in jars
        if 'purpur-api-26.3' in jar.name and '2654-stable' in jar.name
    ]
    if len(purpur_api) != 1:
        raise ValueError('exactly one Purpur 26.3 build 2654 stable API is required')

    # Exclude older Bukkit APIs and unrelated plugin implementations.
    libraries = [
        jar for jar in jars
        if 'purpur-api-' not in jar.name
        and ('libraries' in jar.parts or 'MyPet' in jar.name or 'Vault' in jar.name)
    ]
    return os.pathsep.join(map(str, purpur_api + libraries))


def compile_sources(source_directory, destination, classpath):
    sources = sorted(source_directory.rglob('*.java'))
    subprocess.run([
        'javac', '--release', '21', '-encoding', 'UTF-8',
        '-cp', classpath, '-d', str(destination), *map(str, sources),
    ], check=True)


def build(classpath):
    output = ROOT / f'build/MenuReturnBridge-{VERSION}.jar'
    output.unlink(missing_ok=True)
    classes = ROOT / 'build/classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    compile_sources(ROOT / 'src/main/java', classes, classpath)

    tests = ROOT / '.work/tests'
    tests.mkdir(parents=True, exist_ok=True)
    test_classpath = str(classes) + os.pathsep + classpath
    compile_sources(ROOT / 'tests', tests, test_classpath)
    subprocess.run([
        'java', '-ea', '-cp', str(tests) + os.pathsep + test_classpath,
        'team.nomi.bridge.NavigationTest',
    ], check=True)

    # Only production classes and resources enter the artifact, never dependencies/tests.
    subprocess.run([
        'jar', '--create', '--date=2026-01-01T00:00:00Z', '--file', str(output),
        '-C', str(classes), '.', '-C', str(ROOT / 'src/main/resources'), '.',
    ], check=True)
    print(output.name + ' SHA-256 ' + hashlib.sha256(output.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--deps-dir', default=os.environ.get('NOMI_DEPS_DIR'))
    arguments = parser.parse_args()
    if not arguments.deps_dir:
        parser.error(
            'supply --deps-dir or NOMI_DEPS_DIR containing Purpur 26.3, MyPet 4.0.4 '
            'and API dependency jars'
        )
    try:
        classpath = dependency_classpath(pathlib.Path(arguments.deps_dir))
    except ValueError as exception:
        parser.error(str(exception))
    build(classpath)


if __name__ == '__main__':
    main()
