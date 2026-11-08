
window.mkHand = (pose, ox=0, oy=0, sc=1) => {
  const P = (x,y)=>({x:x+ox,y:y+oy,z:0});
  const W=[0.5,0.8], mcp={5:[0.47,0.62],9:[0.5,0.6],13:[0.53,0.61],17:[0.56,0.63]};
  const L=Array(21);
  L[0]=P(...W);
  const ext=(b,[x,y])=>{L[b]=P(x,y);L[b+1]=P(x,y-0.06);L[b+2]=P(x,y-0.1);L[b+3]=P(x,y-0.13);};
  const cur=(b,[x,y])=>{L[b]=P(x,y);L[b+1]=P(x,y-0.03);L[b+2]=P(x,y);L[b+3]=P(x,y+0.02);};
  const thumbOut=()=>{L[1]=P(0.45,0.76);L[2]=P(0.42,0.72);L[3]=P(0.36,0.69);L[4]=P(0.31,0.67);};
  const thumbIn=()=>{L[1]=P(0.46,0.76);L[2]=P(0.46,0.71);L[3]=P(0.48,0.68);L[4]=P(0.5,0.67);};
  const f={open:[1,1,1,1,1],point:[0,1,0,0,0],fist:[0,0,0,0,0],two:[0,1,1,0,0],horns:[0,1,0,0,1],three:[0,1,1,1,0],L:[1,1,0,0,0],thumbside:[1,0,0,0,0],thumbup:[1,0,0,0,0]}[pose==='pinch'?'point':pose];
  f[0]?thumbOut():thumbIn();
  [5,9,13,17].forEach((b,i)=> f[i+1]?ext(b,mcp[b]):cur(b,mcp[b]));
  if(pose==='pinch'){ L[4]=P(0.43,0.585); L[8]=P(0.435,0.58); L[7]=P(0.45,0.56); L[6]=P(0.46,0.57); L[3]=P(0.43,0.64); L[2]=P(0.44,0.7);}
  if(pose==='thumbup'){ L[2]=P(0.45,0.66); L[3]=P(0.45,0.55); L[4]=P(0.45,0.45); }
  if (sc !== 1) for (const q of L) { q.x = 0.5+ox + (q.x-0.5-ox)*sc; q.y = 0.7+oy + (q.y-0.7-oy)*sc; }
  return L;
};
