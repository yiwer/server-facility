"""Independent, frozen file-format fixtures. CPython 3.14, xlwt 1.3.0, XlsxWriter 3.2.9.
Run only when intentionally regenerating fixtures; Java tests do not need Python.
"""
from pathlib import Path
from datetime import datetime
import hashlib
import json
import xlwt
import xlsxwriter

root = Path(__file__).parent
legacy = xlwt.Workbook()
sheet = legacy.add_sheet('independent')
sheet.write(0, 0, '历史 XLS / Unicode 😀')
sheet.write(0, 2, 1234.5, xlwt.easyxf(num_format_str='#,##0.00'))
sheet.write(2, 0, datetime(2024, 2, 29), xlwt.easyxf(num_format_str='yyyy-mm-dd'))
sheet.write(2, 1, '=1+2')
legacy.save(str(root / 'historical-xlwt-1.3.0.xls'))

for epoch in (False, True):
    path = root / ('xlsxwriter-1904.xlsx' if epoch else 'xlsxwriter-1900.xlsx')
    workbook = xlsxwriter.Workbook(path, {'date_1904': epoch})
    workbook.set_properties({'created': datetime(2020, 1, 1)})
    sheet = workbook.add_worksheet('independent')
    sheet.write_string(0, 0, 'XLSX / Unicode 😀')
    sheet.write_number(0, 2, 1234.5, workbook.add_format({'num_format': '#,##0.00'}))
    sheet.write_datetime(2, 0, datetime(2024, 2, 29), workbook.add_format({'num_format': 'yyyy-mm-dd'}))
    sheet.write_string(2, 1, '=1+2')
    sheet.write_formula(3, 0, '=UNSUPPORTED_FACILITY_FUNCTION()', None, 17)
    sheet.write_formula(3, 1, '=1+2', None, 3)
    sheet.write_string(4, 0, 'long-' + '界' * 1024)
    workbook.close()

manifest = {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in sorted(root.glob('*.xls*'))}
(root / 'sha256.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(json.dumps(manifest, indent=2))
