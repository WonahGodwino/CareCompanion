import sqlite3

db = 'C:/CareCompanion/cc_db.sqlite'
con = sqlite3.connect(db)
cur = con.cursor()

cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
print('Tables:', cur.fetchall())

try:
    cur.execute('SELECT COUNT(*) FROM patients')
    print('Patient count:', cur.fetchone()[0])
    print()
    print('Sample patients:')
    for r in cur.execute('SELECT id, first_name, surname, facility_id, is_active FROM patients LIMIT 10'):
        print(r)
except Exception as e:
    print('Error querying patients:', e)

try:
    cur.execute("SELECT * FROM sync_log ORDER BY sync_date DESC LIMIT 5")
    print('Sync log:', cur.fetchall())
except Exception as e:
    print('Error querying sync_log:', e)

try:
    cur.execute('SELECT COUNT(*) FROM biometrics')
    print('Biometric count:', cur.fetchone()[0])
except Exception as e:
    print('Error querying biometrics:', e)

con.close()
