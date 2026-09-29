#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    echo "Usage: $0 GROUP_NUMBER [MANUAL_PDF]" >&2
    exit 2
fi

name="group-$1"
root="$(cd "$(dirname "$0")/.." && pwd)"
manual="${2:-$root/manual/user-manual.pdf}"
out="${root:?}/dist"

echo "Running the tests and building the jar"
# clean 
(cd "$root" && mvn -q clean package)
jar="$(ls "$root"/target/spl-compiler-*.jar | head -n 1)"

version="$(unzip -p "$jar" za/ac/up/cos341/Main.class | od -An -j7 -N1 -tu1 | tr -d ' ')"
if [ "$version" != "52" ]; then
    echo "The classes need a Java newer than 8 (class file version $version). Check maven.compiler.release in pom.xml." >&2
    exit 1
fi

rm -rf "$out"
mkdir -p "$out"
cp "$jar" "$out/$name.jar"

echo "Checking the jar on tests/lexer/valid_03_function.txt"
check="$(mktemp -d)"
cp "$root/tests/lexer/valid_03_function.txt" "$check/SPL.txt"
(cd "$check" && java -jar "$out/$name.jar")
if [ ! -f "$check/tree.xml" ]; then
    echo "The jar did not write tree.xml." >&2
    exit 1
fi
rm -rf "$check"
echo "Jar: dist/$name.jar"

if [ ! -f "$manual" ]; then
    echo "No zip was made because the manual $manual does not exist." >&2
    exit 0
fi
cp "$manual" "$out/$name.pdf"
if command -v zip > /dev/null; then
    (cd "$out" && zip -q "$name.zip" "$name.jar" "$name.pdf")
else
    (cd "$out" && python3 -m zipfile -c "$name.zip" "$name.jar" "$name.pdf")
fi
echo "Upload file: dist/$name.zip"