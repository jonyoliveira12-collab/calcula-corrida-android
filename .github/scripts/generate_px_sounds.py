"""Original short radio-control effects, generated without third-party recordings."""
import math,random,wave,struct
from pathlib import Path
rate=24000
out=Path(__file__).resolve().parents[2]/'app/src/main/res/raw'
out.mkdir(parents=True,exist_ok=True)
def radio(length,seed,closing=False):
 rng=random.Random(seed);last=low=high=0.;values=[]
 for i in range(int(rate*length)):
  t=i/rate;x=rng.uniform(-1,1);high=.92*(high+x-last);last=x;low+=.42*(high-low)
  envelope=min(1,t/.0018)*math.exp(-t/(.017 if closing else .013))
  click=math.sin(2*math.pi*1250*t)*math.exp(-t/.0025)
  values.append(.65*low*envelope+.12*click)
 return values
def digital(close=False):
 values=[]
 for frequency,length in ([(1350,.045),(0,.018),(1050,.055)]if close else[(1700,.035),(0,.016),(2100,.045)]):
  for i in range(int(rate*length)):
   t=i/rate;env=min(1,t/.003,(length-t)/.005);values.append(.18*env*math.sin(2*math.pi*frequency*t))
 return values
def save(name,values):
 assert max(map(abs,values))<.9
 with wave.open(str(out/(name+'.wav')),'wb')as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes(b''.join(struct.pack('<h',round(x*32767))for x in values))
save('px_open',radio(.065,102));save('px_close',radio(.085,227,True));save('px_digital_open',digital());save('px_digital_close',digital(True))
