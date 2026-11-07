from pathlib import Path
import sqlite3
import unicodedata

bank = Path(__file__).resolve().parents[1] / "app/src/main/assets/bank.db"
db = sqlite3.connect(f"{bank.as_uri()}?mode=ro", uri=True)
assert db.execute("PRAGMA integrity_check").fetchone() == ("ok",)
assert not db.execute("PRAGMA foreign_key_check").fetchall()
rows = db.execute("SELECT id,about,correct_answer,option_a,option_b,option_c,option_d FROM questions_flat").fetchall()
def normalized(text):
    return "".join(c for c in unicodedata.normalize("NFKC", text).casefold() if c.isalnum())
assert len(rows) == len(set(r[0] for r in rows)) == 400
for ident, category, key, *options in rows:
    assert key in ("A", "B", "C", "D"), ident
    assert len(set(map(normalized, options))) == 4 and all(map(normalized, options)), ident
for size, quotas in [(25,(17,3,5)), (50,(35,5,10))]:
    for _ in range(100):
        ids = []
        for category, count in zip(("huquq","kasbiy ta'lim","pedagogika"), quotas):
            draw = db.execute("SELECT id FROM questions_flat WHERE about=? ORDER BY random() LIMIT ?",(category,count)).fetchall()
            assert len(draw) == count
            ids.extend(draw)
        assert len(set(ids)) == size
print("400 keys, option uniqueness, integrity and 200 random sessions passed")
