import gzip, glob, os
for f in sorted(glob.glob(r"D:\Documents\deepseek-harness\ponko-craftfriend\.test-server\logs\*.log*")):
    try:
        text = gzip.open(f, "rt", encoding="utf-8", errors="ignore").read() if f.endswith(".gz") else open(f, "r", encoding="utf-8", errors="ignore").read()
    except Exception:
        continue
    for line in text.splitlines():
        low = line.lower()
        if "issued server command" in low or ("model set" in low and "ysm" in low):
            print(os.path.basename(f), "|", line[:400])
