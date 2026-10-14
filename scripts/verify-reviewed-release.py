"""Compare the reviewed signed APK's payload with an independently checked build."""
import argparse
from pathlib import Path
from zipfile import ZipFile


VCS_METADATA = 'META-INF/version-control-info.textproto'


def is_signature(name: str) -> bool:
    return name.startswith('META-INF/') and name.count('/') == 1 and (
        name == 'META-INF/MANIFEST.MF' or name.endswith(('.SF', '.RSA', '.DSA', '.EC'))
    )


def verify(unsigned_path: Path, signed_path: Path) -> None:
    with ZipFile(unsigned_path) as unsigned, ZipFile(signed_path) as signed:
        if unsigned.testzip() is not None or signed.testzip() is not None:
            raise ValueError('APK ZIP integrity check failed')
        unsigned_names = [n for n in unsigned.namelist() if n != VCS_METADATA and not is_signature(n)]
        signed_names = [n for n in signed.namelist() if n != VCS_METADATA and not is_signature(n)]
        if len(unsigned_names) != len(set(unsigned_names)) or len(signed_names) != len(set(signed_names)):
            raise ValueError('Duplicate APK payload entries')
        if set(unsigned_names) != set(signed_names):
            raise ValueError('APK payload entry names differ')
        different = [n for n in unsigned_names if unsigned.read(n) != signed.read(n)]
        if different:
            raise ValueError(f'APK payload differs: {different}')
    print(f'Verified {len(unsigned_names)} identical app payload entries; only signatures/VCS metadata excluded')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('unsigned_apk', type=Path)
    parser.add_argument('signed_apk', type=Path)
    arguments = parser.parse_args()
    verify(arguments.unsigned_apk, arguments.signed_apk)
