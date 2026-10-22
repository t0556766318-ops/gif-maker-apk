"use strict";

const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const source = path.join(repoRoot, "app", "src", "main", "assets");
const destination = path.join(__dirname, "web");
const htmlFile = path.join(destination, "index.html");

if (!fs.existsSync(path.join(source, "index.html"))) {
  console.error("Could not find the original app assets at " + source);
  process.exit(1);
}

fs.rmSync(destination, { recursive: true, force: true });
fs.cpSync(source, destination, { recursive: true });

let html = fs.readFileSync(htmlFile, "utf8");

function replaceExactlyOnce(search, replacement, label) {
  const first = html.indexOf(search);
  if (first < 0 || html.indexOf(search, first + search.length) >= 0) {
    console.error("Expected exactly one occurrence for Windows patch: " + label);
    process.exit(1);
  }
  html = html.slice(0, first) + replacement + html.slice(first + search.length);
}

// The Android build lists video items in its native gallery. Provide an equivalent
// explicit local-video picker in the Windows desktop edition.
replaceExactlyOnce(
  '<button id="gbtn" class="big" type="button">📷 גלריה</button>',
  '<button id="gbtn" class="big" type="button">📷 בחירת תמונות</button><button id="gif-style-video" class="ghost" style="width:100%;margin:0 0 10px" type="button">🎬 יצירת GIF מסרטון</button>',
  "visible Windows video picker button"
);

// On Windows, the file picker supplies the local video. Use its metadata to
// initialize the same trim controls that the Android gallery initializes.
replaceExactlyOnce(
  "$('vf').onchange=e=>{const f=e.target.files[0];e.target.value='';if(!f)return;$('vp').src=URL.createObjectURL(f);vshow(1)};",
  "$('vf').onchange=e=>{const f=e.target.files&&e.target.files[0];e.target.value='';if(!f)return;const v=$('vp');if(v._gifStyleUrl)URL.revokeObjectURL(v._gifStyleUrl);v._gifStyleUrl=URL.createObjectURL(f);v.onloadedmetadata=()=>{if(!Number.isFinite(v.duration)||!v.videoWidth)return;vdu=Math.round(v.duration*1000);const sec=Math.max(1,Math.floor(v.duration));$('vst').max=sec;$('vst').value=0;$('vdur').max=Math.max(1,Math.min(8,sec));$('vdur').value=Math.min(4,+$('vdur').max);$('vdv').textContent=$('vdur').value;$('vfv').textContent=$('vfps').value};v.src=v._gifStyleUrl;v.load();vshow(1)};",
  "video file picker"
);

// Extract each frame with the browser's built-in video decoder and a local canvas.
// This avoids packaging the large FFmpeg executable and works without network.
const helper = `function gifStyleDesktopFrame(video,seconds){return new Promise((resolve,reject)=>{if(!video.videoWidth||!video.videoHeight)return reject(new Error('הסרטון עדיין לא נטען'));const target=Math.min(Math.max(0,seconds),Math.max(0,video.duration-0.04));const capture=()=>{try{const scale=Math.min(1,480/Math.max(video.videoWidth,video.videoHeight));const c=document.createElement('canvas');c.width=Math.max(2,Math.round(video.videoWidth*scale));c.height=Math.max(2,Math.round(video.videoHeight*scale));const x=c.getContext('2d');if(!x)throw new Error('לא ניתן ליצור תמונה מהסרטון');x.drawImage(video,0,0,c.width,c.height);resolve(c.toDataURL('image/jpeg',0.85))}catch(e){reject(e)}};if(video.readyState>=2&&Math.abs(video.currentTime-target)<0.015){capture();return}let timer;const cleanup=()=>{video.removeEventListener('seeked',onSeeked);if(timer)clearTimeout(timer)};const onSeeked=()=>{cleanup();capture()};video.addEventListener('seeked',onSeeked,{once:true});timer=setTimeout(()=>{cleanup();reject(new Error('לא ניתן לקרוא פריים בזמן המבוקש'))},10000);try{video.currentTime=target}catch(e){cleanup();reject(e)}})}`;

replaceExactlyOnce(
  "$('vgo').onclick=async()=>",
  helper + ";$('gif-style-video').onclick=()=>$('vf').click();$('vgo').onclick=async()=>",
  "local video-frame helper insertion"
);

replaceExactlyOnce(
  "AndroidBridge.videoFrame(String(Math.round((st+k/fps)*1000)))",
  "await gifStyleDesktopFrame($('vp'),st+k/fps)",
  "local canvas frame extraction"
);

fs.writeFileSync(htmlFile, html, "utf8");
console.log("Staged GIF Style with local assets and browser-native offline video frame extraction (no FFmpeg binary).");
