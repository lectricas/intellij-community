#!/usr/bin/env python3
"""Test typeshed's third party stubs using stubtest"""

from __future__ import annotations

import argparse
import functools
import subprocess
import sys
import tempfile
import venv
from glob import glob
from pathlib import Path
from typing import Any, NoReturn

import tomli


@functools.lru_cache()
def get_mypy_req():
    # Use pre-release stubtest. Keep the following in sync:
    # - get_mypy_req in tests/stubtest_third_party.py
    # - stubtest-stdlib in .github/workflows/daily.yml
    # - stubtest-stdlib in .github/workflows/tests.yml
    return "git+git://github.com/python/mypy@c7a81620bef7585cca6905861bb7ef34ec12da2f"

    with open("requirements-tests.txt") as f:
        return next(line.strip() for line in f if "mypy" in line)


def run_stubtest(dist: Path) -> bool:
    with open(dist / "METADATA.toml") as f:
        metadata = dict(tomli.loads(f.read()))

    if not run_stubtest_for(metadata, dist):
        print(f"Skipping stubtest for {dist.name}\n\n")
        return True

    with tempfile.TemporaryDirectory() as tmp:
        venv_dir = Path(tmp)
        venv.create(venv_dir, with_pip=True, clear=True)

        pip_exe = str(venv_dir / "bin" / "pip")
        python_exe = str(venv_dir / "bin" / "python")

        dist_version = metadata["version"]
        assert isinstance(dist_version, str)
        dist_req = f"{dist.name}=={dist_version}"

        # If @tests/requirements-stubtest.txt exists, run "pip install" on it.
        req_path = dist / "@tests" / "requirements-stubtest.txt"
        if req_path.exists():
            try:
                pip_cmd = [pip_exe, "install", "-r", str(req_path)]
                subprocess.run(pip_cmd, check=True, capture_output=True)
            except subprocess.CalledProcessError as e:
                print(f"Failed to install requirements for {dist.name}", file=sys.stderr)
                print(e.stdout.decode(), file=sys.stderr)
                print(e.stderr.decode(), file=sys.stderr)
                return False

        # We need stubtest to be able to import the package, so install mypy into the venv
        # Hopefully mypy continues to not need too many dependencies
        # TODO: Maybe find a way to cache these in CI
        dists_to_install = [dist_req, get_mypy_req()]
        dists_to_install.extend(metadata.get("requires", []))
        pip_cmd = [pip_exe, "install"] + dists_to_install
        print(" ".join(pip_cmd), file=sys.stderr)
        try:
            subprocess.run(pip_cmd, check=True, capture_output=True)
        except subprocess.CalledProcessError as e:
            print(f"Failed to install {dist.name}", file=sys.stderr)
            print(e.stdout.decode(), file=sys.stderr)
            print(e.stderr.decode(), file=sys.stderr)
            return False

        packages_to_check = [d.name for d in dist.iterdir() if d.is_dir() and d.name.isidentifier()]
        modules_to_check = [d.stem for d in dist.iterdir() if d.is_file() and d.suffix == ".pyi"]
        cmd = [
            python_exe,
            "-m",
            "mypy.stubtest",
            # Use --ignore-missing-stub, because if someone makes a correct addition, they'll need to
            # also make a allowlist change and if someone makes an incorrect addition, they'll run into
            # false negatives.
            "--ignore-missing-stub",
            # Use --custom-typeshed-dir in case we make linked changes to stdlib or _typeshed
            "--custom-typeshed-dir",
            str(dist.parent.parent),
            *packages_to_check,
            *modules_to_check,
        ]
        allowlist_path = dist / "@tests/stubtest_allowlist.txt"
        if allowlist_path.exists():
            cmd.extend(["--allowlist", str(allowlist_path)])

        try:
            print(f"MYPYPATH={dist}", " ".join(cmd), file=sys.stderr)
            subprocess.run(cmd, env={"MYPYPATH": str(dist), "MYPY_FORCE_COLOR": "1"}, check=True)
        except subprocess.CalledProcessError:
            print(f"stubtest failed for {dist.name}", file=sys.stderr)
            print("\n\n", file=sys.stderr)
            if allowlist_path.exists():
                print(
                    f'To fix "unused allowlist" errors, remove the corresponding entries from {allowlist_path}', file=sys.stderr
                )
            else:
                print(f"Re-running stubtest with --generate-allowlist.\nAdd the following to {allowlist_path}:", file=sys.stderr)
                subprocess.run(cmd + ["--generate-allowlist"], env={"MYPYPATH": str(dist)})
                print("\n\n", file=sys.stderr)
            return False
        else:
            print(f"stubtest succeeded for {dist.name}", file=sys.stderr)
        print("\n\n", file=sys.stderr)
    return True


def run_stubtest_for(metadata: dict[str, Any], dist: Path) -> bool:
    return has_py3_stubs(dist) and metadata.get("stubtest", True)


# Keep this in sync with mypy_test.py
def has_py3_stubs(dist: Path) -> bool:
    return len(glob(f"{dist}/*.pyi")) > 0 or len(glob(f"{dist}/[!@]*/__init__.pyi")) > 0


def main() -> NoReturn:
    parser = argparse.ArgumentParser()
    parser.add_argument("--num-shards", type=int, default=1)
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument("dists", metavar="DISTRIBUTION", type=str, nargs=argparse.ZERO_OR_MORE)
    args = parser.parse_args()

    typeshed_dir = Path(".").resolve()
    if len(args.dists) == 0:
        dists = sorted((typeshed_dir / "stubs").iterdir())
    else:
        dists = [typeshed_dir / "stubs" / d for d in args.dists]

    result = 0
    for i, dist in enumerate(dists):
        if i % args.num_shards != args.shard_index:
            continue
        if not run_stubtest(dist):
            result = 1
    sys.exit(result)


if __name__ == "__main__":
    main()
