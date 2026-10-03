"""Independent CPython csv.writer fixture, seed 0x15C5. Not required at test runtime."""
import base64
import csv
import hashlib
import pathlib
import random

root = pathlib.Path(__file__).resolve().parent
rng = random.Random(0x15C5)
alphabet = ['a', 'b', ',', '"', '\r', '\n', ' ', '中', '😀', '\t']
rows = [['plain', 'a,b', 'x"y', 'last\r\nline', ''], [''], [], [' lead', 'trail ']]
for _ in range(160):
    rows.append([''.join(rng.choice(alphabet) for _ in range(rng.randrange(25))) for _ in range(1 + rng.randrange(6))])
with (root / 'python-golden.csv').open('w', encoding='utf-8-sig', newline='') as out:
    csv.writer(out, dialect='excel', lineterminator='\r\n').writerows(rows)
with (root / 'python-golden.expected').open('w', encoding='ascii', newline='\n') as out:
    for row in rows:
        out.write(','.join(base64.b64encode(cell.encode('utf-8')).decode('ascii') for cell in (row or [''])) + '\n')
for name in ['python-golden.csv', 'python-golden.expected']:
    print(name, hashlib.sha256((root / name).read_bytes()).hexdigest())
