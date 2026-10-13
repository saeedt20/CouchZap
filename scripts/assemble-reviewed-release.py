"""Reconstruct one reviewed, externally signed APK without exposing its private key."""
import argparse
import hashlib
import json
from pathlib import Path


def assemble(recipe_path: Path, unsigned_path: Path, output_path: Path) -> None:
    recipe = json.loads(recipe_path.read_text())
    unsigned = unsigned_path.read_bytes()
    if hashlib.sha256(unsigned).hexdigest() != recipe['unsigned_sha256']:
        raise ValueError('Unsigned APK does not match the reviewed build')
    patch_name = recipe['patch_file']
    if Path(patch_name).name != patch_name:
        raise ValueError('Signature data must be a sibling file')
    sources = {'unsigned': unsigned, 'signature': (recipe_path.parent / patch_name).read_bytes()}
    pieces = []
    for segment in recipe['segments']:
        source = sources[segment['source']]
        offset, length = segment['offset'], segment['length']
        if not isinstance(offset, int) or not isinstance(length, int) or not 0 <= offset <= offset + length <= len(source):
            raise ValueError('Invalid release segment bounds')
        pieces.append(source[offset:offset + length])
    signed = b''.join(pieces)
    if hashlib.sha256(signed).hexdigest() != recipe['signed_sha256']:
        raise ValueError('Reconstructed APK checksum mismatch')
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(signed)
    print(f'Verified release APK: {output_path.name}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recipe', type=Path)
    parser.add_argument('unsigned_apk', type=Path)
    parser.add_argument('output_apk', type=Path)
    arguments = parser.parse_args()
    assemble(arguments.recipe, arguments.unsigned_apk, arguments.output_apk)
