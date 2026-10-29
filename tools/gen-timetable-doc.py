# Writes docs/TIMETABLE_CSV.md. The prompt block is read from core/src/main/kotlin/efa/TimetableFormat.kt by building the same lines,
# so the document and the app cannot drift (a core test fails if they do). Run from the efa folder: python tools/gen-timetable-doc.py
import re

cols = 'kind,date,end_date,start,end,title,type,venue,heaviness,study,action,notes'
src = open('core/src/main/kotlin/efa/TimetableFormat.kt', encoding='utf-8').read()
tt = open('core/src/main/kotlin/efa/TimetableCsv.kt', encoding='utf-8').read()
types = re.search(r'val EVENT_TYPES = listOf\((.*?)\)', tt).group(1)
types = ', '.join(t.strip().strip('"') for t in types.split(','))

block = src[src.index('val TIMETABLE_AI_PROMPT'):src.index('.joinToString("\\n")')]
lines = re.findall(r'^\s+"(.*)",?\s*$', block, re.M)
prompt = '\n'.join(l.replace('${CSV_COLUMNS.joinToString(",")}', cols).replace('${EVENT_TYPES.joinToString(", ")}', types).replace('\\"', '"') for l in lines)

ex_block = src[src.index('val TIMETABLE_EXAMPLE_CSV'):]
ex_lines = [l for l in re.findall(r'^\s+(CSV_COLUMNS\.joinToString\(","\)|".*"),?\s*$', ex_block, re.M)]
example = '\n'.join(cols if l.startswith('CSV_COLUMNS') else l.strip('"') for l in ex_lines)

doc = f'''# Timetable CSV, version 1

One format, read the same way on every platform (ledger IM1 to IM3). Spark checks the whole file before it changes anything. A file with
any error is rejected as a whole, and each error names the line and the column.

## Getting a file into Spark

- **Open with** Spark from a file manager, or **Share** a file or plain text to Spark from any app (an AI app, email, Drive).
- Or **Setup, Import a timetable file**, then choose the file.
- Spark always shows a preview first: what is added, what is cancelled, the Buffer before and after, and any warnings. Nothing is applied
  until you press Apply. A file that cancels something or takes time from a work block needs the typed wall of 75 characters (IM6).
- Importing the same file twice changes nothing the second time (IM8).

## The columns

Every column must be in the header (any order). Leave a cell empty when it is not used.

| Column | Used by | Meaning |
| --- | --- | --- |
| kind | all | `event`, `deadline` or `period` |
| date | all | `2026-11-02` or `02/11/2026`. For a period, the first day. |
| end_date | period | The last day. A period is 1 to 14 days. |
| start | event, deadline | `09:00`, 24-hour UK time. For a deadline, the time it is due. |
| end | event | `10:00`. After the start, on the same day. |
| title | all | Up to 120 characters. Required for event and deadline. |
| type | event, period | Event: {types}. Period: `assignment_week` or `catch_up_week`. |
| venue | event | The building and room, as on the timetable. Spark matches it to its venue directory (L4). |
| heaviness | deadline | A whole number 1 to 10. Empty means 5 (D2). |
| study | event | `standard`, `light` or `none`. Empty means standard for an added lecture or tutorial, otherwise none (D1). |
| action | event | Empty or `add`, or `cancel`. |
| notes | event | Free text. |

## What a file can and cannot do

- **Add** events, deadlines and periods freely. Adding only tightens.
- **Cancel** a fixed commitment, or add an event that takes time from a study or code block, and Spark asks for the typed wall once for the
  whole file (IM6).
- A file **cannot** create, shorten or remove a study block, a meal, sleep or a day off. Those types are refused. A day off and a library
  period are planned in the app (PW1, H1).
- An event on top of sleep, a meal, a walk or another commitment is an error that names it. To move a lecture, cancel the old row and its
  walk, then add the new rows (the same file).
- A lecture or tutorial that is added brings its study block with it (D1), unless `study` is `none`.
- Rows dated before today are skipped with a warning. Dates more than a year ahead are treated as a typo.
- Up to 1 MB and 2000 rows, UTF-8, commas, semicolons or tabs between cells. Nothing in the file is ever calculated.

## Give this to an AI

Copy the text below, or press **Copy the format for an AI** in Spark's import screen, paste it, then paste your timetable underneath.

```
{prompt}
```

## Example

```
{example}
```

The files used to test every platform are in `core/src/test/resources/csv/`. A `good-` file must be accepted. A `bad-LINE-COLUMN-` file
must be rejected with an error on that line and column (`file` means the whole file).
'''
open('docs/TIMETABLE_CSV.md', 'w', encoding='utf-8', newline='\n').write(doc)
print('docs/TIMETABLE_CSV.md', len(doc), 'chars')
