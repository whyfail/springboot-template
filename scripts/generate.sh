#!/usr/bin/env bash
# Generate a runnable project from this template by substituting the placeholders:
#   {{ package }}     full Java package (e.g. com.mycompany.myapp)
#   {{ packagePath }} the same package as a directory path (com/mycompany/myapp)
#   {{ name }}        project name (artifact id, image name, service name)
#
# Usage: scripts/generate.sh -n my-app -p com.mycompany.myapp -d /path/to/dest
set -euo pipefail

name="springboot-template"
package="com.example.app"
dest=""

while getopts "n:p:d:h" opt; do
  case "$opt" in
    n) name="$OPTARG" ;;
    p) package="$OPTARG" ;;
    d) dest="$OPTARG" ;;
    h) echo "Usage: $0 -n <project-name> -p <java-package> -d <destination-dir>"; exit 0 ;;
    *) echo "Usage: $0 -n <project-name> -p <java-package> -d <destination-dir>"; exit 1 ;;
  esac
done

if [[ -z "$dest" ]]; then
  echo "Destination (-d) is required"; exit 1
fi
if [[ ! "$package" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "Invalid Java package: $package (expected e.g. com.mycompany.myapp)"; exit 1
fi
if [[ -e "$dest" ]]; then
  echo "Destination already exists: $dest"; exit 1
fi

src="$(cd "$(dirname "$0")/.." && pwd)"
package_path="$(printf '%s' "$package" | tr '.' '/')"

echo "Generating $name (package $package) into $dest"
mkdir -p "$dest"
(cd "$src" && tar cf - --exclude './.git' --exclude './target' --exclude './node_modules' .) | (cd "$dest" && tar xf -)

python3 - "$dest" "$name" "$package" "$package_path" <<'PY'
import shutil
import sys
from pathlib import Path

dest, name, package, package_path = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
root = Path(dest)

# 1) rename the placeholder package directory
for base in ("src/main/java", "src/test/java"):
    placeholder = root / base / "{{ packagePath }}"
    if placeholder.is_dir():
        target = root / base / package_path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(placeholder), str(target))

# 2) substitute placeholders in every text file
replacements = {
    "{{ packagePath }}": package_path,
    "{{ package }}": package,
    "{{ name }}": name,
}
count = 0
for path in root.rglob("*"):
    if not path.is_file():
        continue
    try:
        text = path.read_text()
    except UnicodeDecodeError:
        continue
    new = text
    for token, value in replacements.items():
        new = new.replace(token, value)
    if new != text:
        path.write_text(new)
        count += 1
print(f"substituted placeholders in {count} files")
PY

echo "Done. Next steps:"
echo "  cd $dest"
echo "  cp .env.example .env   # fill in passwords"
echo "  ./mvnw clean verify"
