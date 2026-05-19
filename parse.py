import json
d=json.load(open('BENCHMARK_RESULTS.json'))
for b in d:
  s=b['primaryMetric']['score']
  u=b['primaryMetric']['scoreUnit']
  if u == 'ms/op': s = s * 1000000; u = 'ns/op'
  name=b['benchmark'].split('.')[-1]
  print(f'{name:<30} {b["mode"]:<6} {b["threads"]:<4} {s:<15.3f} {u}')
