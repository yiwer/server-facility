"""Independent export oracle, manually run with pinned openpyxl 3.1.5; not a Java build dependency."""
import sys
import openpyxl
assert openpyxl.__version__ == '3.1.5'
with open(sys.argv[1], 'rb') as stream:
    workbook = openpyxl.load_workbook(stream, read_only=True, data_only=False)
    sheet = workbook['Sheet1']
    rows = list(sheet.iter_rows())
    assert [[cell.value for cell in row] for row in rows] == [
        ['=1+2', 'Unicode-界-😀', ''], ['00123', '2024-02-29', '1,234.50']]
    assert all(cell.data_type == 's' for row in rows for cell in row)
    workbook.close()
print('OPENPYXL_EXPORT_PASS version=3.1.5 rows=2 cells=6 formulaLike=string unicode=true')
