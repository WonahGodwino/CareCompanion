import sqlite3

con = sqlite3.connect('tmp/care_companion_db_device.sqlite')
cur = con.cursor()

tables = [r[0] for r in cur.execute("select name from sqlite_master where type='table' order by name").fetchall()]
print('has_hiv_status_tracker=', 'hiv_status_tracker' in tables)
print('tables=', tables)

if 'hiv_status_tracker' in tables:
    count = cur.execute('select count(*) from hiv_status_tracker').fetchone()[0]
    print('hiv_status_tracker_count=', count)
    dist = cur.execute('select hiv_status, count(*) from hiv_status_tracker group by hiv_status order by count(*) desc').fetchall()
    print('hiv_status_distribution=', dist)
else:
    cols = [r[1] for r in cur.execute('pragma table_info(patient_person)').fetchall()]
    print('patient_person_columns=', cols)
    if 'currentStatus' in cols:
        dist = cur.execute('select currentStatus, count(*) from patient_person group by currentStatus order by count(*) desc').fetchall()
        print('patient_currentStatus_distribution=', dist)
    else:
        print('patient_currentStatus_distribution=column_not_present')

con.close()
