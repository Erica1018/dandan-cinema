"""Check the publishable tree, excluding runtime/build/dependency directories."""
import ast,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SKIP={'.git','.venv','runtime','build','dist','__pycache__'}
required=['README.md','README.zh-CN.md','LICENSE','SECURITY.md','CONTRIBUTING.md','docs/INSTALL.md','docs/INSTALL.zh-CN.md','docs/AGENTS.md','docs/BUILD.md','llms.txt']
errors=[]
for name in required:
    if not (ROOT/name).is_file():errors.append('Missing '+name)
files=[p for p in ROOT.rglob('*') if p.is_file() and not set(p.relative_to(ROOT).parts)&SKIP]
for p in files:
    relative=p.relative_to(ROOT)
    if p.suffix.lower() in {'.jks','.keystore','.apk','.log','.pem','.key'} or p.name in {'library.json','tv-access.json','tv-signing.json'}:errors.append('Private/generated file: '+str(relative));continue
    if p.suffix.lower() in {'.png','.jpg','.mp4'}:continue
    text=p.read_text(encoding='utf-8')
    if p.suffix=='.py':
        try:ast.parse(text)
        except SyntaxError:errors.append('Syntax: '+str(relative))
    patterns=[r'(?i)[A-Z]:[/\\]Users[/\\](?!USERNAME\b)[^/\\\s]+',r'\b(?:ghp|github_pat)_[A-Za-z0-9_]{20,}',r'-----BEGIN [A-Z ]*PRIVATE KEY-----',r'192\.168\.31\.[0-9]+']
    if any(re.search(pattern,text) for pattern in patterns):errors.append('Possible personal information/secret: '+str(relative))
    if p.suffix=='.md':
        for match in re.finditer(r'(?<!!)\[[^\]]*\]\(([^)]+)\)',text):
            target=match.group(1).split('#')[0]
            if target and not re.match(r'[a-zA-Z]+:|#|<',target) and not (p.parent/target).exists():errors.append('Broken link in '+str(relative)+': '+target)
if errors:raise SystemExit('\n'.join(errors))
print(f'Release checks passed for {len(files)} files. No runtime data or recognized secrets in the publishable tree.')
