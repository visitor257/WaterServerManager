import pathlib
p = pathlib.Path(r"D:\Documents\deepseek-harness\ponko-craftfriend\.test-server\config\yes_steve_model\custom\DS鲸鱼娘.ysm")
data = p.read_bytes()
print("len", len(data))
for sig in (b"PK\x03\x04", b"ysm.json", b"YSGP"):
    idx = data.find(sig)
    print(sig, idx)
print(data[:200])
