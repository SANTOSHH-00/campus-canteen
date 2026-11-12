const fs = require('fs');
const envText = fs.readFileSync('.env', 'utf8');
const match = envText.match(/GEMINI_API_KEY=([^\r\n]+)/);
const key = match[1].trim();

const modelsToTest = [
  'gemini-2.5-flash',
  'gemini-3.8-flash',
  'gemini-2.5-flash-lite',
  'gemini-3.5-flash',
  'gemini-3.7-flash',
  'gemini-3.1-pro-preview',
  'gemini-flash-latest',
  'gemini-pro-latest'
];

async function run() {
  for (const m of modelsToTest) {
    const modelPath = m.startsWith('models/') ? m : 'models/' + m;
    const url = 'https://generativelanguage.googleapis.com/v1beta/' + modelPath + ':generateContent?key=' + encodeURIComponent(key);
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 4000);
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: 'Hello' }] }]
        }),
        signal: controller.signal
      });
      clearTimeout(timer);
      const data = await res.json();
      const txt = data.candidates?.[0]?.content?.parts?.[0]?.text;
      console.log(m, '-> HTTP', res.status, txt ? '✓ "' + txt.trim().slice(0, 50) + '"' : (data.error?.message?.slice(0, 100) || 'no output'));
      if (res.status === 200) {
        console.log('>>> WORKING MODEL CONFIRMED:', m);
        break;
      }
    } catch(e) {
      console.log(m, 'error:', e.message);
    }
  }
}
run();
